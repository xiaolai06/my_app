package com.example.dailylife

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.ledger.LedgerForm
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Real Compose gestures and Room commits. Creates and removes only this test's finance data. */
class LedgerUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun createCorrectAndRecreateRealLedgerPages() {
        val app=compose.activity.application as DailyLifeApplication
        val repo=app.ledger
        val name="界面验证账本 "+newId().take(8)
        val appearance=runBlocking {app.preferences.appearance.first()}
        val dashboard=runBlocking {app.preferences.dashboard.first()}
        var ledgerId:String?=null;var accountId:String?=null
        val accountName="界面验证账户 "+newId().take(8)
        try {
            runBlocking {repo.initialize();repo.saveLedger(name);ledgerId=repo.load().ledgers.first {it.name==name}.id;app.preferences.set(Appearance(theme="light",reducedMotion=true));app.preferences.setDashboard(dashboard.copy(hideAmounts=false))}
            compose.waitUntil(15000) {compose.onAllNodesWithTag("root:账本").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("root:账本").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("selectLedger").fetchSemanticsNodes().isNotEmpty()}
            ready();compose.onNodeWithTag("selectLedger").performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
            compose.onNodeWithTag("addLedgerTransaction").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerAmount").fetchSemanticsNodes().isNotEmpty()};ready()
            compose.onNodeWithTag("ledgerAmount").performTextReplacement("12.34")
            compose.onNodeWithTag("ledgerSubject").performScrollTo().performTextReplacement("认真记下每一笔")
            screenshot("ledger-editor-light.png")
            compose.onNodeWithTag("saveLedgerTransaction").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("backLedgerDetail").fetchSemanticsNodes().isNotEmpty()};ready()
            val t=runBlocking {repo.load().transactions.single {it.ledgerId==ledgerId}}
            Assert.assertEquals(1234L,t.amountMinor);Assert.assertNull(t.accountId);Assert.assertNull(t.occurredTime)
            compose.onNodeWithText("更正",substring=false).performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerAmount").fetchSemanticsNodes().isNotEmpty()};ready()
            compose.onNodeWithTag("ledgerAmount").performTextReplacement("15.68")
            compose.onNodeWithTag("saveLedgerTransaction").performClick()
            compose.waitUntil(15000) {runBlocking {repo.load().transactions.first {it.id==t.id}.amountMinor==1568L}};ready()
            screenshot("ledger-detail-light.png")
            compose.onNodeWithTag("backLedgerDetail").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("ledgerRoot").fetchSemanticsNodes().isNotEmpty()}
            screenshot("ledger-overview-light.png")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerRoot").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("selectLedger").assertTextContains(name,substring=true)
            compose.onNodeWithTag("ledgerOverview").performScrollToNode(hasText("认真记下每一笔"))
            compose.onNodeWithText("认真记下每一笔").assertIsDisplayed()
            tab("stats");screenshot("ledger-statistics-light.png")
            tab("accounts")
            compose.onNodeWithTag("ledgerSettings").performScrollTo().performClick()
            compose.onNodeWithText("预算",substring=false).performScrollTo().performClick();compose.onNodeWithText("设置预算",substring=false).performClick()
            compose.onNodeWithText("预算额度",substring=false).performTextInput("100.00")
            compose.onNodeWithText("保存预算",substring=false).assertIsDisplayed().performClick()
            compose.waitUntil(15000) {runBlocking {repo.load().budgets.any {it.ledgerId==ledgerId&&it.limitMinor==10000L}}};ready()
            screenshot("ledger-budgets-light.png")
            compose.onNodeWithTag("backLedgerPage").performClick()
            compose.onNodeWithTag("backLedgerPage").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerTab:accounts").fetchSemanticsNodes().isNotEmpty()}
            val customFrom=YearMonth.now().minusMonths(1).atDay(15).toString()
            runBlocking {
                repo.saveAccount(accountName,"cash","100.50",YearMonth.now().minusMonths(1).atDay(1).toString())
                accountId=repo.load().accounts.single {it.name==accountName}.id
                repo.save(repo.form(t.id).set("account",accountId!!))
            }
            tab("accounts")
            compose.waitUntil(15000) {compose.onAllNodesWithText(accountName).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(accountName).performScrollTo().performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerAccountFlow").fetchSemanticsNodes().isNotEmpty()}
            reportDate("开始日期",customFrom)
            compose.onNodeWithTag("ledgerAccountFlow").performScrollToNode(hasText("此笔后余额 ¥ 84.82"))
            compose.onNodeWithText("此笔后余额 ¥ 84.82").assertIsDisplayed()
            compose.onNodeWithText("认真记下每一笔").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("backLedgerDetail").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("backLedgerDetail").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerAccountFlow").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("ledgerAccountFlow").performScrollToNode(hasText(customFrom,substring=false))
            compose.onNodeWithText(customFrom,substring=false).assertIsDisplayed()
            screenshot("ledger-account-flow-light.png")
            compose.onNodeWithTag("backLedgerPage").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("ledgerTab:overview").fetchSemanticsNodes().isNotEmpty()}
            runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true));app.preferences.setDashboard(dashboard.copy(hideAmounts=true))}
            tab("overview")
            compose.onNodeWithTag("hideLedgerAmounts").assertDoesNotExist()
            compose.onNodeWithTag("ledgerPeriod:month").assertIsSelected()
            compose.onNodeWithTag("ledgerOverview").performScrollToIndex(0)
            compose.onNodeWithTag("ledgerMonthExpense").assertTextEquals("¥ 15.68")
            screenshot("ledger-overview-dark.png")
        } catch(e:Throwable) {runCatching {screenshot("ledger-ui-failure.png",false)};throw e}
        finally {runBlocking {
            val id=ledgerId
            if(id!=null) {
                repo.load().drafts.filter {runCatching {LedgerForm.parse(it.payload).text("ledger")==id}.getOrDefault(false)}.forEach {repo.discard(it.id)}
                repo.load().budgets.filter {it.ledgerId==id}.forEach {repo.removeBudget(it.id)}
                repo.load().transactions.filter {it.ledgerId==id}.forEach {if(it.deletedAt==null) repo.manage(it.id,"trash");repo.permanentlyDelete(it.id)}
                repo.db.openHelper.writableDatabase.execSQL("DELETE FROM ledgers WHERE id=?",arrayOf(id))
            }
            accountId?.let {id->if(repo.load().entries.none {it.accountId==id} && repo.load().transactions.none {it.accountId==id||it.toAccountId==id}) repo.db.openHelper.writableDatabase.execSQL("DELETE FROM money_accounts WHERE id=?",arrayOf(id))}
            app.preferences.set(appearance);app.preferences.setDashboard(dashboard)
        }}
    }
    private fun ready() {
        compose.waitUntil(15000) {compose.onAllNodesWithTag("appBusy").fetchSemanticsNodes().isEmpty()}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {compose.onNodeWithTag("dismissMessage").performClick();compose.waitForIdle()}
    }
    private fun tab(value:String) {
        val node=compose.onNodeWithTag("ledgerTab:"+value)
        if(compose.activity.resources.configuration.fontScale>1.3f) node.performScrollTo()
        node.performClick();compose.waitForIdle()
    }
    private fun reportDate(label:String,value:String) {
        compose.onNodeWithText(label,substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("calendarMode:list").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("calendarPrevious").performClick()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:"+value))
        compose.onNodeWithTag("calendarDay:"+value).performClick()
        compose.onNodeWithTag("ledgerCalendarConfirm").assertIsDisplayed().performClick()
        compose.waitForIdle()
    }
    private fun screenshot(name:String,requireFocus:Boolean=true) {
        ready()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {File(it).apply {mkdirs()}} ?: compose.activity.getExternalFilesDir(null)!!
        val focus=runCatching {compose.waitUntil(8000) {compose.activity.hasWindowFocus()}}.isSuccess
        instrumentation.uiAutomation.takeScreenshot().let {bitmap->File(directory,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        if(requireFocus) Assert.assertTrue("System dialog obscures the app",focus)
    }
}
