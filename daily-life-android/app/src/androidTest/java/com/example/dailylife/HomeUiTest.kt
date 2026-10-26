package com.example.dailylife

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.PlanForm
import com.example.dailylife.feature.tools.ToolForm
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File
import java.time.LocalDate

class HomeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun homeUsesOriginalTaskAndPersistsCardPreferencesWithoutChangingData() {
        val app=compose.activity.application as DailyLifeApplication
        val prefs=runBlocking {app.preferences.dashboard.first()}
        val appearance=runBlocking {app.preferences.appearance.first()}
        val task=PlanForm("task").set("title","今天，从一件小事开始").set("planned",LocalDate.now().toString()).set("due",LocalDate.now().toString())
        val anniversary=ToolForm("anniversary").set("title","把普通的一天记在心上").set("date",LocalDate.now().toString()).set("showHome","true")
        try {
            runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true));app.preferences.setDashboard(com.example.dailylife.data.repository.DashboardPreferences());app.plans.initialize();app.plans.prepareDraft(task);app.plans.save(task);app.tools.prepare(anniversary);app.tools.save(anniversary)}
            compose.waitUntil(20000) {compose.onAllNodesWithTag("homeContent").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText("今天",substring=false).assertCountEquals(1)
            compose.onNodeWithTag("completeTask:"+task.id).performScrollTo().performClick()
            compose.waitUntil(10000) {runBlocking {app.plans.dao.tasks().first {it.id==task.id}.status=="done"}}
            ready()
            compose.onNodeWithTag("homeContent").performScrollToNode(hasText("把普通的一天记在心上"))
            compose.onNodeWithText("把普通的一天记在心上").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("backToolDetail").fetchSemanticsNodes().isNotEmpty()};ready()
            compose.onNodeWithTag("backToolDetail").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("homeContent").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("root:今天").assertIsSelected()
            compose.onNodeWithTag("homeContent").performScrollToIndex(0)
            screenshot("home-light.png")
            compose.onNodeWithTag("editHome").performClick()
            compose.onNodeWithTag("homeCard:budget").performScrollTo().performClick()
            compose.onNodeWithText("保存首页设置").performScrollTo().performClick()
            compose.waitUntil(10000) {runBlocking {"budget" in app.preferences.dashboard.first().hiddenCards}}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("homeContent").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText("本月账本",substring=false).assertCountEquals(0)
            Assert.assertEquals(1,runBlocking {app.plans.dao.tasks().count {it.id==task.id}})
            compose.onNodeWithTag("globalSearch").performClick()
            compose.onNodeWithText("标题、正文、商户或位置").performTextInput("今天，从一件")
            compose.onNodeWithText("今天，从一件小事开始").assertExists()
            compose.onNodeWithText("关闭搜索").performScrollTo().performClick()
            runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
            screenshot("home-dark.png")
        } finally {runBlocking {
            app.plans.discard(task.id)
            app.tools.discard(anniversary.id)
            if(app.tools.load().entries.any {it.id==anniversary.id}) {app.tools.manage(anniversary.id,"trash");app.tools.deleteForever(anniversary.id)}
            if(app.plans.dao.tasks().any {it.id==task.id}) {app.plans.manage("task",task.id,"trash");app.plans.permanentlyDelete("task",task.id)}
            app.preferences.setDashboard(prefs);app.preferences.set(appearance)
        }}
    }
    private fun ready() {
        compose.waitUntil(10000) {compose.onAllNodesWithTag("appBusy").fetchSemanticsNodes().isEmpty()}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("dismissMessage").performClick()
    }
    private fun screenshot(name:String) {
        ready();compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {File(it).apply {mkdirs()}} ?: compose.activity.getExternalFilesDir(null)!!
        instrumentation.uiAutomation.takeScreenshot().let {b->File(folder,name).outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    }
}
