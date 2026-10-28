package com.example.dailylife.feature.ledger

import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.LedgerTransaction
import java.time.LocalDate
import java.time.YearMonth

const val UnclassifiedLedgerCategory="__uncategorized__"

data class LedgerPeriod(val mode:String,val date:String) {
    private val anchor=LocalDate.parse(date)
    val from:String=if(mode=="day") date else YearMonth.from(anchor).atDay(1).toString()
    val to:String=if(mode=="day") date else YearMonth.from(anchor).atEndOfMonth().toString()
}
fun LedgerUi.period()=LedgerPeriod(periodMode,selectedDate)
fun LedgerSnapshot.periodRows(ledgerId:String?,period:LedgerPeriod)=transactions.filter {
    it.deletedAt==null && (ledgerId==null || it.ledgerId==ledgerId) && it.occurredOn in period.from..period.to
}
fun List<LedgerTransaction>.ledgerSummary()=LedgerSummary(
    filter {it.type=="income"}.sumOf {it.amountMinor},filter {it.type=="expense"}.sumOf {it.amountMinor},
    filter {it.type=="refund"}.sumOf {it.amountMinor},filter {it.type=="reimbursement"}.sumOf {it.amountMinor})

/** One predicate for the visible list and its filtered totals. Drafts never enter this list. */
fun LedgerSnapshot.browseRows(ledgerId:String?,period:LedgerPeriod,filter:LedgerTransactionFilter):List<LedgerTransaction> {
    val from=filter.from.ifBlank {if(filter.to.isBlank()) period.from else "0001-01-01"}
    val to=filter.to.ifBlank {if(filter.from.isBlank()) period.to else "9999-12-31"}
    if(runCatching {LocalDate.parse(from)<=LocalDate.parse(to)}.getOrDefault(false).not()) return emptyList()
    val min=filter.min.takeIf {it.isNotBlank()}?.let {runCatching {LedgerRules.minor(it,true)}.getOrNull() ?: return emptyList()}
    val max=filter.max.takeIf {it.isNotBlank()}?.let {runCatching {LedgerRules.minor(it,true)}.getOrNull() ?: return emptyList()}
    if(min!=null&&max!=null&&min>max) return emptyList()
    val categoryById=categories.associateBy {it.id}
    val selectedCategories=setOf(filter.category)+categories.filter {it.parentId==filter.category}.map {it.id}
    val query=filter.query.trim()
    return transactions.filter {t->
        t.deletedAt==null && (ledgerId==null || t.ledgerId==ledgerId) && t.occurredOn in from..to &&
            (filter.type=="all" || t.type==filter.type) &&
            (filter.category.isBlank() || if(filter.category==UnclassifiedLedgerCategory) t.categoryId.isNullOrBlank() else t.categoryId in selectedCategories) &&
            (filter.account.isBlank() || t.accountId==filter.account || t.toAccountId==filter.account) &&
            (!filter.onlyBudget || t.includeInBudget) && (min==null||t.amountMinor>=min) && (max==null||t.amountMinor<=max) &&
            listOf(t.subject,t.counterparty,t.note,t.location,categoryById[t.categoryId]?.let {c->
                listOfNotNull(categoryById[c.parentId]?.name,c.name).joinToString(" / ")}.orEmpty())
                .any {it.contains(query,ignoreCase=true)}
    }
}
data class LedgerDayTotal(val date:String,val amount:Long)
/** Monthly charts include zero days; day view provides the selected day and its preceding week. */
fun LedgerSnapshot.dailyTrend(ledgerId:String?,period:LedgerPeriod,direction:String):List<LedgerDayTotal> {
    require(direction in setOf("expense","income"))
    val end=LocalDate.parse(period.to)
    val start=if(period.mode=="day") end.minusDays(6) else LocalDate.parse(period.from)
    val totals=transactions.filter {it.deletedAt==null && (ledgerId==null||it.ledgerId==ledgerId) && it.type==direction && it.occurredOn in start.toString()..end.toString()}
        .groupBy {it.occurredOn}.mapValues {(_,rows)->rows.sumOf {it.amountMinor}}
    return generateSequence(start) {it.plusDays(1)}.takeWhile {it<=end}.map {LedgerDayTotal(it.toString(),totals[it.toString()] ?: 0L)}.toList()
}
