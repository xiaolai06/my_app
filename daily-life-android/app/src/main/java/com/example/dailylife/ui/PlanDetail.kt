package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.plans.*
import java.time.*
import java.time.temporal.ChronoUnit

@Composable fun PlanDetail(ui:PlansUi,vm:PlansViewModel,records:RecordsSnapshot,view:(Attachment)->Unit,openRecord:(String)->Unit) {
    val id=ui.detailId ?: return;val kind=ui.detailKind ?: return;val s=ui.data
    val t=s.tasks.find {it.id==id};val e=s.events.find {it.id==id};val h=s.habits.find {it.id==id}
    val focus=s.sessions.find {it.id==id};val g=s.goals.find {it.id==id}
    val occurrence=s.occurrences.find {it.id==ui.instanceId}
    val title=when(kind) {"task"->t?.title;"event"->e?.title;"habit"->h?.name;"focus"->focus?.title;else->g?.name}
    val note=when(kind) {"task"->t?.note;"event"->e?.note;"habit"->h?.note;"focus"->focus?.note;else->g?.note}.orEmpty()
    val deleted=when(kind) {"task"->t?.deletedAt;"event"->e?.deletedAt;"habit"->h?.deletedAt;"focus"->focus?.deletedAt;else->g?.deletedAt}!=null
    var action by remember(id) {mutableStateOf<String?>(null)}
    var logEdit by remember(id) {mutableStateOf<String?>(null)}
    var logAdd by remember(id) {mutableStateOf(false)}
    var logDelete by remember(id) {mutableStateOf<String?>(null)}
    var moveDate by remember(id) {mutableStateOf(false)}
    var instanceTitle by remember(occurrence?.id) {mutableStateOf(occurrence?.title ?: title.orEmpty())}
    var selectSources by remember(id) {mutableStateOf(false)}
    var milestone by remember(id) {mutableStateOf<Milestone?>(null)}
    var newMilestone by remember(id) {mutableStateOf(false)}
    var correction by remember(id) {mutableStateOf(false)}
    var focusNote by remember(id) {mutableStateOf(false)}
    var habitCalendar by remember(id) {mutableStateOf(false)}
    var habitDate by remember(id) {mutableStateOf<String?>(null)}
    BackHandler {vm.detail(null)}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={vm.detail(null)},modifier=Modifier.testTag("backPlanDetail")) {Text("返回")}
            Text(planNames.first {it.first==kind}.second+"详情",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            if(!deleted && kind!="focus") TextButton(enabled=!ui.busy,onClick={vm.begin(kind,id=id)}) {Text(if(occurrence==null) "编辑" else "编辑系列")}
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(title==null) {Text("此内容已不存在");return@Column}
            Text(occurrence?.title ?: title,style=MaterialTheme.typography.headlineMedium)
            if(deleted) Text("已在回收站，恢复后可继续操作",color=MaterialTheme.colorScheme.error)
            if(note.isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp)) {MarkdownBody(note)}}
            if(kind=="task" && t!=null) {
                TaskCard(TaskRow(t,occurrence),ui,vm)
                if(!deleted) {
                    FlowChoices(listOf("doing" to "开始处理","todo" to "回到待办","cancelled" to "取消任务"),null) {vm.task(TaskRow(t,occurrence),it)}
                    TextButton(onClick={if(occurrence!=null) moveDate=true else vm.begin("task",id=id)}) {Text(if(occurrence!=null) "只调整本次日期" else "调整计划日期")}
                    t.sourceRecordId?.let {source->TextButton(onClick={openRecord(source)}) {Text("查看来源记录")}}
                }
            }
            if(kind=="event" && e!=null) {
                val date=occurrence?.scheduledDate ?: e.startDate
                val span=ChronoUnit.DAYS.between(LocalDate.parse(e.startDate),LocalDate.parse(e.endDateExclusive))-1
                Text(date+" "+(if(e.allDay) "全天" else e.startTime)+" 至 "+LocalDate.parse(date).plusDays(span)+" "+if(e.allDay) "全天" else e.endTime)
                Text(listOf(e.location,e.zoneId,stateName(occurrence?.status ?: e.status)).filter {it.isNotBlank()}.joinToString(" · "),color=LocalGlass.current.muted)
                Text("日程与任务分别管理，时间结束不会自动完成任务。",color=LocalGlass.current.muted)
                e.taskId?.let {task->TextButton(onClick={vm.detail("task",task)}) {Text("打开关联任务")}}
                if(!deleted) {
                    if(occurrence!=null) {
                        TextButton(onClick={vm.run("本次状态已更新") {instance(occurrence.id,null,null,if(occurrence.status=="cancelled") "scheduled" else "cancelled")}}) {Text(if(occurrence.status=="cancelled") "恢复本次日程" else "取消本次日程")}
                        TextButton(onClick={moveDate=true}) {Text("只调整本次日期")}
                    } else TextButton(onClick={vm.manage(kind,id,"cancel")}) {Text(if(e.status=="cancelled") "恢复安排" else "取消日程")}
                }
            }
            if(occurrence!=null && !deleted) {
                SectionLabel("本次例外","原实例日期 "+occurrence.originalKey+"；调整后仍保留原身份")
                OutlinedTextField(instanceTitle,{instanceTitle=it},label={Text("仅本次名称")},modifier=Modifier.fillMaxWidth())
                TextButton(onClick={vm.run("本次名称已更新") {instance(occurrence.id,null,instanceTitle,null)}}) {Text("保存本次名称")}
                if(kind=="task") TextButton(onClick={vm.run("本次已跳过") {instance(occurrence.id,null,null,"skipped")}}) {Text("跳过本次")}
                TextButton(onClick={action="stop"}) {Text("停止本系列后续安排")}
            }
            if(kind=="habit" && h!=null) {
                val today=LocalDate.now().toString();val r=s.habitRule(id,today)
                Text(stateName(h.status)+" · "+h.startDate+" 起"+(h.endDate?.let {"，至 "+it} ?: ""),color=LocalGlass.current.muted)
                Text("当前连续达标："+s.habitStreak(h,LocalDate.now()).toString()+(if(r?.scheduleType=="weekly") " 周" else " 个应做日"),color=LocalGlass.current.accent)
                if(!deleted) {
                    Button(onClick={logAdd=true}) {Text("登记打卡")}
                    Row {
                        TextButton(onClick={vm.manage(kind,id,"pause")}) {Text(if(h.status=="paused") "恢复习惯" else "暂停习惯")}
                        if(h.status!="ended") TextButton(onClick={action="end"}) {Text("结束习惯")}
                    }
                }
                SectionLabel("打卡日志","编辑会替换原条目，不会重复累加；时长以秒计")
                TextButton(onClick={habitCalendar=true}) {Text("打卡月历 · "+(habitDate ?: "全部历史"))}
                if(habitDate!=null) TextButton(onClick={habitDate=null}) {Text("查看全部日志")}
                val allLogs=s.logs.filter {it.habitId==id}
                val totals=allLogs.groupBy {s.habitRule(id,it.logDate)?.unit.orEmpty()}.map {(unit,logs)->PlanRules.display(logs.sumOf {it.valueMilli})+" "+unit}
                Text("历史共 "+allLogs.map {it.logDate}.distinct().size+" 天"+(if(totals.isEmpty()) "" else "，累计 "+totals.joinToString("、")),color=LocalGlass.current.muted)
                val logs=allLogs.filter {habitDate==null || it.logDate==habitDate}
                if(logs.isEmpty()) Text("暂无打卡")
                logs.forEach {l->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {
                    val rule=s.habitRule(id,l.logDate)
                    Text(l.logDate+" · "+PlanRules.display(l.valueMilli)+" "+rule?.unit.orEmpty())
                    if(l.note.isNotBlank()) Text(l.note,color=LocalGlass.current.muted)
                    Row {TextButton(enabled=!deleted,onClick={logEdit=l.id}) {Text("编辑")}
                        TextButton(enabled=!deleted,onClick={logDelete=l.id}) {Text("删除此条")}}
                }}}
                SectionLabel("规则历史")
                s.habitRules.filter {it.habitId==id}.forEach {v->Text(v.effectiveFrom+" 起 · "+PlanRules.display(v.targetMilli)+" "+v.unit+" · "+
                    when(v.scheduleType) {"daily"->"每天";"weekly"->"每周 "+v.weeklyDays+" 天";else->"星期 "+v.weekdays})}
                s.pauses.filter {it.habitId==id}.forEach {p->Text("暂停："+p.startDate+" 至 "+(p.endDate ?: "现在"),color=LocalGlass.current.muted)}
            }
            if(kind=="focus" && focus!=null) {
                FocusControls(focus,ui,vm,{correction=true})
                SectionLabel("分段记录","只统计工作段；原始秒数与修正原因一并保留")
                TextButton(enabled=!deleted,onClick={focusNote=true}) {Text("补充专注备注")}
                if(focus.endedAt!=null) TextButton(onClick={action=if(deleted) "restore" else "trash"}) {Text(if(deleted) "恢复专注记录" else "移入回收站")}
                s.segments.filter {it.sessionId==id}.forEach {seg->
                    Text((if(seg.phase=="work") "工作" else "休息")+" · 第 "+seg.round+" 轮 · "+clockText(seg.seconds)+
                        if(seg.endAt==null) " · 计时中" else "",color=LocalGlass.current.muted)
                    seg.correctionReason?.let {Text("修正前 "+clockText(seg.originalSeconds ?: 0)+"；原因："+it)}
                }
                if(focus.endedAt!=null && focus.taskId!=null && focus.state!="abandoned") {
                    val linked=s.tasks.find {it.id==focus.taskId && it.deletedAt==null}
                    if(linked!=null && linked.status!="done") {
                        val needsChoice=s.subtasks.any {it.taskId==linked.id && it.completedAt==null} || s.rules.any {it.entityType=="task" && it.entityId==linked.id}
                        TextButton(enabled=!ui.busy && !deleted,onClick={if(needsChoice) vm.detail("task",linked.id) else vm.task(TaskRow(linked),"done")}) {
                            Text(if(needsChoice) "打开关联任务并确认完成" else "确认完成关联任务")
                        }
                    }
                }
            }
            if(kind=="goal" && g!=null) {
                val(v,target)=s.goalValue(g)
                Text(PlanRules.display(v)+" / "+PlanRules.display(target)+" "+g.unit+" · "+stateName(g.state),style=MaterialTheme.typography.titleLarge,color=LocalGlass.current.accent)
                if(s.goalReview(g)) Text("来源变化使进度回落，请复核并重新打开目标。",color=MaterialTheme.colorScheme.error)
                Text(listOfNotNull(g.startDate?.let {"开始 "+it},g.endDate?.let {"结束 "+it}).joinToString(" · "),color=LocalGlass.current.muted)
                if(!deleted) {
                    if(g.progressSource=="manual") Button(onClick={logAdd=true}) {Text("记录进度")}
                    if(g.progressSource in setOf("tasks","focus")) Button(onClick={selectSources=true}) {Text("管理关联来源")}
                    if(g.progressSource=="milestones") Button(onClick={newMilestone=true}) {Text("添加里程碑")}
                    Row {
                        TextButton(onClick={action=if(g.state=="done") "reopen" else "done"}) {Text(if(g.state=="done") "重新打开" else "确认完成")}
                        if(g.state!="done") TextButton(onClick={vm.manage(kind,id,"pause")}) {Text(if(g.state=="paused") "继续目标" else "暂停目标")}
                    }
                }
                if(g.progressSource=="manual") s.progress.filter {it.goalId==id}.forEach {l->
                    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {
                        Text(l.logDate+" · "+PlanRules.display(l.deltaMilli)+" "+g.unit);Text(l.note,color=LocalGlass.current.muted)
                        Row {TextButton(enabled=!deleted,onClick={logEdit=l.id}) {Text("编辑")}
                            TextButton(enabled=!deleted,onClick={logDelete=l.id}) {Text("删除此条")}}
                    }}
                }
                if(g.progressSource in setOf("tasks","focus")) s.goalLinks.filter {it.goalId==id}.forEach {l->
                    val label=if(l.sourceType=="task") s.tasks.find {it.id==l.sourceId}?.let {it.title+" · "+stateName(it.status)+(if(it.deletedAt!=null) " · 已删除，不计入进度" else "")}
                        else s.sessions.find {it.id==l.sourceId}?.let {it.title+" · "+clockText(s.focusSeconds(it.id))}
                    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp)) {
                        TextButton(onClick={vm.detail(l.sourceType,l.sourceId)}) {Text(label ?: "来源已不存在")}
                        TextButton(enabled=!deleted,onClick={action="unlink:"+l.sourceId}) {Text("解除关联并重算进度")}
                    }}
                }
                if(g.progressSource=="milestones") s.milestones.filter {it.goalId==id}.forEach {m->
                    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(m.completedAt!=null,{vm.run {milestone(id,m.title,m.dueDate,m.id,toggle=true)}},enabled=!ui.busy && !deleted)
                            Text(m.title,Modifier.weight(1f))
                        }
                        m.dueDate?.let {Text("截止 "+it,color=LocalGlass.current.muted)}
                        Row {TextButton(enabled=!deleted,onClick={milestone=m}) {Text("编辑")}
                            TextButton(enabled=!deleted,onClick={action="milestone:"+m.id}) {Text("删除里程碑")}}
                    }}
                }
            }
            if(kind!="focus") {
                val files=records.attachmentsFor(kind,id)
                if(files.isNotEmpty()) {SectionLabel("附件");files.forEach {a->TextButton(onClick={view(a)}) {Text(a.displayName)}}}
                if(deleted) TextButton(enabled=!ui.busy,onClick={vm.manage(kind,id,"restore")}) {Text("从回收站恢复")}
                else {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        if(kind in setOf("task","event")) TextButton(enabled=!ui.busy,onClick={vm.duplicate(kind,id)}) {Text("复制为新草稿")}
                        if(kind in setOf("task","event","goal")) TextButton(enabled=!ui.busy,onClick={vm.manage(kind,id,"archive")}) {Text("切换归档")}
                        TextButton(enabled=!ui.busy,onClick={action="trash"}) {Text("移入回收站")}
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    if(moveDate) CalendarDialog(occurrence?.scheduledDate ?: t?.plannedDate ?: e?.startDate ?: LocalDate.now().toString(),{moveDate=false}) {date->
        if(occurrence!=null) vm.run("本次日期已调整") {instance(occurrence.id,date,null,null)}
    }
    action?.let {a->ConfirmDialog("确认操作",when {
        a=="trash"->"内容与附件引用会保留在回收站，可以恢复。"+if(s.goalLinks.any {it.sourceType==kind && it.sourceId==id}) "此项同时是目标来源，删除后会重新计算目标进度，已完成目标可能需要复核。" else ""
        a=="stop"->"从今天起未处理的重复实例会撤销，已经完成和过去的实例保留。"
        a.startsWith("unlink:")->"解除后会重新计算分母和已完成数量，目标进度可能变化。"
        a.startsWith("milestone:")->"删除后会重新计算目标进度。"
        a=="done"->"按当前进度确认完成；来源后续回退时会显示待复核。"
        else->"此操作会更新当前状态。"
    },{action=null},{
        when {
            a=="stop" && occurrence!=null->vm.run {stop(occurrence.ruleId)}
            a.startsWith("unlink:") && g!=null->vm.run {link(id,if(g.progressSource=="tasks") "task" else "focus",a.substringAfter(":"),true)}
            a.startsWith("milestone:")->vm.run {val m=s.milestones.first {it.id==a.substringAfter(":")};milestone(id,m.title,m.dueDate,m.id,delete=true)}
            else->vm.manage(kind,id,a)
        }
    })}
    if(kind=="habit" && h!=null && (logAdd || logEdit!=null)) {
        val l=s.logs.find {it.id==logEdit}
        ValueLogSheet("习惯打卡",l?.logDate ?: LocalDate.now().toString(),l?.let {PlanRules.display(it.valueMilli)} ?: "1",l?.note.orEmpty(),h.trackingType=="once",
            {logAdd=false;logEdit=null}) {d,v,n->vm.run(onSuccess={logAdd=false;logEdit=null}) {habitLog(id,d,v,n,l?.id)}}
    }
    if(kind=="goal" && (logAdd || logEdit!=null)) {
        val l=s.progress.find {it.id==logEdit}
        ValueLogSheet("目标进度",l?.logDate ?: LocalDate.now().toString(),l?.let {PlanRules.display(it.deltaMilli)} ?: "1",l?.note.orEmpty(),
            dismiss={logAdd=false;logEdit=null}) {d,v,n->vm.run(onSuccess={logAdd=false;logEdit=null}) {goalLog(id,d,v,n,l?.id)}}
    }
    logDelete?.let {deleteId->ConfirmDialog("删除这条日志？","只删除所选条目，并重新计算进度。",{logDelete=null},{
        if(kind=="habit") vm.run {val l=s.logs.first {it.id==deleteId};habitLog(id,l.logDate,"1",id=l.id,delete=true)}
        else vm.run {val l=s.progress.first {it.id==deleteId};goalLog(id,l.logDate,"1","",l.id,true)}
    })}
    if(selectSources && g!=null) GlassSheet("关联来源",{selectSources=false}) {
        val options=if(g.progressSource=="tasks") s.tasks.filter {it.deletedAt==null && s.rules.none {r->r.entityId==it.id}}.map {it.id to it.title}
            else s.sessions.filter {it.deletedAt==null && it.state in setOf("finished","early")}.map {it.id to (it.title+" · "+clockText(s.focusSeconds(it.id)))}
        Text("同一来源只计入一次。已关联的来源可在详情解除。")
        options.forEach {(source,label)->val linked=s.goalLinks.any {it.goalId==id && it.sourceId==source}
            GlassChoice(label,linked,Modifier.fillMaxWidth()) {if(!linked) vm.run {link(id,if(g.progressSource=="tasks") "task" else "focus",source)}}
        }
        if(options.isEmpty()) Text("暂无可关联内容，请先创建任务或完成一段专注")
    }
    if(newMilestone || milestone!=null) MilestoneSheet(milestone,ui.busy,{newMilestone=false;milestone=null}) {name,date->
        val item=milestone
        vm.run(onSuccess={newMilestone=false;milestone=null}) {milestone(id,name,date,item?.id)}
    }
    if(correction && focus!=null) ValueLogSheet("修正有效工作秒数",LocalDate.now().toString(),s.focusSeconds(id).toString(),"",
        dismiss={correction=false}) {_,v,n->vm.run(onSuccess={correction=false}) {focusAction(id,"correct",n,v.toLongOrNull())}}
    if(habitCalendar) CalendarDialog(habitDate ?: LocalDate.now().toString(),{habitCalendar=false},
        s.logs.filter {it.habitId==id}.map {it.logDate}.toSet()) {habitDate=it}
    if(focusNote && focus!=null) {
        var text by remember {mutableStateOf(focus.note)}
        GlassSheet("专注备注",{focusNote=false}) {
            OutlinedTextField(text,{text=it},label={Text("备注 · 支持 Markdown")},minLines=4,modifier=Modifier.fillMaxWidth())
            Button(enabled=!ui.busy,onClick={vm.run(onSuccess={focusNote=false}) {focusNote(id,text)}}) {Text("保存备注")}
        }
    }
}
@Composable private fun FocusControls(s:FocusSession,ui:PlansUi,vm:PlansViewModel,correct:()->Unit) {
    val total=ui.data.focusSeconds(s.id)+(if(s.state=="running" && s.phase=="work") ui.liveSeconds else 0)
    Text(clockText(total),style=MaterialTheme.typography.displayMedium)
    Text(stateName(s.state)+" · "+if(s.phase=="work") "工作，第 "+s.round+" 轮" else "休息，不计入专注",color=LocalGlass.current.accent)
    val active=s.state in setOf("running","paused","review","awaiting")
    if(active) {
        if(s.state=="review" || ui.clockReview) Text("计时基准发生变化。继续会丢弃无法确认的开放时段，已完成工作段保留。",color=MaterialTheme.colorScheme.error)
        val choices=when(s.state) {
            "running"->listOf("pause" to "暂停")
            "paused"->listOf("resume" to "继续")
            else->listOf("recover" to "核对后继续")
        }+if(s.mode=="pomodoro") {
            if(s.phase=="work") listOf("break" to "完成本轮，休息") else listOf("next" to "开始下一轮")
        } else emptyList()
        FlowChoices(choices,null) {vm.focus(s.id,it)}
        var confirm by remember {mutableStateOf<String?>(null)}
        FlowChoices(listOf("finish" to "正常结束","early" to "提前结束","abandon" to "放弃本次"),null) {confirm=it}
        confirm?.let {a->ConfirmDialog("结束这次专注？",if(a=="abandon") "放弃的记录会保留，但不进入有效专注统计和目标。" else "只计入工作时间，暂停与休息会排除。",{confirm=null},{vm.focus(s.id,a)})}
    } else if(s.state in setOf("finished","early")) TextButton(onClick=correct) {Text("修正有效时长并填写原因")}
}
@Composable private fun MilestoneSheet(item:Milestone?,busy:Boolean,dismiss:()->Unit,save:(String,String?)->Unit) {
    var title by remember {mutableStateOf(item?.title.orEmpty())};var date by remember {mutableStateOf(item?.dueDate.orEmpty())}
    var calendar by remember {mutableStateOf(false)}
    GlassSheet("里程碑",dismiss) {
        OutlinedTextField(title,{title=it},label={Text("里程碑名称")},modifier=Modifier.fillMaxWidth())
        DateRow("截止日期",date,{calendar=true},{date=""})
        Button(enabled=!busy,onClick={save(title,date.ifBlank {null})}) {Text("保存里程碑")}
    }
    if(calendar) CalendarDialog(date,{calendar=false}) {date=it}
}
