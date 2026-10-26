package com.example.dailylife.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.dailylife.core.RecordRules
import com.example.dailylife.data.local.Attachment
import com.example.dailylife.data.local.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class AttachmentStore(private val context: Context) {
    val root = File(context.filesDir, "attachments").apply { mkdirs() }
    private val staging = File(context.noBackupFilesDir, "attachment-staging").apply { mkdirs() }
    fun file(value: Attachment): File {
        require(value.relativePath == "attachments/" + value.id + ".bin") { "附件路径不合法" }
        return File(root, value.id + ".bin")
    }
    suspend fun import(uri: Uri, owner: String): Attachment = withContext(Dispatchers.IO) {
        var name = "附件"
        var expected: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null)?.use { c -> if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0 && !c.isNull(n)) name = c.getString(n).take(200)
            if (s >= 0 && !c.isNull(s)) expected = c.getLong(s).takeIf { it >= 0 }
        } }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val limit = if (mime.startsWith("image/")) RecordRules.MAX_IMAGE_BYTES else RecordRules.MAX_FILE_BYTES
        require(expected == null || expected!! <= limit) { "图片上限 20 MB，其他文件上限 100 MB" }
        require(context.filesDir.usableSpace > (expected ?: limit) + 16L * 1024 * 1024) { "本机空间不足，请整理空间后重试" }
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件，请重新选择")
        copy(input, owner, name, mime, limit)
    }
    suspend fun importStream(input: InputStream, owner: String, name: String, mime: String): Attachment =
        withContext(Dispatchers.IO) { copy(input, owner, name.take(200), mime,
            if (mime.startsWith("image/")) RecordRules.MAX_IMAGE_BYTES else RecordRules.MAX_FILE_BYTES) }
    private fun copy(input: InputStream, owner: String, name: String, mime: String, limit: Long): Attachment {
        val id = newId()
        val temp = File(staging, id + ".part")
        val target = File(root, id + ".bin")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var size = 0L
            input.use { source -> FileOutputStream(temp).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    size += count
                    require(size <= limit) { "文件超过大小上限" }
                    hash.update(buffer, 0, count); out.write(buffer, 0, count)
                }
                out.fd.sync()
            } }
            require(size > 0) { "文件为空，未导入" }
            check(temp.renameTo(target)) { "文件保存失败，请重试" }
            return Attachment(id, owner, name, name, mime, size,
                hash.digest().joinToString("") { "%02x".format(it) },
                "attachments/" + id + ".bin", System.currentTimeMillis())
        } catch (e: Exception) { temp.delete(); target.delete(); throw e }
    }
    fun recoverStaging(now: Long = System.currentTimeMillis()) {
        staging.listFiles()?.filter { now - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    }
}
