package com.example.dailylife.data.local
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
@Dao interface ToolsDao {
    @RawQuery(observedEntities=[ToolEntry::class,ToolChild::class,ToolLog::class,ToolLink::class,ToolDraft::class,ToolCalculation::class,ToolPaymentPeriod::class,ToolPaymentSettlement::class,FeaturePreference::class])
    fun changes(query:SupportSQLiteQuery):Flow<Int>
    @Query("SELECT * FROM tool_entries ORDER BY pinned DESC,updatedAt DESC") suspend fun entries():List<ToolEntry>
    @Query("SELECT * FROM tool_children ORDER BY sortOrder") suspend fun children():List<ToolChild>
    @Query("SELECT * FROM tool_logs ORDER BY date DESC,createdAt DESC") suspend fun logs():List<ToolLog>
    @Query("SELECT * FROM tool_links ORDER BY sortOrder") suspend fun links():List<ToolLink>
    @Query("SELECT * FROM tool_drafts ORDER BY savedAt DESC") suspend fun drafts():List<ToolDraft>
    @Query("SELECT * FROM tool_calculations ORDER BY createdAt DESC") suspend fun calculations():List<ToolCalculation>
    @Query("SELECT * FROM tool_payment_periods ORDER BY dueDate") suspend fun periods():List<ToolPaymentPeriod>
    @Query("SELECT * FROM tool_payment_settlements") suspend fun settlements():List<ToolPaymentSettlement>
    @Query("SELECT * FROM feature_preferences ORDER BY sortOrder") suspend fun preferences():List<FeaturePreference>
    @Upsert suspend fun put(v:ToolEntry)
    @Upsert suspend fun put(v:ToolChild)
    @Upsert suspend fun put(v:ToolLog)
    @Upsert suspend fun put(v:ToolLink)
    @Upsert suspend fun put(v:ToolDraft)
    @Upsert suspend fun put(v:ToolCalculation)
    @Upsert suspend fun put(v:ToolPaymentPeriod)
    @Upsert suspend fun put(v:ToolPaymentSettlement)
    @Upsert suspend fun put(v:FeaturePreference)
    @Query("DELETE FROM tool_children WHERE id=:id") suspend fun removeChild(id:String)
    @Query("DELETE FROM tool_logs WHERE id=:id") suspend fun removeLog(id:String)
    @Query("DELETE FROM tool_links WHERE entryId=:id AND entityType=:type AND entityId=:target") suspend fun unlink(id:String,type:String,target:String)
    @Query("DELETE FROM tool_drafts WHERE id=:id") suspend fun removeDraft(id:String)
    @Query("DELETE FROM tool_calculations WHERE id=:id") suspend fun removeCalculation(id:String)
    @Query("DELETE FROM tool_calculations") suspend fun clearCalculations()
    @Query("DELETE FROM tool_payment_settlements WHERE periodId=:id") suspend fun removeSettlement(id:String)
    @Query("DELETE FROM tool_entries WHERE id=:id") suspend fun removeEntry(id:String)
    @Query("DELETE FROM tool_children WHERE entryId=:id") suspend fun removeChildren(id:String)
    @Query("DELETE FROM tool_logs WHERE entryId=:id") suspend fun removeLogs(id:String)
    @Query("DELETE FROM tool_links WHERE entryId=:id") suspend fun removeLinks(id:String)
    @Query("DELETE FROM tool_payment_periods WHERE sourceId=:id") suspend fun removePeriods(id:String)
}
