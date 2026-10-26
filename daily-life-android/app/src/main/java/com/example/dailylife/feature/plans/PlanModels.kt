package com.example.dailylife.feature.plans

import com.example.dailylife.data.local.*
import com.example.dailylife.core.PlanRules
import org.json.JSONObject
import java.time.*

data class PlanForm(val kind: String, val id: String = newId(), val revision: Long = 0, val payload: String = "{}") {
    fun text(key: String, default: String = "") = JSONObject(payload).optString(key, default)
    fun set(key: String, value: String) = copy(payload = JSONObject(payload).put(key,value).toString())
    fun json() = JSONObject().put("version",1).put("kind",kind).put("id",id).put("revision",revision).put("data",JSONObject(payload)).toString()
    companion object {
        fun parse(json: String): PlanForm { val o=JSONObject(json); require(o.getInt("version")==1)
            return PlanForm(o.getString("kind"),o.getString("id"),o.getLong("revision"),o.getJSONObject("data").toString()) }
    }
}
data class TaskRow(val task: Task, val occurrence: Occurrence? = null) {
    val key get() = occurrence?.id ?: task.id
    val title get() = occurrence?.title ?: task.title
    val date get() = occurrence?.scheduledDate ?: task.plannedDate
    val due get() = if (occurrence != null && task.dueDate != null) LocalDate.parse(occurrence.scheduledDate)
        .plusDays(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(task.plannedDate ?: task.dueDate),LocalDate.parse(task.dueDate))).toString() else task.dueDate
    val status get() = occurrence?.status ?: task.status
}
data class PlansSnapshot(
    val tasks: List<Task> = emptyList(), val subtasks: List<Subtask> = emptyList(), val groups: List<TaskGroup> = emptyList(),
    val events: List<PlanEvent> = emptyList(), val rules: List<RepeatRule> = emptyList(), val occurrences: List<Occurrence> = emptyList(),
    val habits: List<Habit> = emptyList(), val habitRules: List<HabitRuleVersion> = emptyList(), val logs: List<HabitLog> = emptyList(),
    val pauses: List<HabitPause> = emptyList(), val sessions: List<FocusSession> = emptyList(), val segments: List<FocusSegment> = emptyList(),
    val goals: List<Goal> = emptyList(), val progress: List<GoalProgress> = emptyList(), val goalLinks: List<GoalSourceLink> = emptyList(),
    val milestones: List<Milestone> = emptyList(), val drafts: List<PlanDraft> = emptyList(), val reminders: List<PlanReminder> = emptyList()
) {
    fun taskRows() = tasks.filter { it.deletedAt == null && it.archivedAt == null }.flatMap { t ->
        if (rules.any { it.entityType=="task" && it.entityId==t.id })
            occurrences.filter { it.entityType=="task" && it.entityId==t.id }.map { TaskRow(t,it) }
        else listOf(TaskRow(t))
    }
    fun todayTasks(date: LocalDate = LocalDate.now()) = taskRows().filter { it.status in setOf("todo","doing") && (it.date==date.toString() || it.due==date.toString()) }
    fun habitRule(id: String, date: String) = habitRules.filter { it.habitId==id && it.effectiveFrom<=date }.maxByOrNull { it.effectiveFrom }
    fun habitTotal(id: String, date: String) = logs.filter { it.habitId==id && it.logDate==date }.sumOf { it.valueMilli }
    fun habitScheduled(h: Habit, date: String): Boolean {
        if (date<h.startDate || h.endDate?.let { date>it }==true || pauses.any { it.habitId==h.id && date>=it.startDate && (it.endDate==null || date<it.endDate) }) return false
        val r=habitRule(h.id,date) ?: return false
        return r.scheduleType!="weekdays" || LocalDate.parse(date).dayOfWeek.value.toString() in r.weekdays.split(",")
    }
    fun weekDays(h: Habit, day: LocalDate): Int {
        val start=day.with(DayOfWeek.MONDAY)
        return (0L..6L).count { n -> val d=start.plusDays(n).toString(); val r=habitRule(h.id,d)
            r!=null && habitScheduled(h,d) && habitTotal(h.id,d)>=r.targetMilli }
    }
    fun habitStreak(h:Habit,day:LocalDate):Int {
        val r=habitRule(h.id,day.toString()) ?: return 0
        var cursor=day;var count=0;var first=true
        if(r.scheduleType=="weekly") {
            cursor=cursor.with(DayOfWeek.MONDAY)
            while(cursor.plusDays(6).toString()>=h.startDate && count<10000) {
                val rule=habitRule(h.id,cursor.plusDays(6).toString()) ?: break
                val eligible=(0L..6L).any {habitScheduled(h,cursor.plusDays(it).toString())}
                if(eligible) {
                    if(weekDays(h,cursor)>=rule.weeklyDays) count++ else if(!first) break
                }
                first=false;cursor=cursor.minusWeeks(1)
            }
        } else {
            var inspected=0
            while(cursor.toString()>=h.startDate && inspected++<100000) {
                val rule=habitRule(h.id,cursor.toString())
                if(rule!=null && habitScheduled(h,cursor.toString())) {
                    if(habitTotal(h.id,cursor.toString())>=rule.targetMilli) count++ else if(!first) break
                }
                first=false;cursor=cursor.minusDays(1)
            }
        }
        return count
    }
    fun focusSeconds(id: String) = segments.filter { it.sessionId==id && it.phase=="work" }.sumOf { it.seconds }
    fun focusRange(ids:Set<String>,start:LocalDate,endExclusive:LocalDate):Long {
        val zone=ZoneId.systemDefault()
        val from=start.atStartOfDay(zone).toInstant().toEpochMilli();val to=endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return segments.filter {it.sessionId in ids && it.phase=="work"}.sumOf {
            (minOf(it.startAt+it.seconds*1000,to)-maxOf(it.startAt,from)).coerceAtLeast(0)/1000
        }
    }
    fun goalValue(g: Goal): Pair<Long,Long> = when(g.progressSource) {
        "tasks" -> { val linked=goalLinks.filter { it.goalId==g.id && it.sourceType=="task" }.map { it.sourceId }.toSet()
            val sources=tasks.filter { it.id in linked && it.deletedAt==null }
            (sources.count { it.status=="done" }*1000L) to (sources.size*1000L) }
        "milestones" -> { val items=milestones.filter { it.goalId==g.id }; (items.count { it.completedAt!=null }*1000L) to (items.size*1000L) }
        "focus" -> { val linked=goalLinks.filter { it.goalId==g.id && it.sourceType=="focus" }.map { it.sourceId }.toSet()
            val ids=sessions.filter { it.id in linked && it.deletedAt==null && it.state in setOf("finished","early") }.map { it.id }.toSet()
            val zone=ZoneId.systemDefault()
            val from=g.startDate?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() } ?: Long.MIN_VALUE
            val to=g.endDate?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() } ?: Long.MAX_VALUE
            segments.filter { it.sessionId in ids && it.phase=="work" }.sumOf {
                val end=it.startAt+it.seconds*1000; (minOf(end,to)-maxOf(it.startAt,from)).coerceAtLeast(0)/1000*1000
            } to g.targetMilli }
        else -> progress.filter { it.goalId==g.id && (g.startDate==null || it.logDate>=g.startDate) && (g.endDate==null || it.logDate<=g.endDate) }.sumOf { it.deltaMilli } to g.targetMilli
    }
    fun goalReview(g: Goal): Boolean { val(v,t)=goalValue(g); return g.state=="done" && (t==0L || v<t) }
}
