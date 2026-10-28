package com.example.dailylife

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.local.CardField
import com.example.dailylife.data.local.newId
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.ledger.LedgerCsvExport
import com.example.dailylife.feature.ledger.LedgerForm
import com.example.dailylife.feature.ledger.LedgerViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.records.EditorData
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The personal data area exports committed snapshots; editor payloads are never export sources. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataTransferLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private val ledger get()=ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            records.ui.value.let {it.ready&&!it.busy} && ledger.ui.value.let {it.ready&&!it.busy} &&
                ViewModelProvider(compose.activity)[PlansViewModel::class.java].ui.value.let {it.ready&&!it.busy} &&
                ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.let {it.ready&&!it.busy}
        }
        compose.waitForIdle()
    }
    @Before fun setup() {
        ready()
        runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))}
        compose.waitUntil(15_000) {compose.waitForIdle();records.ui.value.appearance.reducedMotion}
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}
    private fun dataArea() {
        compose.onNodeWithTag("root:功能箱").performClick();ready()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("personalBackup"))
        compose.onNodeWithTag("personalBackup").performClick()
    }
    private fun exported(file:File) {
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            file.isFile&&file.length()>0&&!records.ui.value.busy&&!ledger.ui.value.busy
        }
        assertNull(ledger.ui.value.error,ledger.ui.value.error)
        assertNull(records.ui.value.editorError,records.ui.value.editorError)
    }

    @Test fun fileActionsAreReachableFromMyDataAreaAndRecordSelectionOmitsDrafts() {
        val ids=runBlocking {
            val type=app.records.dao.types().first {it.kind=="note"&&it.subtype=="plain"}.id
            val saved=app.records.newDraft(type)
            val savedData=EditorData.fromJson(saved.payload).copy(title="可以导出的正式记录",body="正式内容")
            app.records.commit(saved.id,savedData)
            val draft=app.records.newDraft(type)
            val draftData=EditorData.fromJson(draft.payload).copy(title="不能导出的未完成记录",body="尚未确认")
            app.records.saveDraft(draft.id,draftData)
            savedData.recordId to draftData.recordId
        }
        compose.waitUntil(15_000) {compose.waitForIdle();records.ui.value.snapshot.records.any {it.record.id==ids.first}&&records.ui.value.snapshot.drafts.size==1}
        listOf("记录","计划","账本").forEach {page->
            compose.onNodeWithTag("root:$page").performClick();ready()
            listOf("csv","markdown","attachment").forEach {kind->compose.onNodeWithTag("dataTransfer:$kind").assertDoesNotExist()}
        }
        dataArea()
        listOf("records","plans","ledger","tools").forEach {domain->
            compose.onNodeWithTag("backup:$domain").performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("restore:$domain").assertIsEnabled()
        }
        listOf("importCsv","csv","markdown","attachment").forEach {kind->
            compose.onNodeWithTag("dataTransfer:$kind").performScrollTo().assertIsDisplayed().assertIsEnabled()
        }
        compose.onNodeWithTag("dataTransfer:csv").performScrollTo().performClick()
        compose.onNodeWithTag("dataCsvCount").performScrollTo().assertTextEquals("当前范围共 0 笔")
        compose.onNodeWithTag("exportDataCsv").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("cancelDataCsv").performClick()
        dataArea()
        compose.onNodeWithTag("dataTransfer:markdown").performScrollTo().performClick()
        compose.onNodeWithTag("selectionOption:${ids.first}").assertIsDisplayed()
        compose.onNodeWithTag("selectionOption:${ids.second}").assertDoesNotExist()
    }

    @Test fun csvExportUsesLatestCommittedAmountsAndDoesNotTouchBalancesOrPendingEdits() {
        val id=runBlocking {
            app.ledger.saveAccount("导出核对账户","cash","100",LocalDate.now().toString())
            val s=app.ledger.load()
            val form=LedgerForm("expense").set("ledger",s.defaultLedger()!!.id).set("account",s.accounts.single().id)
                .set("date",LocalDate.now().toString()).set("amount","11").set("note","最初保存的备注")
            app.ledger.save(form);form.id
        }
        compose.runOnIdle {ledger.configureCsv(LedgerCsvExport(fields=listOf("id","amount","note")))}
        val before=runBlocking {
            // Content changes after choosing export options, as it can while the system file picker is open.
            app.ledger.save(app.ledger.form(id).set("amount","22.50").set("note","最近正式保存的备注"))
            val unfinished=app.ledger.form(id).set("amount","99.99").set("note","未完成更正不能导出")
            app.ledger.prepareDraft(unfinished);app.ledger.draft(unfinished)
            val fresh=LedgerForm("expense").set("ledger",app.ledger.load().defaultLedger()!!.id)
                .set("date",LocalDate.now().toString()).set("amount","8.88").set("note","仅在未完成列表中")
            app.ledger.prepareDraft(fresh)
            app.ledger.load()
        }
        val file=File(app.cacheDir,"data-transfer-${newId()}.csv")
        compose.runOnIdle {ledger.exportCsv(Uri.fromFile(file))};exported(file)
        val rows=LedgerRules.csv(file.readText())
        assertEquals(listOf(listOf("交易编号","金额","备注"),listOf(id,"22.50","最近正式保存的备注")),rows)
        val after=runBlocking {app.ledger.load()}
        assertEquals(before.transactions,after.transactions);assertEquals(before.entries,after.entries)
        assertEquals(before.accounts,after.accounts);assertEquals(before.drafts,after.drafts)
        assertEquals(7750L,after.balance(before.accounts.single().id))
    }

    @Test fun markdownExportReadsLatestFormalCardAndOmitsPrivateFieldsAndUnfinishedText() {
        val id=runBlocking {
            val type=app.records.dao.types().first {it.subtype=="card"}.id
            val draft=app.records.newDraft(type)
            val first=EditorData.fromJson(draft.payload)
            val data=first.copy(title="导出验证信息卡",body="过期正文",fields=listOf(
                CardField(newId(),first.recordId,"公开资料","过期公开值"),
                CardField(newId(),first.recordId,"私密凭据","private-value-1",isPrivate=true)
            ))
            app.records.commit(draft.id,data);data.recordId
        }
        val before=runBlocking {
            val saved=app.records.dao.record(id)!!
            val update=app.records.newDraft(saved.record.typeId,target=id)
            val latest=EditorData.fromJson(update.payload).let {data->data.copy(body="最近正式保存的正文",fields=data.fields.map {
                it.copy(value=if(it.isPrivate) "private-value-2" else "最近正式保存的公开值")
            })}
            app.records.commit(update.id,latest)
            val pending=app.records.newDraft(saved.record.typeId,target=id)
            val unfinished=EditorData.fromJson(pending.payload).copy(body="未完成正文不得导出")
            app.records.saveDraft(pending.id,unfinished)
            app.records.dao.record(id)!! to app.records.dao.drafts()
        }
        val file=File(app.cacheDir,"data-transfer-${newId()}.md")
        compose.runOnIdle {records.exportMarkdown(Uri.fromFile(file),id)};exported(file)
        val text=file.readText()
        assertTrue(text.contains("最近正式保存的正文"));assertTrue(text.contains("最近正式保存的公开值"))
        listOf("过期正文","过期公开值","未完成正文不得导出","私密凭据","private-value-1","private-value-2").forEach {assertFalse("Export must omit $it",text.contains(it))}
        val after=runBlocking {app.records.dao.record(id)!! to app.records.dao.drafts()}
        assertEquals(before,after)
        assertTrue(runBlocking {app.ledger.load()}.transactions.isEmpty())
    }
}
