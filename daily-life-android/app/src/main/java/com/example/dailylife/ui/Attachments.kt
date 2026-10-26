package com.example.dailylife.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun AttachmentPreview(attachment: Attachment, store: AttachmentStore, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<ImageBitmap?>(null, attachment.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val path = store.file(attachment).absolutePath
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap!!, attachment.displayName, modifier.clip(RoundedCornerShape(16.dp)), contentScale = contentScale)
    else Box(modifier.background(LocalGlass.current.selected, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(if (attachment.mime.startsWith("image/")) "图片预览暂不可用" else attachment.mime.substringAfterLast('/').uppercase().take(8))
    }
}
@Composable fun AttachmentList(items: List<Attachment>, store: AttachmentStore, onOpen: (Attachment) -> Unit,
    onRemove: ((Attachment) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { a -> GlassPanel(radius = 18, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (a.mime.startsWith("image/")) AttachmentPreview(a, store, Modifier.size(64.dp))
                else Text("↗", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(a.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Text(formatBytes(a.size), style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.muted)
                }
                if (onRemove != null) TextButton(onClick = { onRemove(a) }) { Text("移除") }
            }
        } }
    }
}
fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) (bytes / 1024).coerceAtLeast(1).toString() + " KB"
    else String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0)
