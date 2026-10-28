package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.plans.*
import java.time.*

@Composable fun PlanDetail(ui:PlansUi,vm:PlansViewModel,records:RecordsSnapshot,view:(Attachment)->Unit,openRecord:(String)->Unit) {
    val id=ui.detailId ?: return
    val kind=ui.detailKind ?: return
    val s=ui.data
    val task=s.tasks.find {it.id==id};val event=s.events.find {it.id==id};val habit=s.habits.find {it.id==id}
    val focus=s.sessions.find {it.id==id};val goal=s.goals.find {it.id==id}
    val occurrence=s.occurrences.find {it.id==ui.instanceId && it.entityType==kind && it.entityId==id}
    val title=occurrence?.title ?: when(kind) {"task"->task?.title;"event"->event?.title;"habit"->habit?.name;"focus"->focus?.title;else->goal?.name}
    val note=when(kind) {"task"->task?.note;"event"->event?.note;"habit"->habit?.note;"focus"->focus?.note;else->goal?.note}.orEmpty()
    val deleted=when(kind) {"task"->task?.deletedAt;"event"->event?.deletedAt;"habit"->habit?.deletedAt;"focus"->focus?.deletedAt;else->goal?.deletedAt}!=null
    val archived=when(kind) {"task"->task?.archivedAt;"event"->event?.archivedAt;"habit"->habit?.archivedAt;else->goal?.archivedAt}!=null
    val editable=title!=null && !deleted && !archived
    val writable=editable && !ui.busy
    val repeating=s.rules.any {it.entityType==kind && it.entityId==id}
    var menu by remember(id,ui.instanceId) {mutableStateOf(false)}
    var action by remember(id,ui.instanceId) {mutableStateOf<String?>(null)}
    var entryMenu by remember(id) {mutableStateOf<String?>(null)}
    var logEdit by remember(id) {mutableStateOf<String?>(null)}
    var logAdd by remember(id) {mutableStateOf(false)}
    var logDelete by remember(id) {mutableStateOf<String?>(null)}
    var moveDate by remember(id,ui.instanceId) {mutableStateOf(false)}
    var rename by remember(id,ui.instanceId) {mutableStateOf(false)}
    var selectSources by remember(id) {mutableStateOf(false)}
    var milestoneEdit by remember(id) {mutableStateOf<Milestone?>(null)}
    var newMilestone by remember(id) {mutableStateOf(false)}
    var correction by remember(id) {mutableStateOf(false)}
    var focusNote by remember(id) {mutableStateOf(false)}
    var reminder by remember(id,ui.instanceId) {mutableStateOf(false)}
    var habitCalendar by remember(id) {mutableStateOf(false)}
    var habitDate by remember(id) {mutableStateOf<String?>(null)}
    val today=LocalDate.now().toString()
    BackHandler(enabled=!ui.busy) {vm.detail(null)}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        GlassPageHeader(planNames.first {it.first==kind}.second,back={vm.detail(null)},enabled=!ui.busy,backTag="backPlanDetail") {
            IconButton(enabled=!ui.busy && title!=null,onClick={vm.clearError();menu=true},modifier=Modifier.testTag("planDetailMenu").semantics {contentDescription="更多操作"}) {PlanGlyph("more",color=LocalGlass.current.muted)}
        }
        LazyColumn(Modifier.fillMaxSize().testTag("planDetailList"),contentPadding=PaddingValues(18.dp,10.dp,18.dp,28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(title==null) {item {PlanQuietEmpty("此内容已不存在")};return@LazyColumn}
            item(key="header") {
                if(kind=="task" && task!=null && (!repeating || occurrence!=null)) TaskCard(TaskRow(task,occurrence),ui,vm)
                else Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    if(kind=="task" && repeating) Text("重复任务",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
                if(deleted || archived) Text(if(deleted) "已在回收站" else "已归档",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }
            when(kind) {
                "task"->if(task!=null) {
                    if(repeating && occurrence==null) {
                        val instances=s.occurrences.filter {it.entityType=="task" && it.entityId==id}.sortedBy {it.scheduledDate}
                        item {PlanDetailHeading("各次安排",instances.size)}
                        items(instances,key={"instance:"+it.id}) {TaskCard(TaskRow(task,it),ui,vm)}
                    }
                    task.sourceRecordId?.let {source->item {PlanDetailEntry("来源记录",tag="planSourceRecord",kind="task",onClick={openRecord(source)})}}
                }
                "event"->if(event!=null) {
                    val row=PlanAgendaEvent(event,occurrence)
                    item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(planDateLabel(row.startDate.toString())+(if(row.endDateExclusive>row.startDate.plusDays(1)) " — "+planDateLabel(row.endDateExclusive.minusDays(1).toString()) else ""),style=MaterialTheme.typography.titleMedium)
                        Text(if(event.allDay) "全天" else event.startTime+"–"+event.endTime,style=MaterialTheme.typography.headlineSmall,color=LocalGlass.current.accent)
                        if(event.location.isNotBlank()) Text(event.location,style=MaterialTheme.typography.bodyLarge)
                        if(event.zoneId!=ZoneId.systemDefault().id) Text(event.zoneId,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                        if(row.status=="cancelled") Text("已取消",color=LocalGlass.current.muted)
                    }}}
                    event.taskId?.let {linked->item {PlanDetailEntry(s.tasks.find {it.id==linked}?.title ?: "关联任务",tag="planLinkedTask",kind="task",onClick={vm.detail("task",linked)})}}
                }
                "habit"->if(habit!=null) {
                    val rule=s.habitRule(id,today);val value=s.habitTotal(id,today)
                    val ratio=if(rule!=null && rule.targetMilli>0) (value.toDouble()/rule.targetMilli).toFloat() else 0f
                    val canLog=writable && habit.status=="active" && s.habitScheduled(habit,today)
                    item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            PlanProgressRing(ratio,if(ratio>=1f) "✓" else "${(ratio*100).toInt().coerceIn(0,100)}%","今日打卡进度",measurable=rule!=null)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(if(rule!=null) planQuantity(value,rule.unit,habit.trackingType=="duration")+" / "+planQuantity(rule.targetMilli,rule.unit,habit.trackingType=="duration") else "暂无目标",style=MaterialTheme.typography.titleMedium)
                                Text(stateName(habit.status)+" · 连续 "+s.habitStreak(habit,LocalDate.now())+(if(rule?.scheduleType=="weekly") " 周" else " 天"),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                            }
                        }
                        HabitWeek(s,habit,LocalDate.now())
                        if(editable) GlassButton(enabled=canLog && !(habit.trackingType=="once" && value>=1000),onClick={logAdd=true},modifier=Modifier.fillMaxWidth().testTag("planPrimaryAction")) {
                            Text(if(habit.trackingType=="once" && value>=1000) "今日已打卡" else if(habit.status=="paused") "已暂停" else if(habit.status=="ended") "已结束" else if(!s.habitScheduled(habit,today)) "今日休息" else "打卡")
                        }
                    }}}
                    val all=s.logs.filter {it.habitId==id}.sortedWith(compareByDescending<HabitLog> {it.logDate}.thenByDescending {it.id})
                    val logs=all.filter {habitDate==null || it.logDate==habitDate}
                    item {PlanDetailHeading("打卡记录",logs.size) {IconButton(onClick={habitCalendar=true},modifier=Modifier.testTag("planHistoryDate").semantics {contentDescription="筛选打卡日期"}) {PlanGlyph("date")}}}
                    if(habitDate!=null) item {TextButton(onClick={habitDate=null}) {Text(planDateLabel(habitDate!!)+" · 查看全部")}}
                    if(logs.isEmpty()) item {PlanQuietEmpty("暂无打卡")}
                    items(logs,key={"habit-log:"+it.id}) {log->
                        val logRule=s.habitRule(id,log.logDate)
                        PlanDetailEntry(planDateLabel(log.logDate)+" · "+planQuantity(log.valueMilli,logRule?.unit.orEmpty(),habit.trackingType=="duration"),log.note,
                            "planLog:"+log.id,enabled=!ui.busy,onClick={vm.clearError();entryMenu="log:"+log.id})
                    }
                    item {GlassFormSection("规则与历史",id+":rule-history",collapsible=true,initiallyExpanded=false) {
                        Text(habit.startDate+(habit.endDate?.let {" — $it"} ?: " 起"),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                        s.habitRules.filter {it.habitId==id}.sortedByDescending {it.effectiveFrom}.forEach {r->
                            Text(r.effectiveFrom+" · "+planQuantity(r.targetMilli,r.unit,habit.trackingType=="duration")+" · "+when(r.scheduleType) {"daily"->"每天";"weekly"->"每周 ${r.weeklyDays} 天";else->"星期 "+r.weekdays},style=MaterialTheme.typography.bodyMedium)
                        }
                        s.pauses.filter {it.habitId==id}.forEach {p->Text("暂停 "+p.startDate+" — "+(p.endDate ?: "至今"),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
                    }}
                }
                "focus"->if(focus!=null) {
                    item {FocusControls(focus,ui,vm,{correction=true})}
                    focus.taskId?.let {linked->
                        val linkedTask=s.tasks.find {it.id==linked}
                        item {PlanDetailEntry(linkedTask?.title ?: "关联任务",tag="planLinkedTask",kind="task",onClick={vm.detail("task",linked)})}
                        if(editable && focus.endedAt!=null && focus.state!="abandoned" && linkedTask!=null && linkedTask.deletedAt==null && linkedTask.archivedAt==null && linkedTask.status!="done") {
                            item {TextButton(enabled=writable,onClick={
                                val choice=s.subtasks.any {it.taskId==linked && it.completedAt==null} || s.rules.any {it.entityType=="task" && it.entityId==linked}
                                if(choice) vm.detail("task",linked) else vm.task(TaskRow(linkedTask),"done")
                            }) {Text("完成关联任务")}}
                        }
                    }
                    val segments=s.segments.filter {it.sessionId==id}
                    item {PlanDetailHeading("分段记录",segments.size)}
                    items(segments,key={"segment:"+it.id}) {segment->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text((if(segment.phase=="work") "工作" else "休息")+" · 第 ${segment.round} 轮",style=MaterialTheme.typography.bodyMedium)
                        Text(if(segment.endAt==null) "计时中" else clockText(segment.seconds),color=LocalGlass.current.accent)
                        segment.correctionReason?.let {Text("原时长 "+clockText(segment.originalSeconds ?: 0)+" · "+it,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
                    }}}
                }
                "goal"->if(goal!=null) {
                    val(value,target)=s.goalValue(goal);val ratio=if(target>0) (value.toDouble()/target).toFloat() else 0f
                    item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            PlanProgressRing(ratio,if(target>0) "${(ratio*100).toInt().coerceIn(0,100)}%" else "—","目标进度",measurable=target>0)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(if(target>0) planQuantity(value,goal.unit,goal.goalType=="duration")+" / "+planQuantity(target,goal.unit,goal.goalType=="duration") else "待关联进度",style=MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(stateName(goal.state),goal.endDate?.let {planDateLabel(it)+"截止"}).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                            }
                        }
                        if(s.goalReview(goal)) Text("进度回落，待复核",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        if(editable && goal.state!="done") GlassButton(enabled=writable,onClick={
                            if(goal.state=="paused") vm.manage(kind,id,"pause") else when(goal.progressSource) {"manual"->logAdd=true;"milestones"->newMilestone=true;else->selectSources=true}
                        },modifier=Modifier.fillMaxWidth().testTag("planPrimaryAction")) {Text(if(goal.state=="paused") "继续目标" else when(goal.progressSource) {"manual"->"记录进度";"milestones"->"添加里程碑";else->"关联事项"})}
                    }}}
                    when(goal.progressSource) {
                        "manual"->{
                            val logs=s.progress.filter {it.goalId==id}.sortedWith(compareByDescending<GoalProgress> {it.logDate}.thenByDescending {it.id})
                            item {PlanDetailHeading("进度记录",logs.size)}
                            if(logs.isEmpty()) item {PlanQuietEmpty("暂无记录")}
                            items(logs,key={"goal-log:"+it.id}) {log->PlanDetailEntry(planDateLabel(log.logDate)+" · "+planQuantity(log.deltaMilli,goal.unit,goal.goalType=="duration"),log.note,
                                "planLog:"+log.id,enabled=!ui.busy,onClick={vm.clearError();entryMenu="log:"+log.id})}
                        }
                        "tasks","focus"->{
                            val links=s.goalLinks.filter {it.goalId==id}
                            item {PlanDetailHeading("关联事项",links.size)}
                            if(links.isEmpty()) item {PlanQuietEmpty("暂无关联")}
                            items(links,key={"source:"+it.sourceId}) {link->
                                val sourceTask=s.tasks.find {it.id==link.sourceId};val sourceFocus=s.sessions.find {it.id==link.sourceId}
                                val sourceTitle=if(link.sourceType=="task") sourceTask?.title else sourceFocus?.title
                                val subtitle=if(link.sourceType=="task") sourceTask?.let {stateName(it.status)+(if(it.deletedAt!=null) " · 已删除" else "")} else sourceFocus?.let {clockText(s.focusSeconds(it.id))+(if(it.deletedAt!=null) " · 已删除" else "")}
                                PlanDetailEntry(sourceTitle ?: "来源已不存在",subtitle,"planLinkedSource:"+link.sourceId,kind=link.sourceType,enabled=!ui.busy,
                                    onMore=if(editable) ({entryMenu="source:"+link.sourceId}) else null,
                                    onClick={if(sourceTitle!=null) vm.detail(link.sourceType,link.sourceId) else if(editable) entryMenu="source:"+link.sourceId})
                            }
                        }
                        "milestones"->{
                            val milestones=s.milestones.filter {it.goalId==id}.sortedBy {it.sortOrder}
                            item {PlanDetailHeading("里程碑",milestones.size)}
                            if(milestones.isEmpty()) item {PlanQuietEmpty("暂无里程碑")}
                            items(milestones,key={"milestone:"+it.id}) {m->GlassPanel(Modifier.fillMaxWidth().testTag("planMilestone:"+m.id)) {
                                Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                                    GlassCheckbox(m.completedAt!=null,{vm.run {milestone(id,m.title,m.dueDate,m.id,toggle=true)}},enabled=writable,modifier=Modifier.semantics {contentDescription="完成里程碑 ${m.title}"})
                                    Column(Modifier.weight(1f).clickable(enabled=writable) {entryMenu="milestone:"+m.id}.padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(m.title,style=MaterialTheme.typography.bodyLarge)
                                        m.dueDate?.let {Text(planDateLabel(it)+"截止",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
                                    }
                                    if(editable) IconButton(enabled=writable,onClick={entryMenu="milestone:"+m.id},modifier=Modifier.semantics {contentDescription="管理里程碑 ${m.title}"}) {PlanGlyph("more",color=LocalGlass.current.muted)}
                                }
                            }}
                        }
                    }
                }
            }
            if(note.isNotBlank()) item {PlanDetailHeading("备注");GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {MarkdownBody(note)}}}
            if(!deleted && kind in setOf("task","event","habit")) item {
                val summary=planReminderSummary(s,kind,id,occurrence)
                PlanDetailEntry("提醒",summary.label,"planReminderStatus",kind="bell",enabled=!ui.busy,onClick={reminder=true})
            }
            val files=records.attachmentsFor(kind,id)
            if(files.isNotEmpty()) {
                item {PlanDetailHeading("附件",files.size)}
                items(files,key={"file:"+it.id}) {file->PlanDetailEntry(file.displayName,tag="planAttachment:"+file.id,onClick={view(file)})}
            }
            if(ui.error!=null) item {Text(ui.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    }
    if(menu) GlassSheet("更多操作",{menu=false},dismissEnabled=!ui.busy) {
        fun closeAnd(block:()->Unit) {menu=false;block()}
        if(deleted) PlanDetailMenuAction("恢复","restorePlanDetail",!ui.busy) {vm.run("已恢复",onSuccess={menu=false}) {manage(kind,id,"restore")}}
        else {
            if(archived && kind in setOf("task","event","goal")) PlanDetailMenuAction("取消归档","unarchivePlanDetail",!ui.busy) {vm.run("已取消归档",onSuccess={menu=false}) {manage(kind,id,"archive")}}
            if(editable && kind!="focus") PlanDetailMenuAction(if(repeating) "编辑系列" else "编辑信息","editPlanDetail",!ui.busy) {closeAnd {vm.begin(kind,id=id)}}
            if(editable && task!=null && (!repeating || occurrence!=null)) {
                val row=TaskRow(task,occurrence)
                listOf("doing" to "开始处理","todo" to "回到待办","cancelled" to "取消任务").filter {it.first!=row.status}.forEach {(state,label)->
                    PlanDetailMenuAction(label,"taskState:$state",!ui.busy) {vm.run("状态已更新",onSuccess={menu=false}) {taskAction(row,state)}}
                }
            }
            if(editable && event!=null) PlanDetailMenuAction(if((occurrence?.status ?: event.status)=="cancelled") "恢复安排" else "取消日程","eventState",!ui.busy) {
                vm.run("状态已更新",onSuccess={menu=false}) {if(occurrence!=null) instance(occurrence.id,null,null,if(occurrence.status=="cancelled") "scheduled" else "cancelled") else manage(kind,id,"cancel")}
            }
            if(editable && occurrence!=null) {
                PlanDetailMenuAction("修改本次名称","renamePlanInstance",!ui.busy) {closeAnd {rename=true}}
                PlanDetailMenuAction("调整本次日期","movePlanInstance",!ui.busy) {closeAnd {moveDate=true}}
                if(kind=="task") PlanDetailMenuAction("跳过本次","skipPlanInstance",!ui.busy) {vm.run("本次已跳过",onSuccess={menu=false}) {instance(occurrence.id,null,null,"skipped")}}
                PlanDetailMenuAction("停止后续安排","stopPlanSeries",!ui.busy) {closeAnd {action="stop"}}
            }
            if(editable && habit!=null && habit.status!="ended") {
                PlanDetailMenuAction(if(habit.status=="paused") "继续习惯" else "暂停习惯","pausePlanHabit",!ui.busy) {vm.run("状态已更新",onSuccess={menu=false}) {manage(kind,id,"pause")}}
                PlanDetailMenuAction("结束习惯","endPlanHabit",!ui.busy) {closeAnd {action="end"}}
            }
            if(editable && goal!=null) {
                if(goal.progressSource in setOf("tasks","focus")) PlanDetailMenuAction("管理关联事项","manageGoalSources",!ui.busy) {closeAnd {selectSources=true}}
                PlanDetailMenuAction(if(goal.state=="done") "重新打开" else "确认完成","completePlanGoal",!ui.busy) {closeAnd {action=if(goal.state=="done") "reopen" else "done"}}
                if(goal.state!="done") PlanDetailMenuAction(if(goal.state=="paused") "继续目标" else "暂停目标","pausePlanGoal",!ui.busy) {vm.run("状态已更新",onSuccess={menu=false}) {manage(kind,id,"pause")}}
            }
            if(editable && focus!=null) {
                PlanDetailMenuAction(if(note.isBlank()) "添加备注" else "修改备注","notePlanFocus",!ui.busy) {closeAnd {focusNote=true}}
                if(focus.state in setOf("finished","early")) PlanDetailMenuAction("修正有效时长","correctPlanFocus",!ui.busy) {closeAnd {correction=true}}
            }
            if(kind in setOf("task","event")) PlanDetailMenuAction("复制为新事项","duplicatePlanDetail",!ui.busy) {closeAnd {vm.duplicate(kind,id)}}
            if(editable && kind in setOf("task","event","goal")) PlanDetailMenuAction("归档","archivePlanDetail",!ui.busy) {vm.run("已归档",onSuccess={menu=false}) {manage(kind,id,"archive")}}
            if(kind!="focus" || focus?.endedAt!=null) PlanDetailMenuAction("移入回收站","trashPlanDetail",!ui.busy,true) {closeAnd {action="trash"}}
        }
        ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(entryMenu!=null) GlassSheet("记录",{entryMenu=null},dismissEnabled=!ui.busy) {
        val key=entryMenu!!
        when {
            key.startsWith("log:")->{
                val logId=key.substringAfter(":")
                val habitEntry=s.logs.find {it.habitId==id && it.id==logId}
                val goalEntry=s.progress.find {it.goalId==id && it.id==logId}
                val date=habitEntry?.logDate ?: goalEntry?.logDate
                if(date!=null) {
                    Text(planDateLabel(date),style=MaterialTheme.typography.titleMedium)
                    val amount=habitEntry?.let {entry->planQuantity(entry.valueMilli,s.habitRule(id,date)?.unit.orEmpty(),habit?.trackingType=="duration")}
                        ?: goalEntry?.let {entry->planQuantity(entry.deltaMilli,goal?.unit.orEmpty(),goal?.goalType=="duration")}.orEmpty()
                    Text(amount,style=MaterialTheme.typography.headlineSmall,color=LocalGlass.current.accent)
                    (habitEntry?.note ?: goalEntry?.note)?.takeIf {it.isNotBlank()}?.let {MarkdownBody(it)}
                    if(editable) {
                        HorizontalDivider(color=LocalGlass.current.accent.copy(alpha=.12f))
                        PlanDetailMenuAction("修改记录","editPlanLog",!ui.busy) {entryMenu=null;logEdit=logId}
                        PlanDetailMenuAction("删除记录","deletePlanLog",!ui.busy,true) {entryMenu=null;logDelete=logId}
                    }
                } else Text("此记录已不存在",color=LocalGlass.current.muted)
            }
            key.startsWith("milestone:")->{
                val m=s.milestones.find {it.id==key.substringAfter(":")}
                if(m!=null && editable) {
                    PlanDetailMenuAction("调整里程碑","editPlanMilestone",!ui.busy) {entryMenu=null;milestoneEdit=m}
                    PlanDetailMenuAction("删除里程碑","deletePlanMilestone",!ui.busy,true) {entryMenu=null;action="milestone:"+m.id}
                }
            }
            key.startsWith("source:") && editable->PlanDetailMenuAction("解除关联","unlinkGoalSource",!ui.busy,true) {entryMenu=null;action="unlink:"+key.substringAfter(":")}
        }
    }
    if(reminder) GlassSheet("提醒",{reminder=false}) {PlanReminderStatus(ui,vm,kind,id,occurrence)}
    if(moveDate && occurrence!=null && editable) PlanDatePicker(occurrence.scheduledDate,{moveDate=false}) {date->vm.run("本次日期已调整",onSuccess={moveDate=false}) {instance(occurrence.id,date,null,null)}}
    if(rename && occurrence!=null && editable) {
        var text by remember(occurrence.id) {mutableStateOf(occurrence.title ?: title.orEmpty())}
        GlassSheet("本次名称",{rename=false},dismissEnabled=!ui.busy,footer={GlassButton(enabled=!ui.busy,onClick={vm.run("本次名称已更新",onSuccess={rename=false}) {instance(occurrence.id,null,text,null)}},modifier=Modifier.fillMaxWidth().testTag("savePlanInstanceName")) {Text("保存")}}) {
            GlassTextField(text,{text=it},label={Text("名称")},modifier=Modifier.fillMaxWidth().testTag("planInstanceName"))
            ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    action?.let {a->ConfirmDialog(if(a=="trash") "移入回收站？" else "确认操作",when {
        a=="trash"->"可在回收站恢复。"+(if(repeating) "此操作作用于整个重复系列。" else "")+(if(s.goalLinks.any {it.sourceType==kind && it.sourceId==id}) "关联目标的进度会重新计算。" else "")
        a=="stop"->"撤销今天起未处理的安排，已完成和过去记录保留。"
        a.startsWith("unlink:")->"解除后将重新计算目标进度。"
        a.startsWith("milestone:")->"删除后将重新计算目标进度。"
        a=="done"->"按当前进度确认完成。"
        else->"确认更新当前状态？"
    },{action=null},{
        if(!ui.busy) vm.run("已更新",onSuccess={action=null}) {when {
            a=="stop" && occurrence!=null->stop(occurrence.ruleId)
            a.startsWith("unlink:") && goal!=null->link(id,if(goal.progressSource=="tasks") "task" else "focus",a.substringAfter(":"),true)
            a.startsWith("milestone:")->{val m=s.milestones.first {it.id==a.substringAfter(":")};milestone(id,m.title,m.dueDate,m.id,delete=true)}
            else->manage(kind,id,a)
        }}
    })}
    if(kind=="habit" && habit!=null && editable && (logAdd || s.logs.any {it.habitId==id && it.id==logEdit})) {
        val log=s.logs.find {it.id==logEdit}
        val defaultValue=if(habit.trackingType=="duration") s.habitRule(id,today)?.let {PlanRules.display(it.targetMilli)} ?: "1800" else "1"
        ValueLogSheet("习惯打卡",log?.logDate ?: today,log?.let {PlanRules.display(it.valueMilli)} ?: defaultValue,log?.note.orEmpty(),habit.trackingType=="once",
            {logAdd=false;logEdit=null},busy=ui.busy,duration=habit.trackingType=="duration",error=ui.error) {d,v,n->vm.run(onSuccess={logAdd=false;logEdit=null}) {habitLog(id,d,v,n,log?.id)}}
    }
    if(kind=="goal" && goal!=null && editable && (logAdd || s.progress.any {it.goalId==id && it.id==logEdit})) {
        val log=s.progress.find {it.id==logEdit}
        ValueLogSheet("目标进度",log?.logDate ?: today,log?.let {PlanRules.display(it.deltaMilli)} ?: if(goal.goalType=="duration") "900" else "1",log?.note.orEmpty(),dismiss={logAdd=false;logEdit=null},busy=ui.busy,duration=goal.goalType=="duration",error=ui.error,allowNegative=true) {d,v,n->vm.run(onSuccess={logAdd=false;logEdit=null}) {goalLog(id,d,v,n,log?.id)}}
    }
    logDelete?.let {logId->ConfirmDialog("删除记录？","删除后将重新计算进度。",{logDelete=null},{if(writable) vm.run("记录已删除",onSuccess={logDelete=null}) {
        if(kind=="habit") {val log=s.logs.first {it.id==logId};habitLog(id,log.logDate,"1",id=log.id,delete=true)}
        else {val log=s.progress.first {it.id==logId};goalLog(id,log.logDate,"1","",log.id,true)}
    }})}
    if(selectSources && goal!=null && editable) {
        val existing=s.goalLinks.filter {it.goalId==id}.map {it.sourceId}.toSet()
        val options=if(goal.progressSource=="tasks") s.tasks.filter {it.id in existing || it.deletedAt==null && it.archivedAt==null && s.rules.none {r->r.entityType=="task" && r.entityId==it.id}}.map {it.id to (it.title+(if(it.deletedAt!=null) " · 已删除" else if(it.archivedAt!=null) " · 已归档" else ""))}
            else s.sessions.filter {it.id in existing || it.deletedAt==null && it.state in setOf("finished","early")}.map {it.id to it.title}
        val allOptions=options+existing.filter {source->options.none {it.first==source}}.map {it to "来源已不存在"}
        GlassMultiSelectionSheet("关联事项",allOptions,existing,{selectSources=false},busy=ui.busy,error=ui.error,closeOnConfirm=false) {chosen->vm.run("关联已更新",onSuccess={selectSources=false}) {setGoalSources(id,chosen)}}
    }
    if((newMilestone || milestoneEdit!=null) && editable) MilestoneSheet(milestoneEdit,ui.busy,ui.error,{newMilestone=false;milestoneEdit=null}) {name,date->
        val item=milestoneEdit
        vm.run(onSuccess={newMilestone=false;milestoneEdit=null}) {milestone(id,name,date,item?.id)}
    }
    if(correction && focus!=null && editable) ValueLogSheet("修正有效时长",today,s.focusSeconds(id).toString(),"",dismiss={correction=false},busy=ui.busy,duration=true,error=ui.error,integerSeconds=true) {_,v,n->
        vm.run(onSuccess={correction=false}) {focusAction(id,"correct",n,v.toLongOrNull())}
    }
    if(habitCalendar) PlanDatePicker(habitDate ?: today,{habitCalendar=false},s.logs.filter {it.habitId==id}.map {it.logDate}.toSet()) {habitDate=it}
    if(focusNote && focus!=null && editable) {
        var text by remember(id) {mutableStateOf(focus.note)}
        GlassSheet("专注备注",{focusNote=false},dismissEnabled=!ui.busy,footer={GlassButton(enabled=!ui.busy,onClick={vm.run(onSuccess={focusNote=false}) {focusNote(id,text)}},modifier=Modifier.fillMaxWidth()) {Text("保存")}}) {
            GlassTextField(text,{text=it},label={Text("备注")},minLines=4,modifier=Modifier.fillMaxWidth())
            ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}

@Composable private fun MilestoneSheet(item:Milestone?,busy:Boolean,error:String?,dismiss:()->Unit,save:(String,String?)->Unit) {
    var title by remember(item?.id) {mutableStateOf(item?.title.orEmpty())}
    var date by remember(item?.id) {mutableStateOf(item?.dueDate.orEmpty())}
    var calendar by remember {mutableStateOf(false)}
    GlassSheet("里程碑",dismiss,dismissEnabled=!busy,footer={GlassButton(enabled=!busy,onClick={save(title,date.ifBlank {null})},modifier=Modifier.fillMaxWidth().testTag("saveMilestone")) {Text("保存")}}) {
        GlassTextField(title,{title=it},label={Text("名称")},modifier=Modifier.fillMaxWidth().testTag("milestoneName"))
        DateRow("截止日期",date,{calendar=true},{date=""})
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(calendar) PlanDatePicker(date,{calendar=false}) {date=it}
}
