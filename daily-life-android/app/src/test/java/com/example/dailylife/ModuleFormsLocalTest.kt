package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import com.example.dailylife.feature.tools.field
import com.example.dailylife.feature.ledger.LedgerViewModel
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModuleFormsLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val tools get()=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private fun ready() {compose.waitUntil(30_000) {tools.ui.value.ready && records.ui.value.ready && !tools.ui.value.busy && !records.ui.value.busy};compose.waitForIdle()}
    @Before fun setup() {ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))};compose.waitForIdle()}
    @After fun close() {compose.activity.finish();app.backgroundScope.cancel();app.database.close()}
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("myLife.componentOutput","build/reports/components"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun begin(kind:String) {
        compose.runOnIdle {tools.begin(kind)}
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor!=null && !tools.ui.value.busy}
    }
    @Test fun allLifeEditorsKeepUnfinishedInputWithoutCreatingFormalData() {
        listOf("checklist","checklist_template","anniversary","subscription","bill","item","loan","project","outfit").forEachIndexed {index,kind->
            begin(kind)
            compose.onNodeWithTag("toolTitle").performTextInput("示例 · "+kind)
            compose.onNodeWithTag("saveTool").assertIsDisplayed()
            compose.onNodeWithText("稍后继续").assertIsDisplayed()
            shot("editor-"+kind)
            compose.onNodeWithTag("backToolEditor").performClick()
            compose.onNodeWithTag("discardEditing").assertDoesNotExist()
            compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor==null && tools.ui.value.data.drafts.size==index+1 && !tools.ui.value.busy}
        }
        assertTrue(tools.ui.value.data.entries.isEmpty())
    }
    @Test fun collapsedItemDetailsKeepInputAndCommitWithoutAnExpense() {
        begin("item")
        compose.onNodeWithTag("toolTitle").performTextInput("通勤包")
        compose.onNodeWithTag("section:item-identity").performScrollTo().performClick()
        compose.onNodeWithText("品牌",substring=false).performScrollTo().performTextInput("轻行")
        compose.onNodeWithTag("section:item-identity").performScrollTo().performClick()
        compose.onNodeWithText("品牌",substring=false).assertDoesNotExist()
        assertEquals("轻行",tools.ui.value.editor!!.text("brand"))
        compose.onNodeWithTag("section:item-identity").performClick()
        compose.onNodeWithText("品牌",substring=false).performScrollTo().assertTextContains("轻行")
        shot("item-optional-details")
        compose.onNodeWithTag("saveTool").performClick()
        compose.waitUntil(15_000) {compose.waitForIdle();tools.ui.value.editor==null && tools.ui.value.data.entries.any {it.title=="通勤包"} && !tools.ui.value.busy}
        assertEquals("轻行",tools.ui.value.data.entries.single {it.title=="通勤包"}.field("brand"))
        assertTrue(ViewModelProvider(compose.activity)[LedgerViewModel::class.java].ui.value.data.transactions.isEmpty())
    }
    @Test fun subscriptionFormCommitsRealDataWithoutCreatingAnExpense() {
        begin("subscription")
        compose.onNodeWithTag("toolTitle").performTextInput("阅读会员")
        compose.onNodeWithText("单期金额（元，0 为免费）").performTextReplacement("25")
        val originalDate=tools.ui.value.editor!!.text("date")
        val initialDate=LocalDate.parse(originalDate)
        val changedDate=initialDate.withDayOfMonth(if(initialDate.dayOfMonth==1) 2 else 1).toString()
        compose.onNodeWithText("首次续费日期",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("calendarMode:grid").assertIsSelected()
        compose.onNodeWithTag("calendarMode:list").performClick().assertIsSelected()
        compose.onNodeWithTag("calendarDays").performScrollToNode(hasTestTag("calendarDay:$changedDate"))
        compose.onNodeWithTag("calendarDay:$changedDate").performClick()
        compose.onNodeWithTag("cancelDate").performClick()
        assertEquals(originalDate,tools.ui.value.editor!!.text("date"))
        compose.onNodeWithTag("saveTool").performClick()
        compose.waitUntil(15_000) {compose.waitForIdle();tools.ui.value.editor==null && tools.ui.value.data.entries.any {it.title=="阅读会员"} && !tools.ui.value.busy}
        val entry=tools.ui.value.data.entries.single {it.title=="阅读会员"}
        assertEquals("subscription",entry.kind)
        assertTrue(tools.ui.value.data.periods.any {it.sourceId==entry.id})
        assertTrue(ViewModelProvider(compose.activity)[LedgerViewModel::class.java].ui.value.data.transactions.isEmpty())
        shot("subscription-detail")
    }
    @Test fun editedCalculatorInputsInvalidateOldResultsBeforeTheyCanBeReused() {
        compose.onNodeWithTag("root:功能箱").performClick()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:dates"))
        compose.onNodeWithTag("toolFeature:dates").performClick();ready()
        compose.onNodeWithText("计算结果").performScrollTo().performClick();ready()
        compose.onNodeWithTag("calculationResult").assertTextEquals("0 天")
        compose.onNodeWithText("包含开始日").performScrollTo().performClick()
        compose.onNodeWithTag("calculationResult").assertDoesNotExist()
        compose.onNodeWithText("计算结果").performScrollTo().performClick();ready()
        compose.onNodeWithTag("calculationResult").assertTextEquals("1 天")
        shot("calculator-result")
    }
    @Test fun failedAttachmentReuseRetainsEditorAndDoesNotSignalSuccess() {
        val type=records.ui.value.snapshot.types.first {it.kind=="note" && it.subtype=="plain"}
        compose.runOnIdle {records.begin(type.id)}
        compose.waitUntil(10_000) {compose.waitForIdle();records.ui.value.editor!=null && !records.ui.value.busy}
        compose.onNodeWithTag("recordBody").performTextInput("失败时仍然保留的正文")
        var success=false
        compose.runOnIdle {records.attachExisting("missing-attachment") {success=true}}
        compose.waitUntil(10_000) {compose.waitForIdle();records.ui.value.editorError!=null && !records.ui.value.busy}
        assertFalse(success);assertNotNull(records.ui.value.editor)
        compose.onNodeWithTag("recordBody").assertTextContains("失败时仍然保留的正文")
    }
}
