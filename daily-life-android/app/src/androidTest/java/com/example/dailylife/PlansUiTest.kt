package com.example.dailylife
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.plans.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File
import java.time.LocalDate
class PlansUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun fivePlanPagesCreatePersistAndNavigateWithRealData() {
        val app=compose.activity.application as DailyLifeApplication
        val repo=app.plans
        val initial=runBlocking {repo.load()}
        val appearance=runBlocking {app.preferences.appearance.first()}
        try {
            runBlocking {app.preferences.set(Appearance(theme="light",reducedMotion=true))}
            compose.waitUntil(15000) {compose.onAllNodesWithTag("root:计划").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("root:计划").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("addPlan").fetchSemanticsNodes().isNotEmpty()}
            add("task","把想做的事，慢慢完成")
            compose.onNodeWithTag("backPlanDetail").performClick()
            ready()
            val task=runBlocking {repo.dao.tasks().first {it.title=="把想做的事，慢慢完成"}}
            compose.onNodeWithTag("completeTask:"+task.id).performScrollTo().performClick()
            compose.waitUntil(8000) {runBlocking {repo.dao.tasks().first {it.id==task.id}.status=="done"}}
            ready()
            compose.onNodeWithTag("completeTask:"+task.id).performClick()
            compose.waitUntil(8000) {runBlocking {repo.dao.tasks().first {it.id==task.id}.status=="todo"}}
            screenshot("plans-tasks-light.png")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) {compose.onAllNodesWithTag("addPlan").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("把想做的事，慢慢完成").assertExists()
            runBlocking {
                val today=LocalDate.now().toString()
                val e=PlanForm("event").set("title","午后，留一点自己的时间").set("allDay","true").set("start",today).set("end",today).set("note","## 慢一点\n\n读书、散步，或者只是发一会儿呆。")
                repo.prepareDraft(e);repo.save(e)
            }
            tab("日历");compose.onNodeWithTag("planCalendar").assertExists()
            screenshot("plans-calendar-light.png")
            tab("习惯");add("habit","每天，读几页喜欢的书")
            compose.onNodeWithTag("backPlanDetail").performClick()
            compose.onNodeWithText("登记打卡").performScrollTo().performClick()
            compose.onNodeWithText("保存本次记录").performScrollTo().performClick()
            compose.waitUntil(10000) {runBlocking {repo.dao.habitLogs().any {it.habitId !in initial.habits.map {h->h.id}}}}
            screenshot("plans-habits-light.png")
            tab("目标");add("goal","给生活一点小小的期待")
            compose.onNodeWithText("记录进度").performScrollTo().performClick()
            compose.onNodeWithText("保存本次记录").performScrollTo().performClick()
            compose.waitUntil(10000) {runBlocking {repo.dao.progress().isNotEmpty()}}
            compose.onNodeWithTag("backPlanDetail").performClick()
            screenshot("plans-goals-light.png")
            tab("专注");add("focus","留一段完整的时间")
            compose.onNodeWithText("暂停",substring=false).performScrollTo().performClick()
            compose.waitUntil(10000) {runBlocking {repo.dao.sessions().any {it.title=="留一段完整的时间" && it.state=="paused"}}}
            compose.onNodeWithText("继续",substring=false).performScrollTo().performClick()
            compose.waitUntil(5000) {runBlocking {repo.dao.sessions().firstOrNull {it.title=="留一段完整的时间"}?.let {repo.openSeconds(it)?.let {n->n>=2}==true}==true}}
            compose.onNodeWithText("提前结束",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("确认",substring=false).performClick()
            compose.waitUntil(10000) {runBlocking {repo.dao.sessions().any {it.title=="留一段完整的时间" && it.state=="early"}}}
            compose.onNodeWithTag("backPlanDetail").performClick()
            screenshot("plans-focus-light.png")
            runBlocking {app.preferences.set(Appearance(theme="dark",reducedMotion=true))}
            tab("任务");screenshot("plans-tasks-dark.png")
        } catch(e:Throwable) {runCatching {screenshot("plans-ui-failure.png",false)};throw e}
        finally {runBlocking {
            val s=repo.load()
            s.drafts.filter {d->initial.drafts.none {it.id==d.id}}.forEach {repo.discard(it.id)}
            s.goals.filter {g->initial.goals.none {it.id==g.id}}.forEach {repo.manage("goal",it.id,"trash");repo.permanentlyDelete("goal",it.id)}
            s.sessions.filter {f->initial.sessions.none {it.id==f.id}}.forEach {
                if(it.endedAt==null) repo.focusAction(it.id,"abandon")
                repo.manage("focus",it.id,"trash");repo.permanentlyDelete("focus",it.id)
            }
            s.events.filter {e->initial.events.none {it.id==e.id}}.forEach {repo.manage("event",it.id,"trash");repo.permanentlyDelete("event",it.id)}
            s.habits.filter {h->initial.habits.none {it.id==h.id}}.forEach {repo.manage("habit",it.id,"trash");repo.permanentlyDelete("habit",it.id)}
            s.tasks.filter {t->initial.tasks.none {it.id==t.id}}.forEach {repo.manage("task",it.id,"trash");repo.permanentlyDelete("task",it.id)}
            app.planAlarms.reconcile();app.preferences.set(appearance)
        }}
    }
    private fun add(kind:String,title:String) {
        compose.onNodeWithTag("addPlan").performClick()
        compose.onNodeWithTag("newPlan:"+kind).performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("planTitle").fetchSemanticsNodes().isNotEmpty()}
        ready()
        compose.onNodeWithTag("planTitle").performTextReplacement(title)
        compose.onNodeWithTag("savePlan").performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithTag("backPlanDetail").fetchSemanticsNodes().isNotEmpty()}
        ready()
    }
    private fun ready() {
        compose.waitUntil(10000) {compose.onAllNodesWithTag("appBusy").fetchSemanticsNodes().isEmpty()}
        compose.waitForIdle()
        if(compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dismissMessage").performClick();compose.waitForIdle()
        }
    }
    private fun tab(name:String) {
        compose.onNodeWithTag("planTab:"+name).performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun screenshot(name:String,requireFocus:Boolean=true) {
        compose.waitForIdle()
        if(requireFocus && compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dismissMessage").performClick();compose.waitForIdle()
        }
        val focused=runCatching {compose.waitUntil(8000) {compose.activity.hasWindowFocus()}}.isSuccess
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let {File(it).apply {mkdirs()}}
            ?: compose.activity.getExternalFilesDir(null)!!
        instrumentation.uiAutomation.takeScreenshot().let {b->File(dir,name).outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        if(requireFocus) Assert.assertTrue("System dialog covers the application",focused)
    }
}
