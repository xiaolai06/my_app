package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.ledger.*
import java.time.*

@Composable private fun LedgerRange(from:String,to:String,changeFrom:(String)->Unit,changeTo:(String)->Unit,clearable:Boolean=false) {
    var picker by remember {mutableStateOf<String?>(null)}
    LedgerSelectRow("开始日期",from.ifBlank {"不限"}) {picker="from"}
    LedgerSelectRow("结束日期",to.ifBlank {"不限"}) {picker="to"}
    if(clearable) TextButton(onClick={changeFrom("");changeTo("")}) {Text("不限日期")}
    picker?.let {key->CalendarDialog((if(key=="from") from else to).ifBlank {LocalDate.now().toString()},{picker=null}) {if(key=="from") changeFrom(it) else changeTo(it);picker=null}}
}
private fun rangeError(from:String,to:String,minimum:String?=null,emptyAllowed:Boolean=false):String? {
    val a=from.takeIf {it.isNotBlank()}?.let {runCatching {LocalDate.parse(it)}.getOrNull()}
    val b=to.takeIf {it.isNotBlank()}?.let {runCatching {LocalDate.parse(it)}.getOrNull()}
    return when {
        (!emptyAllowed && (a==null||b==null)) || (from.isNotBlank()&&a==null) || (to.isNotBlank()&&b==null)->"请填写有效的开始和结束日期"
        a!=null&&b!=null&&a>b->"结束日期不能早于开始日期"
        a!=null&&minimum!=null&&from<minimum->"期初基准前无余额依据，请从 "+minimum+" 起查询"
        else->null
    }
}

@Composable internal fun LedgerAccountFlow(ui:LedgerUi,vm:LedgerViewModel) {
    val account=ui.data.accounts.find {it.id==ui.accountId}
    if(account==null) {LedgerEmpty("账户不可用","请返回账户列表重新选择。");return}
    val from=ui.accountFrom ?: maxOf(YearMonth.parse(ui.month).atDay(1).toString(),account.openingAsOfDate)
    val to=ui.accountTo ?: maxOf(YearMonth.parse(ui.month).atEndOfMonth().toString(),account.openingAsOfDate)
    val error=rangeError(from,to,account.openingAsOfDate)
    val statement=remember(ui.data,account,from,to,error) {if(error==null) ui.data.statement(account,from,to) else null}
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerAccountFlow"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Text(account.name,style=MaterialTheme.typography.titleLarge);Text("全账本真实账户分录 · 期初基准 "+account.openingAsOfDate,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            LedgerRange(from,to,{vm.accountRange(it,to)},{vm.accountRange(from,it)})
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }}}
        statement?.let {report->
            item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                LedgerReportValue("区间开始余额",ledgerMoney(ui,report.opening));LedgerReportValue("期间入账",ledgerMoney(ui,report.inflow));LedgerReportValue("期间出账",ledgerMoney(ui,report.outflow));LedgerReportValue("区间结束余额",ledgerMoney(ui,report.closing))
                Text("包含转入、转出与校准。这里的入账与出账表示资金方向，消费收支请看现金流报告。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }}}
            if(report.lines.isEmpty()) item {LedgerEmpty("这段时间没有账户流水","未指定账户的账目不会补记到该账户。")}
            items(report.lines.asReversed(),key={it.transaction.id}) {line->GlassPanel(Modifier.fillMaxWidth().clickable {vm.detail(line.transaction.id)}) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(ui.data.title(line.transaction),style=MaterialTheme.typography.titleMedium)
                Text(line.transaction.occurredOn+" · "+line.transaction.occurredTime.orEmpty().ifBlank {"仅日期"}+" · "+ledgerType(line.transaction.type),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                Text(ui.data.ledgerName(line.transaction.ledgerId),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
                Text((if(line.delta>=0) "入账 " else "出账 ")+ledgerMoney(ui,kotlin.math.abs(line.delta)),color=LocalGlass.current.accent)
                Text("此笔后余额 "+ledgerMoney(ui,line.balance),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            }}}
        }
    }
}

@Composable internal fun LedgerCashFlow(ui:LedgerUi,vm:LedgerViewModel) {
    val from=ui.cashFrom ?: YearMonth.parse(ui.month).atDay(1).toString()
    val to=ui.cashTo ?: YearMonth.parse(ui.month).atEndOfMonth().toString()
    val error=rangeError(from,to)
    val report=remember(ui.data,from,to,error) {if(error==null) ui.data.cashFlow(from,to) else null}
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerCashFlow"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Text("所有账户 · 全部账本",style=MaterialTheme.typography.titleMedium);Text("账户之间转账不计流入或流出；手续费作为真实支出。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {LedgerRange(from,to,{vm.cashRange(it,to)},{vm.cashRange(from,it)});error?.let {Text(it,color=MaterialTheme.colorScheme.error)}}}}
        report?.let {r->
            item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                LedgerReportValue("账户流入",ledgerMoney(ui,r.inflow));LedgerReportValue("账户流出",ledgerMoney(ui,r.outflow));LedgerReportValue("净流入",ledgerMoney(ui,r.inflow-r.outflow))
                HorizontalDivider();LedgerReportValue("普通收入",ledgerMoney(ui,r.income));LedgerReportValue("消费支出",ledgerMoney(ui,r.expense));LedgerReportValue("退款到账",ledgerMoney(ui,r.refunds));LedgerReportValue("报销到账",ledgerMoney(ui,r.reimbursement))
                Text("退款与报销各自列示，不冒充普通收入。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }}}
            item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("单独核对",style=MaterialTheme.typography.titleMedium)
                LedgerReportValue("余额校准净额",ledgerMoney(ui,r.adjustment));LedgerReportValue("未指定账户收入",ledgerMoney(ui,r.unassignedIncome));LedgerReportValue("未指定账户支出",ledgerMoney(ui,r.unassignedExpense))
                Text("期初值与校准不计消费收支；未指定账户的账目参与消费统计，但没有可确认的账户现金流。",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            }}}
            item {SectionLabel("期间原始账目","查看原账目核对；转账可在账户流水中查看。")}
            if(r.transactions.isEmpty()) item {LedgerEmpty("期间暂无账目","调整日期范围可以查看其他月份。")}
            items(r.transactions,key={it.id}) {t->LedgerTransactionRow(t,ui) {vm.detail(t.id)}}
        }
    }
}

@Composable private fun LedgerReportValue(label:String,value:String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(label,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall);Text(value,style=MaterialTheme.typography.titleMedium)}
}

@Composable internal fun LedgerCsvExportSheet(ui:LedgerUi,vm:LedgerViewModel,dismiss:()->Unit,chooseFile:()->Unit) {
    var options by remember {mutableStateOf(ui.csvExport ?: YearMonth.parse(ui.month).let {LedgerCsvExport(ui.ledgerId,it.atDay(1).toString(),it.atEndOfMonth().toString())})}
    var chooseBook by remember {mutableStateOf(false)}
    val error=rangeError(options.from,options.to,emptyAllowed=true)
    val count=ui.data.transactions.count {it.deletedAt==null && (options.ledgerId==null||it.ledgerId==options.ledgerId) && (options.from.isBlank()||it.occurredOn>=options.from) && (options.to.isBlank()||it.occurredOn<=options.to) && it.type in options.types}
    GlassSheet("导出 CSV 明细",dismiss) {
        LedgerSelectRow("账本范围",if(options.ledgerId==null) "全部账本" else ui.data.ledgerName(options.ledgerId)) {chooseBook=true}
        LedgerRange(options.from,options.to,{options=options.copy(from=it)},{options=options.copy(to=it)},clearable=true)
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        SectionLabel("交易类型","转账、退款和报销使用明确类型代码，导出不改变本机账目。")
        MultiChoices(ledgerCsvTypes.map {it to ledgerType(it)},options.types) {options=options.copy(types=it)}
        SectionLabel("导出字段","按下方顺序输出；金额固定两位小数，单位为人民币元。")
        MultiChoices(ledgerCsvFields.toList(),options.fields.toSet()) {selected->options=options.copy(fields=options.fields.filter {it in selected}+ledgerCsvFields.keys.filter {it in selected&&it !in options.fields})}
        options.fields.forEachIndexed {i,key->Row(Modifier.fillMaxWidth()) {
            Text((i+1).toString()+". "+ledgerCsvFields.getValue(key),Modifier.weight(1f))
            TextButton(enabled=i>0,onClick={val values=options.fields.toMutableList();java.util.Collections.swap(values,i,i-1);options=options.copy(fields=values)}) {Text("上移")}
            TextButton(enabled=i<options.fields.lastIndex,onClick={val values=options.fields.toMutableList();java.util.Collections.swap(values,i,i+1);options=options.copy(fields=values)}) {Text("下移")}
        }}
        Text("类型代码：expense 支出；income 收入；transfer 转账；refund 退款；reimbursement 报销回款；adjustment 余额校准。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        Text("UTF-8 BOM · CSV 含真实金额与选中的备注，金额隐藏不影响导出。文件不包含附件原件。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        Text(if(error==null) "将导出 "+count+" 笔交易" else "请先修正日期范围",color=LocalGlass.current.accent)
        Button(enabled=!ui.busy&&error==null&&options.types.isNotEmpty()&&options.fields.isNotEmpty(),onClick={vm.configureCsv(options);dismiss();chooseFile()},modifier=Modifier.fillMaxWidth().testTag("chooseLedgerCsvTarget")) {Text("选择文件位置并导出")}
        TextButton(onClick=dismiss) {Text("取消")}
    }
    if(chooseBook) LedgerPicker("导出账本范围",listOf("" to "全部账本")+ui.data.ledgers.map {it.id to it.name},options.ledgerId.orEmpty(),{chooseBook=false}) {options=options.copy(ledgerId=it.ifBlank {null});chooseBook=false}
}
