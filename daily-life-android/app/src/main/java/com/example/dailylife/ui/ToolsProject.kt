package com.example.dailylife.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.LedgerSnapshot
import com.example.dailylife.feature.plans.PlansSnapshot
import com.example.dailylife.feature.tools.*

private val projectSections=listOf("task" to "任务","record" to "记录","material" to "资料","checklist" to "清单","ledger" to "账目","goal" to "目标")
private fun projectSection(link:ToolLink,data:ToolsSnapshot):String=if(link.section!="overview") link.section else when(link.entityType) {
    "task","event","habit","focus"->"task";"record"->"record";"ledger"->"ledger";"goal"->"goal"
    "tool"->if(data.entries.find {it.id==link.entityId}?.kind in listOf("checklist","checklist_template")) "checklist" else "material"
    else->"material"
}

@Composable internal fun ToolProject(project:ToolEntry,ui:ToolsUi,vm:ToolsViewModel,ledger:LedgerSnapshot,plans:PlansSnapshot,open:(String,String)->Unit) {
    var section by rememberSaveable(project.id) {mutableStateOf("all")};var choosing by remember {mutableStateOf(false)};var creating by remember {mutableStateOf(false)}
    val all=ui.data.links.filter {it.entryId==project.id}.sortedBy {it.sortOrder}
    val visible=all.filter {section=="all" || projectSection(it,ui.data)==section}
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {(listOf("all" to "概览")+projectSections).forEach {(id,label)->GlassChoice(label,section==id) {section=id}}}
    if(project.deletedAt==null) Row(Modifier.horizontalScroll(rememberScrollState())) {
        Button(onClick={creating=true},enabled=!ui.busy) {Text("新建并加入")}
        TextButton(onClick={choosing=true},enabled=!ui.busy) {Text("选择已有内容")}
    }
    if(visible.isEmpty()) ToolEmpty("这个分区还没有内容","创建新内容或关联原对象，修改来源内容会同步显示在这里。")
    visible.forEach {link->
        val title=projectSourceTitle(link,ui,ledger,plans);val exists=projectSourceExists(link,ui,ledger,plans)
        GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title ?: "原内容已删除或不存在",style=MaterialTheme.typography.titleMedium)
            if(title!=null || exists) TextButton(onClick={open(link.entityType,link.entityId)}) {Text(if(title!=null) "打开原内容" else "查看回收站中的原内容")}
            else Text("保留来源位置；原对象恢复后会重新显示。",color=LocalGlass.current.muted)
            if(project.deletedAt==null) {
                ToolPicker("分区",projectSections.find {it.first==projectSection(link,ui.data)}?.second ?: "资料",projectSections,projectSection(link,ui.data)) {next->vm.run("分区已更新") {moveProjectLink(project.id,link.entityType,link.entityId,section=next)}}
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={vm.run(null) {moveProjectLink(project.id,link.entityType,link.entityId,direction=-1,scope=section.takeIf {it!="all"})}}) {Text("上移")}
                    TextButton(onClick={vm.run(null) {moveProjectLink(project.id,link.entityType,link.entityId,direction=1,scope=section.takeIf {it!="all"})}}) {Text("下移")}
                    TextButton(onClick={vm.run("已移出专题，原内容保留") {unlink(project.id,link.entityType,link.entityId)}}) {Text("移出专题")}
                }
            }
        }}
    }
    val txIds=all.filter {it.entityType=="ledger"}.map {it.entityId}.toSet()
    if(txIds.isNotEmpty()) {
        val transactions=ledger.transactions.filter {it.deletedAt==null && (it.id in txIds || it.type=="refund" && it.originalExpenseId in txIds)}.distinctBy {it.id}
        val net=transactions.sumOf {if(it.type=="expense") it.amountMinor else if(it.type=="refund") -it.amountMinor else 0L}
        Text("关联净支出 ¥"+toolMoney(net,ui.hideAmounts)+"（退款去重，转账不计消费）")
    }
    if(choosing) ToolProjectSources(project,ui,ledger,plans,{choosing=false}) {type,id,targetSection->vm.run("已加入专题") {link(project.id,type,id,targetSection)}}
    if(creating) GlassSheet("新建并加入「"+project.title+"」",{creating=false}) {
        Text("由原模块保存，同一次提交加入专题；未保存的草稿不会参与专题统计。",color=LocalGlass.current.muted)
        listOf("note" to "记录","task" to "任务","event" to "日程","goal" to "目标","expense" to "支出").forEach {(kind,label)->TextButton(enabled=!ui.busy,onClick={creating=false;open("project_new",project.id+"|"+kind)},modifier=Modifier.fillMaxWidth()) {Text("新建"+label)}}
        HorizontalDivider()
        toolKinds.filter {it.first!="project"}.forEach {(kind,label)->TextButton(enabled=!ui.busy,onClick={creating=false;vm.begin(kind,projectId=project.id)},modifier=Modifier.fillMaxWidth()) {Text("新建"+label)}}
    }
}

private fun projectSourceTitle(link:ToolLink,ui:ToolsUi,ledger:LedgerSnapshot,plans:PlansSnapshot):String?=when(link.entityType) {
    "record"->ui.records.records.find {it.record.id==link.entityId && it.record.deletedAt==null}?.let {ui.records.title(it)}
    "task"->plans.tasks.find {it.id==link.entityId && it.deletedAt==null}?.title
    "event"->plans.events.find {it.id==link.entityId && it.deletedAt==null}?.title
    "habit"->plans.habits.find {it.id==link.entityId && it.deletedAt==null}?.name
    "focus"->plans.sessions.find {it.id==link.entityId && it.deletedAt==null}?.title
    "goal"->plans.goals.find {it.id==link.entityId && it.deletedAt==null}?.name
    "tool"->ui.data.entries.find {it.id==link.entityId && it.deletedAt==null}?.title
    "ledger"->ledger.transactions.find {it.id==link.entityId && it.deletedAt==null}?.let {it.subject.ifBlank {"账目"}+" · ¥"+toolMoney(it.amountMinor,ui.hideAmounts)}
    else->null
}
private fun projectSourceExists(link:ToolLink,ui:ToolsUi,ledger:LedgerSnapshot,plans:PlansSnapshot)=when(link.entityType) {
    "record"->ui.records.records.any {it.record.id==link.entityId};"task"->plans.tasks.any {it.id==link.entityId};"event"->plans.events.any {it.id==link.entityId}
    "habit"->plans.habits.any {it.id==link.entityId};"focus"->plans.sessions.any {it.id==link.entityId};"goal"->plans.goals.any {it.id==link.entityId}
    "tool"->ui.data.entries.any {it.id==link.entityId};"ledger"->ledger.transactions.any {it.id==link.entityId};else->false
}
private data class ProjectSource(val type:String,val id:String,val title:String,val section:String)
@Composable private fun ToolProjectSources(project:ToolEntry,ui:ToolsUi,ledger:LedgerSnapshot,plans:PlansSnapshot,dismiss:()->Unit,choose:(String,String,String)->Unit) {
    var query by remember {mutableStateOf("")};var section by remember {mutableStateOf("auto")};var type by remember {mutableStateOf("all")}
    val choices=ui.records.records.filter {it.record.deletedAt==null}.map {ProjectSource("record",it.record.id,ui.records.title(it),"record")}+
        plans.tasks.filter {it.deletedAt==null}.map {ProjectSource("task",it.id,it.title,"task")}+plans.events.filter {it.deletedAt==null}.map {ProjectSource("event",it.id,it.title,"task")}+
        plans.habits.filter {it.deletedAt==null}.map {ProjectSource("habit",it.id,it.name,"task")}+plans.sessions.filter {it.deletedAt==null}.map {ProjectSource("focus",it.id,it.title,"task")}+
        plans.goals.filter {it.deletedAt==null}.map {ProjectSource("goal",it.id,it.name,"goal")}+ui.data.entries.filter {it.id!=project.id && it.deletedAt==null}.map {ProjectSource("tool",it.id,it.title,if(it.kind in listOf("checklist","checklist_template")) "checklist" else "material")}+
        ledger.transactions.filter {it.deletedAt==null}.map {ProjectSource("ledger",it.id,it.subject.ifBlank {"账目"}+" · ¥"+toolMoney(it.amountMinor,ui.hideAmounts),"ledger")}
    val visible=choices.filter {(type=="all" || it.type==type) && it.title.contains(query.trim(),true)}
    GlassSheet("选择已有内容",dismiss) {
        ToolField("搜索内容",query,{query=it})
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("all" to "全部","record" to "记录","task" to "任务","event" to "日程","tool" to "生活资料","ledger" to "账目","goal" to "目标").forEach {(id,label)->GlassChoice(label,type==id) {type=id}}}
        ToolPicker("加入分区",projectSections.find {it.first==section}?.second ?: "按来源自动选择",listOf("auto" to "按来源自动选择")+projectSections,section) {section=it}
        visible.take(200).forEach {source->val exists=ui.data.links.any {it.entryId==project.id && it.entityType==source.type && it.entityId==source.id};TextButton(enabled=!exists && !ui.busy,onClick={choose(source.type,source.id,if(section=="auto") source.section else section)},modifier=Modifier.fillMaxWidth()) {Text(source.title+if(exists) " · 已加入" else "")}}
        if(visible.isEmpty()) Text("没有符合条件的内容",color=LocalGlass.current.muted)
        if(visible.size>200) Text("显示前200项，请继续输入名称缩小范围。",color=LocalGlass.current.muted)
    }
}
