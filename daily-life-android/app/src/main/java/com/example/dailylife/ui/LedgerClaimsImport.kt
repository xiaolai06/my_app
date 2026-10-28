package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import java.time.*
import org.json.JSONObject

@Composable internal fun LedgerClaims(ui:LedgerUi,vm:LedgerViewModel,view:(Attachment)->Unit) {
    var action by remember {mutableStateOf<Pair<ReimbursementClaim,String>?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,40.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text("垫付与到账分开记录",Modifier.weight(1f),color=LocalGlass.current.muted);TextButton(enabled=!ui.busy,onClick={vm.beginClaim()},modifier=Modifier.testTag("addLedgerClaim")) {Text("新建报销单")}}}
        if(ui.data.claims.isEmpty()) item {LedgerEmpty("暂无报销申请","先选择已经发生的支出，申请提交不会改变账户余额。")}
        items(ui.data.claims,key={it.id}) {c->val requested=ui.data.claimRequested(c.id);val paid=ui.data.claimPaid(c.id)
            GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(c.name,style=MaterialTheme.typography.titleMedium)
                val status=when {c.deletedAt!=null->"已在回收站";c.status=="cancelled"->"已取消";c.status=="draft"->"待提交";paid>=requested&&requested>0->"全部到账";paid>0->"部分到账";else->"已提交"}
                Text(status+(if(c.needsReview) " · 待核对" else ""),color=if(c.needsReview) MaterialTheme.colorScheme.error else LocalGlass.current.accent)
                Text("申请 "+ledgerMoney(ui,requested)+" · 到账 "+ledgerMoney(ui,paid));Text("待收 "+ledgerMoney(ui,requested-paid),color=LocalGlass.current.muted)
                if(c.needsReview) Text("原费用退款或变化已影响可报额度，请更正分配后再登记回款。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                ui.data.items.filter {it.claimId==c.id}.forEach {line->val expense=ui.data.transactions.find {it.id==line.expenseId};TextButton(onClick={vm.detail(line.expenseId)}) {Text((expense?.let {ui.data.title(it)} ?: "原支出")+" · "+ledgerMoney(ui,line.requestedMinor)+" ›")}}
                ui.data.transactions.filter {it.claimId==c.id&&it.deletedAt==null}.forEach {t->TextButton(onClick={vm.detail(t.id)}) {Text("到账 "+t.occurredOn+" · "+ledgerMoney(ui,t.amountMinor)+" ›")}}
                AttachmentList(ui.records.attachmentsFor("reimbursement",c.id),vm.files,view)
                if(c.deletedAt==null) {
                    if(c.status!="cancelled") {
                        TextButton(enabled=!ui.busy,onClick={vm.beginClaim(c.id)}) {Text(if(c.status=="submitted") "更正申请" else "编辑并提交")}
                        if(c.status=="submitted"&&!c.needsReview&&paid<requested) TextButton(enabled=!ui.busy,onClick={vm.begin("reimbursement",claim=c.id)}) {Text("登记实际到账")}
                        TextButton(enabled=!ui.busy,onClick={action=c to "cancel"}) {Text("取消申请")}
                    }
                    TextButton(enabled=!ui.busy,onClick={action=c to "trash"}) {Text("移入回收站")}
                } else TextButton(enabled=!ui.busy,onClick={vm.run("报销单已恢复") {manageClaim(c.id,"restore")}}) {Text("恢复报销单")}
            }}
        }
    }
    action?.let {(c,a)->ConfirmDialog(if(a=="cancel") "取消申请" else "删除报销单",if(a=="cancel") "无回款的申请可取消并释放分配额度；已有回款时请先核对真实账目。" else "先检查所有回款历史，允许删除时进入回收站。",{action=null},{vm.run("报销单已更新") {manageClaim(c.id,a)}})}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LedgerClaimEditor(ui:LedgerUi,vm:LedgerViewModel,addFiles:()->Unit,view:(Attachment)->Unit) {
    val f=ui.editor ?: return;val c=ui.data.claims.find {it.id==f.id}
    var picker by remember {mutableStateOf<String?>(null)};var calendar by remember {mutableStateOf(false)};var preview by rememberSaveable(f.id) {mutableStateOf(false)}
    val allocations=remember(f.payload) {val data=JSONObject(f.text("allocations","{}"));data.keys().asSequence().associateWith {data.getString(it)}}
    val submit=f.text("submitDate").isNotBlank()
    fun set(key:String,value:String)=vm.edit(f.set(key,value))
    fun allocate(value:Map<String,String>)=set("allocations",JSONObject(value).toString())
    BackHandler {if(!ui.busy) vm.leaveEditor()}
    Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp),radius=22) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            EditorDraftStatus(ui.saved,ui.busy)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(enabled=!ui.busy,onClick=vm::leaveEditor,modifier=Modifier.testTag("cancelLedgerClaimEditor")) {Text("稍后继续")}
                GlassButton(enabled=!ui.busy,onClick=vm::saveClaimDraft,modifier=Modifier.testTag("saveLedgerClaim")) {Text(if(submit) "保存并提交" else "保存申请")}
            }
        }}
    }) {padding->Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
        GlassPageHeader(if(c==null) "新建报销申请" else "更正报销申请",back=vm::leaveEditor,backTag="backLedgerClaimEditor",enabled=!ui.busy)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("ledgerClaimEditor"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            LedgerField(f.text("name"),{set("name",it)},"报销单名称",tag="ledgerClaimName")
            LedgerSelectRow("结算账本",ui.data.ledgerName(f.text("ledger"))) {picker="ledger"}
            LedgerField(f.text("counterparty"),{set("counterparty",it)},"对方单位或称呼（选填）")
            SectionLabel("费用分配","同一笔支出可分到不同报销单，所有有效分配不能超过原消费减退款。")
            allocations.forEach {(expenseId,amount)->val t=ui.data.transactions.find {it.id==expenseId}
                GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(t?.let {ui.data.title(it)+" · "+it.occurredOn} ?: "原支出不可用")
                    LedgerField(amount,{allocate(allocations+(expenseId to it))},"本单申请金额",numeric=true)
                    TextButton(enabled=!ui.busy,onClick={allocate(allocations-expenseId)}) {Text("移除此费用")}
                }}
            }
            TextButton(enabled=!ui.busy,onClick={picker="expense"}) {Text("选择支出加入申请")}
            Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(submit,{set("submitDate",if(it) LocalDate.now().toString() else "")},enabled=!ui.busy&&c?.status!="submitted");Text(if(c?.status=="submitted") "已正式提交，修改会保留提交事实" else "保存时正式提交申请")}
            if(submit) LedgerSelectRow("提交日期",f.text("submitDate")) {calendar=true}
            if(c?.status=="submitted") Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(f.text("confirmCorrection")=="true",{set("confirmCorrection",it.toString())},enabled=!ui.busy);Text("我已核对本次费用更正与已到账金额")}
            TextButton(onClick={preview=!preview}) {Text(if(preview) "编辑备注" else "预览 Markdown 备注")}
            if(preview) MarkdownBody(f.text("note").ifBlank {"暂无备注"}) else LedgerField(f.text("note"),{set("note",it)},"备注 · 支持 Markdown",single=false)
            SectionLabel("图片与凭证","复用原件引用；移除本单附件不会删除仍被其他内容使用的原件。")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(enabled=!ui.busy,onClick=addFiles,modifier=Modifier.testTag("addClaimFiles")) {Text("添加图片与文件")}
                TextButton(enabled=!ui.busy,onClick={picker="file"}) {Text("复用已有附件")}
            }
            AttachmentList(ui.records.attachmentsFor("ledger_draft",f.id),vm.files,view,{a->vm.run("附件已从当前申请移除") {removeAttachment(f.id,a.id)}})
            Text("保存申请不会改变余额；实际到账后再登记回款。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        }
    }}
    if(picker=="ledger") LedgerPicker("结算账本",ui.data.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},f.text("ledger"),{picker=null}) {set("ledger",it);picker=null}
    if(picker=="expense") LedgerPicker("选择原支出",ui.data.transactions.filter {it.deletedAt==null&&it.type=="expense"&&it.id !in allocations}.map {t->t.id to (ui.data.title(t)+" · "+t.occurredOn+" · "+ledgerMoney(ui,t.amountMinor-ui.data.refunded(t.id)))},null,{picker=null}) {id->
        val t=ui.data.transactions.first {it.id==id};val used=ui.data.items.filter {it.expenseId==id&&it.claimId!=f.id&&ui.data.claims.any {claim->claim.id==it.claimId&&claim.deletedAt==null&&claim.status!="cancelled"}}.sumOf {it.requestedMinor}
        allocate(allocations+(id to LedgerRules.display((t.amountMinor-ui.data.refunded(id)-used).coerceAtLeast(0))));picker=null
    }
    if(picker=="file") LedgerPicker("复用已有附件",ui.records.attachments.map {it.id to it.displayName},null,{picker=null}) {id->vm.run("附件已加入当前申请",onSuccess={picker=null}) {attachExisting(f.id,id)}}
    if(calendar) CalendarDialog(f.text("submitDate",LocalDate.now().toString()),{calendar=false}) {set("submitDate",it);calendar=false}
}

@Composable internal fun LedgerImports(ui:LedgerUi,vm:LedgerViewModel,importCsv:()->Unit) {
    var selected by rememberSaveable {mutableStateOf<String?>(null)};var undo by remember {mutableStateOf<String?>(null)};var confirm by remember {mutableStateOf<String?>(null)};var allowSuspected by remember {mutableStateOf(false)}
    val batch=ui.data.batches.find {it.id==selected}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,4.dp,18.dp,32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {GlassButton(onClick=importCsv,enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) {Text("选择 UTF-8 CSV 文件")}}
        item {Text("只导入普通支出和收入。退款、回款与转账需有可靠关联，不能直接当作收入导入。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
        if(ui.data.batches.isEmpty()) item {LedgerEmpty("暂无导入批次","先匹配日期、金额和类型字段；预览确认前不会写入正式账目。")}
        ui.data.batches.forEach {b->val rows=ui.data.rows.filter {it.batchId==b.id}
            item(key=b.id) {GlassPanel(Modifier.fillMaxWidth().clickable {selected=if(selected==b.id) null else b.id}) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(b.source+" · "+ui.data.ledgerName(b.ledgerId),style=MaterialTheme.typography.titleMedium)
                Text("共 "+rows.size+" 行 · 成功 "+rows.count {it.result=="imported"}+" · 跳过 "+rows.count {it.result=="skipped"}+" · 错误 "+rows.count {it.error.isNotBlank()&&it.result!="skipped"},color=LocalGlass.current.muted)
                Text("状态："+when(b.status) {"preview"->"待确认";"partial"->"部分完成";"completed"->"已导入";"undone"->"已撤销";else->b.status},color=LocalGlass.current.accent)
                Text(if(selected==b.id) "收起预览 ▴" else "展开逐行预览 ▾",color=LocalGlass.current.accent)
            }}}
            if(selected==b.id) {
                item(key="controls"+b.id) {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("文件校验值："+b.fileHash.take(16)+"…",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                    if(rows.any {it.suspectedDuplicate&&it.result=="pending"}) Row(verticalAlignment=Alignment.CenterVertically) {GlassCheckbox(allowSuspected,{allowSuspected=it});Text("我已核对，保留疑似重复的真实消费")}
                    if(rows.any {it.result=="pending"&&it.error.isBlank()}) GlassButton(enabled=!ui.busy,onClick={confirm=b.id},modifier=Modifier.fillMaxWidth()) {Text("确认导入有效行")}
                    if(rows.any {it.result=="imported"}) TextButton(enabled=!ui.busy,onClick={undo=b.id}) {Text("撤销本批次已导入账目")}
                }}}
                items(rows,key={"row"+b.id+"-"+it.rowNumber}) {r->val f=runCatching {LedgerForm.parse(r.parsedValues)}.getOrNull()
                    GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("第 "+r.rowNumber+" 行 · "+when(r.result) {"pending"->"待导入";"imported"->"已导入";"deleted"->"已永久删除（保留去重编号）";"skipped"->"已跳过";"error"->"未通过校验";else->r.result})
                        Text(f?.text("date").orEmpty()+" · "+ledgerType(f?.kind.orEmpty())+" · "+(if(ui.hideAmounts) "••••" else f?.text("amount").orEmpty()))
                        f?.text("merchant")?.takeIf {it.isNotBlank()}?.let {Text(it,color=LocalGlass.current.muted)}
                        if(r.suspectedDuplicate) Text("与本机账目日期、商户、金额相同，请核对",color=LocalGlass.current.accent,style=MaterialTheme.typography.bodySmall)
                        if(r.error.isNotBlank()) Text(r.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        r.transactionId?.let {id->TextButton(onClick={vm.detail(id)}) {Text("查看正式账目")}}
                    }}
                }
            }
        }
    }
    if(ui.csvPending) LedgerCsvMapping(ui,vm)
    confirm?.let {id->ConfirmDialog("确认导入账单","仅导入校验通过的普通收支。可靠外部编号重复会跳过，未确认的疑似重复行保留待处理。",{confirm=null},{vm.run("导入完成，请查看逐行结果") {commitImport(id,allowSuspected)}},"确认导入")}
    undo?.let {id->ConfirmDialog("撤销导入批次","仅处理本批次新增账目。若有退款、报销或生活付款依赖，将停止并保留原数据。",{undo=null},{vm.run("批次已撤销") {undoImport(id)}},"确认撤销")}
}
@Composable private fun LedgerCsvMapping(ui:LedgerUi,vm:LedgerViewModel) {
    val defaults=mapOf("date" to 0,"amount" to 1,"type" to 2,"category" to 3,"merchant" to 4,"note" to 5,"external" to 6)
    var mapping by remember(ui.csvHeaders) {mutableStateOf(defaults.filterValues {it<ui.csvHeaders.size})};var choosing by remember {mutableStateOf<String?>(null)}
    var ledger by remember {mutableStateOf(ui.ledgerId.orEmpty())};var account by remember {mutableStateOf("")};var source by remember {mutableStateOf("CSV")};var sourceAccount by remember {mutableStateOf("")}
    val names=mapOf("date" to "日期（必填）","amount" to "金额（必填）","type" to "类型（必填）","category" to "分类","merchant" to "商户","note" to "备注","external" to "外部交易号")
    GlassSheet("匹配 CSV 字段",vm::dismissCsv) {
        Text("编码：UTF-8 / BOM。日期为 YYYY-MM-DD；金额最多两位小数；类型填 支出/收入 或 expense/income。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
        LedgerSelectRow("导入账本",ui.data.ledgerName(ledger)) {choosing="ledger"};LedgerSelectRow("指定账户",ui.data.accountName(account)) {choosing="account"}
        LedgerField(source,{source=it},"来源平台（用于去重）");LedgerField(sourceAccount,{sourceAccount=it},"来源账户标识（选填，用于去重）")
        names.forEach {(key,label)->LedgerSelectRow(label,mapping[key]?.let {ui.csvHeaders.getOrNull(it)} ?: "不映射") {choosing=key}}
        SectionLabel("前 3 行原值","请核对列的含义；预览不会立即记账。")
        if(ui.hideAmounts) Text("金额隐藏已开启。关闭隐藏后可核对原始行；文件本身仍包含真实金额。",color=LocalGlass.current.muted)
        else ui.csvSample.forEachIndexed {i,cells->Text((i+2).toString()+"："+cells.joinToString(" · ").take(400),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)}
        GlassButton(enabled=!ui.busy&&listOf("date","amount","type").all {it in mapping},onClick={vm.stageCsv(ledger,account.ifBlank {null},source,sourceAccount,mapping)},modifier=Modifier.fillMaxWidth()) {Text("生成校验预览")}
        TextButton(onClick=vm::dismissCsv) {Text("取消导入")}
    }
    when(choosing) {
        "ledger"->LedgerPicker("导入账本",ui.data.ledgers.filter {it.archivedAt==null}.map {it.id to it.name},ledger,{choosing=null}) {ledger=it;choosing=null}
        "account"->LedgerPicker("指定账户",listOf("" to "未指定账户")+ui.data.accounts.filter {it.archivedAt==null}.map {it.id to it.name},account,{choosing=null}) {account=it;choosing=null}
        null->Unit
        else->{val key=choosing!!;LedgerPicker("匹配 · "+names[key],(if(key in setOf("date","amount","type")) emptyList() else listOf("" to "不映射"))+ui.csvHeaders.mapIndexed {i,h->i.toString() to ("第 "+(i+1)+" 列 · "+h)},mapping[key]?.toString(),{choosing=null}) {value->mapping=if(value.isBlank()) mapping-key else mapping+(key to value.toInt());choosing=null}}
    }
}
