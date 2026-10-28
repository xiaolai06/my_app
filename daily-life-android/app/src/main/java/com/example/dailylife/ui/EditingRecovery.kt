package com.example.dailylife.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun EditorSaveActions(enabled:Boolean,cancel:()->Unit,save:()->Unit,label:String,tag:String,leaveTag:String="leaveEditor") {
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        TextButton(enabled=enabled,onClick=cancel,modifier=Modifier.testTag(leaveTag)) {Text("稍后继续")}
        Spacer(Modifier.width(10.dp))
        GlassButton(enabled=enabled,onClick=save,modifier=Modifier.testTag(tag)) {Text(label)}
    }
}

@Composable internal fun EditorDraftStatus(saved:Boolean,busy:Boolean) {
    Text(if(busy) "正在保存…" else if(saved) "草稿已保存" else "正在保存草稿…",
        style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,modifier=Modifier.testTag("editorDraftStatus"))
}

/** Discarding is explicit in the unfinished list; navigating away keeps the current draft. */
@Composable internal fun DiscardEditingDialog(enabled:Boolean,stay:()->Unit,discard:()->Unit) {
    GlassAlertDialog(onDismissRequest={if(enabled) stay()},title={Text("放弃这份未完成内容？")},
        text={Text("这份草稿及本次修改将被移除，已经正式保存的内容不受影响。")},
        confirmButton={TextButton(enabled=enabled,onClick=discard,modifier=Modifier.testTag("discardEditing")) {Text("放弃修改")}},
        dismissButton={TextButton(enabled=enabled,onClick=stay) {Text("继续保留")}})
}

data class UnfinishedEditSummary(val domain:String,val id:String,val entityId:String,val kind:String,
    val title:String,val details:String,val savedAt:Long,val readable:Boolean=true)

internal fun unfinishedModule(domain:String)=when(domain) {"record"->"记录";"plan"->"计划";"ledger"->"账本";else->"生活工具"}
private fun summaryText(value:String,limit:Int=90)=value.trim().replace(Regex("\\s+")," ").take(limit)
private fun summaryAmount(value:String,hidden:Boolean):String?=value.takeIf {it.isNotBlank()}?.let {
    if(hidden) "金额 ••••" else runCatching {"¥ "+LedgerRules.display(LedgerRules.minor(it,allowZero=true))}
        .getOrElse {"金额输入："+summaryText(value,30)}
}
private fun summaryDetails(vararg values:String?)=values.filterNotNull().filter {it.isNotBlank()}.joinToString(" · ")
internal fun unfinishedEditedAt(value:Long)=runCatching {
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault("时间待核对")

/** Summaries read saved draft payloads, never add them to formal records or financial totals. */
internal fun unfinishedEdits(records:RecordsSnapshot,plans:PlansSnapshot,ledger:LedgerSnapshot,tools:ToolsSnapshot,
    hideAmounts:Boolean=false):List<UnfinishedEditSummary> {
    val result=mutableListOf<UnfinishedEditSummary>()
    fun unreadable(domain:String,id:String,savedAt:Long,entityId:String=id)=UnfinishedEditSummary(domain,id,entityId,unfinishedModule(domain),
        "未完成的"+unfinishedModule(domain),"原内容已保留，暂时无法打开",savedAt,false)
    records.drafts.forEach {draft->result+=runCatching {
        val f=EditorData.fromJson(draft.payload)
        val type=records.types.find {it.id==f.typeId}
        val kind=type?.name ?: "记录"
        val note=if(type?.subtype=="card") "${f.fields.size} 个信息字段" else summaryText(f.body).ifBlank {summaryText(f.url)}
        UnfinishedEditSummary("record",draft.id,draft.targetId ?: f.recordId,kind,
            summaryText(f.title).ifBlank {"待完成的"+kind},
            summaryDetails(f.diaryDate.takeIf {type?.kind=="diary"},note),draft.savedAt)
    }.getOrElse {unreadable("record",draft.id,draft.savedAt,draft.targetId ?: draft.id)}}
    plans.drafts.forEach {draft->result+=runCatching {
        val f=PlanForm.parse(draft.payload)
        val kind=planNames.find {it.first==f.kind}?.second ?: "计划"
        val date=listOf("date","planned","start","due","end").firstNotNullOfOrNull {key->f.text(key).takeIf {it.isNotBlank()}}
        UnfinishedEditSummary("plan",draft.id,f.id,kind,summaryText(f.text("title")).ifBlank {"待完成的"+kind},
            summaryDetails(date,summaryText(f.text("note"))),draft.savedAt)
    }.getOrElse {unreadable("plan",draft.id,draft.savedAt)}}
    ledger.drafts.forEach {draft->result+=runCatching {
        val f=LedgerForm.parse(draft.payload)
        val kind=if(f.kind=="claim") "报销申请" else ledgerType(f.kind)
        val title=if(f.kind=="claim") f.text("name") else f.text("subject").ifBlank {f.text("merchant")}
            .ifBlank {ledger.categories.find {it.id==f.text("category")}?.name.orEmpty()}
        val claimTotal=if(f.kind=="claim") runCatching {
            val allocations=JSONObject(f.text("allocations","{}"))
            val values=allocations.keys().asSequence().map {LedgerRules.minor(allocations.getString(it),allowZero=true)}.toList()
            values.takeIf {it.isNotEmpty()}?.fold(0L) {total,value->Math.addExact(total,value)}?.let {LedgerRules.display(it)}
        }.getOrNull() else null
        UnfinishedEditSummary("ledger",draft.id,f.id,kind,summaryText(title).ifBlank {"待填写的"+kind},
            summaryDetails(summaryAmount(claimTotal ?: f.text("amount"),hideAmounts),
                f.text(if(f.kind=="claim") "submitDate" else "date"),f.text("channel")),draft.savedAt)
    }.getOrElse {unreadable("ledger",draft.id,draft.savedAt)}}
    tools.drafts.forEach {draft->result+=runCatching {
        val f=ToolForm.parse(draft.payload)
        val kind=if(f.kind=="item"&&f.text("wardrobe")=="true") "衣物"
            else toolKinds.find {it.first==f.kind}?.second ?: "清单模板"
        UnfinishedEditSummary("tool",draft.id,f.id,kind,summaryText(f.text("title")).ifBlank {"待完成的"+kind},
            summaryDetails(summaryAmount(f.text("amount"),hideAmounts),f.text("date"),summaryText(f.text("note"))),draft.savedAt)
    }.getOrElse {unreadable("tool",draft.id,draft.savedAt)}}
    return result.sortedByDescending {it.savedAt}.distinctBy {it.domain+":"+it.entityId}
}

@Composable internal fun UnfinishedSummaryContent(item:UnfinishedEditSummary,modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(unfinishedModule(item.domain)+" · "+item.kind,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.accent)
        Text(item.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
        if(item.details.isNotBlank()) Text(item.details,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text("草稿已保存 · 编辑于 "+unfinishedEditedAt(item.savedAt),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RecoverEditing(records:RecordsUi,recordVm:RecordsViewModel,plans:PlansUi,planVm:PlansViewModel,
    ledger:LedgerUi,ledgerVm:LedgerViewModel,tools:ToolsUi,toolsVm:ToolsViewModel,
    initialResume:UnfinishedEditSummary?=null,dismiss:()->Unit) {
    val entries=remember(records.snapshot,plans.data,ledger.data,tools.data,tools.hideAmounts) {
        unfinishedEdits(records.snapshot,plans.data,ledger.data,tools.data,tools.hideAmounts)
    }
    var remove by remember {mutableStateOf<UnfinishedEditSummary?>(null)}
    var opening by remember {mutableStateOf<UnfinishedEditSummary?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    val busy=records.busy || plans.busy || ledger.busy || tools.busy
    val pendingError=when(opening?.domain ?: remove?.domain) {
        "record"->records.message;"plan"->plans.error;"ledger"->ledger.error;"tool"->tools.error;else->null
    }
    LaunchedEffect(pendingError) {pendingError?.let {error=it}}
    LaunchedEffect(opening,records.editor?.draftId,plans.editor?.id,ledger.editor?.id,tools.editor?.id) {
        opening?.let {item->
            val opened=when(item.domain) {"record"->records.editor?.draftId==item.id;"plan"->plans.editor?.id==item.entityId;
                "ledger"->ledger.editor?.id==item.entityId;else->tools.editor?.id==item.entityId}
            if(opened) {opening=null;dismiss()}
        }
    }
    LaunchedEffect(entries) {
        remove?.let {item->if(entries.none {it.domain==item.domain&&it.id==item.id}) remove=null}
    }
    fun resume(item:UnfinishedEditSummary) {
        if(busy) return
        if(item.domain=="record") recordVm.message(null)
        error=null;opening=item
        val found=when(item.domain) {
            "record"->records.snapshot.drafts.find {it.id==item.id}?.also {recordVm.resume(it)}
            "plan"->plans.data.drafts.find {it.id==item.id}?.also {planVm.resume(it)}
            "ledger"->ledger.data.drafts.find {it.id==item.id}?.also {ledgerVm.resume(it)}
            else->tools.data.drafts.find {it.id==item.id}?.also {toolsVm.resume(it)}
        }
        if(found==null) {opening=null;error="内容已更新，请重新选择。"}
    }
    var initialAttempted by remember(initialResume?.id) {mutableStateOf(false)}
    LaunchedEffect(initialResume,busy) {
        if(!initialAttempted&&!busy) initialResume?.let {initialAttempted=true;resume(it)}
    }
    GlassSheet("未完成编辑",dismiss,scrollContent=false,dismissEnabled=!busy) {
        Text("退出时会保存当前输入，点击继续编辑；正式保存后会从这里移除。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        (error ?: pendingError)?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(entries.isEmpty()) GlassEmptyState("没有未完成内容","新的编辑退出后会保存在这里。")
        else LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("unfinishedEditingList"),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)) {
            listOf("record","plan","ledger","tool").forEach {domain->
                val group=entries.filter {it.domain==domain}
                if(group.isNotEmpty()) {
                    item(key="group:$domain") {Text(unfinishedModule(domain)+" · "+group.size,style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=10.dp))}
                    items(group,key={it.domain+":"+it.id}) {item->
                        PersonalGroup {Column(Modifier.testTag("unfinished:${item.domain}:${item.id}")) {
                            UnfinishedSummaryContent(item,Modifier.fillMaxWidth().clickable(enabled=!busy&&item.readable,role=Role.Button,onClickLabel="继续编辑") {resume(item)}.padding(16.dp))
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.End) {
                                TextButton(enabled=!busy,onClick={remove=item;opening=null;error=null;if(item.domain=="record") recordVm.message(null)},modifier=Modifier.testTag("discardUnfinished:${item.domain}:${item.id}")) {Text("放弃")}
                                TextButton(enabled=!busy&&item.readable,onClick={resume(item)},modifier=Modifier.testTag("continueEditing:${item.domain}:${item.id}")) {Text("继续编辑")}
                            }
                        }}
                    }
                }
            }
        }
    }
    remove?.let {item->DiscardEditingDialog(!busy,{remove=null}) {
        error=null
        if(item.domain=="record") recordVm.message(null)
        when(item.domain) {
            "record"->recordVm.discard(item.id)
            "plan"->planVm.run("已放弃未完成编辑") {discard(item.id)}
            "ledger"->ledgerVm.run("已放弃未完成编辑") {discard(item.id)}
            else->toolsVm.run("已放弃未完成编辑") {discard(item.id)}
        }
    }}
}
