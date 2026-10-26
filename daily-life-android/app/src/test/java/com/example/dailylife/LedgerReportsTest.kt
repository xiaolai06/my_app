package com.example.dailylife

import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import org.junit.Assert.*
import org.junit.Test

class LedgerReportsTest {
    private val account=MoneyAccount("a","owner","现金","现金",openingBalanceMinor=10000,openingAsOfDate="2026-09-01")
    private fun transaction(id:String,type:String,amount:Long,date:String="2026-10-02",book:String="book1",account:String?="a",deleted:Boolean=false)=
        LedgerTransaction(id,"owner",book,type,amount,date,accountId=account,deletedAt=if(deleted) 1 else null,createdAt=1)

    @Test fun statementIncludesOtherLedgersAndUsesEntryDirectionWithPriorBalance() {
        val prior=transaction("prior","income",2000,"2026-09-30")
        val expense=transaction("expense","expense",3000,book="book2")
        val transfer=transaction("transfer","transfer",4000,"2026-10-03").copy(toAccountId="b")
        val refund=transaction("refund","refund",500,"2026-10-04")
        val deleted=transaction("deleted","expense",900,"2026-10-05",deleted=true)
        val later=transaction("later","expense",111,"2026-10-20")
        val s=LedgerSnapshot(transactions=listOf(later,deleted,refund,transfer,expense,prior),entries=listOf(
            AccountEntry(prior.id,"a",2000,"income"),AccountEntry(expense.id,"a",-3000,"expense"),AccountEntry(transfer.id,"a",-4000,"out"),
            AccountEntry(transfer.id,"b",4000,"in"),AccountEntry(refund.id,"a",500,"refund"),AccountEntry(deleted.id,"a",-900,"expense"),AccountEntry(later.id,"a",-111,"expense")))
        val report=s.statement(account,"2026-10-01","2026-10-05")
        assertEquals(12000L,report.opening);assertEquals(5500L,report.closing)
        assertEquals(500L,report.inflow);assertEquals(7000L,report.outflow)
        assertEquals(listOf(9000L,5000L,5500L),report.lines.map {it.balance})
        assertEquals(listOf("expense","transfer","refund"),report.lines.map {it.transaction.id})
    }

    @Test fun cashFlowSeparatesTransferCalibrationAndUnassignedTransactions() {
        val rows=listOf(transaction("in","income",2000),transaction("expense","expense",3000,book="book2"),transaction("refund","refund",500),
            transaction("receipt","reimbursement",700),transaction("transfer","transfer",4000).copy(toAccountId="b"),transaction("adjust","adjustment",-200),
            transaction("unassignedExpense","expense",999,account=null),transaction("unassignedIncome","income",888,account=null),transaction("deleted","income",1000,deleted=true))
        val s=LedgerSnapshot(transactions=rows,entries=listOf(AccountEntry("in","a",2000,"income"),AccountEntry("expense","a",-3000,"expense"),
            AccountEntry("refund","a",500,"refund"),AccountEntry("receipt","a",700,"reimbursement"),AccountEntry("transfer","a",-4000,"out"),
            AccountEntry("transfer","b",4000,"in"),AccountEntry("adjust","a",-200,"adjustment"),AccountEntry("deleted","a",1000,"income")))
        val report=s.cashFlow("2026-10-01","2026-10-31")
        assertEquals(3200L,report.inflow);assertEquals(3000L,report.outflow);assertEquals(-200L,report.adjustment)
        assertEquals(2000L,report.income);assertEquals(3000L,report.expense);assertEquals(500L,report.refunds);assertEquals(700L,report.reimbursement)
        assertEquals(999L,report.unassignedExpense);assertEquals(888L,report.unassignedIncome)
        assertFalse(report.transactions.any {it.type=="transfer"||it.deletedAt!=null})
    }

    @Test fun statementRejectsPeriodBeforeOpeningBasisAndReversedDates() {
        assertThrows(IllegalArgumentException::class.java) {LedgerSnapshot().statement(account,"2026-08-31","2026-10-01")}
        assertThrows(IllegalArgumentException::class.java) {LedgerSnapshot().statement(account,"2026-10-02","2026-10-01")}
    }
}
