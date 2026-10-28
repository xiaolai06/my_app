package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.Habit
import com.example.dailylife.feature.plans.*
import java.time.LocalDate

internal fun planQuantity(value:Long,unit:String,duration:Boolean):String {
    if(!duration) return PlanRules.display(value)+" "+unit
    if(value<0 || value%1000!=0L) return PlanRules.display(value)+"秒"
    val seconds=value/1000
    if(seconds<60) return "${seconds}秒"
    return buildString {
        if(seconds>=3600) append("${seconds/3600}小时")
        if(seconds/60%60>0) append("${seconds/60%60}分钟")
        if(seconds%60>0) append("${seconds%60}秒")
    }
}

@Composable internal fun HabitPane(ui:PlansUi,vm:PlansViewModel) {
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var scope by rememberSaveable {mutableStateOf("current")}
    var picker by remember {mutableStateOf(false)}
    var log by remember {mutableStateOf<Habit?>(null)}
    val habits=ui.data.habits.filter {it.deletedAt==null && it.archivedAt==null && (scope=="all" || if(scope=="ended") it.status=="ended" else it.status!="ended")}
    LazyColumn(Modifier.fillMaxSize().testTag("planHabitList"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            PlanInlineTabs(listOf("current" to "当前","ended" to "已结束","all" to "全部"),scope,Modifier.weight(1f),"planHabitFilter") {scope=it}
            TextButton(onClick={picker=true},modifier=Modifier.testTag("habitDate")) {Text(planDateLabel(date));PlanGlyph("down",Modifier.size(16.dp))}
        }}
        if(habits.isEmpty()) item {PlanQuietEmpty("暂无习惯")}
        listOf("active" to "进行中","paused" to "已暂停","ended" to "已结束").forEach {(status,label)->
            val entries=habits.filter {it.status==status}.sortedBy {it.createdAt}
            if(entries.isNotEmpty()) {
                item {PlanSectionHeading(label,entries.size)}
                items(entries,key={it.id}) {habit->
                    var week by rememberSaveable(habit.id) {mutableStateOf(false)}
                    val rule=ui.data.habitRule(habit.id,date)
                    val value=ui.data.habitTotal(habit.id,date)
                    val ratio=if(rule!=null && rule.targetMilli>0) (value.toDouble()/rule.targetMilli).toFloat() else 0f
                    val scheduled=ui.data.habitScheduled(habit,date)
                    val large=LocalDensity.current.fontScale>1.3f
                    GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("habit",habit.id)}.testTag("habitRow:"+habit.id)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PlanGlyph(if(ratio>=1f) "check" else "habit")
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(habit.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                                    if(rule!=null) Text(planQuantity(value,rule.unit,habit.trackingType=="duration")+" / "+planQuantity(rule.targetMilli,rule.unit,habit.trackingType=="duration"),
                                        style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                                }
                                if(!large) TextButton(enabled=!ui.busy && habit.status=="active" && scheduled,onClick={log=habit},modifier=Modifier.testTag("logHabit:"+habit.id)) {Text("打卡")}
                                PlanReminderMark(planReminderSummary(ui.data,"habit",habit.id))
                            }
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                if(rule!=null && rule.targetMilli>0) PlanLinearProgress(ratio,"${habit.name} 当日进度",Modifier.weight(1f))
                                else Spacer(Modifier.weight(1f))
                                TextButton(onClick={week=!week},modifier=Modifier.testTag("habitWeek:"+habit.id),contentPadding=PaddingValues(horizontal=4.dp)) {
                                    Text(if(habit.status=="active" && !scheduled) "休息日" else "本周",style=MaterialTheme.typography.labelMedium)
                                    PlanGlyph(if(week) "up" else "down",Modifier.size(16.dp))
                                }
                                if(large) TextButton(enabled=!ui.busy && habit.status=="active" && scheduled,onClick={log=habit},modifier=Modifier.testTag("logHabit:"+habit.id)) {Text("打卡")}
                            }
                            if(week) {
                                HabitWeek(ui.data,habit,LocalDate.parse(date))
                                val streak=ui.data.habitStreak(habit,LocalDate.parse(date))
                                Text("连续 $streak "+if(rule?.scheduleType=="weekly") "周" else "天",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                            }
                        }
                    }
                }
            }
        }
    }
    if(picker) PlanDatePicker(date,{picker=false}) {date=it}
    log?.let {habit->ValueLogSheet("习惯打卡",date,if(habit.trackingType=="duration") ui.data.habitRule(habit.id,date)?.let {PlanRules.display(it.targetMilli)} ?: "1800" else "1","",habit.trackingType=="once",{log=null},busy=ui.busy,duration=habit.trackingType=="duration",error=ui.error) {day,value,note->
        vm.run(onSuccess={log=null}) {habitLog(habit.id,day,value,note)}
    }}
}

@Composable internal fun GoalPane(ui:PlansUi,vm:PlansViewModel) {
    var scope by rememberSaveable {mutableStateOf("current")}
    val goals=ui.data.goals.filter {it.deletedAt==null && if(scope=="archive") it.archivedAt!=null else it.archivedAt==null}
    LazyColumn(Modifier.fillMaxSize().testTag("planGoalList"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {PlanInlineTabs(listOf("current" to "全部目标","archive" to "已归档"),scope,tagPrefix="planGoalFilter") {scope=it}}
        if(goals.isEmpty()) item {PlanQuietEmpty("暂无目标")}
        listOf("active" to "进行中","paused" to "已暂停","done" to "已完成").forEach {(state,label)->
            val entries=goals.filter {it.state==state}.sortedWith(compareBy {it.endDate ?: "9999"})
            if(entries.isNotEmpty()) {
                item {PlanSectionHeading(label,entries.size)}
                items(entries,key={it.id}) {goal->
                    val(value,target)=ui.data.goalValue(goal)
                    val ratio=if(target>0) (value.toDouble()/target).toFloat() else 0f
                    GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("goal",goal.id)}.testTag("goalRow:"+goal.id)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                PlanGlyph(if(goal.state=="done") "check" else "goal")
                                Text(goal.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
                                Text(if(target>0) "${(ratio*100).toInt().coerceIn(0,100)}%" else "—",style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.accent)
                            }
                            if(target>0) PlanLinearProgress(ratio,"${goal.name} 目标进度")
                            val amount=if(target>0) planQuantity(value,goal.unit,goal.goalType=="duration")+" / "+planQuantity(target,goal.unit,goal.goalType=="duration") else "待关联进度"
                            Text(listOfNotNull(amount,goal.endDate?.let {planDateLabel(it)+"截止"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                            if(ui.data.goalReview(goal)) Text("来源变化，待复核",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
                            else if(target>0 && value>=target && goal.state!="done") Text("可确认完成",style=MaterialTheme.typography.labelMedium,color=LocalGlass.current.accent)
                        }
                    }
                }
            }
        }
    }
}
