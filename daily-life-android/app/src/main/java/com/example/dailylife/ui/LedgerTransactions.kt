package com.example.dailylife.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.dailylife.feature.ledger.*
import java.time.LocalDate

@Composable internal fun LedgerTransactions(ui:LedgerUi,vm:LedgerViewModel) {
    val f=ui.transactionFilter
    val rows=remember(ui.data,ui.ledgerId,ui.periodMode,ui.selectedDate,f) {ui.data.browseRows(ui.ledgerId,ui.period(),f).ledgerOrder()}
    val summary=rows.ledgerSummary()
    val details=listOfNotNull(f.account.takeIf {it.isNotBlank()}?.let {ui.data.accountName(it)},
        if(f.from.isNotBlank()||f.to.isNotBlank()) f.from.ifBlank {"不限"}+" 至 "+f.to.ifBlank {"不限"} else null,
        if(f.min.isNotBlank()||f.max.isNotBlank()) "金额 "+f.min.ifBlank {"不限"}+"–"+f.max.ifBlank {"不限"} else null,
        if(f.onlyBudget) "仅预算账目" else null)
    val active=f!=LedgerTransactionFilter() && f!=LedgerTransactionFilter(advanced=true)
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerTransactions"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("共 ${rows.size} 笔",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
                if(active) TextButton(onClick={vm.filter(LedgerTransactionFilter())},modifier=Modifier.testTag("clearLedgerFilters")) {Text("清除筛选")}
            }
            LedgerListTotals(ui,summary.expense,summary.income)
            if(details.isNotEmpty()) Text(details.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.accent,modifier=Modifier.testTag("ledgerActiveFilters"))
        }}
        if(rows.isEmpty()) item {LedgerEmpty("没有符合条件的账目","换个日期，或清除筛选后再看看。")}
        rows.groupBy {it.occurredOn}.forEach {(date,transactions)->
            item(key="date:$date") {Column(Modifier.padding(top=6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val day=LocalDate.parse(date)
                Text("${day.monthValue}月${day.dayOfMonth}日 · "+listOf("周一","周二","周三","周四","周五","周六","周日")[day.dayOfWeek.value-1],style=MaterialTheme.typography.titleSmall)
                val s=transactions.ledgerSummary()
                LedgerListTotals(ui,s.expense,s.income)
            }}
            items(transactions,key={it.id}) {LedgerTransactionRow(it,ui) {vm.detail(it.id)}}
        }
    }
}

@Composable private fun LedgerListTotals(ui:LedgerUi,expense:Long,income:Long) {
    val out="支出 "+ledgerMoney(ui,expense)
    val incoming="收入 "+ledgerMoney(ui,income)
    if(LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(out,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        Text(incoming,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
    } else Text(out+" · "+incoming,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
}
