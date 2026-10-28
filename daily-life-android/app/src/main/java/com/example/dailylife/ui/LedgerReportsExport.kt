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

@Composable private fun LedgerRange(from:String,to:String,changeFrom:(String)->Unit,changeTo:(String)->Unit,clearable:Boolean=false,marked:Set<String> = emptySet()) {
    var picker by remember {mutableStateOf<String?>(null)}
    LedgerSelectRow("开始日期",from.ifBlank {"不限"}) {picker="from"}
    LedgerSelectRow("结束日期",to.ifBlank {"不限"}) {picker="to"}
    if(clearable) TextButton(onClick={changeFrom("");changeTo("")}) {Text("不限日期")}
    picker?.let {key->GlassDatePicker((if(key=="from") from else to).ifBlank {LocalDate.now().toString()},{picker=null},marked,
        confirmLabel="确认日期",confirmTag="ledgerCalendarConfirm",cancelTag="ledgerCalendarCancel") {if(key=="from") changeFrom(it) else changeTo(it);picker=null}}
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
    val marked=remember(ui.data,account.id) {
        val transactions=ui.data.entries.filter {it.accountId==account.id}.map {it.transactionId}.toSet()
        ui.data.transactions.filter {it.deletedAt==null&&it.id in transactions}.map {it.occurredOn}.toSet()
    }
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerAccountFlow"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Text(account.name,style=MaterialTheme.typography.titleLarge);Text("全账本真实账户分录 · 期初基准 "+account.openingAsOfDate,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)}
        item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            LedgerRange(from,to,{vm.accountRange(it,to)},{vm.accountRange(from,it)},marked=marked)
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }}}
        statement?.let {report->
            item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                LedgerReportValue("区间开始余额",ledgerMoney(ui,report.opening));LedgerReportValue("期间入账",ledgerMoney(ui,report.inflow));LedgerReportValue("期间出账",ledgerMoney(ui,report.outflow));LedgerReportValue("区间结束余额",ledgerMoney(ui,report.closing))
                Text("包含转入、转出与校准。消费收支汇总可在统计中查看。",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
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

@Composable private fun LedgerReportValue(label:String,value:String) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {Text(label,color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall);Text(value,style=MaterialTheme.typography.titleMedium)}
}
