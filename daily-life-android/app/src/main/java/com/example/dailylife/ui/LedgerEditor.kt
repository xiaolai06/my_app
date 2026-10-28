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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*

@Composable internal fun LedgerField(value:String,change:(String)->Unit,label:String,modifier:Modifier=Modifier,single:Boolean=true,numeric:Boolean=false,tag:String?=null,enabled:Boolean=true) {
    GlassTextField(value,change,label={Text(label)},singleLine=single,minLines=if(single) 1 else 3,enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),
        shape=RoundedCornerShape(18.dp),modifier=modifier.fillMaxWidth().then(if(tag!=null) Modifier.testTag(tag) else Modifier))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LedgerEditor(ui:LedgerUi,vm:LedgerViewModel,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return
    var picker by remember {mutableStateOf<String?>(null)};var calendar by remember {mutableStateOf(false)}
    var more by remember(f.id) {mutableStateOf(false)};var calculator by remember {mutableStateOf(false)}
    fun set(key:String,value:String)=vm.edit(f.set(key,value))
    val special=f.kind in setOf("refund","reimbursement")
    val linkedSource=f.text("linkedSource")
    val contextSource=f.text("contextSource")
    val original=ui.data.transactions.find {it.id==f.text("original")};val claim=ui.data.claims.find {it.id==f.text("claim")}
    BackHandler {if(!ui.busy) vm.leaveEditor()}
    Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),radius=22) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            EditorDraftStatus(ui.saved,ui.busy)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(enabled=!ui.busy,onClick=vm::leaveEditor,modifier=Modifier.testTag("cancelLedgerEditor")) {Text("稍后继续")}
                if(!special) TextButton(enabled=!ui.busy,onClick={vm.save(keepAdding=true)}) {Text("保存并继续")}
                GlassButton(enabled=!ui.busy,onClick={vm.save()},modifier=Modifier.testTag("saveLedgerTransaction")) {Text("保存账目")}
            }
        }}
    }) {padding->Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
        GlassPageHeader((if(f.revision==0L) "记一笔" else "更正账目")+(if(special) " · "+ledgerType(f.kind) else ""),back=vm::leaveEditor,backTag="backLedgerEditor",enabled=!ui.busy) {
            if(ui.busy) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("ledgerEditor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(f.text("project").isNotBlank()) {
                Text("保存后加入原专题",color=LocalGlass.current.muted)
                TextButton(enabled=!ui.busy,onClick={set("project","")}) {Text("取消加入专题")}
            }
            if(!special && linkedSource.isBlank() && contextSource.isBlank()) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("expense" to "支出","income" to "收入").forEach {(type,label)->GlassChoice(label,f.kind==type,modifier=Modifier.testTag("ledgerType:"+type),enabled=!ui.busy) {
                    if(type!=f.kind) vm.edit(f.copy(kind=type).set("category","").set("toAccount","").set("fee",""))
                }}
                TextButton(enabled=!ui.busy,onClick={if(f.kind!="transfer") vm.edit(f.copy(kind="transfer").set("category","").set("channel","").set("toAccount","").set("fee",""))},
                    modifier=Modifier.testTag("ledgerType:transfer").semantics {selected=f.kind=="transfer"}) {Text("账户转账",color=if(f.kind=="transfer") LocalGlass.current.accent else LocalGlass.current.muted)}
            }
            if(f.kind=="transfer") Text("账户间转账",style=MaterialTheme.typography.titleMedium,color=LocalGlass.current.accent)
            GlassTextField(f.text("amount"),{set("amount",it)},enabled=!ui.busy,label={Text(if(special) "实际到账金额" else "金额")},leadingIcon={Text("¥",style=MaterialTheme.typography.titleLarge,color=LocalGlass.current.accent)},trailingIcon={TextButton(enabled=!ui.busy,onClick={calculator=true}) {Text("计算")}},textStyle=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Medium),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth().testTag("ledgerAmount"))
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
            if(f.kind=="refund") {
                LedgerSelectRow("原支出",original?.let {ui.data.title(it)} ?: "请选择原支出") {if(!ui.busy) picker="original"}
                original?.let {Text("原消费 "+ledgerMoney(ui,it.amountMinor)+"，已退款 "+ledgerMoney(ui,ui.data.refunded(it.id))+"。账本、分类与预算标记随原消费。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
            } else if(f.kind=="reimbursement") Text("报销单："+(claim?.name ?: "未选择")+"；回款单独统计，不计普通收入。",color=LocalGlass.current.muted)
            if(f.kind in setOf("expense","income")) LedgerEntrySelect("分类",ui.data.categoryName(f.text("category")),ui.data.categoryName(f.text("category")),!ui.busy,"ledgerCategorySelect") {picker="category"}
            LedgerEntrySelect("发生日期",f.text("date",LocalDate.now().toString()),"日期",!ui.busy,"ledgerDateSelect") {calendar=true}
            LedgerField(f.text("subject"),{set("subject",it)},"记点什么 · 如午餐、交通",tag="ledgerSubject",enabled=!ui.busy)
            if(f.kind in setOf("expense","income")) {
                LedgerEntrySelect("支付方式 · 选填",f.text("channel").ifBlank {"不选择"},f.text("channel").ifBlank {"账户"},!ui.busy,"ledgerPaymentChannel") {picker="channel"}
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(2.dp),itemVerticalAlignment=Alignment.CenterVertically) {
                    Text("常用："+ui.defaultPaymentChannel.ifBlank {"未设置"},style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted,modifier=Modifier.testTag("ledgerDefaultPaymentChannel"))
                    if(f.text("channel").isNotBlank()&&f.text("channel")!=ui.defaultPaymentChannel)
                        TextButton(enabled=!ui.busy,onClick={vm.setDefaultPaymentChannel(f.text("channel"))},modifier=Modifier.testTag("setDefaultLedgerPaymentChannel")) {Text("设为常用")}
                    if(ui.defaultPaymentChannel.isNotBlank()) TextButton(enabled=!ui.busy,onClick={vm.setDefaultPaymentChannel("")},modifier=Modifier.testTag("clearDefaultLedgerPaymentChannel")) {Text("清除常用")}
                }
            }
            if(special) LedgerEntrySelect("到账账户 · 必填",ui.data.accountName(f.text("account")),"账户",!ui.busy,"ledgerAccountSelect") {picker="account"}
            if(f.kind=="transfer") {
                LedgerEntrySelect("转出账户 · 必填",ui.data.accounts.find {it.id==f.text("account")}?.name ?: "选择转出账户","账户",!ui.busy,"ledgerTransferFrom") {picker="account"}
                LedgerEntrySelect("转入账户 · 必填",ui.data.accounts.find {it.id==f.text("toAccount")}?.name ?: "选择另一个账户","账户",!ui.busy,"ledgerTransferTo") {picker="toAccount"}
                LedgerField(f.text("fee"),{set("fee",it)},"手续费（选填，默认 0）",numeric=true,enabled=!ui.busy)
                if(f.text("fee").isNotBlank() && f.text("fee")!="0") {
                    LedgerSelectRow("手续费账户",ui.data.accountName(f.text("feeAccount",f.text("account")))) {if(!ui.busy) picker="feeAccount"}
                    LedgerSelectRow("手续费分类",ui.data.categories.find {it.id==f.text("feeCategory")}?.name ?: "手续费") {if(!ui.busy) picker="feeCategory"}
                }
                Text("转账本体不计普通收支；手续费是一笔关联支出，一起提交。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled=!ui.busy,onClick={more=!more},modifier=Modifier.testTag("ledgerMoreInformation")) {Text(if(more) "收起更多信息" else "更多信息 · 备注、凭证等")}
            if(more) {
                if(f.kind in setOf("expense","income")) LedgerEntrySelect((if(f.kind=="income") "收款账户" else "付款账户")+" · 选填",if(f.text("account").isBlank()) "不选账户，只记录收支" else ui.data.accountName(f.text("account")),"银行卡",!ui.busy,"ledgerAccountSelect") {picker="account"}
                if(!special&&linkedSource.isBlank()) LedgerSelectRow("保存到",ui.data.ledgerName(f.text("ledger"))) {if(!ui.busy) picker="ledger"}
                LedgerField(f.text("note"),{set("note",it)},"备注 · 支持 Markdown",single=false,enabled=!ui.busy)
                LedgerField(f.text("merchant"),{set("merchant",it)},"商户或对方（选填）",enabled=!ui.busy)
                if(special) LedgerSelectRow("支付方式 · 选填",f.text("channel").ifBlank {"不选择"}) {if(!ui.busy) picker="channel"}
                LedgerField(f.text("location"),{set("location",it)},"地点（手动填写）",enabled=!ui.busy)
                LedgerField(f.text("time"),{set("time",it)},"具体时刻（选填 HH:mm）",enabled=!ui.busy)
                LedgerField(f.text("zone",ZoneId.systemDefault().id),{set("zone",it)},"输入时区",enabled=!ui.busy)
                if(f.kind=="expense") Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(f.text("budget","true")=="true",{set("budget",it.toString())},enabled=!ui.busy);Text("计入消费预算")}
                LedgerSelectRow("标签",f.text("tags").split(",").filter {it.isNotBlank()}.mapNotNull {id->ui.records.tags.find {it.id==id}?.name}.joinToString("、").ifBlank {"选择共享标签"}) {if(!ui.busy) picker="tags"}
                Row {TextButton(enabled=!ui.busy,onClick=addFiles) {Text("图片与文件")};TextButton(enabled=!ui.busy,onClick={picker="file"}) {Text("复用已有附件")}}
                AttachmentList(ui.records.attachmentsFor("ledger_draft",f.id),vm.files,view,{a->vm.run("附件已移除") {removeAttachment(f.id,a.id)}})
            }
        }
    }}
    if(calendar) LedgerCalendarPicker(f.text("date",LocalDate.now().toString()),{calendar=false}) {set("date",it);calendar=false}
    val options=when(picker) {
        "ledger"->ui.data.ledgers.filter {it.archivedAt==null || it.id==f.text("ledger")}.map {it.id to it.name}
        "account","feeAccount","toAccount"->(if(picker=="account"&&!special&&f.kind!="transfer") listOf("" to "未指定账户") else emptyList())+ui.data.accounts.filter {it.archivedAt==null || it.id==f.text(picker.orEmpty())}.map {it.id to it.name}
        "original"->ui.data.transactions.filter {it.deletedAt==null&&it.type=="expense"}.map {it.id to (ui.data.title(it)+" · "+it.occurredOn+" · "+ledgerMoney(ui,it.amountMinor))}
        "channel"->listOf("" to "不选择","微信" to "微信","支付宝" to "支付宝","银行卡" to "银行卡","现金" to "现金","其他" to "其他")
        else->emptyList()
    }
    if(picker in setOf("category","feeCategory")) LedgerCategoryPicker(ui,vm,if(picker=="feeCategory") "expense" else f.kind,f.text(picker.orEmpty()),{picker=null}) {value->set(picker.orEmpty(),value);picker=null}
    if(picker!=null && picker !in setOf("tags","file","category","feeCategory")) LedgerPicker("选择"+when(picker) {"ledger"->"账本";"original"->"原支出";"channel"->"支付方式";else->"账户"},options,f.text(picker.orEmpty()),{picker=null}) {value->set(picker.orEmpty(),value);picker=null}
    if(picker=="tags") GlassSheet("共享标签",{picker=null}) {
        val selected=f.text("tags").split(",").filter {it.isNotBlank()}.toSet()
        MultiChoices(ui.records.tags.map {it.id to it.name},selected) {set("tags",it.joinToString(","))}
        TextButton(onClick={picker=null}) {Text("完成")}
    }
    if(picker=="file") LedgerPicker("复用已有附件",ui.records.attachments.map {it.id to it.displayName},null,{picker=null}) {id->vm.run("附件已加入当前编辑",onSuccess={picker=null}) {attachExisting(f.id,id)}}
    if(calculator) LedgerCalculator(f.text("amount"),{calculator=false}) {set("amount",it);calculator=false}
}

@Composable private fun LedgerCalculator(initial:String,dismiss:()->Unit,use:(String)->Unit) {
    var expression by remember {mutableStateOf(initial)};var error by remember {mutableStateOf<String?>(null)}
    GlassSheet("金额计算",dismiss) {
        Text("支持加减，例如 18.5 + 12 - 3；计算后回填，仍需保存账目。",color=LocalGlass.current.muted)
        LedgerField(expression,{expression=it;error=null},"计算式")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        GlassButton(onClick={runCatching {
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
        GlassPageHeader("账目详情",back={vm.detail(null)},backTag="backLedgerDetail") {
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
        if(fees.isNotEmpty()) {Text("关联费用 "+fees.size+" 笔，请选择处理方式。");Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(includeFee,{includeFee=it});Text("关联费用一起删除；取消勾选则解除关联并保留")}}
        GlassButton(enabled=!ui.busy,onClick={vm.run("账目已移入回收站",onSuccess={action=null;vm.detail(null)}) {manage(t.id,"trash",includeFee)}},modifier=Modifier.fillMaxWidth()) {Text("确认移入回收站")}
        TextButton(onClick={action=null}) {Text("取消")}
    }
    if(template) GlassSheet("保存常用模板",{template=false}) {
        LedgerField(templateName,{templateName=it},"模板名称")
        Text("保留金额、分类与账户，不复制附件、退款、报销或付款关系。",color=LocalGlass.current.muted)
        GlassButton(enabled=!ui.busy,onClick={vm.run("模板已保存",onSuccess={template=false}) {saveTemplate(templateName,form(t.id))}},modifier=Modifier.fillMaxWidth()) {Text("保存模板")}
    }
}
