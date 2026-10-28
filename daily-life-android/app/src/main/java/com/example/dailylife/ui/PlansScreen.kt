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
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.*
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
@Composable fun PlansPane(ui:PlansUi,vm:PlansViewModel,initialTab:String?=null,tools:ToolsSnapshot=ToolsSnapshot(),openTool:(String)->Unit={},onLandingHandled:()->Unit={}) {
    var tab by rememberSaveable { mutableStateOf("任务") }
    var add by rememberSaveable { mutableStateOf(false) }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val tabStates=rememberSaveableStateHolder()
    LaunchedEffect(initialTab) {if(initialTab in listOf("任务","日历","习惯","专注","目标")) {tab=initialTab!!;onLandingHandled()}}
    Column(Modifier.fillMaxSize()) {
        PlanTabBar(tab) {tab=it}
        if(!ui.ready) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            if(ui.error!=null) Text(ui.error) else CircularProgressIndicator() }; return@Column }
        Box(Modifier.weight(1f).testTag("plansContent")) {
            GlassPageTransition(targetState=tab,label="plan pages") {current->
            tabStates.SaveableStateProvider(current) {when(current) {
                "任务"->TaskPane(ui,vm,{reminders=true},{add=true})
                "日历"->CalendarPane(ui,vm,date,{date=it},tools,openTool)
                "习惯"->HabitPane(ui,vm)
                "专注"->FocusPane(ui,vm)
                "目标"->GoalPane(ui,vm)
            }}}
        }
        if(tab!="专注" || ui.data.sessions.none {it.deletedAt==null && it.state in setOf("running","paused","review","awaiting")}) GlassFloatingActionRow(when(tab) {"任务"->"新任务";"日历"->"新日程";"习惯"->"新习惯";"专注"->"开始专注";else->"新目标"},"addPlan",enabled=!ui.busy) {
            vm.begin(when(tab) {"日历"->"event";"习惯"->"habit";"专注"->"focus";"目标"->"goal";else->"task"},if(tab=="日历") date.takeIf {it.isNotBlank()} else null)
        }
    }
    if(add) GlassSheet("安排一件事",{add=false}) {
        planNames.forEach { (kind,name) -> GlassOutlinedButton(enabled=!ui.busy,onClick={add=false;vm.begin(kind,if(kind=="event") date else null)},
            modifier=Modifier.fillMaxWidth().testTag("newPlan:"+kind)) {
            PlanGlyph(kind);Spacer(Modifier.width(12.dp));Text(name,Modifier.weight(1f))
        } }
        HorizontalDivider()
        TextButton(onClick={add=false;reminders=true},modifier=Modifier.fillMaxWidth()) {Text("提醒与管理")}
    }
    if(reminders) ReminderSheet(ui,vm,openTool) {reminders=false}
}
@Composable private fun TaskPane(ui:PlansUi,vm:PlansViewModel,reminders:()->Unit,add:()->Unit) {
    var filter by rememberSaveable { mutableStateOf("pending") };var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable {mutableStateOf(false)};var views by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var group by rememberSaveable { mutableStateOf("all") };var priority by rememberSaveable { mutableStateOf("all") }
    var groups by remember { mutableStateOf(false) };var filters by remember { mutableStateOf(false) }
    var status by rememberSaveable {mutableStateOf("all")}
    var filterDate by rememberSaveable {mutableStateOf("")};var datePicker by remember {mutableStateOf(false)}
    var completeBatch by remember {mutableStateOf(false)}
    var trashSeries by remember {mutableStateOf(false)}
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
            "undated"->row.status in setOf("todo","doing") && row.date==null && row.due==null
            "done"->row.status=="done";"pending"->row.status in setOf("todo","doing")
            else->true
        }
    }.sortedWith(compareBy<TaskRow> { it.status=="done" }.thenByDescending { it.task.priority }.thenBy { it.date ?: it.due ?: "9999" })
    val visibleKeys=visible.map {it.key}.toSet()
    LaunchedEffect(visibleKeys) {selected=selected.intersect(visibleKeys)}
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={views=true},modifier=Modifier.weight(1f).testTag("planViews"),contentPadding=PaddingValues(horizontal=4.dp)) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(when(filter) {"pending"->"待办";"today"->"今天";"week"->"近 7 天";"undated"->"待安排";"done"->"已完成";"archive"->"归档";"trash"->"回收站";else->"全部任务"},
                        style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=LocalGlass.current.ink)
                    PlanGlyph("down",Modifier.size(16.dp),LocalGlass.current.muted)
                    if(LocalDensity.current.fontScale<=1.3f) Text("${visible.size} 项",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                }
            }
            IconButton({search=!search},Modifier.semantics {contentDescription="搜索任务"}) {PlanGlyph("search",color=LocalGlass.current.muted)}
            IconButton({menu=true},Modifier.testTag("planMenu").semantics {contentDescription="计划更多操作"}) {PlanGlyph("more",color=LocalGlass.current.muted)}
        }
        if(search) Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
            GlassTextField(query,{query=it},placeholder={Text("搜索任务与备注")},singleLine=true,modifier=Modifier.weight(1f).testTag("planSearch"))
            TextButton(onClick={query="";search=false}) {Text("收起")}
        }
        if(group!="all" || priority!="all" || status!="all" || filterDate.isNotBlank()) {
            TextButton(onClick={filters=true},modifier=Modifier.padding(horizontal=18.dp)) {PlanGlyph("filter");Text("已应用筛选 · "+visible.size+" 项")}
        }
        if(selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            TextButton(enabled=!ui.busy,onClick={selected=emptySet()}) {Text("取消 "+selected.size)}
            if(filter !in setOf("archive","trash")) {
                TextButton(enabled=!ui.busy,onClick={completeBatch=true}) {Text("完成")}
                TextButton(enabled=!ui.busy,onClick={batchMove=true}) {Text("移分组")}
            }
            TextButton(enabled=!ui.busy,onClick={
                val targets=visible.filter {it.key in selected}
                val ids=targets.map {it.task.id}.toSet()
                val action=if(filter=="trash") "restore" else "trash"
                if(action=="trash" && targets.any {it.occurrence!=null}) trashSeries=true
                else vm.run("已更新",onSuccess={selected=emptySet()}) {manageTasks(ids,action)}
            }) {Text(if(filter=="trash") "恢复" else "回收站")}
        }
        LazyColumn(Modifier.fillMaxSize().testTag("planTaskList"),contentPadding=PaddingValues(18.dp,8.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(visible.isEmpty()) item {PlanQuietEmpty(if(query.isNotBlank()) "没有匹配任务" else "暂无任务")}
            val sections=visible.groupBy {taskSection(it,today)}
            listOf("已逾期","今天","此前安排","接下来","待安排","已完成","已取消与跳过").forEach {section->
                val entries=sections[section].orEmpty()
                if(entries.isNotEmpty()) {
                    item(key="section:$section") {PlanSectionHeading(section,entries.size)}
                    items(entries,key={it.key}) {row->TaskCard(row,ui,vm,row.key in selected,{selected=if(row.key in selected) selected-row.key else selected+row.key},selected.isNotEmpty())}
                }
            }
        }
    }
    if(views) GlassSheet("任务视图",{views=false}) {
        FlowChoices(listOf("pending" to "待办","today" to "今天","week" to "近7天","undated" to "无日期","done" to "已完成","all" to "全部"),filter) {filter=it;status="all";selected=emptySet();views=false}
    }
    if(menu) GlassSheet("计划管理",{menu=false}) {
        listOf("筛选任务" to {filters=true},"管理分组" to {groups=true},"提醒与管理" to reminders,
            "已归档" to {filter="archive"},"回收站" to {filter="trash"},"新建其他计划" to add).forEach {(name,action)->
            TextButton(onClick={menu=false;selected=emptySet();action()},modifier=Modifier.fillMaxWidth()) {Text(name)}
        }
    }
    if(groups) GroupSheet(ui,vm) {groups=false}
    if(filters) GlassSheet("任务筛选",{filters=false}) {
        SectionLabel("分组");FlowChoices(listOf("all" to "全部","none" to "未分组")+ui.data.groups.map {it.id to it.name},group) {group=it}
        SectionLabel("优先级");FlowChoices(listOf("all" to "全部","0" to "低","1" to "中","2" to "高"),priority) {priority=it}
        SectionLabel("状态");FlowChoices(listOf("all" to "全部","todo" to "待办","doing" to "进行中","done" to "完成","cancelled" to "取消","skipped" to "跳过"),status) {
            status=it
            if(filter !in setOf("all","archive","trash") && (it in setOf("done","cancelled","skipped") || filter=="done" && it in setOf("todo","doing"))) filter="all"
        }
        DateRow("指定日期",filterDate,{datePicker=true},{filterDate=""})
        TextButton(onClick={group="all";priority="all";status="all";filterDate="";query=""}) {Text("清除筛选")}
        GlassButton(onClick={filters=false}) {Text("查看结果")}
    }
    if(datePicker) PlanDatePicker(filterDate,{datePicker=false}) {filterDate=it}
    if(completeBatch) GlassSheet("批量完成任务",{completeBatch=false}) {
        Text("有子任务时，选择是否同时完成。各次重复实例独立处理。")
        listOf(true to "同时完成子任务",false to "保留子任务状态").forEach {(all,label)->
            GlassButton(enabled=!ui.busy,onClick={val targets=visible.filter {it.key in selected}
                vm.run("已批量完成",onSuccess={selected=emptySet();completeBatch=false}) {completeTasks(targets,all)}}) {Text(label)}
        }
    }
    if(batchMove) GlassSheet("批量移到分组",{batchMove=false}) {
        if(visible.any {it.key in selected && it.occurrence!=null}) Text("重复任务的分组会应用到整个系列。",color=LocalGlass.current.muted)
        (listOf(null to "未分组")+ui.data.groups.filter {it.archivedAt==null}.map {it.id to it.name}).forEach { (id,name) ->
            TextButton(enabled=!ui.busy,onClick={
                val ids=visible.filter {it.key in selected}.map {it.task.id}.toSet()
                vm.run("分组已更新",onSuccess={batchMove=false;selected=emptySet()}) {moveTasks(ids,id)}
            }) {Text(name)}
        }
    }
    if(trashSeries) GlassSheet("移入回收站",{trashSeries=false}) {
        Text("所选内容含重复任务，移入回收站会收起整个系列及其各次安排。若只处理本次，请打开任务选择「跳过本次」。")
        GlassButton(enabled=!ui.busy,onClick={
            val ids=visible.filter {it.key in selected}.map {it.task.id}.toSet()
            vm.run("已移入回收站",onSuccess={selected=emptySet();trashSeries=false}) {manageTasks(ids,"trash")}
        }) {Text("将所选任务移入回收站")}
        TextButton(onClick={trashSeries=false}) {Text("保留任务")}
    }
}
@Composable private fun GroupSheet(ui:PlansUi,vm:PlansViewModel,dismiss:()->Unit) {
    var name by remember {mutableStateOf("")};var editing by remember {mutableStateOf<TaskGroup?>(null)}
    var remove by remember {mutableStateOf<TaskGroup?>(null)}
    GlassSheet("任务分组",dismiss) {
        GlassTextField(name,{name=it},label={Text(if(editing==null) "新分组名称" else "分组新名称")},modifier=Modifier.fillMaxWidth())
        GlassButton(enabled=!ui.busy,onClick={vm.run(onSuccess={name="";editing=null}) {group(name,editing?.id)}}) {Text("保存分组")}
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
    GlassSheet("提醒与管理",dismiss) {
        Text(if(app.planAlarms.enabled()) "系统通知已开启 · "+app.planAlarms.timingDescription() else "系统提醒未启用，事项与提醒配置仍保存在本机",color=LocalGlass.current.muted)
        GlassButton(onClick={
            if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        }) {Text("启用或管理系统通知")}
        TextButton(onClick={vm.refresh()}) {Text("重新核对提醒")}
        FlowChoices(listOf("future" to "近期提醒","history" to "提醒历史","archive" to "归档事项","trash" to "计划回收站"),scope) {scope=it}
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
                val jobs=ui.data.reminders.filter {if(scope=="future") (it.deferredUntil ?: it.triggerAt)>System.currentTimeMillis() && it.state!="cancelled" else it.triggerAt<=System.currentTimeMillis() || it.postedAt!=null}
                if(jobs.isEmpty()) Text("暂无提醒")
                jobs.take(100).forEach {j->GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp)) {
                    Text(j.title);Text(Instant.ofEpochMilli(j.deferredUntil ?: j.triggerAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace("T"," "),color=LocalGlass.current.muted)
                    Text(mapOf("snoozed" to "稍后提醒","scheduled" to "已安排","blocked" to "通知权限阻止","posted" to "已交给系统通知","expired" to "已过期 · 未补弹","cancelled" to "已撤销","failed" to "排程失败","pending" to "待安排")[j.state] ?: j.state)
                    TextButton(onClick={if(j.sourceType=="tool") openTool(j.sourceId) else vm.detail(j.sourceType,j.sourceId);dismiss()}) {Text("打开原事项")}
                    if(j.postedAt!=null) TextButton(onClick={vm.run("已标为读过") {readReminder(j.id)}}) {Text(if(j.readAt==null) "标为读过" else "已读")}
                }} }
            }
        }
    }
    permanent?.let {(kind,id)->ConfirmDialog("彻底删除？","此操作不可恢复。仍被其他事项引用时会阻止删除，附件只在全部引用解除后清理。",{permanent=null},
        {vm.run("已彻底删除") {permanentlyDelete(kind,id)}})}
}
@Composable fun ValueLogSheet(title:String,date:String,value:String,note:String,once:Boolean=false,dismiss:()->Unit,
    busy:Boolean=false,duration:Boolean=false,error:String?=null,integerSeconds:Boolean=false,allowNegative:Boolean=false,save:(String,String,String)->Unit) {
    var d by remember {mutableStateOf(date)};var v by remember {mutableStateOf(value)};var n by remember {mutableStateOf(note)}
    var calendar by remember {mutableStateOf(false)}
    GlassSheet(title,dismiss,dismissEnabled=!busy,footer={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        GlassButton(enabled=!busy,onClick={save(d,v,n)},modifier=Modifier.fillMaxWidth().testTag("savePlanLog")) {Text("保存本次记录")}
    }}) {
        GlassSelectorRow("日期",planDateLabel(d),enabled=!busy) {calendar=true}
        if(!once) {
            if(duration) PlanDurationValue("时长",v,{v=it},"planLogValue",enabled=!busy,integerSeconds=integerSeconds,
                maximumSeconds=if(integerSeconds) 604800 else 1_000_000_000,allowNegative=allowNegative,allowZero=integerSeconds)
            else {
                if(allowNegative) FlowChoices(listOf("add" to "增加","subtract" to "扣减"),if(v.startsWith("-")) "subtract" else "add") {mode->v=v.removePrefix("-").let {if(mode=="subtract") "-$it" else it}}
                GlassTextField(if(allowNegative) v.removePrefix("-") else v,{value->v=if(allowNegative && v.startsWith("-")) "-"+value.removePrefix("-") else value},enabled=!busy,label={Text("数量")},
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),modifier=Modifier.fillMaxWidth().testTag("planLogValue"))
            }
        }
        GlassTextField(n,{n=it},enabled=!busy,label={Text(if(integerSeconds) "修正原因" else "备注")},minLines=2,modifier=Modifier.fillMaxWidth().testTag("planLogNote"))
    }
    if(calendar) PlanDatePicker(d,{calendar=false}) {d=it}
}
