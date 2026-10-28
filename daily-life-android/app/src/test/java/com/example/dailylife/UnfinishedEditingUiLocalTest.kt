package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.data.repository.RecordsSnapshot
import com.example.dailylife.data.local.Draft
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.plans.PlansSnapshot
import com.example.dailylife.feature.records.EditorData
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import com.example.dailylife.feature.tools.ToolsSnapshot
import com.example.dailylife.ui.unfinishedEdits
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Full editor -> saved unfinished content -> personal center -> same editor journeys. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnfinishedEditingUiLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private val plans get()=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
    private val ledger get()=ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
    private val tools get()=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {compose.waitForIdle();records.ui.value.let {it.ready&&!it.busy} &&
            plans.ui.value.let {it.ready&&!it.busy} && ledger.ui.value.let {it.ready&&!it.busy} && tools.ui.value.let {it.ready&&!it.busy}}
        compose.waitForIdle()
    }
    private fun act(block:()->Unit) {compose.runOnIdle(block);ready()}
    private fun until(predicate:()->Boolean) {compose.waitUntil(15_000) {compose.waitForIdle();predicate()};ready()}
    private fun personal() {compose.onNodeWithTag("root:功能箱").performClick();ready()}
    private fun list() {
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("recoverEditing"))
        compose.onNodeWithTag("recoverEditing").performClick();compose.waitForIdle()
    }
    private fun listAction(tag:String) {
        compose.onNodeWithTag("unfinishedEditingList").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick();ready()
    }
    @Before fun setup() {
        ready();runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))}
        until {records.ui.value.appearance.reducedMotion}
    }
    @After fun close() {compose.activity.finish();app.backgroundScope.cancel();app.database.close()}

    private fun seedFourThroughEditors():Map<String,String> {
        val ids=linkedMapOf<String,String>()
        val type=records.ui.value.snapshot.types.first {it.kind=="note"&&it.subtype=="plain"}
        act {records.begin(type.id)}
        act {records.edit(records.ui.value.editor!!.data.copy(title="旅行准备",body="先整理车票和证件"))}
        ids["record"]=records.ui.value.editor!!.draftId
        compose.onNodeWithTag("backRecordEditor").performClick()
        until {records.ui.value.editor==null&&records.ui.value.snapshot.drafts.size==1}
        act {plans.begin("task",date="2026-10-30")}
        act {plans.edit(plans.ui.value.editor!!.set("title","周末整理书架"))}
        ids["plan"]=plans.ui.value.editor!!.id
        compose.onNodeWithTag("leavePlanEditor").performClick()
        until {plans.ui.value.editor==null&&plans.ui.value.data.drafts.size==1}
        act {ledger.begin("expense")}
        act {ledger.edit(ledger.ui.value.editor!!.set("subject","午餐待核对").set("amount","25.60").set("date","2026-10-28"))}
        ids["ledger"]=ledger.ui.value.editor!!.id
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        until {ledger.ui.value.editor==null&&ledger.ui.value.data.drafts.size==1}
        act {tools.begin("subscription")}
        act {tools.edit(tools.ui.value.editor!!.set("title","阅读会员待续费").set("amount","18.00").set("date","2026-11-01"))}
        ids["tool"]=tools.ui.value.editor!!.id
        compose.onNodeWithTag("backToolEditor").performClick()
        until {tools.ui.value.editor==null&&tools.ui.value.data.drafts.size==1}
        compose.onNodeWithTag("discardEditing").assertDoesNotExist()
        assertTrue(records.ui.value.snapshot.records.isEmpty())
        assertTrue(plans.ui.value.data.tasks.isEmpty())
        assertTrue(ledger.ui.value.data.transactions.isEmpty())
        assertTrue(tools.ui.value.data.entries.isEmpty())
        return ids
    }

    @Test fun allModulesAppearWithConcreteSummariesAndResumeTheirOriginalEditor() {
        val ids=seedFourThroughEditors()
        val summaries=unfinishedEdits(records.ui.value.snapshot,plans.ui.value.data,ledger.ui.value.data,tools.ui.value.data)
        assertEquals(4,summaries.size)
        assertTrue(summaries.single {it.domain=="ledger"}.details.contains("25.60"))
        assertTrue(summaries.single {it.domain=="plan"}.details.contains("2026-10-30"))
        assertTrue(summaries.all {it.savedAt>0&&it.readable})
        personal()
        ids.forEach {(domain,id)->
            list();listAction("continueEditing:$domain:$id")
            when(domain) {
                "record"->{compose.onNodeWithTag("recordBody").assertTextContains("先整理车票和证件");compose.onNodeWithTag("backRecordEditor").performClick();until {records.ui.value.editor==null}}
                "plan"->{compose.onNodeWithTag("planTitle").assertTextContains("周末整理书架");compose.onNodeWithTag("backPlanEditor").performClick();until {plans.ui.value.editor==null}}
                "ledger"->{compose.onNodeWithTag("ledgerAmount").assertTextContains("25.60");compose.onNodeWithTag("backLedgerEditor").performClick();until {ledger.ui.value.editor==null}}
                else->{compose.onNodeWithTag("toolTitle").assertTextContains("阅读会员待续费");compose.onNodeWithTag("backToolEditor").performClick();until {tools.ui.value.editor==null}}
            }
        }
        assertEquals(4,unfinishedEdits(records.ui.value.snapshot,plans.ui.value.data,ledger.ui.value.data,tools.ui.value.data).size)
    }

    @Test fun previewContinuesSameDraftRepeatedExitUpdatesItAndFormalSaveRemovesIt() {
        act {ledger.begin("expense")}
        val id=ledger.ui.value.editor!!.id
        act {ledger.edit(ledger.ui.value.editor!!.set("subject","公交车费").set("amount","2.00").set("date","2026-10-28"))}
        compose.onNodeWithTag("cancelLedgerEditor").performClick()
        until {ledger.ui.value.editor==null&&ledger.ui.value.data.drafts.size==1}
        personal()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("unfinishedPreview:ledger:$id"))
        compose.onNodeWithTag("unfinishedPreview:ledger:$id").performClick();ready()
        until {ledger.ui.value.editor?.id==id}
        compose.onNodeWithTag("ledgerAmount").performTextReplacement("4.00")
        compose.onNodeWithTag("backLedgerEditor").performClick()
        until {ledger.ui.value.editor==null&&ledger.ui.value.data.drafts.singleOrNull()?.let {LedgerForm.parse(it.payload).text("amount")=="4.00"}==true}
        assertTrue(ledger.ui.value.data.transactions.isEmpty())
        list();listAction("continueEditing:ledger:$id")
        compose.onNodeWithTag("ledgerAmount").assertTextContains("4.00")
        compose.onNodeWithTag("saveLedgerTransaction").performClick()
        until {ledger.ui.value.editor==null&&ledger.ui.value.data.drafts.isEmpty()&&ledger.ui.value.data.transactions.any {it.id==id}}
        assertEquals(400L,ledger.ui.value.data.transactions.single {it.id==id}.amountMinor)
        act {ledger.detail(null)}
        compose.onNodeWithTag("recoverEditing").assertDoesNotExist()
    }

    @Test fun centralDiscardRequiresExplicitConfirmationAndDoesNotChangeOriginalTransaction() {
        val id=runBlocking {
            val snapshot=app.ledger.load()
            val f=LedgerForm().set("ledger",snapshot.defaultLedger()!!.id).set("amount","38.50").set("date","2026-10-28").set("subject","原始消费")
            app.ledger.save(f);f.id
        }
        until {ledger.ui.value.data.transactions.any {it.id==id}}
        val original=ledger.ui.value.data.transactions.single {it.id==id}
        act {ledger.begin(id=id)}
        act {ledger.edit(ledger.ui.value.editor!!.set("amount","58.50").set("subject","未保存更正"))}
        compose.onNodeWithTag("backLedgerEditor").performClick()
        until {ledger.ui.value.editor==null&&ledger.ui.value.data.drafts.size==1}
        personal();list();listAction("discardUnfinished:ledger:$id")
        compose.onNodeWithText("继续保留").performClick()
        assertEquals(1,ledger.ui.value.data.drafts.size)
        listAction("discardUnfinished:ledger:$id")
        compose.onNodeWithTag("discardEditing").performClick()
        until {ledger.ui.value.data.drafts.isEmpty()}
        compose.onNodeWithTag("discardEditing").assertDoesNotExist()
        compose.onNodeWithText("没有未完成内容").assertIsDisplayed()
        assertEquals(original,ledger.ui.value.data.transactions.single {it.id==id})
    }

    @Test @Config(qualifiers="w320dp-h740dp-xxhdpi")
    fun twoTimesFontKeepsCentralActionsReachableAndMasksPrivateAmounts() {
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration)
            config.fontScale=2f
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        ready()
        assertEquals(320,compose.activity.resources.configuration.screenWidthDp)
        assertEquals(2f,compose.activity.resources.configuration.fontScale,0f)
        val ids=seedFourThroughEditors()
        val hidden=unfinishedEdits(records.ui.value.snapshot,plans.ui.value.data,ledger.ui.value.data,tools.ui.value.data,hideAmounts=true)
        assertFalse(hidden.any {it.details.contains("25.60")||it.details.contains("18.00")})
        assertTrue(hidden.single {it.domain=="ledger"}.details.contains("••••"))
        runBlocking {app.preferences.setDashboard(app.preferences.dashboard.first().copy(hideAmounts=true))}
        until {ViewModelProvider(compose.activity)[HomeViewModel::class.java].state.value.hideAmounts}
        personal();shot("01-personal-font2")
        compose.onAllNodesWithText("25.60",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("18.00",substring=true).assertCountEquals(0)
        list()
        ids.forEach {(domain,id)->
            val tag="continueEditing:$domain:$id"
            compose.onNodeWithTag("unfinishedEditingList").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        shot("02-unfinished-font2")
    }

    private fun shot(name:String) {
        compose.waitForIdle();compose.runOnIdle {
            val root=generateSequence(File(System.getProperty("user.dir")).absoluteFile) {it.parentFile}
                .first {File(it,"AGENTS.md").isFile&&File(it,"daily-life-android").isDirectory}
            val dir=File(root,"diagnostics/unfinished-editing-evidence/screens").apply {mkdirs()}
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap));File(dir,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}

/** Payload summaries require Android's JSON implementation, but no activity or database. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class UnfinishedEditingSummaryTest {
    @Test fun damagedLatestRecordDraftStillDeduplicatesUsingItsKnownTargetWithoutDeletingEitherPayload() {
        val valid=Draft("old-draft","owner","original-record",payload=EditorData(recordId="original-record",typeId="note",title="较早的编辑").json(),savedAt=100L)
        val damaged=Draft("new-draft","owner","original-record",payload="{broken",savedAt=200L)
        val source=RecordsSnapshot(drafts=listOf(valid,damaged))
        val rows=unfinishedEdits(source,PlansSnapshot(),LedgerSnapshot(),ToolsSnapshot())
        assertEquals(1,rows.size)
        assertEquals("new-draft",rows.single().id)
        assertEquals("original-record",rows.single().entityId)
        assertFalse(rows.single().readable)
        assertEquals(listOf(valid,damaged),source.drafts)
        val updated=source.copy(drafts=listOf(valid.copy(savedAt=300L),damaged))
        val latest=unfinishedEdits(updated,PlansSnapshot(),LedgerSnapshot(),ToolsSnapshot()).single()
        assertEquals("old-draft",latest.id)
        assertEquals("较早的编辑",latest.title)
        assertTrue(latest.readable)
    }
}
