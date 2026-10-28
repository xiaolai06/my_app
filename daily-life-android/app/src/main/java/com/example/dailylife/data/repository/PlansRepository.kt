package com.example.dailylife.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.dailylife.core.*
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.plans.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.time.*
import java.time.temporal.ChronoUnit

interface FocusClock { fun wall(): Long; fun elapsed(): Long; fun boot(): Int }
class AndroidFocusClock(private val context: Context): FocusClock {
    override fun wall()=System.currentTimeMillis()
    override fun elapsed()=SystemClock.elapsedRealtime()
    override fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
}
class PlansRepository(val records: RecordsRepository, val clock: FocusClock) {
    val db get()=records.db
    val dao get()=db.plans()
    val writes get()=records.writes
    val snapshot=dao.changes(SimpleSQLiteQuery("SELECT 1")).map { db.withTransaction { load() } }
    suspend fun load()=PlansSnapshot(dao.tasks(),dao.subtasks(),dao.groups(),dao.events(),dao.rules(),dao.occurrences(),
        dao.habits(),dao.habitRules(),dao.habitLogs(),dao.pauses(),dao.sessions(),dao.segments(),dao.goals(),dao.progress(),
        dao.goalLinks(),dao.milestones(),dao.drafts(),dao.reminders())
    private suspend fun owner()=records.owner()
    private fun requireWritable(deletedAt:Long?,archivedAt:Long?) {
        require(deletedAt==null && archivedAt==null) {"请先恢复或取消归档，再修改内容"}
    }
    suspend fun initialize() { records.initialize(); refresh() }
    suspend fun draft(f: PlanForm) = writes.withLock {
        val current=dao.drafts().firstOrNull {it.id==f.id} ?: error("未完成编辑已保存或删除，请重新打开")
        val previous=PlanForm.parse(current.payload)
        require(previous.kind==f.kind && previous.revision==f.revision) {"编辑来源已变化，未完成内容仍保留，请重新核对"}
        dao.put(PlanDraft(f.id,f.kind,f.json()))
    }
    suspend fun prepareDraft(f:PlanForm,sourceKind:String?=null,sourceId:String=f.id)=writes.withLock {db.withTransaction {
        dao.drafts().firstOrNull {it.id==f.id}?.let {current->
            val existing=PlanForm.parse(current.payload)
            require(existing.kind==f.kind) {"未完成编辑编号已被其他类型使用"}
            return@withTransaction existing
        }
        dao.put(PlanDraft(f.id,f.kind,f.json()))
        if(sourceKind!=null) records.dao.putLinks(records.dao.linksFor(sourceKind,sourceId).map {it.copy(entityType="plan_draft",entityId=f.id)})
        f
    }}
    suspend fun importFile(id:String,uri:android.net.Uri)=writes.withLock {
        require(dao.drafts().any {it.id==id});require(records.dao.linksFor("plan_draft",id).size<50) {"每项最多 50 份附件"}
        val a=records.files.import(uri,owner())
        try {db.withTransaction {records.dao.putAttachment(a);records.dao.putLinks(listOf(AttachmentLink("plan_draft",id,a.id,sortOrder=records.dao.linksFor("plan_draft",id).size))) }}
        catch(e:Exception) {records.files.file(a).delete();throw e}
    }
    suspend fun removeFile(id:String,file:String)=writes.withLock {
        require(dao.drafts().any {it.id==id});records.dao.detach("plan_draft",id,file)
        if(records.dao.attachmentUses(file)==0) {records.dao.attachment(file)?.let {records.files.file(it).delete()};records.dao.deleteAttachment(file)}
    }
    suspend fun attachFile(id:String,file:String)=writes.withLock {
        require(dao.drafts().any {it.id==id})
        records.requireAvailableAttachments(listOf(AttachmentLink("plan_draft",id,file)))
        require(records.dao.linksFor("plan_draft",id).size<50)
        records.dao.putLinks(listOf(AttachmentLink("plan_draft",id,file)))
    }
    suspend fun discard(id: String)=writes.withLock { dao.removeDraft(id); cleanLinks("plan_draft",id) }
    private suspend fun cleanLinks(kind: String,id: String) {
        val refs=records.dao.linksFor(kind,id)
        records.dao.clearLinks(kind,id)
        refs.forEach { a -> if(records.dao.attachmentUses(a.attachmentId)==0) {
            records.dao.attachment(a.attachmentId)?.let { records.files.file(it).delete() }; records.dao.deleteAttachment(a.attachmentId)
        } }
    }
    suspend fun save(f: PlanForm, allowWarnings: Boolean = false) = writes.withLock {
        val detached=mutableListOf<String>()
        db.withTransaction {
        val owner=owner(); val title=PlanRules.title(f.text("title")); val now=clock.wall()
        fun date(k:String)=f.text(k).takeIf { it.isNotBlank() }?.also { LocalDate.parse(it) }
        fun number(k:String,default:String)=f.text(k,default).toIntOrNull() ?: error("数值格式不正确")
        val note=f.text("note"); require(note.length<=50000) { "备注最多 50,000 字" }
        when(f.kind) {
            "task" -> {
                require(f.text("pendingChild").isBlank()) {"还有未加入的子任务，请先点“加入”或清空该输入"}
                val old=dao.tasks().find { it.id==f.id }; checkVersion(old?.revision,f)
                old?.let {requireWritable(it.deletedAt,it.archivedAt)}
                old?.let {freezeHistory(f,it.title)}
                val planned=date("planned"); val due=date("due"); val time=f.text("dueTime").takeIf { it.isNotBlank() }?.also { LocalTime.parse(it) }
                require(time==null || due!=null) { "截止时刻需要先设置截止日期" }
                require(allowWarnings || planned==null || due==null || planned<=due) { "计划日期晚于截止日期。请修改，或选择保留并保存。" }
                val group=f.text("group").ifBlank { null }; require(group==null || dao.groups().any { it.id==group && it.ownerId==owner }) { "分组已不存在" }
                val reminder=f.text("reminder").takeIf { it.isNotBlank() }?.let { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                val source=f.text("source").ifBlank { null }
                require(source==null || records.dao.record(source)?.record?.let { it.ownerId==owner && it.deletedAt==null }==true) { "来源记录不可用" }
                val priority=number("priority","1"); require(priority in 0..2)
                require(f.text("minutes").isBlank() || due!=null || planned!=null) {"相对提醒需要计划日期或截止日期"}
                dao.put(Task(f.id,owner,title,note,group,planned,due,time,priority,
                    old?.status ?: "todo",old?.completedAt,reminder,source,old?.createdAt ?: now,now,(old?.revision ?: 0)+1,old?.deletedAt,old?.archivedAt,
                    f.text("minutes").ifBlank { null }?.toInt()?.also { require(it in 0..525600) }))
                val previous=dao.subtasks().filter { it.taskId==f.id }.associateBy { it.id }
                val children=JSONArray(f.text("subtasks","[]")); require(children.length()<=100) { "子任务最多 100 项" }
                dao.removeSubtasks(f.id)
                val childIds=mutableSetOf<String>()
                for(i in 0 until children.length()) { val c=children.getJSONObject(i); val id=c.optString("id").ifBlank { newId() }
                    require(childIds.add(id)) { "子任务标识重复" }
                    require(previous[id]!=null || dao.subtasks().none { it.id==id }) { "子任务标识冲突" }
                    dao.put(Subtask(id,f.id,PlanRules.title(c.getString("title")),previous[id]?.completedAt,i)) }
                saveRepeat(f,planned ?: due)
            }
            "event" -> {
                val old=dao.events().find { it.id==f.id }; checkVersion(old?.revision,f)
                old?.let {requireWritable(it.deletedAt,it.archivedAt)}
                old?.let {freezeHistory(f,it.title)}
                val start=LocalDate.parse(f.text("start",LocalDate.now().toString())); val end=LocalDate.parse(f.text("end",start.toString()))
                val allDay=f.text("allDay")=="true"; require(end>=start) { "结束日期不能早于开始日期" }
                val zone=ZoneId.of(f.text("zone",ZoneId.systemDefault().id))
                val a=f.text("startTime","09:00"); val b=f.text("endTime","10:00")
                val utc=if(allDay) null else PlanRules.duration(start.atTime(LocalTime.parse(a)),end.atTime(LocalTime.parse(b)),zone)
                val linked=f.text("task").ifBlank { null }; require(linked==null || dao.tasks().any { it.id==linked && it.ownerId==owner && it.deletedAt==null })
                if(!allowWarnings && utc!=null) require(dao.events().none { it.id!=f.id && it.deletedAt==null && it.status=="scheduled" && !it.allDay &&
                    it.startUtc!!<utc.second && it.endUtc!!>utc.first }) { "此时段与已有日程重叠。可修改，或选择保留并保存。" }
                val minutes=f.text("minutes").takeIf { it.isNotBlank() }?.toInt(); require(minutes==null || minutes in 0..525600)
                dao.put(PlanEvent(f.id,owner,title,allDay,start.toString(),end.plusDays(1).toString(),a,b,zone.id,utc?.first,utc?.second,
                    f.text("location"),note,linked,minutes,old?.status ?: "scheduled",old?.createdAt ?: now,now,(old?.revision ?: 0)+1,old?.deletedAt,old?.archivedAt))
                saveRepeat(f,start.toString())
            }
            "habit" -> {
                val old=dao.habits().find { it.id==f.id }; checkVersion(old?.revision,f)
                old?.let {requireWritable(it.deletedAt,it.archivedAt)}
                val tracking=f.text("tracking","once"); require(tracking in setOf("once","quantity","duration"))
                require(old==null || old.trackingType==tracking) { "已有习惯请保留记录方式；新建习惯可使用其他单位" }
                val start=date("start") ?: LocalDate.now().toString(); val end=date("end"); require(end==null || end>=start) {"结束日期不能早于开始日期"}
                require(old==null || old.startDate==start) { "开始日期已有历史，不能改写" }
                val reminder=f.text("reminder").ifBlank { null }; reminder?.let(LocalTime::parse)
                dao.put(Habit(f.id,owner,title,tracking,start,end,old?.status ?: "active",note,reminder,
                    number("color",old?.color?.toString() ?: "0").also {require(it in 0..5)},old?.createdAt ?: now,now,(old?.revision ?: 0)+1,old?.deletedAt,old?.archivedAt))
                val effective=if(old==null) start else date("effective") ?: LocalDate.now().toString()
                val target=if(tracking=="once") 1000 else PlanRules.milli(f.text("target","1"))
                require(target>0 && target<=1_000_000_000_000L) { "每日目标需大于 0，且不超过十亿" }
                val unit=if(tracking=="once") "次" else if(tracking=="duration") "秒" else RecordRules.name(f.text("unit"))
                val schedule=f.text("schedule","daily"); val days=number("weeklyDays","3"); require(days in 1..7 && schedule in setOf("daily","weekdays","weekly"))
                val weekdays=f.text("weekdays","1,2,3,4,5"); if(schedule=="weekdays") validWeekdays(weekdays)
                val existing=dao.habitRules().filter { it.habitId==f.id }
                val current=existing.lastOrNull()
                val candidate=HabitRuleVersion(newId(),f.id,effective,target,unit,schedule,weekdays,days)
                if(current==null || current.copy(id=candidate.id,effectiveFrom=effective)!=candidate) {
                    require(old==null || effective>=LocalDate.now().toString() && effective>=start) { "新规则只能从今天或未来生效" }
                    val same=existing.find { it.effectiveFrom==effective }
                    require(same==null || dao.habitLogs().none { it.habitId==f.id && it.logDate>=effective }) { "此规则已有打卡，请选择未来生效日期" }
                    dao.put(candidate.copy(id=same?.id ?: candidate.id))
                }
            }
            "goal" -> {
                val old=dao.goals().find { it.id==f.id }; checkVersion(old?.revision,f)
                old?.let {requireWritable(it.deletedAt,it.archivedAt)}
                val source=f.text("source","manual"); require(source in setOf("manual","tasks","focus","milestones"))
                require(old==null || old.progressSource==source) { "已有目标请保留进度来源，避免混算历史" }
                val target=PlanRules.milli(f.text("target","1")); require(target>0 && target<=1_000_000_000_000L) {"目标值需大于 0，且不超过十亿"}
                val start=date("start"); val end=date("end"); require(start==null || end==null || end>=start) {"结束日期不能早于开始日期"}
                val kind=when(source) { "focus"->"duration"; "tasks"->"tasks"; "milestones"->"milestones"; else->f.text("goalType","quantity") }
                require(kind in setOf("quantity","duration","tasks","milestones"))
                require(old==null || old.goalType==kind) {"已有目标请保留计量方式，避免改写历史单位"}
                dao.put(Goal(f.id,owner,title,kind,source,target,if(kind=="duration") "秒" else if(kind in setOf("tasks","milestones")) "项" else f.text("unit","次").ifBlank { "次" },
                    start,end,note,old?.state ?: "active",old?.completedAt,old?.createdAt ?: now,(old?.revision ?: 0)+1,old?.deletedAt,old?.archivedAt))
                if(old==null && source in setOf("tasks","focus")) {
                    val sources=f.text("sources").split(",").filter {it.isNotBlank()}.toSet()
                    require(sources.isNotEmpty()) {"请至少选择一个进度来源"}
                    val goal=dao.goals().first {it.id==f.id}
                    sources.forEach {id->
                        val type=goalSourceType(goal)
                        requireAvailableGoalSource(goal,type,id)
                        dao.put(GoalSourceLink(f.id,type,id))
                    }
                }
                if(old==null && source=="milestones") {
                    val names=f.text("milestones").lines().filter {it.isNotBlank()}
                    require(names.isNotEmpty() && names.size<=100) {"请至少填写一项里程碑，最多 100 项"}
                    names.forEachIndexed {i,n->dao.put(Milestone(goalId=f.id,title=PlanRules.title(n),sortOrder=i))}
                }
            }
            else -> error("不支持的计划类型")
        }
        val tagIds=f.text("tags").split(",").filter { it.isNotBlank() }.toSet()
        require(tagIds.all { id -> records.dao.tags().any { it.id==id && it.ownerId==owner } })
        records.dao.clearTags(f.id,f.kind)
        records.dao.putTags(tagIds.map { EntityTag(f.kind,f.id,it) })
        val refs=records.dao.linksFor("plan_draft",f.id)
        records.requireAvailableAttachments(refs)
        detached+=records.dao.linksFor(f.kind,f.id).map {it.attachmentId}
        records.dao.clearLinks(f.kind,f.id)
        records.dao.putLinks(refs.map { it.copy(entityType=f.kind,entityId=f.id) })
        records.dao.clearLinks("plan_draft",f.id)
        bindToProject(db,f.text("project").ifBlank {null},f.kind,f.id,owner)
        dao.removeDraft(f.id); expand()
        }
        collectFiles(detached)
    }
    private suspend fun collectFiles(ids:List<String>) {
        ids.distinct().forEach {id->if(records.dao.attachmentUses(id)==0) {
            records.dao.attachment(id)?.let {records.files.file(it).delete()};records.dao.deleteAttachment(id)
        }}
    }
    private fun checkVersion(old: Long?,f:PlanForm) { require((old ?: 0)==f.revision) { "内容已变化，草稿仍保留；请保留需要的文字后重新打开" } }
    private suspend fun freezeHistory(f:PlanForm,oldTitle:String) {
        val from=f.text("repeatFrom").ifBlank {LocalDate.now().toString()}
        dao.occurrences().filter {it.entityId==f.id && it.entityType==f.kind && it.title==null &&
            (it.originalKey<from || it.status !in setOf("todo","scheduled"))}.forEach {dao.put(it.copy(title=oldTitle))}
    }
    private fun validWeekdays(value:String) { require(value.split(",").isNotEmpty() && value.split(",").all { it.toIntOrNull() in 1..7 }) { "请选择有效星期" } }
    private suspend fun saveRepeat(f:PlanForm,anchor:String?) {
        val old=dao.rules().filter { it.entityType==f.kind && it.entityId==f.id && it.stoppedFrom==null }.lastOrNull()
        val frequency=f.text("repeat","none")
        if(frequency=="none") { old?.let { r ->
            val from=LocalDate.now().toString();dao.put(r.copy(stoppedFrom=from))
            dao.occurrences().filter { it.ruleId==r.id && it.originalKey>=from && it.status in setOf("todo","scheduled") }
                .forEach { dao.put(it.copy(status="cancelled")) }
        }; return }
        require(anchor!=null) { "重复事项需要计划日期或起始日期" }
        val proposed=RepeatRule(old?.id ?: newId(),owner(),f.kind,f.id,anchor,frequency,f.text("interval","1").toInt(),
            f.text("weekdays","1,2,3,4,5"),f.text("repeatEnd").ifBlank { null },f.text("count").ifBlank {null}?.toInt(),f.text("invalid","last"))
        PlanRules.dates(proposed,LocalDate.parse(anchor).plusDays(90),1)
        if(old!=null && old!=proposed) {
            val from=f.text("repeatFrom").ifBlank { LocalDate.now().toString() }
            require(from>=LocalDate.now().toString()) { "不能改写过去的重复历史" }
            dao.put(old.copy(stoppedFrom=from))
            dao.occurrences().filter { it.ruleId==old.id && it.originalKey>=from && it.status in setOf("todo","scheduled") }
                .forEach { dao.put(it.copy(status="cancelled")) }
            dao.put(proposed.copy(id=newId(),anchorDate=from))
        } else dao.put(proposed)
    }
    private suspend fun expand() {
        val existing=dao.occurrences().map { it.ruleId to it.originalKey }.toSet()
        for(rule in dao.rules()) {
            val active=if(rule.entityType=="task") dao.tasks().any { it.id==rule.entityId && it.deletedAt==null && it.archivedAt==null && it.status!="cancelled" }
                else dao.events().any { it.id==rule.entityId && it.deletedAt==null && it.archivedAt==null && it.status!="cancelled" }
            if(!active) continue
            for(date in PlanRules.dates(rule,LocalDate.now().plusDays(90),1)) if(rule.id to date.toString() !in existing)
                dao.put(Occurrence(ruleId=rule.id,entityType=rule.entityType,entityId=rule.entityId,originalKey=date.toString(),
                    scheduledDate=date.toString(),
                    status=if(rule.entityType=="event") "scheduled" else "todo"))
        }
    }
    suspend fun refresh()=writes.withLock { db.withTransaction {
        expand()
        dao.sessions().filter { it.state=="running" }.forEach { s -> if(openSeconds(s)==null) dao.put(s.copy(state="review",revision=s.revision+1)) }
    } }
    suspend fun form(kind:String,id:String):PlanForm {
        val s=load(); var f=PlanForm(kind,id)
        fun set(k:String,v:Any?) { if(v!=null) f=f.set(k,v.toString()) }
        when(kind) {
            "task" -> { val t=s.tasks.first { it.id==id }; f=f.copy(revision=t.revision)
                set("title",t.title);set("note",t.note);set("group",t.groupId);set("planned",t.plannedDate);set("due",t.dueDate);set("dueTime",t.dueTime);set("priority",t.priority)
                set("reminder",t.reminderAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime().toString() });set("source",t.sourceRecordId);set("minutes",t.reminderMinutes)
                set("subtasks",JSONArray(s.subtasks.filter { it.taskId==id }.map { JSONObject().put("id",it.id).put("title",it.title) })) }
            "event" -> { val e=s.events.first { it.id==id }; f=f.copy(revision=e.revision)
                set("title",e.title);set("note",e.note);set("allDay",e.allDay);set("start",e.startDate);set("end",LocalDate.parse(e.endDateExclusive).minusDays(1))
                set("startTime",e.startTime);set("endTime",e.endTime);set("zone",e.zoneId);set("location",e.location);set("task",e.taskId);set("minutes",e.reminderMinutes) }
            "habit" -> { val h=s.habits.first { it.id==id }; f=f.copy(revision=h.revision); val r=s.habitRules.last { it.habitId==id }
                set("title",h.name);set("note",h.note);set("tracking",h.trackingType);set("start",h.startDate);set("end",h.endDate);set("reminder",h.reminderTime)
                set("color",h.color)
                set("target",PlanRules.display(r.targetMilli));set("unit",r.unit);set("schedule",r.scheduleType);set("weekdays",r.weekdays);set("weeklyDays",r.weeklyDays) }
            "goal" -> { val g=s.goals.first { it.id==id };f=f.copy(revision=g.revision)
                set("title",g.name);set("note",g.note);set("source",g.progressSource);set("target",PlanRules.display(g.targetMilli));set("unit",g.unit);set("start",g.startDate);set("end",g.endDate);set("goalType",g.goalType) }
        }
        s.rules.lastOrNull { it.entityType==kind && it.entityId==id && it.stoppedFrom==null }?.let { r ->
            set("repeat",r.frequency);set("interval",r.interval);set("weekdays",r.weekdays);set("repeatEnd",r.endDate);set("count",r.count);set("invalid",r.invalidPolicy)
        }
        set("tags",records.dao.tagsForEntity(kind,id).joinToString(",") { it.tagId })
        return f
    }
    suspend fun taskAction(row:TaskRow,action:String,children:Boolean=false)=writes.withLock { db.withTransaction {
        setTaskStatus(row,action,children)
    } }
    suspend fun completeTasks(rows:List<TaskRow>,children:Boolean=false)=writes.withLock { db.withTransaction {
        require(rows.isNotEmpty()) {"请先选择任务"}
        rows.distinctBy {it.key}.forEach {setTaskStatus(it,"done",children)}
    } }
    private suspend fun setTaskStatus(row:TaskRow,action:String,children:Boolean) {
        require(action in setOf("todo","doing","done","cancelled"))
        val current=dao.tasks().first { it.id==row.task.id }
        require(current.deletedAt==null && current.archivedAt==null) {"请先恢复或取消归档，再修改任务状态"}
        if(row.occurrence!=null) {
            val o=dao.occurrences().first { it.id==row.occurrence.id }
            require(o.entityType=="task" && o.entityId==current.id) {"重复实例与任务不匹配，请重新打开任务"}
            dao.put(o.copy(status=action,completedAt=if(action=="done") o.completedAt ?: clock.wall() else null,
            subtaskStates=if(children && action=="done") dao.subtasks().filter {it.taskId==current.id}.joinToString(",") {it.id} else o.subtaskStates)) }
        else if(current.status!=action) dao.put(current.copy(status=action,completedAt=if(action=="done") current.completedAt ?: clock.wall() else null,updatedAt=clock.wall(),revision=current.revision+1))
        if(children && action=="done" && row.occurrence==null) dao.subtasks().filter { it.taskId==current.id && it.completedAt==null }.forEach { dao.put(it.copy(completedAt=clock.wall())) }
    }
    suspend fun subtask(id:String,occurrenceId:String?=null)=writes.withLock { db.withTransaction {
        val v=dao.subtasks().first { it.id==id }
        val parent=dao.tasks().first {it.id==v.taskId}
        require(parent.deletedAt==null && parent.archivedAt==null) {"请先恢复或取消归档，再修改子任务"}
        if(occurrenceId==null) {
            require(parent.status!="cancelled") {"请先将任务恢复为待办"}
            dao.put(v.copy(completedAt=if(v.completedAt==null) clock.wall() else null))
        } else { val o=dao.occurrences().first {it.id==occurrenceId && it.entityId==v.taskId && it.entityType=="task"}
            require(o.status !in setOf("cancelled","skipped")) {"请先将本次任务恢复为待办"}
            val done=o.subtaskStates.split(",").filter {it.isNotBlank()}.toMutableSet()
            if(!done.add(id)) done.remove(id)
            dao.put(o.copy(subtaskStates=done.joinToString(","))) }
    } }
    suspend fun moveTasks(ids:Set<String>,group:String?)=writes.withLock { db.withTransaction {
        require(group==null || dao.groups().any {it.id==group && it.archivedAt==null})
        val targets=dao.tasks().filter {it.id in ids}
        require(ids.isNotEmpty() && targets.size==ids.size && targets.all {it.deletedAt==null && it.archivedAt==null}) {"所选任务已变化，请重新选择"}
        targets.forEach {dao.put(it.copy(groupId=group,revision=it.revision+1,updatedAt=clock.wall()))}
    } }
    suspend fun manageTasks(ids:Set<String>,action:String)=writes.withLock { db.withTransaction {
        require(ids.isNotEmpty() && action in setOf("trash","restore"))
        ids.forEach {manageTask(it,action)}
    } }
    private suspend fun manageTask(id:String,action:String) {
        val t=dao.tasks().firstOrNull {it.id==id} ?: error("所选任务已不存在，请重新选择")
        val now=clock.wall()
        dao.put(when(action) {
            "trash"->t.copy(deletedAt=t.deletedAt ?: now);"restore"->t.copy(deletedAt=null)
            "archive"->t.copy(archivedAt=if(t.archivedAt==null) now else null)
            else->error("未知任务操作")
        }.copy(revision=t.revision+1,updatedAt=now))
    }
    suspend fun readReminder(id:Long)=writes.withLock {dao.reminders().firstOrNull {it.id==id}?.let {dao.put(it.copy(readAt=clock.wall()))}}
    suspend fun instance(id:String,date:String?,title:String?,status:String?)=writes.withLock { db.withTransaction {
        val o=dao.occurrences().firstOrNull { it.id==id } ?: error("重复事项已不存在")
        require(o.entityType in setOf("task","event")) {"不支持的重复事项类型"}
        val rule=dao.rules().firstOrNull {it.id==o.ruleId && it.entityType==o.entityType && it.entityId==o.entityId}
            ?: error("重复事项归属已变化，请重新打开")
        val validStatuses=if(o.entityType=="task") setOf("todo","doing","done","cancelled","skipped") else setOf("scheduled","cancelled")
        require(status==null || status in validStatuses) {"事项状态不正确"}
        if(o.entityType=="task") {
            val parent=dao.tasks().firstOrNull {it.id==o.entityId && it.ownerId==rule.ownerId} ?: error("原任务已不存在")
            requireWritable(parent.deletedAt,parent.archivedAt)
        } else {
            val parent=dao.events().firstOrNull {it.id==o.entityId && it.ownerId==rule.ownerId} ?: error("原日程已不存在")
            requireWritable(parent.deletedAt,parent.archivedAt)
        }
        date?.let(LocalDate::parse)
        dao.put(o.copy(scheduledDate=date ?: o.scheduledDate,title=title?.let(PlanRules::title) ?: o.title,
            status=status ?: o.status,completedAt=if(status=="done") o.completedAt ?: clock.wall() else if(status!=null) null else o.completedAt))
    } }
    suspend fun stop(ruleId:String)=writes.withLock { db.withTransaction {
        val r=dao.rules().first { it.id==ruleId }
        when(r.entityType) {
            "task"->{val parent=dao.tasks().first {it.id==r.entityId && it.ownerId==r.ownerId};requireWritable(parent.deletedAt,parent.archivedAt)}
            "event"->{val parent=dao.events().first {it.id==r.entityId && it.ownerId==r.ownerId};requireWritable(parent.deletedAt,parent.archivedAt)}
            else->error("不支持的重复事项类型")
        }
        val from=LocalDate.now().toString(); dao.put(r.copy(stoppedFrom=from))
        dao.occurrences().filter { it.ruleId==r.id && it.originalKey>=from && it.status in setOf("todo","scheduled") }.forEach { dao.put(it.copy(status="cancelled")) }
    } }
    suspend fun group(name:String,id:String?=null,archive:Boolean=false,moveTo:String?=null,delete:Boolean=false)=writes.withLock { db.withTransaction {
        val value=RecordRules.name(name)
        require(dao.groups().none { it.id!=id && it.normalizedName==RecordRules.normalized(value) }) { "分组名称已存在" }
        require(moveTo==null || moveTo!=id && dao.groups().any { it.id==moveTo && it.archivedAt==null })
        if(delete && id!=null) { dao.tasks().filter { it.groupId==id }.forEach { dao.put(it.copy(groupId=moveTo,revision=it.revision+1)) };dao.removeGroup(id) }
        else dao.put(TaskGroup(id ?: newId(),owner(),value,RecordRules.normalized(value),archivedAt=if(archive) clock.wall() else null))
    } }
    suspend fun manage(kind:String,id:String,action:String)=writes.withLock { db.withTransaction {
        val now=clock.wall()
        when(kind) {
            "task" -> manageTask(id,action)
            "event" -> { val e=dao.events().first { it.id==id };if(action=="cancel") requireWritable(e.deletedAt,e.archivedAt);dao.put(when(action) {
                "trash"->e.copy(deletedAt=now); "restore"->e.copy(deletedAt=null); "cancel"->e.copy(status=if(e.status=="cancelled") "scheduled" else "cancelled")
                "archive"->e.copy(archivedAt=if(e.archivedAt==null) now else null);else->error("未知日程操作") }.copy(revision=e.revision+1)) }
            "habit" -> { val h=dao.habits().first { it.id==id }
                require(action in setOf("trash","restore","end","pause")) {"未知习惯操作"}
                if(action !in setOf("trash","restore")) requireWritable(h.deletedAt,h.archivedAt)
                if(action=="pause") require(h.status!="ended") {"已结束的习惯不能继续，请新建习惯"}
                when(action) {
                    "pause" -> { val open=dao.pauses().firstOrNull { it.habitId==id && it.endDate==null }
                        if(open==null) dao.put(HabitPause(habitId=id,startDate=LocalDate.now().toString()))
                        else dao.put(open.copy(endDate=LocalDate.now().toString())) }
                }
                dao.put(when(action) { "trash"->h.copy(deletedAt=now); "restore"->h.copy(deletedAt=null); "end"->h.copy(status="ended",endDate=LocalDate.now().toString())
                    "pause"->h.copy(status=if(h.status=="paused") "active" else "paused");else->h }.copy(revision=h.revision+1,updatedAt=now)) }
            "goal" -> { val g=dao.goals().first { it.id==id }
                require(action in setOf("trash","restore","archive","pause","done","reopen")) {"未知目标操作"}
                if(action !in setOf("trash","restore","archive")) requireWritable(g.deletedAt,g.archivedAt)
                if(action=="done") require(load().goalValue(g).second>0) {"请先配置有效的进度来源"}
                val state=if(action=="pause") (if(g.state=="paused") "active" else "paused") else if(action=="done") "done" else if(action=="reopen") "active" else g.state
                dao.put(g.copy(state=state,completedAt=if(action=="done") now else if(action=="reopen") null else g.completedAt,deletedAt=if(action=="trash") now else if(action=="restore") null else g.deletedAt,
                    archivedAt=if(action=="archive") (if(g.archivedAt==null) now else null) else g.archivedAt,revision=g.revision+1)) }
            "focus" -> {val f=dao.sessions().first {it.id==id};require(f.endedAt!=null) {"活动专注请先结束"}
                require(action in setOf("trash","restore"))
                dao.put(f.copy(deletedAt=if(action=="trash") now else null,revision=f.revision+1))}
        }
    } }
    suspend fun permanentlyDelete(kind:String,id:String)=writes.withLock {
        val files=mutableListOf<String>()
        db.withTransaction {
            val s=load();val sql=db.openHelper.writableDatabase
            val table=mapOf("task" to "tasks","event" to "events","habit" to "habits","focus" to "focus_sessions","goal" to "goals")[kind] ?: error("类型不支持")
            require(dao.drafts().none {it.id==id}) {"仍有此项草稿，请先处理草稿"}
            val deleted=sql.query("SELECT deletedAt FROM "+table+" WHERE id=?",arrayOf(id)).use {it.moveToFirst() && !it.isNull(0)}
            require(deleted) {"请先移入回收站"}
            require(db.tools().links().none {it.entityType==kind && it.entityId==id}) {"此事项仍被专题引用，请先移出专题"}
            require(s.goalLinks.none {it.sourceType==kind && it.sourceId==id} &&
                (kind!="task" || s.events.none {it.taskId==id} && s.sessions.none {it.taskId==id})) {"仍被日程、专注或目标引用，请先解除关联"}
            files+=records.dao.linksFor(kind,id).map {it.attachmentId}
            records.dao.clearLinks(kind,id);records.dao.clearTags(id,kind)
            if(kind in setOf("task","event")) {
                sql.execSQL("DELETE FROM occurrences WHERE entityType=? AND entityId=?",arrayOf(kind,id))
                sql.execSQL("DELETE FROM repeat_rules WHERE entityType=? AND entityId=?",arrayOf(kind,id))
            }
            val children=when(kind) {"task"->listOf("subtasks" to "taskId");"habit"->listOf("habit_rules","habit_logs","habit_pauses").map {it to "habitId"}
                "focus"->listOf("focus_segments" to "sessionId");"goal"->listOf("goal_progress","goal_links","milestones").map {it to "goalId"};else->emptyList()}
            children.forEach {(child,column)->sql.execSQL("DELETE FROM "+child+" WHERE "+column+"=?",arrayOf(id))}
            sql.execSQL("DELETE FROM "+table+" WHERE id=?",arrayOf(id))
        };collectFiles(files)
    }
    suspend fun habitLog(habitId:String,date:String,value:String,note:String="",id:String?=null,delete:Boolean=false)=writes.withLock { db.withTransaction {
        val s=load();val h=s.habits.firstOrNull { it.id==habitId } ?: error("习惯已不存在")
        requireWritable(h.deletedAt,h.archivedAt);val d=LocalDate.parse(date)
        if(delete && id!=null) {require(s.logs.any {it.id==id && it.habitId==habitId});dao.removeHabitLog(id);return@withTransaction}
        require(d<=LocalDate.now() && date>=h.startDate && (h.endDate==null || date<=h.endDate)) { "只能登记开始日之后、结束日前且不晚于今天的日期" }
        require(s.habitScheduled(h,date)) { "这一天为休息或暂停日期，不能打卡" }
        if(id!=null) require(s.logs.any { it.id==id && it.habitId==habitId })
        if(delete && id!=null) dao.removeHabitLog(id) else {
            val v=if(h.trackingType=="once") 1000 else PlanRules.milli(value); require(v>0 && v<=1_000_000_000_000L) {"本次记录需大于 0，且不超过十亿"}
            val existing=if(h.trackingType=="once") s.logs.firstOrNull { it.habitId==habitId && it.logDate==date }?.id else null
            require(id==null || existing==null || existing==id) {"这一天已有一次打卡，请编辑对应日志"}
            dao.put(HabitLog(id ?: existing ?: newId(),habitId,date,v,note))
        }
    } }
    suspend fun goalLog(goalId:String,date:String,value:String,note:String,id:String?=null,delete:Boolean=false)=writes.withLock { db.withTransaction {
        val g=dao.goals().firstOrNull { it.id==goalId } ?: error("目标已不存在")
        requireWritable(g.deletedAt,g.archivedAt);require(g.progressSource=="manual");LocalDate.parse(date)
        if(id!=null) require(dao.progress().any { it.id==id && it.goalId==goalId })
        if(delete && id!=null) dao.removeProgress(id) else {
            val v=PlanRules.milli(value);require(v!=0L && v in -1_000_000_000_000L..1_000_000_000_000L)
            dao.put(GoalProgress(id ?: newId(),goalId,date,v,note))
        }
    } }
    suspend fun link(goalId:String,type:String,source:String,remove:Boolean=false)=writes.withLock { db.withTransaction {
        val g=dao.goals().firstOrNull { it.id==goalId } ?: error("目标已不存在")
        requireWritable(g.deletedAt,g.archivedAt)
        require(type==goalSourceType(g)) {"目标来源类型不匹配"}
        if(remove) dao.unlink(goalId,source) else {
            if(dao.goalLinks().any {it.goalId==goalId && it.sourceType==type && it.sourceId==source}) return@withTransaction
            requireAvailableGoalSource(g,type,source)
            dao.put(GoalSourceLink(goalId,type,source))
        }
    } }
    /** A selector owns a temporary set; only its Confirm action calls this transaction. */
    suspend fun setGoalSources(goalId:String,sources:Set<String>)=writes.withLock { db.withTransaction {
        val goal=dao.goals().firstOrNull {it.id==goalId} ?: error("目标已不存在")
        requireWritable(goal.deletedAt,goal.archivedAt)
        val type=goalSourceType(goal)
        require(sources.all {it.isNotBlank()}) {"关联来源不正确"}
        val existing=dao.goalLinks().filter {it.goalId==goalId}
        require(existing.all {it.sourceType==type}) {"关联来源类型异常，请先核对"}
        val currentIds=existing.map {it.sourceId}.toSet()
        val additions=sources-currentIds
        // Existing missing/deleted sources can stay selected, or be explicitly removed.
        additions.forEach {requireAvailableGoalSource(goal,type,it)}
        existing.filter {it.sourceId !in sources}.forEach {dao.unlink(goalId,it.sourceId)}
        additions.forEach {dao.put(GoalSourceLink(goalId,type,it))}
    } }
    private fun goalSourceType(goal:Goal)=when(goal.progressSource) {
        "tasks"->"task";"focus"->"focus";else->error("此目标不使用关联来源")
    }
    private suspend fun requireAvailableGoalSource(goal:Goal,type:String,source:String) {
        require(if(type=="task") dao.tasks().any {
            it.id==source && it.ownerId==goal.ownerId && it.deletedAt==null && it.archivedAt==null &&
                dao.rules().none {rule->rule.entityType=="task" && rule.entityId==source}
        } else dao.sessions().any {
            it.id==source && it.ownerId==goal.ownerId && it.deletedAt==null && it.archivedAt==null && it.state in setOf("finished","early")
        }) {"只能关联有效的普通任务或已结束专注"}
    }
    suspend fun milestone(goalId:String,title:String,date:String?=null,id:String?=null,toggle:Boolean=false,delete:Boolean=false)=writes.withLock { db.withTransaction {
        val goal=dao.goals().firstOrNull {it.id==goalId} ?: error("目标已不存在")
        require(goal.progressSource=="milestones") {"此目标不使用里程碑"};requireWritable(goal.deletedAt,goal.archivedAt)
        val old=dao.milestones().find { it.id==id };require(id==null || old!=null) {"里程碑已不存在"};require(old==null || old.goalId==goalId);date?.let(LocalDate::parse)
        if(delete && id!=null) dao.removeMilestone(id) else dao.put(Milestone(id ?: newId(),goalId,PlanRules.title(title),date,
            if(toggle) (if(old?.completedAt==null) clock.wall() else null) else old?.completedAt,old?.sortOrder ?: dao.milestones().count { it.goalId==goalId }))
    } }
    suspend fun startFocus(f:PlanForm)=writes.withLock { db.withTransaction {
        if(dao.sessions().any {it.id==f.id}) {dao.removeDraft(f.id);return@withTransaction}
        require(dao.sessions().none { it.state in setOf("running","paused","review","awaiting") && it.deletedAt==null }) { "已有专注，请先继续或结束" }
        val mode=f.text("mode","pomodoro");require(mode in setOf("pomodoro","countdown","stopwatch"))
        val seconds=f.text("seconds","1500").toLong();require(seconds in 1..86400) {"专注时长需为 1 至 86400 秒"}
        val short=f.text("short","300").toLong();val long=f.text("long","900").toLong();val rounds=f.text("rounds","4").toInt()
        require(short in 1..86400 && long in 1..86400 && rounds in 1..20) {"休息时长需为 1 至 86400 秒，长休间隔为 1 至 20 轮"}
        val task=f.text("task").ifBlank { null };require(task==null || dao.tasks().any { it.id==task && it.deletedAt==null })
        val s=FocusSession(f.id,owner(),f.text("title").ifBlank { "专注" }.let(PlanRules::title),mode,seconds,short,long,rounds,taskId=task,note=f.text("note"),startedAt=clock.wall())
        dao.put(s);dao.put(segment(s))
        val tags=f.text("tags").split(",").filter {it.isNotBlank()}.toSet()
        require(tags.all {id->records.dao.tags().any {it.id==id && it.ownerId==s.ownerId}})
        records.dao.putTags(tags.map {EntityTag("focus",s.id,it)})
        dao.removeDraft(f.id)
    } }
    suspend fun focusNote(id:String,note:String)=writes.withLock { db.withTransaction {
        require(note.length<=50000)
        val s=dao.sessions().firstOrNull {it.id==id} ?: error("专注记录已不存在")
        requireWritable(s.deletedAt,s.archivedAt)
        dao.put(s.copy(note=note,revision=s.revision+1))
    } }
    private fun segment(s:FocusSession)=FocusSegment(sessionId=s.id,phase=s.phase,startAt=clock.wall(),elapsedStart=clock.elapsed(),bootId=clock.boot(),round=s.round)
    suspend fun openSeconds(s:FocusSession):Long? = openSeconds(s,dao.segments())
    fun openSeconds(s:FocusSession,segments:List<FocusSegment>):Long? {
        val seg=segments.lastOrNull { it.sessionId==s.id && it.endAt==null } ?: return 0
        val elapsed=clock.elapsed()-seg.elapsedStart
        val boot=clock.boot()
        if(seg.bootId<0 || boot<0 || boot!=seg.bootId || elapsed<0 || kotlin.math.abs((clock.wall()-seg.startAt)-elapsed)>120000) return null
        val used=segments.filter {it.sessionId==s.id && it.phase==s.phase && it.round==s.round && it.endAt!=null}.sumOf {it.seconds}
        val cap=if(s.phase=="break") (if(s.round % s.rounds==0) s.longBreak else s.shortBreak)-used
            else if(s.mode=="stopwatch") Long.MAX_VALUE else s.plannedSeconds-used
        return (elapsed/1000).coerceAtMost(cap.coerceAtLeast(0))
    }
    suspend fun focusAction(id:String,action:String,reason:String="",corrected:Long?=null)=writes.withLock { db.withTransaction {
        var s=dao.sessions().firstOrNull { it.id==id } ?: error("专注记录已不存在")
        requireWritable(s.deletedAt,s.archivedAt)
        val active=s.state in setOf("running","paused","review","awaiting")
        require(active || action=="correct") { "此专注已经结束" }
        if(action=="pause") require(s.state=="running")
        if(action=="finish" && s.mode!="stopwatch") {
            val work=dao.segments().filter {it.sessionId==id && it.phase=="work" && it.round==s.round}.sumOf {it.seconds}+
                (if(s.phase=="work") openSeconds(s) ?: 0 else 0)
            require(work>=s.plannedSeconds) {"尚未完成当前计时，请选择提前结束"}
        }
        val open=dao.segments().lastOrNull { it.sessionId==id && it.endAt==null }
        if(open!=null) {
            val seconds=if(s.state=="review") null else openSeconds(s)
            if(seconds==null && action!="recover" && action!="abandon") { dao.put(s.copy(state="review",revision=s.revision+1));return@withTransaction }
            dao.put(open.copy(endAt=clock.wall(),seconds=seconds ?: 0))
        }
                when(action) {
            "pause"->s=s.copy(state="paused")
            "resume","recover"->{ require(s.state in setOf("paused","review"));s=s.copy(state="running");dao.put(segment(s)) }
            "break"->{require(s.mode=="pomodoro" && s.phase=="work" && dao.segments().filter {it.sessionId==id && it.phase=="work" && it.round==s.round}.sumOf {it.seconds}>=s.plannedSeconds) {"请先完成当前工作时段"};s=s.copy(state="running",phase="break");dao.put(segment(s)) }
            "next"->{require(s.mode=="pomodoro" && s.phase=="break") {"当前没有可开始的下一轮"};s=s.copy(state="running",phase="work",round=s.round+1);dao.put(segment(s)) }
            "finish","early","abandon"->s=s.copy(state=when(action) { "finish"->"finished";"early"->"early";else->"abandoned" },endedAt=clock.wall())
            "correct"->{ require(s.state in setOf("finished","early") && corrected!=null && corrected in 0..604800 && reason.isNotBlank()) { "修正需填写合理秒数和原因" }
                val segments=dao.segments().filter { it.sessionId==id && it.phase=="work" }
                require(segments.isNotEmpty())
                segments.forEachIndexed { i,it -> dao.put(it.copy(originalSeconds=it.originalSeconds ?: it.seconds,seconds=if(i==0) corrected else 0,correctionReason=reason)) } }
            else->error("不支持的计时操作")
        }
        dao.put(s.copy(revision=s.revision+1))
    } }
}
