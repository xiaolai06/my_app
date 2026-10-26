package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*

@Composable internal fun LedgerField(value:String,change:(String)->Unit,label:String,modifier:Modifier=Modifier,single:Boolean=true,numeric:Boolean=false,tag:String?=null) {
    OutlinedTextField(value,change,label={Text(label)},singleLine=single,minLines=if(single) 1 else 3,
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),
        shape=RoundedCornerShape(18.dp),modifier=modifier.fillMaxWidth().then(if(tag!=null) Modifier.testTag(tag) else Modifier))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LedgerEditor(ui:LedgerUi,vm:LedgerViewModel,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return
    var leave by remember {mutableStateOf(false)};var picker by remember {mutableStateOf<String?>(null)};var calendar by remember {mutableStateOf(false)}
    var more by remember(f.id) {mutableStateOf(false)};var preview by remember {mutableStateOf(false)};var calculator by remember {mutableStateOf(false)}
    fun set(key:String,value:String)=vm.edit(f.set(key,value))
    val special=f.kind in setOf("refund","reimbursement")
    val linkedSource=f.text("linkedSource")
    val contextSource=f.text("contextSource")
    val original=ui.data.transactions.find {it.id==f.text("original")};val claim=ui.data.claims.find {it.id==f.text("claim")}
    BackHandler(!ui.busy) {leave=true}
    Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),radius=22) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(if(ui.saved) "草稿已暂存本机" else "正在暂存…",color=LocalGlass.current.muted,style=MaterialTheme.typography.labelSmall)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(enabled=!ui.busy,onClick=vm::keep) {Text("稍后继续")}
                if(!special) TextButton(enabled=!ui.busy,onClick={vm.save(keepAdding=true)}) {Text("保存并继续")}
                Button(enabled=!ui.busy,onClick={vm.save()},modifier=Modifier.testTag("saveLedgerTransaction")) {Text("保存账目")}
            }
        }}
    }) {padding->Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(enabled=!ui.busy,onClick={leave=true}) {Text("返回")}
            Text((if(f.revision==0L) "记一笔" else "更正账目")+(if(special) " · "+ledgerType(f.kind) else ""),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(ui.busy) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("ledgerEditor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(f.text("project").isNotBlank()) {
                Text("保存后加入原专题",color=LocalGlass.current.muted)
                TextButton(enabled=!ui.busy,onClick={set("project","")}) {Text("取消加入专题")}
            }
            if(!special && linkedSource.isBlank() && contextSource.isBlank()) FlowChoices(listOf("expense" to "支出","income" to "收入","transfer" to "转账"),f.kind) {type->vm.edit(f.copy(kind=type).set("category","").set("toAccount","").set("fee",""))}
            OutlinedTextField(f.text("amount"),{set("amount",it)},label={Text(if(special) "实际到账金额" else "金额 · CNY")},textStyle=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Medium),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().testTag("ledgerAmount"))
            TextButton(onClick={calculator=true}) {Text("金额计算")}
            if(linkedSource.isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("已关联 · "+linkedSource,style=MaterialTheme.typography.titleSmall)
                Text((if(f.kind=="transfer") "手续费金额" else "金额")+"与付款日期更正会一并同步到来源。原维修、购买日期保留。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                Text("类型："+ledgerType(f.kind)+"，账本："+ui.data.ledgerName(f.text("ledger"))+"。更改类型或账本、删除前，请先在功能箱解除这笔关联。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }}
            if(contextSource.isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("物品引用 · "+contextSource,style=MaterialTheme.typography.titleSmall)
                Text("当前类型为"+ledgerType(f.kind)+"。更正只影响原账目，物品档案中的购买、出售金额和日期会保留。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                Text("更改交易类型或删除前，请先在物品详情解除引用；同币种账本可以更换。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }}
            if(!special) {if(linkedSource.isBlank()) LedgerSelectRow("账本",ui.data.ledgerName(f.text("ledger"))) {picker="ledger"}}
            else if(f.kind=="refund") {
                LedgerSelectRow("原支出",original?.let {ui.data.title(it)} ?: "请选择原支出") {picker="original"}
                original?.let {Text("原消费 "+ledgerMoney(ui,it.amountMinor)+"，已退款 "+ledgerMoney(ui,ui.data.refunded(it.id))+"。账本、分类与预算标记随原消费。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
            } else Text("报销单："+(claim?.name ?: "未选择")+"；回款单独统计，不计普通收入。",color=LocalGlass.current.muted)
            LedgerSelectRow("发生日期",f.text("date",LocalDate.now().toString())) {calendar=true}
            if(f.kind in setOf("expense","income")) LedgerSelectRow("分类",ui.data.categoryName(f.text("category"))) {picker="category"}
            LedgerSelectRow(if(f.kind=="transfer") "转出账户" else if(special) "到账账户（必填）" else "账户",ui.data.accountName(f.text("account"))) {picker="account"}
            if(f.kind=="transfer") {
                LedgerSelectRow("转入账户",ui.data.accounts.find {it.id==f.text("toAccount")}?.name ?: "请选择不同账户") {picker="toAccount"}
                LedgerField(f.text("fee"),{set("fee",it)},"手续费（选填，默认 0）",numeric=true)
                if(f.text("fee").isNotBlank() && f.text("fee")!="0") {
                    LedgerSelectRow("手续费账户",ui.data.accountName(f.text("feeAccount",f.text("account")))) {picker="feeAccount"}
                    LedgerSelectRow("手续费分类",ui.data.categories.find {it.id==f.text("feeCategory")}?.name ?: "手续费") {picker="feeCategory"}
                }
                Text("转账本体不计普通收支；手续费是一笔关联支出，一起提交。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }
            LedgerField(f.text("subject"),{set("subject",it)},"事项（选填，如午餐）",tag="ledgerSubject")
            TextButton(onClick={more=!more}) {Text(if(more) "收起更多信息" else "更多信息 · 商户、时刻、备注与附件")}
            if(more) {
                LedgerField(f.text("merchant"),{set("merchant",it)},"商户或对方（选填）")
                LedgerSelectRow("支付渠道",f.text("channel").ifBlank {"未填写"}) {picker="channel"}
                LedgerField(f.text("location"),{set("location",it)},"地点（手动填写）")
                LedgerField(f.text("time"),{set("time",it)},"具体时刻（选填 HH:mm）")
                LedgerField(f.text("zone",ZoneId.systemDefault().id),{set("zone",it)},"输入时区")
                if(f.kind=="expense") Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(f.text("budget","true")=="true",{set("budget",it.toString())});Text("计入消费预算")}
                TextButton(onClick={preview=!preview}) {Text(if(preview) "编辑备注" else "预览 Markdown 备注")}
                if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"}) else LedgerField(f.text("note"),{set("note",it)},"备注 · 支持 Markdown",single=false)
                LedgerSelectRow("标签",f.text("tags").split(",").filter {it.isNotBlank()}.mapNotNull {id->ui.records.tags.find {it.id==id}?.name}.joinToString("、").ifBlank {"选择共享标签"}) {picker="tags"}
                Row {TextButton(enabled=!ui.busy,onClick=addFiles) {Text("图片与文件")};TextButton(enabled=!ui.busy,onClick={picker="file"}) {Text("复用已有附件")}}
                AttachmentList(ui.records.attachmentsFor("ledger_draft",f.id),vm.files,view,{a->vm.run("附件已移除") {removeAttachment(f.id,a.id)}})
            }
            Text("只有实际提交成功的账目才影响余额；未来日期可保留草稿。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        }
    }}
    if(leave) GlassSheet("保留这次输入？",{leave=false}) {
        Text("草稿不会计入收支、预算与余额。")
        Button(enabled=!ui.busy,onClick={leave=false;vm.keep()},modifier=Modifier.fillMaxWidth()) {Text("保留草稿并返回")}
        TextButton(enabled=!ui.busy,onClick={leave=false;vm.discardEditor()}) {Text("丢弃这次输入")}
        TextButton(onClick={leave=false}) {Text("继续填写")}
    }
    if(calendar) CalendarDialog(f.text("date",LocalDate.now().toString()),{calendar=false}) {set("date",it);calendar=false}
    val options=when(picker) {
        "ledger"->ui.data.ledgers.filter {it.archivedAt==null || it.id==f.text("ledger")}.map {it.id to it.name}
        "category","feeCategory"->listOf("" to if(picker=="feeCategory") "手续费" else "未分类")+ui.data.categories.filter {it.active&&it.direction==if(picker=="feeCategory") "expense" else f.kind}.filter {c->ui.data.categories.none {it.parentId==c.id}}.map {it.id to ui.data.categoryName(it.id)}
        "account","feeAccount","toAccount"->(if(picker=="account"&&!special&&f.kind!="transfer") listOf("" to "未指定账户") else emptyList())+ui.data.accounts.filter {it.archivedAt==null || it.id==f.text(picker.orEmpty())}.map {it.id to it.name}
        "original"->ui.data.transactions.filter {it.deletedAt==null&&it.type=="expense"}.map {it.id to (ui.data.title(it)+" · "+it.occurredOn+" · "+ledgerMoney(ui,it.amountMinor))}
        "channel"->listOf("" to "不填写","现金" to "现金","银行卡" to "银行卡","支付宝" to "支付宝","微信" to "微信","其他" to "其他")
        else->emptyList()
    }
    if(picker!=null && picker !in setOf("tags","file")) LedgerPicker("选择"+when(picker) {"ledger"->"账本";"category","feeCategory"->"分类";"original"->"原支出";"channel"->"支付渠道";else->"账户"},options,f.text(picker.orEmpty()),{picker=null}) {value->set(picker.orEmpty(),value);picker=null}
    if(picker=="tags") GlassSheet("共享标签",{picker=null}) {
        val selected=f.text("tags").split(",").filter {it.isNotBlank()}.toSet()
        MultiChoices(ui.records.tags.map {it.id to it.name},selected) {set("tags",it.joinToString(","))}
        TextButton(onClick={picker=null}) {Text("完成")}
    }
    if(picker=="file") LedgerPicker("复用已有附件",ui.records.attachments.map {it.id to it.displayName},null,{picker=null}) {id->vm.run("附件已加入草稿",onSuccess={picker=null}) {attachExisting(f.id,id)}}
    if(calculator) LedgerCalculator(f.text("amount"),{calculator=false}) {set("amount",it);calculator=false}
}

@Composable private fun LedgerCalculator(initial:String,dismiss:()->Unit,use:(String)->Unit) {
    var expression by remember {mutableStateOf(initial)};var error by remember {mutableStateOf<String?>(null)}
    GlassSheet("金额计算",dismiss) {
        Text("支持加减，例如 18.5 + 12 - 3；计算后回填，仍需保存账目。",color=LocalGlass.current.muted)
        LedgerField(expression,{expression=it;error=null},"计算式")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={runCatching {
            val clean=expression.replace(" ","");require(clean.matches(Regex("\\d+(\\.\\d{1,2})?([+-]\\d+(\\.\\d{1,2})?)*"))) {"请使用最多两位小数的金额与加减号"}
            val sum=Regex("[+-]?\\d+(?:\\.\\d{1,2})?").findAll(clean).fold(java.math.BigDecimal.ZERO) {a,m->a+m.value.toBigDecimal()}
            val result=sum.setScale(2).toPlainString();LedgerRules.minor(result);result
        }.onSuccess(use).onFailure {error=it.message ?: "计算式不正确"}},modifier=Modifier.fillMaxWidth()) {Text("计算并回填")}
    }
}

@Composable internal fun LedgerDetail(ui:LedgerUi,vm:LedgerViewModel,view:(Attachment)->Unit) {
    val t=ui.data.transactions.find {it.id==ui.detailId} ?: return
    var action by remember(t.id) {mutableStateOf<String?>(null)};var includeFee by remember {mutableStateOf(true)};var template by remember {mutableStateOf(false)};var templateName by remember {mutableStateOf(ui.data.title(t))}
    BackHandler {vm.detail(null)}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={vm.detail(null)},modifier=Modifier.testTag("backLedgerDetail")) {Text("返回")}
            Text("账目详情",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(t.deletedAt==null&&t.type!="adjustment") TextButton(enabled=!ui.busy,onClick={vm.begin(id=t.id)}) {Text("更正")}
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(ledgerType(t.type),color=LocalGlass.current.muted);Text(ledgerMoney(ui,t.amountMinor),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
            Text(ui.data.title(t),style=MaterialTheme.typography.titleLarge)
            GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("日期  "+t.occurredOn+" · "+(t.occurredTime ?: "仅日期"));Text("账本  "+ui.data.ledgerName(t.ledgerId));Text("分类  "+ui.data.categoryName(t.categoryId));Text("账户  "+ui.data.accountName(t.accountId))
                if(t.type=="transfer") Text("转入  "+ui.data.accountName(t.toAccountId))
                if(t.counterparty.isNotBlank()) Text("商户  "+t.counterparty)
                if(t.location.isNotBlank()) Text("地点  "+t.location)
                if(t.paymentChannel.isNotBlank()) Text("渠道  "+t.paymentChannel)
                if(t.type in setOf("expense","refund")) Text(if(t.includeInBudget) "计入消费预算" else "不计入消费预算",color=LocalGlass.current.muted)
                Text("输入时区 "+t.zoneId,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }}
            if(t.note.isNotBlank()) GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp)) {MarkdownBody(t.note)}}
            if(t.type=="expense") {
                val refunded=ui.data.refunded(t.id)
                GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("原消费与退款",style=MaterialTheme.typography.titleMedium);Text("已退款 "+ledgerMoney(ui,refunded)+" · 净花费 "+ledgerMoney(ui,t.amountMinor-refunded))
                    ui.data.transactions.filter {it.originalExpenseId==t.id && it.deletedAt==null}.forEach {r->TextButton(onClick={vm.detail(r.id)}) {Text(r.occurredOn+" · "+ledgerMoney(ui,r.amountMinor)+" ›")}}
                    if(t.deletedAt==null) TextButton(enabled=!ui.busy,onClick={vm.begin("refund",original=t.id)}) {Text("登记实际退款")}
                    Text("退款按实际到账月冲减净支出，不改写原消费。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                }}
            }
            t.originalExpenseId?.let {id->TextButton(onClick={vm.detail(id)}) {Text("查看原支出")}}
            t.relatedTransactionId?.let {id->TextButton(onClick={vm.detail(id)}) {Text("查看关联转账")}}
            ui.data.transactions.filter {it.relatedTransactionId==t.id&&it.deletedAt==null}.forEach {fee->TextButton(onClick={vm.detail(fee.id)}) {Text("关联手续费 "+ledgerMoney(ui,fee.amountMinor)+" ›")}}
            AttachmentList(ui.records.attachmentsFor("transaction",t.id),vm.files,view)
            if(t.deletedAt==null) {
                if(t.type in setOf("expense","income","transfer")) {TextButton(onClick={vm.duplicate(t)}) {Text("复制为新账目")};TextButton(onClick={template=true}) {Text("保存为常用模板")}}
                if(t.type=="expense") TextButton(onClick={vm.detail(null);vm.page("claims")}) {Text("管理报销申请")}
                TextButton(enabled=!ui.busy,onClick={action="trash"}) {Text("移入回收站")}
            } else TextButton(enabled=!ui.busy,onClick={vm.run("账目已恢复") {manage(t.id,"restore")}}) {Text("恢复账目")}
        }
    }
    if(action=="trash") GlassSheet("删除这笔账目？",{action=null}) {
        Text("账目进入回收站，不再计入余额或统计。有退款、报销与生活付款依赖时会阻止删除。")
        val fees=ui.data.transactions.filter {it.relatedTransactionId==t.id&&it.deletedAt==null}
        if(fees.isNotEmpty()) {Text("关联费用 "+fees.size+" 笔，请选择处理方式。");Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(includeFee,{includeFee=it});Text("关联费用一起删除；取消勾选则解除关联并保留")}}
        Button(enabled=!ui.busy,onClick={vm.run("账目已移入回收站",onSuccess={action=null;vm.detail(null)}) {manage(t.id,"trash",includeFee)}},modifier=Modifier.fillMaxWidth()) {Text("确认移入回收站")}
        TextButton(onClick={action=null}) {Text("取消")}
    }
    if(template) GlassSheet("保存常用模板",{template=false}) {
        LedgerField(templateName,{templateName=it},"模板名称")
        Text("保留金额、分类与账户，不复制附件、退款、报销或付款关系。",color=LocalGlass.current.muted)
        Button(enabled=!ui.busy,onClick={vm.run("模板已保存",onSuccess={template=false}) {saveTemplate(templateName,form(t.id))}},modifier=Modifier.fillMaxWidth()) {Text("保存模板")}
    }
}
