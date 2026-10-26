package com.example.dailylife

import android.content.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.newId
import com.example.dailylife.data.repository.Appearance
import com.example.dailylife.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.*

class RecordComponentsTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var directory: File
    private lateinit var store: AttachmentStore
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        directory = File(base.cacheDir, "record-components-" + newId()).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "nobackup").apply { mkdirs() }
        }
        store = AttachmentStore(context)
    }
    @After fun cleanup() { directory.deleteRecursively() }
    @Test fun nativePdfPreviewLoadsAndChangesPages() {
        val bytes = ByteArrayOutputStream()
        val document = PdfDocument()
        try {
            for (i in 1..2) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, i).create())
                page.canvas.drawColor(if (i == 1) android.graphics.Color.WHITE else android.graphics.Color.LTGRAY)
                document.finishPage(page)
            }
            document.writeTo(bytes)
        } finally { document.close() }
        val attachment = runBlocking { store.importStream(bytes.toByteArray().inputStream(), newId(), "两页资料.pdf", "application/pdf") }
        compose.setContent { DailyTheme(Appearance(reducedMotion = true)) {
            AttachmentViewer(attachment, store, {}, {}, {})
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("1 / 2").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下一页").performClick()
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithText("放大").performClick()
        compose.onNodeWithText("还原").assertExists()
    }
    @Test fun nativeImagePreviewDecodesInternalOriginal() {
        val bytes = ByteArrayOutputStream()
        Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888).let {
            it.eraseColor(android.graphics.Color.rgb(190, 210, 232))
            it.compress(Bitmap.CompressFormat.PNG, 100, bytes); it.recycle()
        }
        val attachment = runBlocking { store.importStream(bytes.toByteArray().inputStream(), newId(), "本地图片.png", "image/png") }
        compose.setContent { DailyTheme(Appearance(theme = "dark", reducedMotion = true)) { AttachmentViewer(attachment, store, {}, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("本地图片.png").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("导出原件").assertExists()
    }
    @Test fun customCalendarAtLargeFontRejectsInvalidLeapDateAndReturnsValidDate() {
        var chosen: String? = null
        compose.setContent { DailyTheme(Appearance(reducedMotion = true)) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CalendarDialog("2028-02-29", {}, choose = { chosen = it })
            }
        } }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("2025-02-29")
        compose.onNodeWithText("确定").performScrollTo().performClick()
        Assert.assertNull(chosen)
        compose.onNodeWithText("请输入有效日期，例如 2026-10-24").assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("2024-02-29")
        compose.onNodeWithText("确定").performScrollTo().performClick()
        compose.runOnIdle { Assert.assertEquals("2024-02-29", chosen) }
    }
}
