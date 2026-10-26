package com.example.dailylife.feature.ledger

import com.example.dailylife.data.local.*
import java.time.LocalDate

data class AccountStatementLine(val transaction:LedgerTransaction,val delta:Long,val balance:Long)
data class AccountStatement(val opening:Long,val closing:Long,val inflow:Long,val outflow:Long,val lines:List<AccountStatementLine>)

/** Shared accounts always include the original entries from every ledger. */
fun LedgerSnapshot.statement(account:MoneyAccount,from:String,to:String):AccountStatement {
    val start=LocalDate.parse(from);val end=LocalDate.parse(to)
    require(start<=end) {"结束日期不能早于开始日期"}
    require(start>=LocalDate.parse(account.openingAsOfDate)) {"期初基准前无余额依据，请从 "+account.openingAsOfDate+" 起查询"}
    val active=transactions.filter {it.deletedAt==null}.associateBy {it.id}
    val deltas=entries.filter {it.accountId==account.id && it.transactionId in active}.groupBy {it.transactionId}.mapValues {(_,e)->e.sumOf {it.deltaMinor}}
    val opening=account.openingBalanceMinor+deltas.entries.filter {active.getValue(it.key).occurredOn<from}.sumOf {it.value}
    var balance=opening
    val lines=deltas.keys.map {active.getValue(it)}.filter {it.occurredOn in from..to}
        .sortedWith(compareBy<LedgerTransaction> {it.occurredOn}.thenBy {it.occurredTime.orEmpty()}.thenBy {it.createdAt}.thenBy {it.id}).map {t->
            val delta=deltas.getValue(t.id);balance+=delta;AccountStatementLine(t,delta,balance)
        }
    return AccountStatement(opening,balance,lines.filter {it.delta>0}.sumOf {it.delta},-lines.filter {it.delta<0}.sumOf {it.delta},lines)
}

data class CashFlowReport(val inflow:Long,val outflow:Long,val adjustment:Long,val income:Long,val expense:Long,val refunds:Long,val reimbursement:Long,val unassignedIncome:Long,val unassignedExpense:Long,val transactions:List<LedgerTransaction>)

/** Transfers move funds between owned accounts. Opening facts and calibration are separate. */
fun LedgerSnapshot.cashFlow(from:String,to:String):CashFlowReport {
    require(LocalDate.parse(from)<=LocalDate.parse(to)) {"结束日期不能早于开始日期"}
    val rows=transactions.filter {it.deletedAt==null && it.occurredOn in from..to}
    val byId=rows.associateBy {it.id};val cashEntries=entries.filter {byId[it.transactionId]?.type in setOf("expense","income","refund","reimbursement")}
    fun amount(kind:String)=rows.filter {it.type==kind && it.accountId!=null}.sumOf {it.amountMinor}
    return CashFlowReport(cashEntries.filter {it.deltaMinor>0}.sumOf {it.deltaMinor},-cashEntries.filter {it.deltaMinor<0}.sumOf {it.deltaMinor},
        entries.filter {byId[it.transactionId]?.type=="adjustment"}.sumOf {it.deltaMinor},amount("income"),amount("expense"),amount("refund"),amount("reimbursement"),
        rows.filter {it.type=="income"&&it.accountId==null}.sumOf {it.amountMinor},rows.filter {it.type=="expense"&&it.accountId==null}.sumOf {it.amountMinor},rows.filter {it.type!="transfer"})
}
