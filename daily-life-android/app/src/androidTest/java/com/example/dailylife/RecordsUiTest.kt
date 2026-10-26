package com.example.dailylife

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.feature.records.EditorData
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File

class RecordsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun createEditRestartAndReadChineseMarkdownFromRealStorage() {
        val app = compose.activity.application as DailyLifeApplication
        val repo = app.records
        val created = mutableSetOf<String>()
        val testDrafts = mutableSetOf<String>()
        val initialDrafts = runBlocking { repo.dao.drafts().map { it.id }.toSet() }
        val initialAppearance = runBlocking { app.preferences.appearance.first() }
        val title = "把日常写成喜欢的样子"
        try {
            runBlocking { app.preferences.set(Appearance(theme = "light")) }
            compose.waitUntil(15000) {compose.onAllNodesWithTag("root:记录").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("root:记录").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("addRecord").fetchSemanticsNodes().isNotEmpty() }
            val noteType = runBlocking { repo.dao.types().first { it.kind == "note" && it.subtype == "plain" }.id }
            compose.onNodeWithTag("addRecord").performClick()
            compose.onNodeWithTag("newRecordType:" + noteType).performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("recordTitle").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { testDrafts += repo.dao.drafts().filter { it.id !in initialDrafts }.map { it.id } }
            compose.onNodeWithTag("recordTitle").performTextInput(title)
            compose.onNodeWithTag("recordBody").performTextInput("## 给生活留一点空白\n\n今天沿着河边走了一会儿，风很轻。\n\n- 买一束鲜花\n- 整理书架\n\n**把想法留下来，也把心情安放好。**")
            compose.onNodeWithTag("saveRecord").performClick()
            compose.waitUntil(10000) { runBlocking { repo.dao.allRecords().any { it.record.title == title } } }
            runBlocking { created += repo.dao.allRecords().filter { it.record.title == title }.map { it.record.id } }
            compose.onNodeWithText("给生活留一点空白").assertExists()
            compose.waitForIdle()
            screenshot("records-detail-light.png")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10000) { compose.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("编辑记录").performClick()
            compose.onNodeWithTag("recordBody").performTextReplacement("## 给生活留一点空白\n\n重启后继续编辑，中文 Markdown 已保存。")
            compose.onNodeWithTag("saveRecord").performClick()
            compose.waitUntil(10000) { runBlocking { repo.dao.record(created.first())?.body?.contains("重启后继续编辑") == true } }
            compose.onNodeWithContentDescription("返回记录").performClick()
            runBlocking {
                val diary = repo.newDraft(repo.dao.types().first { it.kind == "diary" }.id)
                val existingDate = repo.dao.allRecords().mapNotNull { it.diary?.diaryDate }.toSet()
                var date = java.time.LocalDate.now()
                while (date.toString() in existingDate) date = date.minusDays(1)
                created += repo.commit(diary.id, EditorData.fromJson(diary.payload).copy(
                    title = "周末，慢一点也很好", body = "一杯热茶，一本读到一半的书。\n今天的心情：晴朗。", diaryDate = date.toString(), mood = "晴朗"))
                val bookmark = repo.newDraft(repo.dao.types().first { it.kind == "bookmark" }.id)
                created += repo.commit(bookmark.id, EditorData.fromJson(bookmark.payload).copy(
                    title = "值得再读的一页", url = "https://developer.android.com/?sample=records-ui", body = "收藏关于创造与日常的小灵感。", allowDuplicateUrl = true))
            }
            compose.onNodeWithTag("recordsList").performScrollToIndex(0)
            compose.waitUntil(10000) { compose.onAllNodesWithText("值得再读的一页").fetchSemanticsNodes().isNotEmpty() }
            screenshot("records-list-light.png")
            // Exercise customized date selection and return-with-draft path.
            compose.onNodeWithTag("addRecord").performClick()
            val diaryType = runBlocking { repo.dao.types().first { it.kind == "diary" }.id }
            compose.onNodeWithTag("newRecordType:" + diaryType).performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("recordTitle").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { testDrafts += repo.dao.drafts().filter { it.id !in initialDrafts }.map { it.id } }
            compose.onNodeWithText("日期", substring = true).performClick()
            compose.onNodeWithText("YYYY-MM-DD").assertExists()
            compose.onNodeWithText("取消").performScrollTo().performClick()
            compose.onNodeWithContentDescription("返回并处理草稿").performClick()
            compose.onNodeWithText("保留草稿并返回").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("addRecord").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { app.preferences.set(Appearance(theme = "dark", reducedMotion = true)) }
            compose.waitForIdle()
            screenshot("records-list-dark.png")
        } catch (failure: Throwable) {
            runCatching { screenshot("records-ui-failure.png", requireFocus = false) }
            throw failure
        } finally {
            runBlocking {
                // Delete only this test's records/drafts, never clear the user's database.
                repo.dao.drafts().filter { d -> d.targetId in created || d.id in testDrafts }
                    .forEach { repo.discardDraft(it.id) }
                if (created.isNotEmpty()) { repo.mutate(created, "trash"); repo.permanentlyDelete(created) }
                app.preferences.set(initialAppearance)
            }
        }
    }
    private fun screenshot(name: String, requireFocus: Boolean = true) {
        compose.waitForIdle()
        if (requireFocus && compose.onAllNodesWithTag("dismissMessage").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("dismissMessage").performClick()
            compose.waitForIdle()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val focused = runCatching { compose.waitUntil(8000) { compose.activity.hasWindowFocus() } }.isSuccess
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val file = File(outputDir?.let { File(it).apply { mkdirs() } } ?: compose.activity.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        if (requireFocus) Assert.assertTrue("System dialog is covering the test app", focused)
    }
}
