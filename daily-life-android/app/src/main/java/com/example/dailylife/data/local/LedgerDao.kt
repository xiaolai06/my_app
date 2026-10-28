package com.example.dailylife.data.local
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
@Dao interface LedgerDao {
    @RawQuery(observedEntities=[Ledger::class,LedgerCategory::class,MoneyAccount::class,LedgerTransaction::class,AccountEntry::class,LedgerDraft::class,TransactionTemplate::class,MonthlyBudget::class,BudgetAlert::class,ReimbursementClaim::class,ReimbursementItem::class,LedgerChangeLog::class,LedgerImportBatch::class,LedgerImportRow::class]) fun changes(q:SupportSQLiteQuery):Flow<Int>
    @Query("SELECT * FROM ledgers ORDER BY sortOrder,name") suspend fun ledgers():List<Ledger>
    @Query("SELECT * FROM ledger_categories ORDER BY sortOrder,name") suspend fun categories():List<LedgerCategory>
    @Query("SELECT * FROM money_accounts ORDER BY sortOrder,name") suspend fun accounts():List<MoneyAccount>
    @Query("SELECT * FROM ledger_transactions ORDER BY occurredOn DESC,occurredTime DESC,createdAt DESC,id") suspend fun transactions():List<LedgerTransaction>
    @Query("SELECT * FROM account_entries") suspend fun entries():List<AccountEntry>
    @Query("SELECT * FROM ledger_drafts ORDER BY savedAt DESC") suspend fun drafts():List<LedgerDraft>
    @Query("SELECT * FROM transaction_templates ORDER BY name") suspend fun templates():List<TransactionTemplate>
    @Query("SELECT * FROM monthly_budgets ORDER BY month DESC") suspend fun budgets():List<MonthlyBudget>
    @Query("SELECT * FROM budget_alerts ORDER BY triggeredAt DESC") suspend fun alerts():List<BudgetAlert>
    @Query("SELECT * FROM reimbursement_claims") suspend fun claims():List<ReimbursementClaim>
    @Query("SELECT * FROM reimbursement_items") suspend fun items():List<ReimbursementItem>
    @Query("SELECT * FROM ledger_change_logs ORDER BY createdAt DESC") suspend fun logs():List<LedgerChangeLog>
    @Query("SELECT * FROM ledger_import_batches ORDER BY createdAt DESC") suspend fun batches():List<LedgerImportBatch>
    @Query("SELECT * FROM ledger_import_rows ORDER BY rowNumber") suspend fun rows():List<LedgerImportRow>
    @Upsert suspend fun put(v:Ledger)
    @Upsert suspend fun put(v:LedgerCategory)
    @Upsert suspend fun put(v:MoneyAccount)
    @Upsert suspend fun put(v:LedgerTransaction)
    @Upsert suspend fun put(v:AccountEntry)
    @Upsert suspend fun put(v:LedgerDraft)
    @Upsert suspend fun put(v:TransactionTemplate)
    @Upsert suspend fun put(v:MonthlyBudget)
    @Upsert suspend fun put(v:BudgetAlert)
    @Upsert suspend fun put(v:ReimbursementClaim)
    @Upsert suspend fun put(v:ReimbursementItem)
    @Upsert suspend fun put(v:LedgerChangeLog)
    @Upsert suspend fun put(v:LedgerImportBatch)
    @Upsert suspend fun put(v:LedgerImportRow)
    @Query("DELETE FROM account_entries WHERE transactionId=:id") suspend fun clearEntries(id:String)
    @Query("DELETE FROM ledger_drafts WHERE id=:id") suspend fun removeDraft(id:String)
    @Query("DELETE FROM reimbursement_items WHERE claimId=:id") suspend fun clearItems(id:String)
    @Query("DELETE FROM monthly_budgets WHERE id=:id") suspend fun removeBudget(id:String)
    @Query("DELETE FROM budget_alerts WHERE budgetId=:id") suspend fun clearBudgetAlerts(id:String)
    @Query("DELETE FROM budget_alerts WHERE budgetId NOT IN (SELECT id FROM monthly_budgets)") suspend fun removeOrphanBudgetAlerts()
    @Query("UPDATE ledger_import_rows SET transactionId=NULL, result='deleted' WHERE transactionId IS NOT NULL AND transactionId NOT IN (SELECT id FROM ledger_transactions)") suspend fun repairDeletedImportRows()
    @Query("UPDATE ledger_import_rows SET transactionId=NULL, result='deleted' WHERE transactionId=:id") suspend fun detachDeletedImport(id:String)
    @Query("DELETE FROM transaction_templates WHERE id=:id") suspend fun removeTemplate(id:String)
    @Query("DELETE FROM ledger_transactions WHERE id=:id") suspend fun removeTransaction(id:String)
}
