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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val previewDecoders=Semaphore(2)
private val previewCache=object:android.util.LruCache<String,android.graphics.Bitmap>(16*1024*1024) {
    override fun sizeOf(key:String,value:android.graphics.Bitmap)=value.byteCount
}
private data class PreviewState(val bitmap:ImageBitmap?=null,val loading:Boolean=true)

/** Decode a bounded, correctly oriented preview; the internal original is never rewritten. */
internal fun decodePhotoPreview(file:java.io.File,maxEdge:Int=720):android.graphics.Bitmap? {
    require(maxEdge>0)
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.absolutePath,bounds)
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=maxEdge) sample*=2
    val decoded=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply {inSampleSize=sample}) ?: return null
    val scale=minOf(1f,maxEdge.toFloat()/maxOf(decoded.width,decoded.height))
    val bitmap=if(scale==1f) decoded else android.graphics.Bitmap.createScaledBitmap(decoded,
        (decoded.width*scale).toInt().coerceAtLeast(1),(decoded.height*scale).toInt().coerceAtLeast(1),true).also {if(it!==decoded) decoded.recycle()}
    val orientation=runCatching {android.media.ExifInterface(file.absolutePath).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
    val matrix=android.graphics.Matrix().apply {when(orientation) {
        2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
        5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
        7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)
    }}
    return if(matrix.isIdentity) bitmap else android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also {if(it!==bitmap) bitmap.recycle()}
}

@Composable fun AttachmentPreview(attachment: Attachment, store: AttachmentStore, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val preview by produceState(PreviewState(), attachment.id, attachment.sha256) {
        value=PreviewState()
        val bitmap = withContext(Dispatchers.IO) {previewDecoders.withPermit {
            runCatching {
                val file=store.file(attachment)
                val key=file.absolutePath+":"+attachment.sha256
                previewCache.get(key)?.let {return@runCatching it.asImageBitmap()}
                decodePhotoPreview(file)?.also {previewCache.put(key,it)}?.asImageBitmap()
            }.getOrNull()
        }}
        value=PreviewState(bitmap,false)
    }
    if (preview.bitmap != null) Image(preview.bitmap!!, attachment.displayName, modifier.clip(RoundedCornerShape(12.dp)), contentScale = contentScale)
    else Box(modifier.background(LocalGlass.current.selected, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(if(preview.loading) "载入图片…" else if (attachment.mime.startsWith("image/")) "图片预览暂不可用" else attachment.mime.substringAfterLast('/').uppercase().take(8),color=LocalGlass.current.muted,style=MaterialTheme.typography.bodySmall)
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
