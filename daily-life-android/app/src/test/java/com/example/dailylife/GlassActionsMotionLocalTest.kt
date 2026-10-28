package com.example.dailylife

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.ledger.LedgerTransactionFilter
import com.example.dailylife.feature.ledger.LedgerViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.records.RecordFilter
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
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.LocalDate

/** Native screen geometry, original add flows, and deterministic animation-clock checks. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassActionsMotionLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private val plans get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private val ledger get()=ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    private val tools get()=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]

    private fun ready() {
        compose.waitUntil(30_000) {
            compose.waitForIdle()
            records.ui.value.let {it.ready&&!it.busy}&&plans.ui.value.let {it.ready&&!it.busy}&&
                ledger.ui.value.let {it.ready&&!it.busy}&&tools.ui.value.let {it.ready&&!it.busy}
        }
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("dismissMessage").performClick()
    }

    private fun appearance(theme:String="light",reduced:Boolean=true) {
        runBlocking {app.preferences.set(Appearance(theme=theme,reducedMotion=reduced))}
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            records.ui.value.appearance.let {it.theme==theme&&it.reducedMotion==reduced}
        }
        compose.waitForIdle()
    }

    @Before fun resetDisplayAndPages() {
        ready();appearance()
        compose.runOnIdle {
            records.filter(RecordFilter())
            ledger.page("overview");ledger.filter(LedgerTransactionFilter())
            tools.page("home")
        }
        ready()
    }

    @After fun close() {
        compose.mainClock.autoAdvance=true
        compose.activity.finish();app.database.close();app.backgroundScope.cancel()
    }

    private fun root(name:String) {
        if(tools.ui.value.page!="home") {compose.runOnIdle {tools.page("home")};ready()}
        compose.onNodeWithTag("root:$name").performClick();ready()
    }

    private fun toolPage(kind:String) {
        root("功能箱")
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:$kind"))
        compose.onNodeWithTag("toolFeature:$kind").performClick();ready()
    }

    private fun assertCompactAction(tag:String,label:String,contentTag:String) {
        val action=compose.onNodeWithTag(tag)
        action.assertIsDisplayed().assertIsEnabled().assertHasClickAction().assertContentDescriptionEquals(label)
            .assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val buttonBounds=action.fetchSemanticsNode().boundsInRoot
        val contentBounds=compose.onNodeWithTag(contentTag).fetchSemanticsNode().boundsInRoot
        assertTrue("$contentTag must end above $tag: $contentBounds / $buttonBounds",contentBounds.bottom<=buttonBounds.top+1f)
    }

    private fun shot(name:String) {
        compose.waitForIdle()
        val buildRoot=File(System.getProperty("dailyLife.testRoot","build/local-tests")).parentFile
        val file=File(buildRoot,"reports/glass-actions/$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView
                ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            assertTrue("Native screenshot has usable dimensions",bitmap.width>300&&bitmap.height>600)
            bitmap.recycle()
        }
    }

    @Test fun compactActionsHaveSharedSizeSemanticsAndSeparateContentSpace() {
        root("记录");assertCompactAction("addRecord","写记录","recordsContent");shot("01-records-light")
        root("计划");assertCompactAction("addPlan","新任务","plansContent");shot("02-plans-light")
        root("账本");assertCompactAction("addLedgerTransaction","记一笔","ledgerContent");shot("03-ledger-light")
        toolPage("checklist");assertCompactAction("addTool","新建生活清单","toolsContent");shot("04-tools-light")
        root("账本");appearance(theme="dark")
        assertCompactAction("addLedgerTransaction","记一笔","ledgerContent");shot("05-ledger-dark")
    }

    @Test fun ledgerSelectedTabsKeepTheirBoundsAndRapidSwitchKeepsFilter() {
        compose.runOnIdle {
            Settings.Global.putFloat(app.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
            ReflectionHelpers.callStaticMethod<Unit>(ValueAnimator::class.java,"setDurationScale",
                ReflectionHelpers.ClassParameter.from(Float::class.javaPrimitiveType!!,1f))
        }
        assertTrue("System animations must be enabled for the motion check",ValueAnimator.areAnimatorsEnabled())
        appearance(reduced=false);root("账本")
        val filter=LedgerTransactionFilter(query="保留条件",type="expense")
        compose.runOnIdle {ledger.filter(filter)}
        compose.waitForIdle()
        fun assertSelectedTab(id:String) {
            listOf("overview","transactions","stats","accounts").forEach {tab->
                val node=compose.onNodeWithTag("ledgerTab:$tab")
                if(tab==id) node.assertIsSelected() else node.assertIsNotSelected()
            }
            compose.onNodeWithTag("ledgerTabIndicator").assertDoesNotExist()
        }
        assertSelectedTab("overview")
        val statsBounds=compose.onNodeWithTag("ledgerTab:stats").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance=false
        try {
            compose.onNodeWithTag("ledgerTab:stats").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(64)
            assertSelectedTab("stats")
            assertEquals("Selected background does not move or resize the tab",statsBounds,
                compose.onNodeWithTag("ledgerTab:stats").fetchSemanticsNode().boundsInRoot)
            // During the page fade only the destination exposes interactive content.
            compose.onNodeWithTag("ledgerOverview").assertDoesNotExist()
            compose.onNodeWithTag("ledgerStatsDirection").assertHasClickAction()
            compose.mainClock.advanceTimeBy(320)
            assertSelectedTab("stats")

            listOf("overview","transactions","stats").forEach {id->
                compose.onNodeWithTag("ledgerTab:$id").performClick()
                compose.mainClock.advanceTimeByFrame()
            }
            compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
            assertEquals("stats",ledger.ui.value.page)
            assertEquals(filter,ledger.ui.value.transactionFilter)
            assertSelectedTab("stats")
            compose.onNodeWithTag("ledgerStatistics").assertExists()
            compose.onNodeWithTag("ledgerOverview").assertDoesNotExist()
            compose.onNodeWithTag("ledgerTransactions").assertDoesNotExist()
        } finally {compose.mainClock.autoAdvance=true}
        // Popup windows also need Android layout/draw passes, beyond the animation frames above.
        compose.waitForIdle()
        compose.onNodeWithTag("ledgerStatsDirection").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("ledgerStatsDirection:income").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("ledgerStatsDirection:income").performClick()
        compose.onNodeWithTag("ledgerStatsDirection").assertTextContains("收入",substring=true)
        compose.onNodeWithTag("ledgerTab:transactions").performClick()
        compose.onNodeWithTag("ledgerSearch").assertTextContains("保留条件")
        assertEquals(filter,ledger.ui.value.transactionFilter)

        appearance(reduced=true)
        compose.mainClock.autoAdvance=false
        try {
            val overviewBounds=compose.onNodeWithTag("ledgerTab:overview").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("ledgerTab:overview").performClick()
            compose.mainClock.advanceTimeByFrame();compose.mainClock.advanceTimeByFrame()
            assertSelectedTab("overview")
            assertEquals(overviewBounds,compose.onNodeWithTag("ledgerTab:overview").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("ledgerOverview").assertExists()
            compose.onNodeWithTag("ledgerTransactions").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(96)
            assertSelectedTab("overview")
            assertEquals(overviewBounds,compose.onNodeWithTag("ledgerTab:overview").fetchSemanticsNode().boundsInRoot)
        } finally {compose.mainClock.autoAdvance=true}
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun narrowLargeFontKeepsCompactActionsOutsideContent() {
        compose.runOnIdle {
            val config=Configuration(compose.activity.resources.configuration);config.fontScale=2f
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        ready()
        root("记录");assertCompactAction("addRecord","写记录","recordsContent");shot("06-records-font2")
        root("计划");assertCompactAction("addPlan","新任务","plansContent");shot("07-plans-font2")
        root("账本");assertCompactAction("addLedgerTransaction","记一笔","ledgerContent");shot("08-ledger-font2")
        toolPage("wardrobe");assertCompactAction("addTool","添加衣物","toolsContent");shot("09-wardrobe-font2")
    }

    @Test fun compactActionsOpenOriginalFlowsAndRespectHiddenBranches() {
        root("记录")
        compose.onNodeWithTag("addRecord").performClick()
        val type=records.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}
        compose.onNodeWithTag("newRecordType:"+type.id).performScrollTo().performClick();ready()
        assertNotNull(records.ui.value.editor)
        compose.onNodeWithTag("recordTitle").performTextInput("新增入口核对")
        compose.onNodeWithTag("recordBody").performTextInput("只用于验证原有保存与选择流程。")
        compose.onNodeWithTag("saveRecord").performClick();ready()
        compose.waitUntil(15_000) {compose.waitForIdle();records.ui.value.editor==null&&records.ui.value.detailId!=null}
        val id=records.ui.value.detailId!!
        compose.runOnIdle {records.detail(null)};ready()
        compose.onNodeWithText("新增入口核对",substring=false).performTouchInput {longClick()}
        assertTrue(id in records.ui.value.selected)
        compose.onNodeWithTag("addRecord").assertDoesNotExist()
        compose.runOnIdle {records.clearSelection();records.filter(RecordFilter(state="trash"))};ready()
        compose.onNodeWithTag("addRecord").assertDoesNotExist()
        compose.runOnIdle {records.filter(RecordFilter())};ready()
        compose.onNodeWithTag("addRecord").assertIsDisplayed()

        root("计划")
        compose.onNodeWithTag("planTab:日历").performClick();ready()
        val date=LocalDate.now().withDayOfMonth(12)
        compose.onNodeWithTag("calendarDay:$date").performScrollTo().performClick();ready()
        compose.onNodeWithTag("calendarDay:$date").assertIsSelected()
        compose.onNodeWithTag("addPlan").assertContentDescriptionEquals("新日程").performClick();ready()
        assertEquals("event",plans.ui.value.editor?.kind)
        assertEquals(date.toString(),plans.ui.value.editor?.text("start"))
        compose.runOnIdle {plans.discardForm()};ready()

        root("账本")
        compose.onNodeWithTag("addLedgerTransaction").performClick();ready()
        assertEquals("expense",ledger.ui.value.editor?.kind)
        compose.onNodeWithTag("ledgerAmount").assertIsDisplayed()
        compose.runOnIdle {ledger.discardEditor()};ready()

        toolPage("checklist")
        compose.onNodeWithTag("addTool").performClick();ready()
        assertEquals("checklist",tools.ui.value.editor?.kind)
        compose.onNodeWithTag("toolTitle").assertIsDisplayed()
        compose.runOnIdle {tools.discardForm()};ready()
        toolPage("wardrobe")
        compose.onNodeWithTag("addTool").performClick();ready()
        assertEquals("item",tools.ui.value.editor?.kind)
        assertEquals("true",tools.ui.value.editor?.text("wardrobe"))
        compose.runOnIdle {tools.discardForm()};ready()
        toolPage("calculator")
        compose.onNodeWithTag("addTool").assertDoesNotExist()
    }
}
