package com.example.dailylife
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File
class ToolsUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun realChecklistBillAndExactAaFlowUseLocalData() {
        val app=compose.activity.application as DailyLifeApplication;val repo=app.tools
        val initial=runBlocking {repo.load()};val initialLedgerDrafts=runBlocking {app.ledger.load().drafts.map {it.id}.toSet()};val appearance=runBlocking {app.preferences.appearance.first()}
        try {
            runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))}
            compose.waitUntil(20000) {compose.onAllNodesWithTag("root:功能箱").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("root:功能箱").performClick();ready()
            compose.waitUntil(20000) {compose.onAllNodesWithTag("toolsHome").fetchSemanticsNodes().isNotEmpty()}
            shot("tools-home-light.png")
            feature("checklist");add("周末，轻装出门")
            compose.onNodeWithText("添加条目").performScrollTo().performClick()
            compose.onNodeWithText("条目名称（必填）").performTextInput("一本读到一半的书")
            compose.onNodeWithText("保存条目").performScrollTo().performClick();ready()
            compose.waitUntil(10000) {runBlocking {repo.dao.children().any {it.title=="一本读到一半的书"}}}
            val c=runBlocking {repo.dao.children().first {it.title=="一本读到一半的书"}}
            compose.onNodeWithTag("toolCheck:"+c.id).performScrollTo().performClick();ready()
            compose.waitUntil(10000) {runBlocking {repo.dao.children().first {it.id==c.id}.completedAt!=null}}
            compose.onNodeWithTag("childExpense:"+c.id).performScrollTo().performClick();ready()
            compose.onNodeWithTag("childExpenseAmount").performTextInput("9.90");compose.onNodeWithTag("childExpenseAmount").performImeAction();ready()
            compose.onNodeWithTag("saveChildExpense").performScrollTo().performClick();ready()
            compose.waitUntil(10000) {runBlocking {repo.dao.children().first {it.id==c.id}.expenseId!=null}}
            val expense=runBlocking {repo.dao.children().first {it.id==c.id}.expenseId}
            Assert.assertEquals(990L,runBlocking {app.ledger.load().transactions.first {it.id==expense}.amountMinor})
            shot("tools-checklist-light.png")
            compose.onNodeWithTag("backToolDetail").performClick();ready();compose.onNodeWithText("返回").performClick();ready()
            feature("bill");add("家里的每一份安心")
            compose.onNodeWithText("登记已付").performScrollTo().performClick();ready()
            compose.onNodeWithText("实际金额（元）").performTextReplacement("35.50")
            compose.onNodeWithText("确认本期付款").performScrollTo().performClick();ready()
            compose.waitUntil(10000) {runBlocking {repo.dao.settlements().any {it.actualMinor==3550L}}}
            shot("tools-payment-light.png")
            compose.onNodeWithTag("backToolDetail").performClick();ready();compose.onNodeWithText("返回").performClick();ready()
            feature("anniversary");add("记住平凡里的一点特别")
            compose.onNodeWithText("就是今天",substring=true).assertExists();shot("tools-anniversary-light.png")
            compose.onNodeWithTag("backToolDetail").performClick();ready();compose.onNodeWithText("返回").performClick();ready()
            val anniversary=runBlocking {repo.dao.entries().first {it.title=="记住平凡里的一点特别"}}
            compose.onNodeWithTag("root:计划").performClick();ready();compose.onNodeWithTag("planTab:日历").performClick();ready()
            val calendarSource="calendarTool:anniversary:"+anniversary.id
            compose.onNodeWithTag("planCalendar").performScrollToNode(hasTestTag(calendarSource))
            compose.onNodeWithTag(calendarSource).performClick();ready()
            compose.onNodeWithTag("backToolDetail").assertExists()
            compose.onNodeWithText("记住平凡里的一点特别").assertExists()
            compose.onNodeWithTag("backToolDetail").performClick();ready();compose.onNodeWithTag("root:功能箱").performClick();ready()
            feature("aa")
            compose.onNodeWithText("总金额（元）").performTextReplacement("100")
            compose.onNodeWithText("人数（1～1000）").performTextReplacement("3")
            compose.onNodeWithText("计算结果",substring=false).performScrollTo().performClick();ready()
            compose.onNodeWithTag("calculationResult").performScrollTo().assertTextContains("第 1 人：¥33.34",substring=true);shot("tools-aa-light.png")
            compose.onNodeWithText("将第1人的金额带入记账").performScrollTo().performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("ledgerAmount").fetchSemanticsNodes().isNotEmpty()};ready()
            compose.onNodeWithTag("ledgerAmount").assertTextContains("33.34")
            compose.onNodeWithText("返回").performClick();compose.onNodeWithText("丢弃这次输入").performClick();ready()
            compose.onNodeWithTag("calculationResult").assertTextContains("第 1 人：¥33.34",substring=true)
        } catch(e:Throwable) {runCatching {shot("tools-ui-failure.png",false)};throw e}
        finally {runBlocking {
            app.ledger.load().drafts.filter {it.id !in initialLedgerDrafts}.forEach {app.ledger.discard(it.id)}
            val newEntries=repo.dao.entries().filter {e->initial.entries.none {it.id==e.id}}.map {it.id}.toSet()
            val expenseIds=repo.dao.children().filter {it.entryId in newEntries}.mapNotNull {it.expenseId}.toSet()
            repo.dao.children().filter {it.entryId in newEntries && it.expenseId!=null}.forEach {repo.detachChildExpense(it.id)}
            repo.dao.drafts().filter {d->initial.drafts.none {it.id==d.id}}.forEach {repo.discard(it.id)}
            repo.dao.entries().filter {e->initial.entries.none {it.id==e.id}}.forEach {repo.manage(it.id,"trash");repo.deleteForever(it.id)}
            expenseIds.forEach {app.ledger.manage(it,"trash");app.ledger.permanentlyDelete(it)}
            app.planAlarms.reconcile();app.preferences.set(appearance)
        }}
    }
    private fun feature(key:String) {compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:"+key));compose.onNodeWithTag("toolFeature:"+key).performClick();ready()}
    private fun add(title:String) {compose.onNodeWithTag("addTool").performClick();compose.waitUntil(10000) {compose.onAllNodesWithTag("toolTitle").fetchSemanticsNodes().isNotEmpty()};ready();compose.onNodeWithTag("toolTitle").performTextReplacement(title);compose.onNodeWithTag("toolTitle").performImeAction();ready();compose.onNodeWithTag("saveTool").performClick();compose.waitUntil(15000) {compose.onAllNodesWithTag("backToolDetail").fetchSemanticsNodes().isNotEmpty()};ready()}
    private fun ready() {compose.waitUntil(15000) {compose.onAllNodesWithTag("appBusy").fetchSemanticsNodes().isEmpty()};compose.waitForIdle();if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {compose.onNodeWithTag("dismissMessage").performClick();compose.waitForIdle()}}
    private fun shot(name:String,requireFocus:Boolean=true) {ready();val focused=runCatching {compose.waitUntil(8000) {compose.activity.hasWindowFocus()}}.isSuccess;val instrumentation=InstrumentationRegistry.getInstrumentation();val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {File(it).apply {mkdirs()}} ?: compose.activity.getExternalFilesDir(null)!!;instrumentation.uiAutomation.takeScreenshot().let {bitmap->File(dir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()};if(requireFocus) Assert.assertTrue("System dialog covers app",focused)}
}
