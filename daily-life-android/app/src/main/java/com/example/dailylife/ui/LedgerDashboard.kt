package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.feature.ledger.*
import java.time.LocalDate

internal fun openLedgerTransactions(vm:LedgerViewModel,type:String="all",category:String="",date:String?=null) {
    if(date!=null) vm.day(date)
    vm.filter(LedgerTransactionFilter(type=type,category=category))
    vm.page("transactions")
}
@Composable internal fun LedgerOverview(ui:LedgerUi,vm:LedgerViewModel) {
    val rows=remember(ui.data,ui.ledgerId,ui.selectedDate,ui.periodMode) {ui.data.periodRows(ui.ledgerId,ui.period()).ledgerOrder()}
    val summary=rows.ledgerSummary()
    val groups=rows.filter {it.type=="expense"}.groupBy {it.categoryId}.map {(id,list)->id to list.sumOf {it.amountMinor}}.sortedByDescending {it.second}.take(3)
    val period=if(ui.periodMode=="day") "当日" else "本月"
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerOverview"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth(),selected=true) {Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(period+"支出",color=LocalGlass.current.muted,style=MaterialTheme.typography.labelLarge)
            Text(ledgerMoney(ui,summary.expense),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("ledgerMonthExpense"))
            HorizontalDivider(color=LocalGlass.current.line)
            LedgerMetricPair(period+"收入",ledgerMoney(ui,summary.income),"收支结余",ledgerMoney(ui,summary.income-summary.netExpense))
            if(summary.refunds!=0L||summary.reimbursement!=0L) LedgerMetricPair("退款到账",ledgerMoney(ui,summary.refunds),"报销到账",ledgerMoney(ui,summary.reimbursement))
            Text("${rows.size} 笔 · "+if(ui.periodMode=="day") ui.selectedDate else "记账 ${rows.map {it.occurredOn}.distinct().size} 天",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }}}
        if(groups.isNotEmpty()) item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("消费去向",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                TextButton(onClick={vm.page("stats")}) {Text("查看统计")}
            }
            groups.forEach {(id,amount)->LedgerCategorySummary(ui,ui.data.categoryName(id),amount,summary.expense,compact=true) {
                openLedgerTransactions(vm,"expense",id ?: UnclassifiedLedgerCategory)
            }}
        }}}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(ui.periodMode=="day") "当天账目" else "最近账目",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={openLedgerTransactions(vm)}) {Text("全部明细")}
        }}
        if(rows.isEmpty()) item {LedgerEmpty("这段时间还没有账目","点右下角「＋」记一笔，也可以点上方日期查看其他时间。")}
        items(rows.take(6),key={it.id}) {LedgerTransactionRow(it,ui) {vm.detail(it.id)}}
    }
}

@Composable internal fun LedgerStatistics(ui:LedgerUi,vm:LedgerViewModel) {
    var direction by rememberSaveable {mutableStateOf("expense")}
    var directionPicker by remember {mutableStateOf(false)}
    val rows=remember(ui.data,ui.ledgerId,ui.periodMode,ui.selectedDate,direction) {ui.data.periodRows(ui.ledgerId,ui.period()).filter {it.type==direction}}
    val total=rows.sumOf {it.amountMinor}
    val groups=rows.groupBy {it.categoryId}.map {(id,list)->id to list.sumOf {it.amountMinor}}.sortedByDescending {it.second}
    val trend=remember(ui.data,ui.ledgerId,ui.periodMode,ui.selectedDate,direction) {ui.data.dailyTrend(ui.ledgerId,ui.period(),direction)}
    val peak=trend.maxByOrNull {it.amount}
    val caption=(if(ui.periodMode=="day") "当日" else "本月")+if(direction=="expense") "支出" else "收入"
    LazyColumn(Modifier.fillMaxSize().testTag("ledgerStatistics"),contentPadding=PaddingValues(18.dp,4.dp,18.dp,24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {GlassPanel(Modifier.fillMaxWidth(),selected=true) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Box {
                TextButton(onClick={directionPicker=true},modifier=Modifier.testTag("ledgerStatsDirection"),contentPadding=PaddingValues(0.dp)) {Text(caption+" ▾",color=LocalGlass.current.muted)}
                DropdownMenu(directionPicker,{directionPicker=false},containerColor=LocalGlass.current.sheet) {
                    listOf("expense" to "支出","income" to "收入").forEach {(id,label)->
                        DropdownMenuItem(text={Text(label)},onClick={direction=id;directionPicker=false},modifier=Modifier.testTag("ledgerStatsDirection:$id").semantics {selected=direction==id})
                    }
                }
            }
            Text(ledgerMoney(ui,total),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("ledgerStatsTotal"))
            Text("${rows.size} 笔 · ${groups.size} 个分类",color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider(color=LocalGlass.current.line)
            Text(if(ui.periodMode=="day") "最近 7 天" else "每日趋势",style=MaterialTheme.typography.titleSmall)
            LedgerDailyChart(ui,trend) {openLedgerTransactions(vm,direction,date=it)}
            if(peak!=null&&peak.amount>0) Text("${LocalDate.parse(peak.date).monthValue}月${LocalDate.parse(peak.date).dayOfMonth}日最多 · "+ledgerMoney(ui,peak.amount),
                style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
            Text(if(direction=="expense") "按原始支出统计，退款单独记入到账日期。" else "普通收入不包含退款、报销到账和转账。",
                style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }}}
        item {Text("分类排行",style=MaterialTheme.typography.titleMedium)}
        if(groups.isEmpty()) item {LedgerEmpty("这段时间暂无"+if(direction=="expense") "支出" else "收入","选择其他日期，或记下一笔新的收支。")}
        else item {GlassPanel(Modifier.fillMaxWidth()) {Column(Modifier.padding(horizontal=18.dp,vertical=8.dp)) {
            groups.forEach {(id,amount)->LedgerCategorySummary(ui,ui.data.categoryName(id),amount,total) {openLedgerTransactions(vm,direction,id ?: UnclassifiedLedgerCategory)}}
        }}}
    }
}
@Composable private fun LedgerDailyChart(ui:LedgerUi,values:List<LedgerDayTotal>,select:(String)->Unit) {
    val peak=values.maxOfOrNull {it.amount}?.coerceAtLeast(1L) ?: 1L
    val large=LocalDensity.current.fontScale>1.3f
    val state=rememberLazyListState()
    LaunchedEffect(ui.selectedDate,ui.periodMode) {state.scrollToItem(values.indexOfFirst {it.date==ui.selectedDate}.coerceAtLeast(0).minus(4).coerceAtLeast(0))}
    LazyRow(Modifier.fillMaxWidth().testTag("ledgerDailyTrend"),state=state,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        items(values,key={it.date}) {point->
            val date=LocalDate.parse(point.date)
            val label=if(ui.periodMode=="day") "${date.monthValue}/${date.dayOfMonth}" else "${date.dayOfMonth}日"
            Column(Modifier.width(if(large) 80.dp else 48.dp).height(if(large) 196.dp else 150.dp)
                .clickable(onClickLabel="查看当天明细") {select(point.date)}.testTag("ledgerTrendDay:"+point.date)
                .semantics(mergeDescendants=true) {contentDescription="${point.date}，${LedgerRules.display(point.amount)}元，查看明细"},
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.BottomCenter) {
                    if(point.amount>0) Box(Modifier.width(if(large) 28.dp else 18.dp).fillMaxHeight((point.amount.toFloat()/peak).coerceIn(.025f,1f))
                        .background(if(point.date==ui.selectedDate) LocalGlass.current.accent else LocalGlass.current.accent.copy(alpha=.45f),RoundedCornerShape(6.dp)))
                }
                Text(label,style=MaterialTheme.typography.labelSmall,color=LocalGlass.current.muted,modifier=Modifier.clearAndSetSemantics {})
            }
        }
    }
    Text("左右滑动 · 点日期查看明细",style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
}
