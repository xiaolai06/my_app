package com.example.dailylife.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.tools.ToolsSnapshot
import java.time.*
import java.time.temporal.ChronoUnit
import org.json.*

internal val planNames=listOf("task" to "任务","event" to "日程","habit" to "习惯","focus" to "专注","goal" to "目标")
internal fun stateName(s:String)=mapOf("todo" to "待办","doing" to "进行中","done" to "已完成","cancelled" to "已取消",
    "skipped" to "已跳过","scheduled" to "已安排","paused" to "已暂停","active" to "进行中","ended" to "已结束",
    "running" to "正在计时","review" to "需要核对","finished" to "正常结束","early" to "提前结束","abandoned" to "已放弃")[s] ?: s
internal fun clockText(seconds:Long):String { val n=seconds.coerceAtLeast(0); return "%02d:%02d:%02d".format(n/3600,n/60%60,n%60) }
@Composable private fun PlanEmpty(title:String,body:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=38.dp,horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.headlineSmall);Text(body,color=LocalGlass.current.muted)
    }
}
@Composable fun PlansPane(ui:PlansUi,vm:PlansViewModel,initialTab:String?=null,tools:ToolsSnapshot=ToolsSnapshot(),openTool:(String)->Unit={},onLandingHandled:()->Unit={}) {
    var tab by rememberSaveable { mutableStateOf("任务") }
    var add by rememberSaveable { mutableStateOf(false) }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val tabStates=rememberSaveableStateHolder()
    val tabScroll=rememberLazyListState()
    val reduceMotion=LocalReduceMotion.current
    val largeFont=LocalDensity.current.fontScale>1.3f
    LaunchedEffect(initialTab) {if(initialTab in listOf("任务","日历","习惯","专注","目标")) {tab=initialTab!!;onLandingHandled()}}
    LaunchedEffect(tab) {val index=listOf("任务","日历","习惯","专注","目标").indexOf(tab);if(index>=0) {if(reduceMotion) tabScroll.scrollToItem(index) else tabScroll.animateScrollToItem(index)}}
    Column(Modifier.fillMaxSize()) {
        LazyRow(state=tabScroll,contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf("任务","日历","习惯","专注","目标")) { name -> GlassChoice(name,tab==name,Modifier.testTag("planTab:"+name)) { tab=name } }
        }
        if(!ui.ready) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            if(ui.error!=null) Text(ui.error) else CircularProgressIndicator() }; return@Column }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState=tab,animationSpec=tween(if(LocalReduceMotion.current) 0 else 160),label="plan pages") {current->
            tabStates.SaveableStateProvider(current) {when(current) {
                "任务"->TaskPane(ui,vm)
                "日历"->CalendarPane(ui,vm,date,{date=it},tools,openTool)
                "习惯"->HabitPane(ui,vm)
                "专注"->FocusPane(ui,vm)
                "目标"->GoalPane(ui,vm)
            }}}
            if(!largeFont) FloatingActionButton(onClick={add=true},modifier=Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("addPlan"),
                shape=RoundedCornerShape(26.dp),containerColor=LocalGlass.current.selected,contentColor=LocalGlass.current.accent) {
                Text("＋ "+when(tab) {"任务"->"新任务";"日历"->"新日程";"习惯"->"新习惯";"专注"->"开始专注";else->"新目标"},Modifier.padding(horizontal=20.dp))
            }
        }
        if(largeFont) FilledTonalButton(onClick={add=true},enabled=!ui.busy,
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).heightIn(min=56.dp).testTag("addPlan"),
            colors=ButtonDefaults.filledTonalButtonColors(containerColor=LocalGlass.current.selected,contentColor=LocalGlass.current.accent)) {
            Text("＋ "+when(tab) {"任务"->"新任务";"日历"->"新日程";"习惯"->"新习惯";"专注"->"开始专注";else->"新目标"})
        }
    }
    if(add) GlassSheet("安排一件事",{add=false}) {
        planNames.forEach { (kind,name) -> TextButton(enabled=!ui.busy,onClick={add=false;vm.begin(kind,if(kind=="event") date else null)},
            modifier=Modifier.fillMaxWidth().testTag("newPlan:"+kind)) { Text(name) } }
        HorizontalDivider()
        TextButton(onClick={add=false;reminders=true},modifier=Modifier.fillMaxWidth()) {Text("提醒与草稿")}
        Text("也可查看归档事项与计划回收站",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
    }
    if(reminders) ReminderSheet(ui,vm,openTool) {reminders=false}
}
@Composable private fun TaskPane(ui:PlansUi,vm:PlansViewModel) {
    var filter by rememberSaveable { mutableStateOf("all") };var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("all") };var priority by rememberSaveable { mutableStateOf("all") }
    var groups by remember { mutableStateOf(false) };var filters by remember { mutableStateOf(false) }
    var status by rememberSaveable {mutableStateOf("all")}
    var filterDate by rememberSaveable {mutableStateOf("")};var datePicker by remember {mutableStateOf(false)}
    var completeBatch by remember {mutableStateOf(false)}
    var selected by remember { mutableStateOf(setOf<String>()) };var batchMove by remember { mutableStateOf(false) }
    val today=LocalDate.now()
    val regular=ui.data.taskRows()
    val rows=if(filter=="trash" || filter=="archive") ui.data.tasks.filter { if(filter=="trash") it.deletedAt!=null else it.deletedAt==null && it.archivedAt!=null }.map { TaskRow(it) } else regular
    val visible=rows.filter { row ->
        (query.isBlank() || row.title.contains(query,true) || row.task.note.contains(query,true)) &&
        (group=="all" || group=="none" && row.task.groupId==null || row.task.groupId==group) &&
        (priority=="all" || row.task.priority.toString()==priority) &&
        (status=="all" || row.status==status) &&
        (filterDate.isBlank() || row.date==filterDate || row.due==filterDate) &&
        when(filter) {
            "today"->row.status in setOf("todo","doing") && (row.date==today.toString() || row.due==today.toString())
            "week"->row.status in setOf("todo","doing") && listOfNotNull(row.date,row.due).any { it>=today.toString() && it<=today.plusDays(6).toString() }
            "undated"->row.date==null && row.due==null
            "done"->row.status=="done";"pending"->row.status in setOf("todo","doing")
            else->true
        }
    }.sortedWith(compareBy<TaskRow> { it.status=="done" }.thenByDescending { it.task.priority }.thenBy { it.date ?: it.due ?: "9999" })
    Column {
        Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedTextField(query,{query=it},placeholder={Text("搜索任务与备注")},singleLine=true,shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f).testTag("planSearch"))
            TextButton(onClick={filters=true}) { Text("筛选") }
        }
        LazyRow(contentPadding=PaddingValues(18.dp,10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf("all" to "全部","pending" to "待办","today" to "今天","week" to "近7天","undated" to "无日期","done" to "已完成","archive" to "归档","trash" to "回收站")) { (id,label) ->
                GlassChoice(label,filter==id) {filter=id;selected=emptySet()}
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(visible.size.toString()+" 项 · 长按可多选",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            TextButton(onClick={groups=true}) {Text("管理分组")}
        }
        if(selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(onClick={selected=emptySet()}) {Text("取消 "+selected.size)}
            TextButton(onClick={completeBatch=true}) {Text("完成")}
            TextButton(onClick={batchMove=true}) {Text("移分组")}
            TextButton(onClick={vm.run("已更新") { visible.filter { it.key in selected }.map {it.task.id}.distinct().forEach { manage("task",it,if(filter=="trash") "restore" else "trash") } };selected=emptySet()}) {Text(if(filter=="trash") "恢复" else "回收站")}
        }
        LazyColumn(Modifier.fillMaxSize().testTag("planTaskList"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(visible.isEmpty()) item {PlanEmpty("给想做的事一个位置","未设日期的任务不会自动算作今天。点击「新任务」添加。")}
            items(visible,key={it.key}) { row -> TaskCard(row,ui,vm,row.key in selected,{selected=if(row.key in selected) selected-row.key else selected+row.key},selected.isNotEmpty()) }
        }
    }
    if(groups) GroupSheet(ui,vm) {groups=false}
    if(filters) GlassSheet("任务筛选",{filters=false}) {
        SectionLabel("分组");FlowChoices(listOf("all" to "全部","none" to "未分组")+ui.data.groups.map {it.id to it.name},group) {group=it}
        SectionLabel("优先级");FlowChoices(listOf("all" to "全部","0" to "低","1" to "中","2" to "高"),priority) {priority=it}
        SectionLabel("状态");FlowChoices(listOf("all" to "全部","todo" to "待办","doing" to "进行中","done" to "完成","cancelled" to "取消","skipped" to "跳过"),status) {status=it}
        DateRow("指定日期",filterDate,{datePicker=true},{filterDate=""})
        Button(onClick={filters=false}) {Text("查看结果")}
    }
    if(datePicker) CalendarDialog(filterDate,{datePicker=false}) {filterDate=it}
    if(completeBatch) GlassSheet("批量完成任务",{completeBatch=false}) {
        Text("有子任务时，选择是否同时完成。各次重复实例独立处理。")
        listOf(true to "同时完成子任务",false to "保留子任务状态").forEach {(all,label)->
            Button(enabled=!ui.busy,onClick={val targets=visible.filter {it.key in selected}
                vm.run("已批量完成",onSuccess={selected=emptySet();completeBatch=false}) {targets.forEach {taskAction(it,"done",all)}}}) {Text(label)}
        }
    }
    if(batchMove) GlassSheet("批量移到分组",{batchMove=false}) {
        (listOf(null to "未分组")+ui.data.groups.filter {it.archivedAt==null}.map {it.id to it.name}).forEach { (id,name) ->
            TextButton(onClick={vm.run("分组已更新") { moveTasks(visible.filter {it.key in selected}.map {it.task.id}.toSet(),id) };batchMove=false;selected=emptySet()}) {Text(name)}
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun TaskCard(row:TaskRow,ui:PlansUi,vm:PlansViewModel,selected:Boolean=false,toggle:()->Unit={},selecting:Boolean=false) {
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) };var complete by remember {mutableStateOf(false)}
    val children=ui.data.subtasks.filter {it.taskId==row.task.id}
    val unfinished=children.count { if(row.occurrence!=null) it.id !in row.occurrence.subtaskStates.split(",") else it.completedAt==null }
    GlassPanel(Modifier.fillMaxWidth().combinedClickable(onClick={if(selecting) toggle() else vm.detail("task",row.task.id,row.occurrence?.id)},onLongClick=toggle),selected=selected) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(row.status=="done",onCheckedChange={
                    if(row.status!="done" && unfinished>0) complete=true else vm.task(row,if(row.status=="done") "todo" else "done")
                },enabled=!ui.busy && row.task.deletedAt==null && row.status!="cancelled",modifier=Modifier.testTag("completeTask:"+row.key))
                Column(Modifier.weight(1f)) {
                    Text(row.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(listOfNotNull(if(row.task.priority==2) "高优先级" else null,ui.data.groups.find {it.id==row.task.groupId}?.name,
                        row.date?.let {"计划 "+it},row.due?.let {"截止 "+it+" "+row.task.dueTime.orEmpty()}).joinToString(" · "),
                        style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
                if(selected) Text("✓",color=LocalGlass.current.accent)
            }
            Text(stateName(row.status)+(if(row.occurrence!=null) " · 重复事项" else "")+
                if(row.status in setOf("todo","doing") && PlanRules.overdue(row.due,row.task.dueTime)) " · 已逾期" else "",
                style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.accent)
            if(children.isNotEmpty()) {
                TextButton(onClick={expanded=!expanded}) {Text((if(expanded) "收起" else "展开")+"子任务 · "+unfinished+" 项未完成")}
                if(expanded) children.forEach { child -> Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(if(row.occurrence!=null) child.id in row.occurrence.subtaskStates.split(",") else child.completedAt!=null,
                        {vm.run("子任务已更新") {subtask(child.id,row.occurrence?.id)}},enabled=!ui.busy)
                    Text(child.title,Modifier.weight(1f))
                } }
            }
        }
    }
    if(complete) GlassSheet("还有 "+unfinished+" 项子任务",{complete=false}) {
        Text("主任务可以独立完成，请选择子任务如何处理。")
        Button(onClick={vm.task(row,"done",true);complete=false}) {Text("同时完成子任务")}
        TextButton(onClick={vm.task(row,"done",false);complete=false}) {Text("保留子任务状态")}
    }
}
internal fun dayEvents(s:PlansSnapshot,date:String):List<Pair<PlanEvent,Occurrence?>> = s.events.filter {it.deletedAt==null && it.archivedAt==null}.flatMap { e ->
    val repeated=s.rules.any {it.entityType=="event" && it.entityId==e.id}
    (if(repeated) s.occurrences.filter {it.entityId==e.id}.map {it as Occurrence?} else listOf(null)).filter { o ->
        val start=o?.scheduledDate ?: e.startDate
        val span=ChronoUnit.DAYS.between(LocalDate.parse(e.startDate),LocalDate.parse(e.endDateExclusive))
        date>=start && date<LocalDate.parse(start).plusDays(span).toString()
    }.map { e to it }
}
@Composable private fun CalendarPane(ui:PlansUi,vm:PlansViewModel,date:String,select:(String)->Unit,tools:ToolsSnapshot,openTool:(String)->Unit) {
    var picker by remember {mutableStateOf(false)};var filter by rememberSaveable {mutableStateOf("all")}
    val d=LocalDate.parse(date);val events=dayEvents(ui.data,date)
    val toolRows=remember(tools,date) {toolCalendarEntries(tools,d)}
    val toolDates=remember(tools,d.year,d.monthValue) {toolCalendarDates(tools,d.withDayOfMonth(1),d.withDayOfMonth(d.lengthOfMonth()))}
    LazyColumn(Modifier.fillMaxSize().testTag("planCalendar"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={select(d.minusMonths(1).toString())}) {Text("上月")}
                TextButton(onClick={picker=true},modifier=Modifier.weight(1f)) {Text(d.year.toString()+" 年 "+d.monthValue+" 月 ▾")}
                TextButton(onClick={select(d.plusMonths(1).toString())}) {Text("下月")}
            }
            PlanMonth(d,select,ui.data,toolDates)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={select(d.minusDays(1).toString())}) {Text("前一天")}
                Text(date,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                TextButton(onClick={select(d.plusDays(1).toString())}) {Text("后一天")}
            }
            TextButton(onClick={select(LocalDate.now().toString())}) {Text("回到今天")}
            FlowChoices(listOf("all" to "全部","task" to "任务","event" to "日程","tool" to "生活提醒"),filter) {filter=it}
        } }}
        if(filter in setOf("all","event")) {
            item {SectionLabel("当天日程","全天与跨日时间均以原输入时区保存")}
            if(events.isEmpty()) item {Text("这一天还没有日程",color=LocalGlass.current.muted)}
            items(events,key={it.second?.id ?: it.first.id}) { (e,o) ->
                GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("event",e.id,o?.id)}) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(o?.title ?: e.title,style=MaterialTheme.typography.titleMedium)
                        val offset=ChronoUnit.DAYS.between(LocalDate.parse(e.startDate),LocalDate.parse(o?.scheduledDate ?: e.startDate))
                        Text(if(e.allDay) "全天 · "+(o?.scheduledDate ?: e.startDate)+" 至 "+LocalDate.parse(e.endDateExclusive).minusDays(1).plusDays(offset)
                            else (o?.scheduledDate ?: e.startDate)+" "+e.startTime+" — "+LocalDate.parse(e.endDateExclusive).minusDays(1).plusDays(offset)+" "+e.endTime,
                            color=LocalGlass.current.muted)
                        Text(e.location.ifBlank {e.zoneId},style=MaterialTheme.typography.bodySmall)
                        Text(if((o?.status ?: e.status)=="cancelled") "已取消" else if(e.allDay) "已安排" else {
                            val now=System.currentTimeMillis();val zone=ZoneId.of(e.zoneId)
                            val start=LocalDate.parse(e.startDate).plusDays(offset).atTime(LocalTime.parse(e.startTime)).atZone(zone).toInstant().toEpochMilli()
                            val end=LocalDate.parse(e.endDateExclusive).minusDays(1).plusDays(offset).atTime(LocalTime.parse(e.endTime)).atZone(zone).toInstant().toEpochMilli()
                            if(now<start) "未开始" else if(now<end) "进行中" else "已结束"
                        },color=LocalGlass.current.accent)
                    }
                }
            }
        }
        if(filter in setOf("all","task")) {
            item {SectionLabel("当天任务","计划日期与截止日期命中同一天只展示一次")}
            val tasks=ui.data.taskRows().filter {it.date==date || it.due==date}
            if(tasks.isEmpty()) item {Text("这一天还没有任务",color=LocalGlass.current.muted)}
            items(tasks,key={"task"+it.key}) {TaskCard(it,ui,vm)}
        }
        if(filter in setOf("all","tool")) {
            item {SectionLabel("生活提醒","纪念日、订阅和缴费直接读取功能箱，点按回到原项目")}
            if(toolRows.isEmpty()) item {Text("这一天没有生活提醒",color=LocalGlass.current.muted)}
            items(toolRows,key={it.key}) {row->GlassPanel(Modifier.fillMaxWidth().clickable {openTool(row.sourceId)}.testTag("calendarTool:"+row.key)) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(row.title,style=MaterialTheme.typography.titleMedium)
                    Text(row.kind+" · "+row.description,color=LocalGlass.current.muted)
                    Text("打开原项目 ›",style=MaterialTheme.typography.labelLarge,color=LocalGlass.current.accent)
                }
            }}
        }
    }
    if(picker) CalendarDialog(date,{picker=false},ui.data.events.map {it.startDate}.toSet()+ui.data.taskRows().mapNotNull {it.date}.toSet()+toolDates) {select(it)}
}
@Composable private fun HabitPane(ui:PlansUi,vm:PlansViewModel) {
    var date by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var picker by remember {mutableStateOf(false)}
    var log by remember {mutableStateOf<Habit?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,8.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick={picker=true}) {Text("打卡日期："+date+" ▾")}}
        val habits=ui.data.habits.filter {it.deletedAt==null}
        if(habits.isEmpty()) item {PlanEmpty("慢慢建立自己的节奏","完成一次、累计数量或累计时长，都可以按自己的目标登记。")}
        items(habits,key={it.id}) {h-> val r=ui.data.habitRule(h.id,date);val value=ui.data.habitTotal(h.id,date)
            GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("habit",h.id)}) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(h.name,style=MaterialTheme.typography.titleLarge)
                    if(r!=null) {
                        Text(PlanRules.display(value)+" / "+PlanRules.display(r.targetMilli)+" "+r.unit+(if(value>=r.targetMilli) " · 已达标" else ""),color=LocalGlass.current.accent)
                        LinearProgressIndicator(progress={ (value.toDouble()/r.targetMilli).toFloat().coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth())
                        Text(if(r.scheduleType=="weekly") "本周已达标 "+ui.data.weekDays(h,LocalDate.parse(date))+" / "+r.weeklyDays+" 天"
                            else if(ui.data.habitScheduled(h,date)) "应做日" else "休息或暂停日",color=LocalGlass.current.muted)
                    }
                    Row {Text(stateName(h.status),Modifier.weight(1f))
                        TextButton(enabled=!ui.busy && h.status!="ended" && ui.data.habitScheduled(h,date),onClick={log=h}) {Text("登记打卡")}}
                }
            }
        }
    }
    if(picker) CalendarDialog(date,{picker=false}) {date=it}
    log?.let {h->ValueLogSheet("登记「"+h.name+"」",date,"1","",h.trackingType=="once",{log=null}) {d,v,n->vm.run(onSuccess={log=null}) {habitLog(h.id,d,v,n)}}}
}
@Composable private fun FocusPane(ui:PlansUi,vm:PlansViewModel) {
    var range by rememberSaveable {mutableStateOf("week")}
    val active=ui.data.sessions.firstOrNull {it.deletedAt==null && it.state in setOf("running","paused","review","awaiting")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,8.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(active!=null) item {
            val seconds=ui.data.focusSeconds(active.id)+if(active.phase=="work") ui.liveSeconds else 0
            GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(active.title,style=MaterialTheme.typography.titleLarge)
                Text(if(active.phase=="break") "休息 · 不计入有效专注" else "工作段 · 第 "+active.round+" 轮",color=LocalGlass.current.muted)
                Text(clockText(if(active.phase=="break") ui.liveSeconds else seconds),style=MaterialTheme.typography.displayMedium,modifier=Modifier.testTag("focusTimer"))
                Text(stateName(active.state),color=LocalGlass.current.accent)
                if(active.state=="review" || ui.clockReview) Text("设备重启或时间发生变化。离线间隔不会直接计入，请核对后继续；历史工作段仍保留。",color=MaterialTheme.colorScheme.error)
                FlowChoices(listOf("detail" to "计时操作与结束"),null) {vm.detail("focus",active.id)}
            }}
        } else item {PlanEmpty("留一段完整的时间","正计时、倒计时或番茄钟。暂停和休息不计入专注，退出页面也会保留计时。")}
        item {FlowChoices(listOf("day" to "今天","week" to "本周","month" to "本月"),range) {range=it}}
        val today=LocalDate.now();val start=when(range) {"day"->today;"week"->today.with(DayOfWeek.MONDAY);else->today.withDayOfMonth(1)}
        val history=ui.data.sessions.filter {it.deletedAt==null && it.endedAt!=null && Instant.ofEpochMilli(it.endedAt).atZone(ZoneId.systemDefault()).toLocalDate()>=start}
        item {SectionLabel("专注历史","正常结束 "+clockText(ui.data.focusRange(history.filter {it.state=="finished"}.map {it.id}.toSet(),start,today.plusDays(1)))+
            " · 提前结束 "+clockText(ui.data.focusRange(history.filter {it.state=="early"}.map {it.id}.toSet(),start,today.plusDays(1))))}
        items(history,key={it.id}) {s->GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("focus",s.id)}) {
            Column(Modifier.padding(18.dp)) {Text(s.title,style=MaterialTheme.typography.titleMedium)
                Text(clockText(ui.data.focusSeconds(s.id))+" · "+stateName(s.state),color=LocalGlass.current.muted)}
        }}
    }
}
@Composable private fun GoalPane(ui:PlansUi,vm:PlansViewModel) {
    var archived by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,8.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {FlowChoices(listOf("active" to "进行与完成","archive" to "已归档"),if(archived) "archive" else "active") {archived=it=="archive"}}
        val goals=ui.data.goals.filter {it.deletedAt==null && (it.archivedAt!=null)==archived}
        if(goals.isEmpty()) item {PlanEmpty("把想要的生活写具体","选择一种进度来源：手动日志、任务、专注或里程碑，达到目标后由你确认完成。")}
        items(goals,key={it.id}) {g->val(value,target)=ui.data.goalValue(g)
            GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail("goal",g.id)}) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(g.name,style=MaterialTheme.typography.titleLarge)
                    Text(PlanRules.display(value)+" / "+PlanRules.display(target)+" "+g.unit,color=LocalGlass.current.accent)
                    if(target>0) LinearProgressIndicator(progress={(value.toDouble()/target).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    Text(stateName(g.state)+(if(ui.data.goalReview(g)) " · 来源变化，待复核" else if(target>0 && value>=target && g.state!="done") " · 已达目标，可确认完成" else ""),color=LocalGlass.current.muted)
                }
            }
        }
    }
}
@Composable private fun GroupSheet(ui:PlansUi,vm:PlansViewModel,dismiss:()->Unit) {
    var name by remember {mutableStateOf("")};var editing by remember {mutableStateOf<TaskGroup?>(null)}
    var remove by remember {mutableStateOf<TaskGroup?>(null)}
    GlassSheet("任务分组",dismiss) {
        OutlinedTextField(name,{name=it},label={Text(if(editing==null) "新分组名称" else "分组新名称")},modifier=Modifier.fillMaxWidth())
        Button(enabled=!ui.busy,onClick={vm.run(onSuccess={name="";editing=null}) {group(name,editing?.id)}}) {Text("保存分组")}
        ui.data.groups.forEach {g->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
            Text(g.name+if(g.archivedAt!=null) " · 已归档" else "",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={editing=g;name=g.name}) {Text("改名")}
                TextButton(onClick={vm.run {group(g.name,g.id,g.archivedAt==null)}}) {Text(if(g.archivedAt==null) "归档" else "取消归档")}
                TextButton(onClick={remove=g}) {Text("删除并迁移")}
            }
        }} }
    }
    remove?.let {g->GlassSheet("删除「"+g.name+"」",{remove=null}) {
        Text("选择原任务移动到哪里。归档分组不删除任务。")
        (listOf(null to "未分组")+ui.data.groups.filter {it.id!=g.id && it.archivedAt==null}.map {it.id to it.name}).forEach { (id,label)->
            TextButton(onClick={vm.run {group(g.name,g.id,moveTo=id,delete=true)};remove=null}) {Text(label)}
        }
    }}
}
@Composable private fun ReminderSheet(ui:PlansUi,vm:PlansViewModel,openTool:(String)->Unit,dismiss:()->Unit) {
    val context=LocalContext.current;val app=context.applicationContext as DailyLifeApplication
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {vm.refresh()}
    var scope by remember {mutableStateOf("future")}
    var permanent by remember {mutableStateOf<Pair<String,String>?>(null)}
    GlassSheet("提醒与草稿",dismiss) {
        Text(if(app.planAlarms.enabled()) "系统通知已开启 · 当前使用非精确提醒，可能受省电策略延迟" else "系统提醒未启用，事项与提醒配置仍保存在本机",color=LocalGlass.current.muted)
        Button(onClick={
            if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        }) {Text("启用或管理系统通知")}
        TextButton(onClick={vm.refresh()}) {Text("重新核对提醒")}
        FlowChoices(listOf("future" to "近期提醒","history" to "提醒历史","draft" to "草稿","archive" to "归档事项","trash" to "计划回收站"),scope) {scope=it}
        when(scope) {
            "archive"->{
                val entries=ui.data.tasks.filter {it.archivedAt!=null && it.deletedAt==null}.map {Triple("task",it.id,it.title)}+
                    ui.data.events.filter {it.archivedAt!=null && it.deletedAt==null}.map {Triple("event",it.id,it.title)}+
                    ui.data.goals.filter {it.archivedAt!=null && it.deletedAt==null}.map {Triple("goal",it.id,it.name)}
                if(entries.isEmpty()) Text("暂无归档事项")
                entries.forEach {(kind,id,title)->Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(modifier=Modifier.weight(1f),onClick={vm.detail(kind,id);dismiss()}) {Text(title)}
                    TextButton(onClick={vm.manage(kind,id,"archive")}) {Text("取消归档")}
                }}
            }
            "draft" -> {
                if(ui.data.drafts.isEmpty()) Text("暂无草稿")
                ui.data.drafts.forEach {d-> val f=runCatching {PlanForm.parse(d.payload)}.getOrNull()
                    Row {TextButton(modifier=Modifier.weight(1f),onClick={vm.resume(d);dismiss()}) {Text(f?.text("title")?.ifBlank {"未命名草稿"} ?: "无法读取的草稿")}
                        TextButton(onClick={vm.run("草稿已丢弃") {discard(d.id)}}) {Text("丢弃")}}
                }
            }
            "trash" -> {
                val entries=ui.data.tasks.filter {it.deletedAt!=null}.map {Triple("task",it.id,it.title)}+
                    ui.data.events.filter {it.deletedAt!=null}.map {Triple("event",it.id,it.title)}+
                    ui.data.habits.filter {it.deletedAt!=null}.map {Triple("habit",it.id,it.name)}+
                    ui.data.goals.filter {it.deletedAt!=null}.map {Triple("goal",it.id,it.name)}+
                    ui.data.sessions.filter {it.deletedAt!=null}.map {Triple("focus",it.id,it.title)}
                if(entries.isEmpty()) Text("回收站为空")
                entries.forEach {(kind,id,title)->Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f));TextButton(onClick={vm.manage(kind,id,"restore")}) {Text("恢复")}
                    TextButton(onClick={permanent=kind to id}) {Text("彻底删除")}
                }}
            }
            else -> {
                val jobs=ui.data.reminders.filter {if(scope=="future") it.triggerAt>System.currentTimeMillis() && it.state!="cancelled" else it.triggerAt<=System.currentTimeMillis() || it.postedAt!=null}
                if(jobs.isEmpty()) Text("暂无提醒")
                jobs.take(100).forEach {j->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(j.title);Text(Instant.ofEpochMilli(j.triggerAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace("T"," "),color=LocalGlass.current.muted)
                    Text(mapOf("scheduled" to "已安排 · 非精确","blocked" to "通知权限阻止","posted" to "已交给系统通知","expired" to "已过期 · 未补弹","cancelled" to "已撤销","failed" to "排程失败","pending" to "待安排")[j.state] ?: j.state)
                    TextButton(onClick={if(j.sourceType=="tool") openTool(j.sourceId) else vm.detail(j.sourceType,j.sourceId);dismiss()}) {Text("打开原事项")}
                    if(j.postedAt!=null) TextButton(onClick={vm.run("已标为读过") {readReminder(j.id)}}) {Text(if(j.readAt==null) "标为读过" else "已读")}
                }} }
            }
        }
    }
    permanent?.let {(kind,id)->ConfirmDialog("彻底删除？","此操作不可恢复。仍被其他事项引用时会阻止删除，附件只在全部引用解除后清理。",{permanent=null},
        {vm.run("已彻底删除") {permanentlyDelete(kind,id)}})}
}
@Composable fun ValueLogSheet(title:String,date:String,value:String,note:String,once:Boolean=false,dismiss:()->Unit,save:(String,String,String)->Unit) {
    var d by remember {mutableStateOf(date)};var v by remember {mutableStateOf(value)};var n by remember {mutableStateOf(note)}
    var calendar by remember {mutableStateOf(false)}
    GlassSheet(title,dismiss) {
        TextButton(onClick={calendar=true}) {Text("日期："+d+" ▾")}
        if(!once) OutlinedTextField(v,{v=it},label={Text("数值（最多三位小数；时长以秒计）")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(n,{n=it},label={Text("备注")},modifier=Modifier.fillMaxWidth())
        Button(onClick={save(d,v,n)}) {Text("保存本次记录")}
    }
    if(calendar) CalendarDialog(d,{calendar=false}) {d=it}
}
