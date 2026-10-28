package com.example.dailylife.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun AttachmentViewer(attachment: Attachment, store: AttachmentStore, dismiss: () -> Unit,
    external: () -> Unit, export: () -> Unit) {
    var page by remember(attachment.id) { mutableIntStateOf(0) }
    var pages by remember(attachment.id) { mutableIntStateOf(1) }
    var error by remember(attachment.id) { mutableStateOf<String?>(null) }
    var zoom by remember(attachment.id, page) { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { scale, _, _ -> zoom = (zoom * scale).coerceIn(1f, 4f) }
    val pdf by produceState<ImageBitmap?>(null, attachment.id, page) {
        if (attachment.mime == "application/pdf") {
            val result = withContext(Dispatchers.IO) { runCatching {
                ParcelFileDescriptor.open(store.file(attachment), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val count = renderer.pageCount
                        require(count > 0) { "PDF 没有页面" }
                        renderer.openPage(page.coerceAtMost(count - 1)).use { source ->
                            val width = 1280
                            val height = (width.toFloat() * source.height / source.width).toInt().coerceIn(1, 4096)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.asImageBitmap() to count
                        }
                    }
                }
            } }
            result.onSuccess { value = it.first; pages = it.second; error = null }
                .onFailure { error = "PDF 暂时无法预览，可尝试用其他应用打开。"; value = null }
        }
    }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(LocalGlass.current.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = dismiss) { Text("关闭") }
                Text(attachment.displayName, Modifier.weight(1f), maxLines = 2)
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().transformable(transform), contentAlignment = Alignment.Center) {
                val visual = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }
                if (attachment.mime.startsWith("image/")) AttachmentPreview(attachment, store, visual, ContentScale.Fit)
                else if (pdf != null) Image(pdf!!, "PDF 第 " + (page + 1) + " 页", visual, contentScale = ContentScale.Fit)
                else Text(error ?: "正在读取…", Modifier.padding(24.dp))
            }
            if (attachment.mime == "application/pdf") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text("上一页") }
                Text((page + 1).toString() + " / " + pages)
                TextButton(enabled = page + 1 < pages, onClick = { page++ }) { Text("下一页") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { zoom = if (zoom == 1f) 2f else 1f }) { Text(if (zoom == 1f) "放大" else "还原") }
                TextButton(onClick = external) { Text("其他应用打开") }
            }
        }
    }
}
