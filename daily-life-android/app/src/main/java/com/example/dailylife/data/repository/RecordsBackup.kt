package com.example.dailylife.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.dailylife.core.RecordRules
import com.example.dailylife.data.local.*
import com.example.dailylife.data.local.Record
import com.example.dailylife.feature.records.EditorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

class RestorePreview internal constructor(internal val manifest: JSONObject, internal val directory: File,
    val records: Int, val attachments: Int, val existing: Int)
private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONObject.nullLong(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun uuid(value: String): String { require(UUID.fromString(value).toString() == value) { "备份中的标识格式无效" }; return value }
private fun sha(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Versioned records-only portable archive. It never replaces a database or restores executable content. */
class RecordsBackup(private val context: Context, private val repo: RecordsRepository) {
    suspend fun export(uri: Uri) = repo.writes.withLock { withContext(Dispatchers.IO) {
        val manifest = repo.db.withTransaction {
            val dao = repo.dao
            val records = dao.allRecords()
            require(records.size <= 2000) { "本次轻量备份最多 2000 条记录，请分批导出 Markdown 或等待完整备份模块" }
            val links = dao.links().filter { link -> link.entityType == "record" && records.any { it.record.id == link.entityId } }
            val attachments = dao.attachments().filter { a -> links.any { it.attachmentId == a.id } }
            require(attachments.size <= 1000 && attachments.sumOf { it.size } <= 256L * 1024 * 1024) { "本次轻量备份最多 1000 份附件、共 256 MB" }
            JSONObject().apply {
                put("format", "daily-life-records"); put("version", 1); put("createdAt", System.currentTimeMillis())
                put("types", JSONArray(dao.types().map { JSONObject().apply {
                    put("id", it.id); put("name", it.name); put("kind", it.kind); put("subtype", it.subtype); put("icon", it.icon); put("sortOrder", it.sortOrder)
                } }))
                put("folders", JSONArray(dao.folders().map { JSONObject().apply { put("id", it.id); put("name", it.name); put("sortOrder", it.sortOrder) } }))
                put("tags", JSONArray(dao.tags().map { JSONObject().apply { put("id", it.id); put("name", it.name); put("color", it.color); put("sortOrder", it.sortOrder) } }))
                put("records", JSONArray(records.map { b -> val r = b.record
                    JSONObject().apply {
                        put("data", JSONObject(EditorData(r.id, r.typeId, r.revision, r.title, b.body,
                            b.bookmark?.url ?: "", b.diary?.diaryDate ?: java.time.LocalDate.now().toString(), b.diary?.mood ?: "",
                            r.folderId, b.tagLinks.filter { it.entityType == "record" }.map { it.tagId }.toSet(),
                            r.organizationStatus == "organized", b.fields).json()))
                        put("kind", r.kind); put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                        put("pinOrder", r.pinOrder ?: JSONObject.NULL); put("archivedAt", r.archivedAt ?: JSONObject.NULL)
                        put("deletedAt", r.deletedAt ?: JSONObject.NULL); put("readAt", b.bookmark?.readAt ?: JSONObject.NULL)
                    }
                }))
                put("attachments", JSONArray(attachments.map { a -> JSONObject().apply {
                    put("id", a.id); put("name", a.originalName); put("displayName", a.displayName); put("mime", a.mime)
                    put("size", a.size); put("sha256", a.sha256); put("createdAt", a.createdAt)
                } }))
                put("links", JSONArray(links.map { l -> JSONObject().apply { put("recordId", l.entityId); put("attachmentId", l.attachmentId); put("sortOrder", l.sortOrder) } }))
                put("templates", JSONArray(dao.templates().map { t -> JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("typeId", t.typeId); put("body", t.body); put("fieldsJson", t.fieldsJson)
                } }))
            }
        }
        val bytes = manifest.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 5 * 1024 * 1024) { "记录文本超过本次轻量备份的 5 MB 上限" }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选位置")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(bytes); zip.closeEntry()
            manifest.getJSONArray("attachments").objects().forEach { o ->
                val a = repo.dao.attachment(o.getString("id")) ?: error("附件已变化")
                val file = repo.files.file(a)
                require(file.exists() && file.length() == a.size && sha(file) == a.sha256) { "附件缺失或损坏：" + a.displayName }
                zip.putNextEntry(ZipEntry("attachments/" + uuid(a.id) + ".bin"))
                file.inputStream().use { it.copyTo(zip, 65536) }; zip.closeEntry()
            }
        }
    } }

    suspend fun prepare(uri: Uri): RestorePreview = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "restore-" + newId()).apply { check(mkdirs()) }
        try {
            var total = 0L; val seen = mutableSetOf<String>()
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取备份")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && seen.add(name) && seen.size <= 1001) { "备份中有重复或过多文件" }
                    val isManifest = name == "manifest.json"
                    if (!isManifest) {
                        require(name.matches(Regex("attachments/[a-f0-9\\-]{36}\\.bin"))) { "备份包含不支持的路径" }
                        uuid(name.substringAfter('/').removeSuffix(".bin"))
                    }
                    val target = if (isManifest) File(directory, "manifest.json") else File(directory, name.substringAfter('/'))
                    val limit = if (isManifest) 5L * 1024 * 1024 else RecordRules.MAX_FILE_BYTES
                    var count = 0L
                    target.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val n = zip.read(buffer); if (n < 0) break
                            count += n; total += n
                            require(count <= limit && total <= 261L * 1024 * 1024) { "备份解压后超过容量限制" }
                            require(context.cacheDir.usableSpace > 8L * 1024 * 1024) { "存储空间不足" }
                            output.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = JSONObject(File(directory, "manifest.json").readText(Charsets.UTF_8))
            validate(manifest, directory, seen)
            val ids = repo.dao.allRecords().map { it.record.id }.toSet()
            val rows = manifest.getJSONArray("records").objects()
            RestorePreview(manifest, directory, rows.size, manifest.getJSONArray("attachments").length(),
                rows.count { it.getJSONObject("data").getString("recordId") in ids })
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    private fun validate(m: JSONObject, directory: File, names: Set<String>) {
        require(m.getString("format") == "daily-life-records" && m.getInt("version") == 1) { "不支持此备份格式或版本" }
        val typeRows = m.getJSONArray("types").objects(); require(typeRows.size in 1..200)
        val types = typeRows.associateBy { uuid(it.getString("id")) }; require(types.size == typeRows.size)
        typeRows.forEach { t ->
            RecordRules.name(t.getString("name")); val kind = t.getString("kind"); val sub = t.getString("subtype")
            require(kind in setOf("note", "diary", "file", "bookmark") && sub in setOf("plain", "card") && (sub != "card" || kind == "note"))
        }
        val folderRows = m.getJSONArray("folders").objects(); val tagRows = m.getJSONArray("tags").objects()
        require(folderRows.size <= 500 && tagRows.size <= 1000)
        val folders = folderRows.map { uuid(it.getString("id")) }.toSet(); require(folders.size == folderRows.size)
        val tags = tagRows.map { uuid(it.getString("id")) }.toSet(); require(tags.size == tagRows.size)
        (folderRows + tagRows).forEach { RecordRules.name(it.getString("name")) }
        val records = m.getJSONArray("records").objects(); require(records.size <= 2000)
        val ids = records.map { uuid(it.getJSONObject("data").getString("recordId")) }.toSet(); require(ids.size == records.size)
        val attachmentRows = m.getJSONArray("attachments").objects(); require(attachmentRows.size <= 1000)
        val attachments = attachmentRows.associateBy { uuid(it.getString("id")) }; require(attachments.size == attachmentRows.size)
        require(names == attachments.keys.map { "attachments/" + it + ".bin" }.toSet() + "manifest.json") { "备份附件清单不一致" }
        attachments.forEach { (id, a) ->
            val size = a.getLong("size"); val mime = a.getString("mime")
            require(size in 1..if (mime.startsWith("image/")) RecordRules.MAX_IMAGE_BYTES else RecordRules.MAX_FILE_BYTES)
            require(a.getString("name").length in 1..255 && a.getString("displayName").length in 1..255 && mime.length <= 200)
            val file = File(directory, id + ".bin")
            require(file.length() == size && sha(file) == a.getString("sha256")) { "附件校验失败：" + a.optString("displayName") }
        }
        val links = m.getJSONArray("links").objects(); require(links.size <= 100000)
        links.forEach { require(it.getString("recordId") in ids && it.getString("attachmentId") in attachments) { "备份中有悬空附件关联" } }
        val fieldIds = mutableSetOf<String>()
        records.forEach { row ->
            val data = EditorData.fromJson(row.getJSONObject("data").toString())
            val type = types[data.typeId] ?: error("记录类型缺失"); val kind = type.getString("kind")
            require(row.getString("kind") == kind && (data.folderId == null || data.folderId in folders) && data.tags.all { it in tags })
            require(data.baseRevision > 0 && row.getLong("createdAt") >= 0 && row.getLong("updatedAt") >= row.getLong("createdAt"))
            require(data.title.length <= 120 && data.body.length <= RecordRules.MAX_BODY)
            val count = links.count { it.getString("recordId") == data.recordId }; require(count <= 50)
            if (kind == "bookmark") RecordRules.url(data.url)
            else if (type.getString("subtype") != "card") RecordRules.content(data.title, data.body, count)
            if (kind == "file") require(count > 0)
            if (kind == "diary") RecordRules.diaryDate(data.diaryDate)
            if (type.getString("subtype") == "card") {
                require(data.title.isNotBlank() && data.fields.size in 1..50)
                data.fields.forEach { f ->
                    require(fieldIds.add(uuid(f.id)) && f.value.length in 1..5000); RecordRules.name(f.name, 40)
                    when (f.valueType) {
                        "number" -> require(f.value.toBigDecimalOrNull() != null)
                        "date" -> RecordRules.diaryDate(f.value)
                        "link" -> RecordRules.url(f.value)
                        "phone" -> require(f.value.matches(Regex("[+\\d\\s()\\-]{3,32}")))
                        "text" -> Unit
                        else -> error("不支持的字段类型")
                    }
                }
            } else require(data.fields.isEmpty())
        }
        val templates = m.getJSONArray("templates").objects(); require(templates.size <= 200)
        val templateIds = mutableSetOf<String>()
        templates.forEach {
            require(templateIds.add(uuid(it.getString("id"))) && it.getString("typeId") in types)
            RecordRules.name(it.getString("name"))
            require(it.getString("body").length <= RecordRules.MAX_BODY)
            val data = EditorData.fromJson(it.getString("fieldsJson"))
            require(data.fields.size <= 50 && data.fields.all { f -> f.value.isEmpty() })
        }
    }
    fun discard(preview: RestorePreview) { preview.directory.deleteRecursively() }
    suspend fun restore(preview: RestorePreview): Pair<Int, Int> = repo.writes.withLock { withContext(Dispatchers.IO) {
        val published = mutableListOf<File>()
        try {
            var inserted = 0; var skipped = 0
            repo.db.withTransaction {
                val dao = repo.dao; val owner = repo.owner(); val m = preview.manifest
                val typeMap = mutableMapOf<String, String>()
                m.getJSONArray("types").objects().forEach { t ->
                    val name = RecordRules.name(t.getString("name")); val key = RecordRules.normalized(name)
                    val old = dao.types().find { it.normalizedName == key }
                    require(old == null || old.kind == t.getString("kind") && old.subtype == t.getString("subtype")) { "同名类型的内容结构不同：" + name }
                    val id = old?.id ?: t.getString("id").takeUnless { imported -> dao.types().any { it.id == imported } } ?: newId()
                    typeMap[t.getString("id")] = id
                    if (old == null) dao.putType(RecordType(id, owner, name, key, t.getString("kind"), t.getString("subtype"), t.optString("icon"), t.optInt("sortOrder")))
                }
                val folderMap = mutableMapOf<String, String>(); val tagMap = mutableMapOf<String, String>()
                m.getJSONArray("folders").objects().forEach { o ->
                    val name = RecordRules.name(o.getString("name")); val key = RecordRules.normalized(name)
                    val old = dao.folders().find { it.normalizedName == key }
                    val id = old?.id ?: o.getString("id").takeUnless { imported -> dao.folders().any { it.id == imported } } ?: newId()
                    folderMap[o.getString("id")] = id
                    if (old == null) dao.putFolder(Folder(id, owner, name, key, o.optInt("sortOrder")))
                }
                m.getJSONArray("tags").objects().forEach { o ->
                    val name = RecordRules.name(o.getString("name")); val key = RecordRules.normalized(name)
                    val old = dao.tags().find { it.normalizedName == key }
                    val id = old?.id ?: o.getString("id").takeUnless { imported -> dao.tags().any { it.id == imported } } ?: newId()
                    tagMap[o.getString("id")] = id
                    if (old == null) dao.putTag(Tag(id, owner, name, key, o.optInt("color"), o.optInt("sortOrder")))
                }
                val allLinks = m.getJSONArray("links").objects()
                val metadata = m.getJSONArray("attachments").objects().associateBy { it.getString("id") }
                m.getJSONArray("records").objects().forEach { row ->
                    val data = EditorData.fromJson(row.getJSONObject("data").toString())
                    val deleted = row.nullLong("deletedAt")
                    if (dao.record(data.recordId) != null || row.getString("kind") == "diary" && deleted == null && dao.diaryConflict(owner, data.diaryDate, data.recordId) != null) {
                        skipped++; return@forEach
                    }
                    require(data.fields.all { dao.field(it.id) == null }) { "信息卡字段标识与现有记录冲突，恢复已中止" }
                    val refs = allLinks.filter { it.getString("recordId") == data.recordId }
                    refs.forEach { link ->
                        val id = link.getString("attachmentId"); val o = metadata.getValue(id); val old = dao.attachment(id)
                        if (old != null) require(old.sha256 == o.getString("sha256") && repo.files.file(old).exists() &&
                            sha(repo.files.file(old)) == old.sha256) { "已有附件的标识冲突或原件缺失、损坏" }
                        else {
                            val a = Attachment(id, owner, o.getString("name"), o.getString("displayName"), o.getString("mime"),
                                o.getLong("size"), o.getString("sha256"), "attachments/" + id + ".bin", o.getLong("createdAt"))
                            val source = File(preview.directory, id + ".bin"); require(sha(source) == a.sha256)
                            val target = repo.files.file(a)
                            require(!target.exists()) { "发现尚未登记的同名附件，请保留备份并检查存储" }
                            target.parentFile?.mkdirs()
                            published += target
                            source.inputStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output); output.fd.sync() } }
                            dao.putAttachment(a)
                        }
                    }
                    val r = Record(data.recordId, owner, row.getString("kind"), typeMap.getValue(data.typeId), data.title,
                        data.folderId?.let { folderMap.getValue(it) }, if (data.organized) "organized" else "inbox",
                        row.nullLong("pinOrder"), row.nullLong("archivedAt"), deleted, row.getLong("createdAt"),
                        row.getLong("updatedAt"), data.baseRevision)
                    dao.putRecord(r)
                    when (r.kind) {
                        "note" -> { dao.putNote(Note(r.id, data.body, noteSubtype = dao.type(r.typeId)!!.subtype)); dao.putFields(data.fields) }
                        "diary" -> dao.putDiary(Diary(r.id, owner, data.diaryDate, data.body, data.mood, if (deleted == null) data.diaryDate else null))
                        "file" -> dao.putFile(FileRecord(r.id, refs.first().getString("attachmentId"), data.body))
                        "bookmark" -> dao.putBookmark(Bookmark(r.id, RecordRules.url(data.url), data.body, row.nullLong("readAt")))
                    }
                    dao.putTags(data.tags.map { EntityTag(entityId = r.id, tagId = tagMap.getValue(it)) })
                    dao.putLinks(refs.map { AttachmentLink("record", r.id, it.getString("attachmentId"), sortOrder = it.optInt("sortOrder")) })
                    inserted++
                }
                m.getJSONArray("templates").objects().forEach { t ->
                    if (dao.templates().none { it.id == t.getString("id") }) dao.putTemplate(ContentTemplate(t.getString("id"), owner,
                        t.getString("name"), typeMap.getValue(t.getString("typeId")), body = t.getString("body"), fieldsJson = t.getString("fieldsJson")))
                }
            }
            discard(preview); inserted to skipped
        } catch (e: Exception) {
            // Database transaction rolled back; remove only files this operation just created.
            published.forEach { it.delete() }; throw e
        }
    } }
}
