package com.example.dailylife.feature.ledger
import com.example.dailylife.data.local.*
import org.json.JSONObject
import java.time.YearMonth
data class LedgerForm(val kind:String="expense",val id:String=newId(),val revision:Long=0,val payload:String="{}") {
    fun text(key:String,default:String="")=JSONObject(payload).optString(key,default)
    fun set(key:String,value:String)=copy(payload=JSONObject(payload).put(key,value).toString())
    fun json()=JSONObject().put("version",1).put("kind",kind).put("id",id).put("revision",revision).put("data",JSONObject(payload)).toString()
    companion object {fun parse(json:String):LedgerForm {val o=JSONObject(json);require(o.getInt("version")==1);return LedgerForm(o.getString("kind"),o.getString("id"),o.getLong("revision"),o.getJSONObject("data").toString())}}
}
data class LedgerSummary(val income:Long=0,val expense:Long=0,val refunds:Long=0,val reimbursement:Long=0) {val netExpense get()=expense-refunds}
data class LedgerSnapshot(val ledgers:List<Ledger> = emptyList(),val categories:List<LedgerCategory> = emptyList(),val accounts:List<MoneyAccount> = emptyList(),val transactions:List<LedgerTransaction> = emptyList(),val entries:List<AccountEntry> = emptyList(),val drafts:List<LedgerDraft> = emptyList(),val templates:List<TransactionTemplate> = emptyList(),val budgets:List<MonthlyBudget> = emptyList(),val alerts:List<BudgetAlert> = emptyList(),val claims:List<ReimbursementClaim> = emptyList(),val items:List<ReimbursementItem> = emptyList(),val changes:List<LedgerChangeLog> = emptyList(),val batches:List<LedgerImportBatch> = emptyList(),val rows:List<LedgerImportRow> = emptyList()) {
    fun defaultLedger()=ledgers.firstOrNull {it.isDefault&&it.archivedAt==null} ?: ledgers.firstOrNull {it.archivedAt==null}
    fun balance(id:String,on:String?=null):Long {val a=accounts.find {it.id==id} ?:return 0;val valid=transactions.filter {it.deletedAt==null&&(on==null||it.occurredOn<=on)}.map {it.id}.toSet();return a.openingBalanceMinor+entries.filter {it.accountId==id&&it.transactionId in valid}.sumOf {it.deltaMinor}}
    fun summary(ledgerId:String?=null,month:String=YearMonth.now().toString()):LedgerSummary {val t=transactions.filter {it.deletedAt==null&&(ledgerId==null||it.ledgerId==ledgerId)&&it.occurredOn.startsWith(month)};return LedgerSummary(t.filter {it.type=="income"}.sumOf {it.amountMinor},t.filter {it.type=="expense"}.sumOf {it.amountMinor},t.filter {it.type=="refund"}.sumOf {it.amountMinor},t.filter {it.type=="reimbursement"}.sumOf {it.amountMinor})}
    fun refunded(id:String)=transactions.filter {it.deletedAt==null&&it.type=="refund"&&it.originalExpenseId==id}.sumOf {it.amountMinor}
    fun claimPaid(id:String)=transactions.filter {it.deletedAt==null&&it.type=="reimbursement"&&it.claimId==id}.sumOf {it.amountMinor}
    fun claimRequested(id:String)=items.filter {it.claimId==id}.sumOf {it.requestedMinor}
    fun budgetUsed(b:MonthlyBudget)=transactions.filter {it.deletedAt==null&&it.ledgerId==b.ledgerId&&it.occurredOn.startsWith(b.month)&&it.includeInBudget&&(b.categoryId==null||it.categoryId==b.categoryId)}.sumOf {if(it.type=="expense") it.amountMinor else if(it.type=="refund") -it.amountMinor else 0L}
}
