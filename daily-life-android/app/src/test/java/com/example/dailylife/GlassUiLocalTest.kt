package com.example.dailylife

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.feature.records.RecordsViewModel
import com.example.dailylife.feature.plans.PlansViewModel
import com.example.dailylife.feature.ledger.LedgerViewModel
import com.example.dailylife.feature.tools.ToolsViewModel
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual Compose screens rendered on Android's native graphics stack, without an emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(application=DailyLifeApplication::class,qualifiers="w412dp-h892dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassUiLocalTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<DailyLifeApplication>()
    private val records get()=ViewModelProvider(compose.activity)[RecordsViewModel::class.java]
    private fun ready() {
        compose.waitUntil(30_000) {records.ui.value.ready && !records.ui.value.busy &&
            ViewModelProvider(compose.activity)[PlansViewModel::class.java].ui.value.ready &&
            ViewModelProvider(compose.activity)[LedgerViewModel::class.java].ui.value.ready &&
            ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.let {it.ready && !it.busy}}
        compose.waitForIdle()
    }
    private fun personal(tag:String) {
        compose.onNodeWithTag("root:功能箱").performClick()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }
    private fun shot(name:String) {
        compose.waitForIdle()
        val file=File(System.getProperty("myLife.uiOutput","build/reports/ui-review"),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf {it.isShowing}?.window?.decorView
                ?: compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            assertTrue(bitmap.width>300 && bitmap.height>600)
        }
    }
    @After fun close() {compose.activity.finish();app.database.close();app.backgroundScope.cancel()}

    @Test fun eachEditorKeepsUnfinishedInputOnExitWithoutCreatingFormalData() {
        ready()
        val plans=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
        val ledger=ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        val tools=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]
        compose.runOnIdle {plans.begin("task")}
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.editor!=null && !plans.ui.value.busy}
        compose.onNodeWithTag("savePlan").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.error!=null && !plans.ui.value.busy}
        assertNotNull(plans.ui.value.editor)
        compose.waitUntil(10_000) {compose.onAllNodes(hasTestTag("planTitle") and isEnabled()).fetchSemanticsNodes().size==1}
        compose.onNodeWithTag("planTitle").assertIsEnabled().performTextInput("保留失败后的输入")
        compose.onNodeWithText("稍后继续").assertIsDisplayed()
        compose.onNodeWithTag("backPlanEditor").performClick()
        compose.onNodeWithTag("discardEditing").assertDoesNotExist()
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.editor==null && !plans.ui.value.busy && plans.ui.value.data.drafts.size==1}
        assertTrue(plans.ui.value.data.drafts.single().payload.contains("保留失败后的输入"))
        assertTrue(plans.ui.value.data.tasks.isEmpty())
        compose.runOnIdle {ledger.begin("expense")}
        compose.waitUntil(10_000) {compose.waitForIdle();ledger.ui.value.editor!=null && !ledger.ui.value.busy}
        compose.onNodeWithTag("ledgerAmount").performTextInput("20")
        compose.onNodeWithText("稍后继续").assertIsDisplayed()
        compose.onNodeWithTag("backLedgerEditor").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();ledger.ui.value.editor==null && !ledger.ui.value.busy && ledger.ui.value.data.drafts.size==1}
        compose.onNodeWithTag("discardEditing").assertDoesNotExist()
        assertTrue(ledger.ui.value.data.transactions.isEmpty())
        compose.runOnIdle {tools.begin("checklist")}
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor!=null && !tools.ui.value.busy}
        compose.onNodeWithTag("toolTitle").performTextInput("未完成的生活清单")
        compose.onNodeWithText("稍后继续").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor==null && !tools.ui.value.busy && tools.ui.value.data.drafts.size==1}
        assertTrue(tools.ui.value.data.entries.isEmpty())
        val type=records.ui.value.snapshot.types.first {it.kind=="note" && it.subtype=="plain"}
        compose.runOnIdle {records.begin(type.id)}
        compose.waitUntil(10_000) {compose.waitForIdle();records.ui.value.editor!=null && !records.ui.value.busy}
        compose.onNodeWithTag("recordBody").performTextInput("记录仍然可以稍后继续")
        compose.onNodeWithText("稍后继续").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();records.ui.value.editor==null && records.ui.value.snapshot.drafts.size==1}
    }

    @Test fun personalCenterRecoversExistingInputAndHasNoRootSearchOrNotificationButtons() {
        ready()
        compose.onNodeWithTag("globalSearch").assertDoesNotExist()
        compose.onNodeWithTag("reminderCenter").assertDoesNotExist()
        val form=com.example.dailylife.feature.plans.PlanForm("task").set("title","旧版未完成事项")
        kotlinx.coroutines.runBlocking {app.plans.prepareDraft(form)}
        val plans=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.data.drafts.any {it.id==form.id}}
        personal("recoverEditing")
        compose.onNode(hasText("旧版未完成事项") and hasAnyAncestor(hasTestTag("unfinishedEditingList"))).assertIsDisplayed()
        compose.onNodeWithTag("continueEditing:plan:${form.id}").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.editor!=null && !plans.ui.value.busy}
        compose.onNodeWithTag("savePlan").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.editor==null && !plans.ui.value.busy &&
            plans.ui.value.data.tasks.any {it.id==form.id && it.title=="旧版未完成事项"} && plans.ui.value.data.drafts.none {it.id==form.id}}
        assertTrue(plans.ui.value.data.tasks.any {it.id==form.id && it.title=="旧版未完成事项"})
        assertTrue(plans.ui.value.data.drafts.none {it.id==form.id})
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("recoverEditing").assertDoesNotExist()
        compose.onNodeWithTag("myLifeLogo").assertIsDisplayed()
        assertEquals("My Life",compose.activity.getString(R.string.app_name))
    }

    @Test fun navigationHighlightMovesWithoutEnlargingIconsAndRespectsReducedMotion() {
        ready()
        kotlinx.coroutines.runBlocking {app.preferences.set(records.ui.value.appearance.copy(reducedMotion=false))}
        compose.waitUntil(10_000) {compose.waitForIdle();!records.ui.value.appearance.reducedMotion}
        val from=compose.onNodeWithTag("navigationHighlight").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("root:账本").performClick()
        compose.waitForIdle()
        val positions=mutableListOf<Float>()
        repeat(12) {
            compose.mainClock.advanceTimeBy(32)
            compose.waitForIdle()
            positions+=compose.onNodeWithTag("navigationHighlight").fetchSemanticsNode().boundsInRoot.left
        }
        compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
        val end=compose.onNodeWithTag("navigationHighlight").fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance=true
        assertTrue("Highlight positions: $from, $positions, $end",positions.any {it>from && it<end})
        compose.onNodeWithTag("root:账本").assertHeightIsEqualTo(androidx.compose.ui.unit.Dp(50f)).assertWidthIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        kotlinx.coroutines.runBlocking {app.preferences.set(records.ui.value.appearance.copy(reducedMotion=true))}
        compose.waitUntil(10_000) {compose.waitForIdle();records.ui.value.appearance.reducedMotion}
        compose.onNodeWithTag("root:功能箱").performClick().assertIsSelected()
        shot("my-life-个人中心")
    }

    @Test fun reminderCenterAcknowledgesWithoutChangingTheOriginalTask() {
        ready()
        val plans=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
        val now=System.currentTimeMillis()
        val task=com.example.dailylife.data.local.Task(ownerId=kotlinx.coroutines.runBlocking {app.records.owner()},title="给绿植浇水",reminderAt=now-60000)
        val id=kotlinx.coroutines.runBlocking {
            app.plans.dao.put(task)
            app.plans.dao.put(com.example.dailylife.data.local.PlanReminder(sourceType="task",sourceId=task.id,instanceKey="single",title=task.title,triggerAt=task.reminderAt!!,state="blocked"))
        }
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.data.reminders.any {it.id==id}}
        compose.onNodeWithTag("reminderCapsule").assertDoesNotExist()
        personal("reminderCenter")
        compose.onNodeWithText("已到时间").performClick()
        compose.onNodeWithText("通知权限未开").performScrollTo().assertIsDisplayed()
        shot("life-06-提醒中心")
        compose.onNodeWithText("知道了").performScrollTo().performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.data.reminders.first {it.id==id}.readAt!=null}
        compose.onNodeWithTag("reminderCapsule").assertDoesNotExist()
        assertEquals("todo",plans.ui.value.data.tasks.first {it.id==task.id}.status)
    }

    @Test fun activeFocusUsesTheHomeCardWithoutAnIslandOverlay() {
        ready()
        val plans=ViewModelProvider(compose.activity)[PlansViewModel::class.java]
        kotlinx.coroutines.runBlocking {
            app.plans.startFocus(com.example.dailylife.feature.plans.PlanForm("focus").set("mode","countdown").set("seconds","1500").set("title","阅读时间"))
        }
        compose.waitUntil(10_000) {compose.waitForIdle();plans.ui.value.data.sessions.any {it.title=="阅读时间"}}
        compose.onNodeWithTag("focusCapsule").assertDoesNotExist()
        compose.onNodeWithTag("reminderCapsule").assertDoesNotExist()
        shot("life-07-专注首页")
        compose.onNodeWithText("继续专注 · 阅读时间").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test fun wardrobeCreatesRealItemsAndOutfitsKeepSelectedClothes() {
        ready()
        val tools=ViewModelProvider(compose.activity)[ToolsViewModel::class.java]
        compose.onNodeWithTag("root:功能箱").performClick()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:wardrobe"))
        compose.onNodeWithTag("toolFeature:wardrobe").performClick()
        ready()
        compose.onNodeWithTag("addTool").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor!=null && !tools.ui.value.busy}
        compose.onNodeWithTag("toolTitle").performTextInput("雾蓝衬衫")
        compose.onNodeWithTag("saveTool").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();!tools.ui.value.busy && (tools.ui.value.editor==null || tools.ui.value.error!=null)}
        assertNull(tools.ui.value.error,tools.ui.value.error)
        val shirt=tools.ui.value.data.entries.single {it.title=="雾蓝衬衫"}
        assertEquals("true",org.json.JSONObject(shirt.payload).getString("wardrobe"))
        compose.onNodeWithContentDescription("收藏").performClick();ready()
        compose.onNodeWithTag("backToolDetail").performClick()
        shot("life-01-衣橱")
        compose.runOnIdle {tools.page("outfit")};ready()
        compose.onNodeWithTag("addTool").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor!=null && !tools.ui.value.busy}
        compose.onNodeWithTag("toolTitle").performTextInput("周一通勤")
        compose.onNodeWithTag("outfitItems").performScrollTo().performClick()
        compose.onNodeWithText("雾蓝衬衫").performClick()
        compose.onNodeWithText("完成选择").performClick()
        compose.onNodeWithTag("saveTool").performClick()
        compose.waitUntil(10_000) {compose.waitForIdle();tools.ui.value.editor==null && !tools.ui.value.busy}
        assertTrue(tools.ui.value.data.links.any {it.entityId==shirt.id && it.section=="outfit"})
        compose.onNodeWithText("今天穿过").performScrollTo().performClick();ready()
        assertEquals(1,tools.ui.value.data.logs.count {it.kind=="wear"})
        shot("life-02-穿搭")
    }

    @Test fun journalStylesAndPhotosRenderWithoutReplacingTheWriting() {
        ready()
        val type=records.ui.value.snapshot.types.first {it.kind=="diary"}
        compose.runOnIdle {records.begin(type.id)}
        compose.waitUntil(10_000) {records.ui.value.editor!=null && !records.ui.value.busy}
        compose.onNodeWithTag("recordTitle").performTextInput("把日子过成喜欢的样子")
        compose.onNodeWithTag("recordBody").performTextInput("## 周末的小确幸\n\n整理衣橱，留下一套喜欢的搭配。\n\n- 慢慢散步\n- 记住今天的光")
        compose.onNodeWithText("手账样式").performScrollTo().performClick()
        compose.onNodeWithText("奶油纸").performScrollTo().performClick()
        compose.onNodeWithText("玫瑰").performScrollTo().performClick()
        compose.onNodeWithText("拍立得").performScrollTo().performClick()
        shot("life-03-手账样式")
        compose.onNodeWithText("应用到这篇记录").performClick()
        compose.onNodeWithTag("saveRecord").performClick()
        compose.waitUntil(10_000) {records.ui.value.editor==null && !records.ui.value.busy}
        val record=records.ui.value.snapshot.records.single {it.record.title=="把日子过成喜欢的样子"}
        assertEquals("film",com.example.dailylife.feature.records.JournalStyle.parse(record.record.presentation).layout)
        assertTrue(record.body.contains("整理衣橱"))
        kotlinx.coroutines.runBlocking {
            repeat(4) {i->
                val bitmap=android.graphics.Bitmap.createBitmap(480,480,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(listOf(0xFFE9E0D5,0xFFDDE6E7,0xFFE2DDEA,0xFFE8DED7)[i].toInt())
                val paint=android.graphics.Paint().apply {color=listOf(0xFF91A9BC,0xFFAD9C8C,0xFFB8A1BB,0xFFA8B7A1)[i].toInt()}
                canvas.drawRoundRect(80f,80f,400f,400f,40f,40f,paint)
                val bytes=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
                val a=app.records.files.importStream(bytes.inputStream(),app.records.owner(),"测试图片-${i+1}.png","image/png")
                app.records.dao.putAttachment(a);app.records.dao.putLinks(listOf(com.example.dailylife.data.local.AttachmentLink("record",record.record.id,a.id,sortOrder=i)))
            }
        }
        compose.waitUntil(10_000) {records.ui.value.snapshot.attachments.size>=4}
        compose.runOnIdle {records.detail(record.record.id)}
        compose.waitUntil(10_000) {compose.onAllNodesWithContentDescription("测试图片-1.png").fetchSemanticsNodes().isNotEmpty()}
        shot("life-04-日记手账")
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun reminderSettingsRemainScrollableAtLargeFont() {
        ready()
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration);config.fontScale=2f
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics);compose.activity.recreate()
        }
        ready();personal("reminderCenter")
        compose.onNodeWithText("显示与权限").performClick()
        compose.onNodeWithText("专注计时通知").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("应用内提醒胶囊").assertDoesNotExist()
        compose.onNodeWithText("系统实况显示设置").assertDoesNotExist()
        compose.onNodeWithText("显示与权限").performScrollTo().performClick()
        compose.onNodeWithText("新建提醒事项").performScrollTo().assertIsDisplayed()
        shot("life-05-大字体提醒中心")
        compose.onNodeWithText("关闭",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("root:功能箱").assertIsSelected()
    }

    @Test fun fiveDestinationsAndRecordSave() {
        ready()
        listOf("今天","记录","计划","账本","功能箱").forEachIndexed {i,page->
            compose.onNodeWithTag("root:$page").performClick().assertIsSelected()
            compose.onNodeWithTag("root:$page").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            shot("0${i+1}-"+if(page=="功能箱") "我的" else page)
        }
        compose.onNodeWithTag("root:记录").performClick()
        compose.onNodeWithTag("addRecord").performClick()
        val note=records.ui.value.snapshot.types.first {it.kind=="note" && it.subtype=="plain"}
        compose.onNodeWithTag("newRecordType:${note.id}").performClick()
        compose.waitUntil(10_000) {records.ui.value.editor!=null}
        compose.onNodeWithTag("recordTitle").performTextInput("周末的小计划")
        compose.onNodeWithTag("recordBody").performTextInput("散步、读书，整理这一周的照片。")
        shot("06-记录编辑")
        compose.onNodeWithTag("saveRecord").performClick()
        compose.waitUntil(10_000) {records.ui.value.editor==null && !records.ui.value.busy}
        compose.onNodeWithText("周末的小计划").assertExists().performClick()
        shot("11-记录详情")
    }

    @Test fun darkThemeAndSettingsRemainUsable() {
        ready()
        compose.runOnIdle {records.appearance(records.ui.value.appearance.copy(theme="dark",reducedMotion=true))}
        compose.waitUntil(10_000) {records.ui.value.appearance.theme=="dark"}
        shot("07-深色今天")
        personal("appSettings")
        compose.onNodeWithText("显示模式").assertIsDisplayed()
        shot("08-设置面板")
        compose.onNodeWithText("关闭",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("root:功能箱").performClick().assertIsSelected()
    }

    @Test @Config(qualifiers="w320dp-h700dp-xxhdpi") fun largeFontKeepsNavigationAndPrimaryActionsReachable() {
        ready()
        compose.runOnIdle {
            val config=android.content.res.Configuration(compose.activity.resources.configuration)
            config.fontScale=2f
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics)
            compose.activity.recreate()
        }
        ready()
        compose.onNodeWithTag("root:记录").performClick()
        compose.onNodeWithTag("addRecord").assertIsDisplayed().assertHasClickAction()
        shot("09-大字体记录")
        compose.onNodeWithTag("root:功能箱").performClick()
        compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:checklist"))
        compose.onNodeWithTag("toolFeature:checklist").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {!ViewModelProvider(compose.activity)[ToolsViewModel::class.java].ui.value.busy}
        compose.onNodeWithTag("addTool").assertIsDisplayed().assertIsEnabled()
        shot("10-大字体清单")
    }

    @Test fun planLedgerAndToolPagesShareTheNewComponents() {
        ready()
        compose.onNodeWithTag("root:计划").performClick()
        listOf("任务","日历","习惯","专注","目标").forEachIndexed {i,tab->
            compose.onNodeWithTag("planTab:$tab").performScrollTo().performClick()
            shot("plan-$i-$tab")
        }
        compose.onNodeWithTag("root:账本").performClick()
        listOf("overview","transactions","stats","accounts").forEach {tab->
            compose.onNodeWithTag("ledgerTab:$tab").performClick()
            shot("ledger-$tab")
        }
        compose.onNodeWithTag("root:功能箱").performClick()
        listOf("checklist","anniversary","subscription","bill","item","loan","project","calculator","dates","aa").forEach {tool->
            compose.onNodeWithTag("toolsHome").performScrollToNode(hasTestTag("toolFeature:$tool"))
            compose.onAllNodesWithTag("toolFeature:$tool").onLast().performClick()
            shot("tool-$tool")
            compose.onNodeWithContentDescription("返回").performClick()
        }
    }
}
