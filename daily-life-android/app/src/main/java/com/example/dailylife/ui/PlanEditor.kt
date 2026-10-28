package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.PlanRules
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.plans.*
import java.time.*

@Composable fun PlanEditor(ui:PlansUi,vm:PlansViewModel,records:RecordsSnapshot,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return
    var preview by remember {mutableStateOf(false)}
    var calendar by remember {mutableStateOf<String?>(null)}
    var chooser by remember {mutableStateOf<String?>(null)}
    var details by rememberSaveable(f.id) {mutableStateOf(false)}
    fun set(k:String,v:String)=vm.edit(f.set(k,v))
    BackHandler {if(!ui.busy) vm.leaveEditor()}
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),
        bottomBar={GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                ui.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                if(ui.error?.contains("保留并保存")==true) TextButton(enabled=!ui.busy,onClick={vm.save(true)}) {Text("确认保留并保存")}
                EditorDraftStatus(ui.saved,ui.busy)
                EditorSaveActions(!ui.busy,vm::leaveEditor,{vm.save()},if(f.kind=="focus") "开始计时" else "保存","savePlan","leavePlanEditor")
            }
        }}) {padding->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
            GlassPageHeader(if(f.kind=="focus") "开始专注" else planNames.first {it.first==f.kind}.second+(if(f.revision==0L) "" else "设置"),back=vm::leaveEditor,backTag="backPlanEditor",enabled=!ui.busy)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=12.dp).testTag("planEditor"),
                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(f.text("project").isNotBlank()) {
                    TextButton(enabled=!ui.busy,onClick={set("project","")}) {Text("取消专题关联")}
                }
                if(f.kind=="focus") PlanField(f,"title","专注名称","专注",vm,tag="planTitle")
                else PlanField(f,"title",planNames.first {it.first==f.kind}.second+"名称",vm,tag="planTitle")
                PlanEditorFields(f,ui,vm,{calendar=it},{chooser=it})
                PlanReminderEditor(f,vm,editable=!ui.busy)
                TextButton(enabled=!ui.busy,onClick={details=!details},modifier=Modifier.fillMaxWidth().testTag("planMoreFields")) {Text(if(details) "收起更多 ⌃" else "更多 ⌄")}
                if(details) {
                    PlanEditorGroup("备注",f.id+":note") {
                        if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"})
                        else PlanField(f,"note","备注",vm,single=false)
                        TextButton(enabled=!ui.busy,onClick={preview=!preview}) {Text(if(preview) "继续编辑" else "预览备注")}
                    }
                    if(f.kind in setOf("task","event")) PlanEditorGroup("重复",f.id+":repeat") {RepeatEditor(f,vm,{calendar=it})}
                    PlanEditorGroup("关联与标签",f.id+":metadata") {
                        if(f.kind=="task") PickerRow("来源记录",records.records.find {it.record.id==f.text("source")}?.let {records.title(it)} ?: "不关联") {chooser="source"}
                        if(f.kind=="event" && f.text("allDay")!="true") PlanField(f,"zone","时区",ZoneId.systemDefault().id,vm)
                        GlassMultiSelectField("标签",records.tags.map {it.id to it.name},f.text("tags").split(",").filter {it.isNotBlank()}.toSet()) {set("tags",it.joinToString(","))}
                    }
                }
                if(f.kind!="focus") {
                    val attachments=records.attachmentsFor("plan_draft",f.id)
                    PlanEditorGroup("附件",f.id+":attachments",collapsible=true,initiallyExpanded=attachments.isNotEmpty(),summary=if(attachments.isEmpty()) "添加图片与文件" else "${attachments.size} 项") {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(enabled=!ui.busy,onClick=addFiles) {Text("图片与文件")}
                            TextButton(enabled=!ui.busy,onClick={chooser="file"}) {Text("已有附件")}
                        }
                        attachments.forEach {a->Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={view(a)},modifier=Modifier.weight(1f)) {Text(a.displayName)}
                            TextButton(enabled=!ui.busy,onClick={vm.run("已移除附件引用") {removeFile(f.id,a.id)}}) {Text("移除")}
                        }}
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    calendar?.let {key->PlanDatePicker(f.text(key,LocalDate.now().toString()),{calendar=null}) {set(key,it)}}
    chooser?.let {key->
        if(key=="goalSources") {
            val options=if(f.text("source")=="tasks") ui.data.tasks.filter {it.deletedAt==null && ui.data.rules.none {r->r.entityId==it.id}}.map {it.id to it.title}
                else ui.data.sessions.filter {it.deletedAt==null && it.state in setOf("finished","early")}.map {it.id to it.title}
            GlassMultiSelectionSheet("选择进度来源",options,f.text("sources").split(",").filter {it.isNotBlank()}.toSet(),{chooser=null}) {set("sources",it.joinToString(","))}
        } else {
            val title=when(key) {"group"->"选择分组";"source"->"选择来源记录";"file"->"复用已有附件";else->"选择关联任务"}
            val options=when(key) {
                "file"->records.attachments.filter {a->records.attachmentsFor("plan_draft",f.id).none {it.id==a.id}}.map {it.id to it.displayName}
                "group"->ui.data.groups.filter {it.archivedAt==null}.map {it.id to it.name}
                "source"->records.records.filter {it.record.deletedAt==null}.map {it.record.id to records.title(it)}
                else->ui.data.tasks.filter {it.deletedAt==null && it.archivedAt==null && ui.data.rules.none {r->r.entityId==it.id}}.map {it.id to it.title}
            }
            GlassSelectionSheet(title,(if(key=="file") emptyList() else listOf("" to "不选择"))+options,f.text(key),{chooser=null}) {id->
                if(key=="file") vm.run("附件已加入当前编辑",onSuccess={chooser=null}) {attachFile(f.id,id)}
                else {set(key,id);chooser=null}
            }
        }
    }

}
@Composable internal fun PlanField(f:PlanForm,key:String,label:String,vm:PlansViewModel,single:Boolean=true,tag:String=key) =
    PlanField(f,key,label,"",vm,single,tag)
@Composable internal fun PlanField(f:PlanForm,key:String,label:String,default:String,vm:PlansViewModel,single:Boolean=true,tag:String=key) {
    val editorState by vm.ui.collectAsState()
    GlassTextField(f.text(key,default),{vm.edit(f.set(key,it))},label={Text(label)},singleLine=single,enabled=!editorState.busy,
        modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(16.dp),minLines=if(single) 1 else 3,
        keyboardOptions=KeyboardOptions(keyboardType=when(key) {
            "target"->KeyboardType.Decimal
            "weeklyDays","seconds","short","long","rounds","interval","count","minutes"->KeyboardType.Number
            else->KeyboardType.Text
        }))
}
@Composable internal fun DateRow(label:String,value:String,click:()->Unit,clear:(()->Unit)?=null) {
    GlassSelectorRow(label,value,icon=Icons.Outlined.DateRange,clear=clear,onClick=click)
}
@Composable internal fun PickerRow(label:String,value:String,click:()->Unit) {
    GlassSelectorRow(label,value,onClick=click)
}

@Composable internal fun WeekdayChoices(f:PlanForm,vm:PlansViewModel) {
    MultiChoices(listOf("一","二","三","四","五","六","日").mapIndexed {i,s->(i+1).toString() to ("周"+s)},
        f.text("weekdays","1,2,3,4,5").split(",").toSet()) {vm.edit(f.set("weekdays",it.sorted().joinToString(",")))}
}
@Composable private fun RepeatEditor(f:PlanForm,vm:PlansViewModel,date:(String)->Unit) {
    GlassSelectField("频率",listOf("none" to "不重复","daily" to "每天","weekly" to "每周","monthly" to "每月","yearly" to "每年"),f.text("repeat","none")) {vm.edit(f.set("repeat",it))}
    if(f.text("repeat","none")!="none") {
        PlanField(f,"interval","间隔周期","1",vm)
        if(f.text("repeat")=="weekly") WeekdayChoices(f,vm)
        if(f.text("repeat") in setOf("monthly","yearly")) {
            FlowChoices(listOf("last" to "不存在时取月末","skip" to "跳过不存在日期")+
                if(f.text("repeat")=="yearly") listOf("march" to "2月29日改为3月1日") else emptyList(),f.text("invalid","last")) {vm.edit(f.set("invalid",it))}
        }
        DateRow("重复结束日",f.text("repeatEnd"),{date("repeatEnd")},{vm.edit(f.set("repeatEnd",""))})
        PlanField(f,"count","最多次数",vm)
        if(f.revision>0L) DateRow("新规则从此日生效",f.text("repeatFrom",LocalDate.now().toString()),{date("repeatFrom")})
        val next=runCatching {
            val anchor=f.text(if(f.kind=="event") "start" else "planned").ifBlank {f.text("due")}.ifBlank {LocalDate.now().toString()}
            PlanRules.dates(RepeatRule(ownerId="",entityType=f.kind,entityId=f.id,anchorDate=anchor,frequency=f.text("repeat"),
                interval=f.text("interval","1").toInt(),weekdays=f.text("weekdays","1,2,3,4,5"),endDate=f.text("repeatEnd").ifBlank {null},
                count=f.text("count").ifBlank {null}?.toInt(),invalidPolicy=f.text("invalid","last")),LocalDate.now(),3,LocalDate.now()).take(3)
        }.getOrNull()
        if(next!=null && next.isNotEmpty()) Text(next.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }
}
