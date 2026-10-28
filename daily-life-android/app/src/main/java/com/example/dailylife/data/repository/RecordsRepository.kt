package com.example.dailylife.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.dailylife.core.RecordRules
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.local.Record
import com.example.dailylife.feature.records.EditorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

data class RecordsSnapshot(
    val records: List<RecordBundle> = emptyList(), val types: List<RecordType> = emptyList(),
    val folders: List<Folder> = emptyList(), val tags: List<Tag> = emptyList(),
    val drafts: List<Draft> = emptyList(), val attachments: List<Attachment> = emptyList(),
    val links: List<AttachmentLink> = emptyList(), val templates: List<ContentTemplate> = emptyList()
) {
    private val attachmentIndex = attachments.associateBy { it.id }
    private val linkIndex = links.groupBy { it.entityType to it.entityId }
    fun title(record: RecordBundle) = record.displayTitle(attachmentsFor("record", record.record.id).firstOrNull()?.displayName)
    fun attachmentsFor(type: String, id: String): List<Attachment> {
        val refs = linkIndex[type to id].orEmpty().sortedBy { it.sortOrder }
        return refs.mapNotNull { ref -> attachmentIndex[ref.attachmentId] }
    }
}

class RecordsRepository(val db: DailyLifeDatabase, val files: AttachmentStore, private val beforeOpen: () -> Unit = {}) {
    val dao = db.records()
    val writes = Mutex()
    private val head = combine(dao.observeRecords(), dao.observeTypes(), dao.observeFolders(),
        dao.observeTags(), dao.observeDrafts()) { r, t, f, g, d -> RecordsSnapshot(r, t, f, g, d) }
    val snapshot = flow {
        initialize()
        emitAll(combine(head, dao.observeAttachments(), dao.observeAttachmentLinks(),
            dao.observeTemplates()) { h, a, l, t -> h.copy(attachments = a, links = l, templates = t) }
            .distinctUntilChanged())
    }.flowOn(Dispatchers.IO)

    private var initialized = false
    suspend fun initialize() = writes.withLock { withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        beforeOpen()
        db.withTransaction {
            if (dao.profile() == null) {
                val owner = newId(); dao.insertProfile(LocalProfile(owner))
                val seeds = listOf(
                    listOf("随记", "note", "plain", "note"), listOf("日记", "diary", "plain", "diary"),
                    listOf("图片", "file", "plain", "image"), listOf("资料", "file", "plain", "file"),
                    listOf("收藏", "bookmark", "plain", "link"), listOf("信息卡", "note", "card", "card"),
                    listOf("灵感", "note", "plain", "idea"), listOf("读书笔记", "note", "plain", "book"),
                    listOf("会议纪要", "note", "plain", "meeting"))
                seeds.forEachIndexed { i, s -> dao.putType(RecordType(
                    UUID.nameUUIDFromBytes(("daily.type." + s[0]).toByteArray()).toString(),
                    owner, s[0], RecordRules.normalized(s[0]), s[1], s[2], s[3], i)) }
                listOf("生活", "工作", "学习").forEachIndexed { i, n ->
                    dao.putFolder(Folder(newId(), owner, n, RecordRules.normalized(n), i))
                }
            }
        }
        files.recoverStaging()
        initialized = true
    } }
    suspend fun owner(): String = dao.profile()?.id ?: error("本地资料尚未初始化")
    suspend fun newDraft(typeId: String, target: String? = null, template: ContentTemplate? = null): Draft =
        writes.withLock { db.withTransaction {
            if (target != null) dao.drafts().filter { it.targetId == target }.maxByOrNull { it.savedAt }?.let { return@withTransaction it }
            val original = target?.let { dao.record(it) }
            require(target == null || original != null && original.record.deletedAt == null) { "记录已删除或不存在" }
            val data = if (original == null) {
                val created = EditorData(typeId = typeId, body = template?.body ?: "")
                created.copy(fields = template?.fieldsJson?.let { EditorData.fromJson(it).fields.map { field ->
                    field.copy(id = newId(), recordId = created.recordId, value = "")
                } } ?: emptyList())
            }
            else EditorData(original.record.id, original.record.typeId, original.record.revision,
                original.record.title, original.body, original.bookmark?.url ?: "",
                original.diary?.diaryDate ?: LocalDate.now().toString(), original.diary?.mood ?: "",
                original.record.folderId, original.tagLinks.filter { it.entityType == "record" }.map { it.tagId }.toSet(),
                original.record.organizationStatus == "organized", original.fields.sortedBy { it.sortOrder }, presentation=original.record.presentation)
            val draft = Draft(newId(), owner(), target, payload = data.json(), savedAt = System.currentTimeMillis())
            dao.putDraft(draft)
            if (original != null) dao.putLinks(dao.linksFor("record", original.record.id).map {
                it.copy(entityType = "draft", entityId = draft.id)
            })
            draft
        } }
    suspend fun saveDraft(id: String, data: EditorData) = writes.withLock {
        val draft = dao.draft(id) ?: error("草稿已关闭，请重新编辑")
        dao.putDraft(draft.copy(payload = data.json(), savedAt = System.currentTimeMillis()))
    }
    /** Shared by domain commits while holding the write coordinator; failure keeps the draft intact. */
    internal suspend fun requireAvailableAttachments(refs: List<AttachmentLink>) = withContext(Dispatchers.IO) {
        val owner = owner()
        refs.map { ref ->
            val attachment = dao.attachment(ref.attachmentId) ?: error("附件信息缺失，请重新选择或移除")
            val file = files.file(attachment)
            require(attachment.ownerId == owner && attachment.status == "available" &&
                file.isFile && file.length() == attachment.size) { "附件原件缺失或损坏，请重新选择或移除后保存" }
            attachment
        }
    }
    /** Cancellation can arrive after the DB commit: never remove an original whose metadata committed. */
    internal suspend fun cleanupUncommittedOriginals(published: List<java.io.File>) = withContext(NonCancellable + Dispatchers.IO) {
        published.forEach { file -> runCatching {
            require(file.canonicalFile.parentFile == files.root.canonicalFile)
            if (dao.attachment(file.nameWithoutExtension) == null) file.delete()
        } }
    }
    suspend fun importAttachment(uri: Uri, draftId: String): Attachment = writes.withLock {
        require(dao.draft(draftId) != null) { "草稿已关闭，请重新编辑" }
        require(dao.linksFor("draft", draftId).size < 50) { "每条记录最多 50 份附件" }
        val attachment = files.import(uri, owner())
        try { db.withTransaction {
            dao.putAttachment(attachment)
            dao.putLinks(listOf(AttachmentLink("draft", draftId, attachment.id,
                sortOrder = dao.linksFor("draft", draftId).size)))
        } } catch (e: Exception) { files.file(attachment).delete(); throw e }
        attachment
    }
    suspend fun attachExisting(draftId: String, attachmentId: String) = writes.withLock {
        require(dao.draft(draftId) != null) { "草稿已关闭" }
        requireAvailableAttachments(listOf(AttachmentLink("draft",draftId,attachmentId)))
        require(dao.linksFor("draft", draftId).size < 50) { "每条记录最多 50 份附件" }
        dao.putLinks(listOf(AttachmentLink("draft", draftId, attachmentId,
            sortOrder = dao.linksFor("draft", draftId).size)))
    }
    suspend fun exportOriginal(context: android.content.Context, uri: Uri, id: String) = writes.withLock { withContext(Dispatchers.IO) {
        val attachment = dao.attachment(id) ?: error("附件不存在")
        val source = files.file(attachment)
        require(source.exists() && source.length() == attachment.size) { "附件原件缺失或损坏" }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选位置")
        output.use { out -> source.inputStream().use { it.copyTo(out, 65536) } }
    } }
    suspend fun removeAttachment(draftId: String, attachmentId: String) = writes.withLock {
        dao.detach("draft", draftId, attachmentId)
        cleanupUnreferenced(listOf(attachmentId))
    }
    suspend fun discardDraft(id: String) = writes.withLock {
        val ids = dao.linksFor("draft", id).map { it.attachmentId }
        db.withTransaction { dao.clearLinks("draft", id); dao.deleteDraft(id) }
        cleanupUnreferenced(ids)
    }
    private suspend fun cleanupUnreferenced(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.distinct().forEach { id -> if (dao.attachmentUses(id) == 0) {
            dao.attachment(id)?.let { value ->
                if (!files.file(value).exists() || files.file(value).delete()) dao.deleteAttachment(id)
            }
        } }
    }
    suspend fun commit(id: String, data: EditorData): String = writes.withLock {
        val detached = mutableListOf<String>()
        val result = withContext(Dispatchers.IO) { db.withTransaction {
            val draft = dao.draft(id) ?: error("草稿已提交或关闭，请返回列表查看")
            val type = dao.type(data.typeId) ?: error("类型已删除，请重新选择")
            val owner = owner()
            require(type.ownerId == owner) { "类型不属于当前资料" }
            require(data.folderId == null || dao.folders().any { it.id == data.folderId && it.ownerId == owner }) { "资料夹已删除，请重新选择" }
            require(data.tags.all { id -> dao.tags().any { it.id == id && it.ownerId == owner } }) { "标签已变化，请重新选择" }
            val refs = dao.linksFor("draft", id)
            val attachments = requireAvailableAttachments(refs)
            val existing = draft.targetId?.let { dao.record(it) }
            if (draft.targetId != null) {
                require(existing != null && existing.record.deletedAt == null) { "原记录已进入回收站，请先恢复记录" }
                require(existing.record.revision == data.baseRevision) { "原记录已更新。草稿仍保留；请先复制需要保留的内容，再放弃旧草稿并重新编辑" }
                require(existing.record.kind == type.kind && (existing.note?.noteSubtype ?: "plain") == type.subtype) { "请选择相同内容结构的类型" }
            } else require(dao.record(data.recordId) == null) { "此记录已存在，未重复保存" }
            require(data.title.length <= 120 && data.body.length <= RecordRules.MAX_BODY) { "标题或正文过长" }
            if (type.subtype == "card") validateCard(data) else if (type.kind != "bookmark") RecordRules.content(data.title, data.body, attachments.size)
            val url = if (type.kind == "bookmark") RecordRules.url(data.url) else ""
            if (type.kind == "bookmark" && !data.allowDuplicateUrl) {
                val duplicates = dao.duplicateBookmarks(url, data.recordId)
                require(duplicates.none { dao.record(it.recordId)?.record?.deletedAt == null }) { "这个链接已存在。可返回打开已有记录，或勾选“允许另存相同链接”" }
            }
            if (type.kind == "file") require(attachments.isNotEmpty()) { "请添加至少一份资料或图片" }
            val date = if (type.kind == "diary") RecordRules.diaryDate(data.diaryDate) else ""
            if (type.kind == "diary") require(dao.diaryConflict(owner, date, data.recordId) == null) { "这一天已有日记，请打开原日记追加内容，或选择另一天；草稿已保留" }
            com.example.dailylife.feature.records.JournalStyle.parse(data.presentation)
            val now = System.currentTimeMillis()
            val record = Record(data.recordId, owner, type.kind, type.id, data.title, data.folderId,
                if (data.organized) "organized" else "inbox", existing?.record?.pinOrder,
                existing?.record?.archivedAt, createdAt = existing?.record?.createdAt ?: now,
                updatedAt = now, revision = (existing?.record?.revision ?: 0) + 1, presentation=data.presentation)
            dao.putRecord(record)
            when (type.kind) {
                "note" -> { dao.putNote(Note(record.id, data.body, noteSubtype = type.subtype))
                    dao.clearFields(record.id); dao.putFields(data.fields.mapIndexed { i, f -> f.copy(recordId = record.id, sortOrder = i) }) }
                "diary" -> dao.putDiary(Diary(record.id, owner, date, data.body, data.mood))
                "bookmark" -> dao.putBookmark(Bookmark(record.id, url, data.body, existing?.bookmark?.readAt))
                "file" -> dao.putFile(FileRecord(record.id, attachments.first().id, data.body))
                else -> error("不支持的内容结构")
            }
            val oldRefs = dao.linksFor("record", record.id)
            detached += oldRefs.map { it.attachmentId }
            dao.clearTags(record.id); dao.putTags(data.tags.map { EntityTag(entityId = record.id, tagId = it) })
            dao.clearLinks("record", record.id)
            dao.putLinks(refs.map { it.copy(entityType = "record", entityId = record.id) })
            bindToProject(db,data.projectId,"record",record.id,owner)
            dao.clearLinks("draft", id); dao.deleteDraft(id)
            record.id
        } }
        cleanupUnreferenced(detached)
        result
    }
    private fun validateCard(data: EditorData) {
        require(data.title.isNotBlank()) { "请填写信息卡名称" }
        require(data.fields.isNotEmpty() && data.fields.size <= 50) { "请添加 1～50 个有效字段" }
        data.fields.forEach { f ->
            RecordRules.name(f.name, 40); require(f.value.isNotBlank()) { "请填写“" + f.name + "”的内容" }
            require(f.value.length <= 5000) { "字段内容过长" }
            when (f.valueType) {
                "number" -> require(f.value.toBigDecimalOrNull() != null) { f.name + "需要有效数字" }
                "date" -> RecordRules.diaryDate(f.value)
                "link" -> RecordRules.url(f.value)
                "phone" -> require(f.value.matches(Regex("[+\\d\\s()\\-]{3,32}"))) { "电话号码格式不正确" }
                "text" -> Unit
                else -> error("字段类型无效")
            }
        }
    }
    suspend fun mutate(ids: Set<String>, action: String, folderId: String? = null, tagIds: Set<String> = emptySet()) =
        writes.withLock { db.withTransaction {
            val now = System.currentTimeMillis()
            if (folderId != null) require(dao.folders().any { it.id == folderId && it.ownerId == owner() })
            require(tagIds.all { t -> dao.tags().any { it.id == t && it.ownerId == owner() } })
            ids.forEach { id -> val b = dao.record(id) ?: error("记录不存在"); val r = b.record
                require(r.ownerId == owner())
                if (action == "restore" && b.diary != null) require(dao.diaryConflict(r.ownerId, b.diary.diaryDate, id) == null) { "同一天已有日记，不能直接恢复；请先处理现有日记" }
                val changed = when (action) {
                    "pin" -> r.copy(pinOrder = if (r.pinOrder == null) now else null)
                    "archive" -> r.copy(archivedAt = if (r.archivedAt == null) now else null)
                    "trash" -> r.copy(deletedAt = now)
                    "restore" -> r.copy(deletedAt = null)
                    "organize" -> r.copy(organizationStatus = if (r.organizationStatus == "inbox") "organized" else "inbox")
                    "folder" -> r.copy(folderId = folderId)
                    "tags" -> r
                    "read" -> { b.bookmark?.let { dao.putBookmark(it.copy(readAt = if (it.readAt == null) now else null)) }; r }
                    else -> error("不支持的操作")
                }
                dao.putRecord(changed.copy(updatedAt = now, revision = r.revision + 1))
                if (action == "trash") b.diary?.let { dao.putDiary(it.copy(activeDate = null)) }
                if (action == "restore") b.diary?.let { dao.putDiary(it.copy(activeDate = it.diaryDate)) }
                if (action == "tags") dao.putTags(tagIds.map { EntityTag(entityId = id, tagId = it) })
            }
        } }
    suspend fun permanentlyDelete(ids: Set<String>) = writes.withLock {
        val attachments = mutableListOf<String>()
        db.withTransaction { ids.forEach { id ->
            val b = dao.record(id) ?: return@forEach
            require(b.record.deletedAt != null) { "请先移入回收站" }
            require(dao.drafts().none { it.targetId == id }) { "该记录仍有未提交草稿，请先处理草稿" }
            require(db.plans().tasks().none {it.sourceRecordId==id}) { "此记录仍是任务来源，请先在任务里解除关联" }
            require(db.tools().links().none {it.entityType=="record" && it.entityId==id}) {"此记录仍被专题引用，请先移出专题"}
            attachments += dao.linksFor("record", id).map { it.attachmentId }
            dao.clearLinks("record", id); dao.clearTags(id); dao.deleteRecord(id)
        } }
        cleanupUnreferenced(attachments)
    }
    suspend fun duplicate(id: String): String = writes.withLock { db.withTransaction {
        val b = dao.record(id) ?: error("记录不存在")
        val r = b.record; require(r.deletedAt == null)
        val targetId = newId(); val now = System.currentTimeMillis()
        val type = if (b.diary != null) dao.types().first { it.kind == "note" && it.subtype == "plain" } else dao.type(r.typeId)!!
        dao.putRecord(r.copy(id = targetId, kind = type.kind, typeId = type.id,
            title = if (r.title.isBlank()) "" else r.title.take(116) + "（副本）", pinOrder = null,
            archivedAt = null, createdAt = now, updatedAt = now, revision = 1))
        if (b.diary != null) dao.putNote(Note(targetId, b.diary.body))
        b.note?.let { dao.putNote(it.copy(recordId = targetId)) }
        b.file?.let { dao.putFile(it.copy(recordId = targetId)) }
        b.bookmark?.let { dao.putBookmark(it.copy(recordId = targetId)) }
        dao.putFields(b.fields.map { it.copy(id = newId(), recordId = targetId) })
        dao.putTags(b.tagLinks.filter { it.entityType == "record" }.map { it.copy(entityId = targetId) })
        dao.putLinks(dao.linksFor("record", id).map { it.copy(entityId = targetId) })
        targetId
    } }
    suspend fun putOrganization(category: String, name: String, id: String? = null, color: Int = 0,
        kind: String = "note", subtype: String = "plain") = writes.withLock { db.withTransaction {
        val clean = RecordRules.name(name); val normalized = RecordRules.normalized(clean); val owner = owner()
        when (category) {
            "tag" -> { require(dao.tags().none { it.id != id && it.normalizedName == normalized }) { "标签已存在" }
                dao.putTag(Tag(id ?: newId(), owner, clean, normalized, color)) }
            "folder" -> { require(dao.folders().none { it.id != id && it.normalizedName == normalized }) { "资料夹已存在" }
                dao.putFolder(Folder(id ?: newId(), owner, clean, normalized)) }
            "type" -> {
                require(kind in setOf("note", "diary", "file", "bookmark") && subtype in setOf("plain", "card"))
                require(subtype != "card" || kind == "note")
                require(dao.types().none { it.id != id && it.normalizedName == normalized }) { "类型已存在" }
                val old = id?.let { dao.type(it) }
                require(old == null || old.kind == kind && old.subtype == subtype) { "已有类型只能修改名称，避免破坏记录结构" }
                dao.putType(RecordType(id ?: newId(), owner, clean, normalized, kind, subtype,
                    old?.icon ?: kind, old?.sortOrder ?: dao.types().size))
            }
        }
    } }
    suspend fun deleteOrganization(category: String, id: String, mergeInto: String? = null) = writes.withLock {
        db.withTransaction { when (category) {
            "folder" -> {
                dao.allRecords().filter { it.record.folderId == id }.forEach { b ->
                    dao.putRecord(b.record.copy(folderId = null, updatedAt = System.currentTimeMillis(), revision = b.record.revision + 1))
                }
                dao.drafts().forEach { draft ->
                    val data = EditorData.fromJson(draft.payload)
                    if (data.folderId == id) dao.putDraft(draft.copy(payload = data.copy(folderId = null).json()))
                }
                dao.deleteFolder(id)
            }
            "tag" -> {
                if (mergeInto != null) {
                    require(id != mergeInto && dao.tags().any { it.id == mergeInto && it.ownerId == owner() })
                    dao.putTags(dao.tagLinks(id).map { it.copy(tagId = mergeInto) })
                }
                dao.drafts().forEach { draft ->
                    val data = EditorData.fromJson(draft.payload)
                    if (id in data.tags) dao.putDraft(draft.copy(payload = data.copy(tags =
                        data.tags - id + listOfNotNull(mergeInto)).json()))
                }
                dao.deleteTag(id)
            }
            "type" -> {
                require(dao.typeUses(id) == 0 && dao.drafts().none { EditorData.fromJson(it.payload).typeId == id } &&
                    dao.templates().none { it.typeId == id }) { "此类型仍有记录、草稿或模板，不能删除" }
                require(dao.types().size > 1) { "至少保留一种类型" }; dao.deleteType(id)
            }
        } }
    }
    suspend fun saveTemplate(name: String, data: EditorData) = writes.withLock {
        dao.putTemplate(ContentTemplate(newId(), owner(), RecordRules.name(name), data.typeId,
            body = data.body, fieldsJson = data.copy(fields = data.fields.map { it.copy(value = "") },projectId=null).json()))
    }
    suspend fun classify(ids: Set<String>, folderId: String?, tags: Set<String>) = writes.withLock {
        db.withTransaction {
            val owner = owner()
            require(folderId == null || dao.folders().any { it.id == folderId && it.ownerId == owner }) { "资料夹已删除" }
            require(tags.all { id -> dao.tags().any { it.id == id && it.ownerId == owner } }) { "标签已变化，请重新选择" }
            ids.forEach { id ->
                val b = dao.record(id) ?: error("记录不存在")
                require(b.record.ownerId == owner && b.record.deletedAt == null)
                dao.putRecord(b.record.copy(folderId = folderId, updatedAt = System.currentTimeMillis(), revision = b.record.revision + 1))
                dao.putTags(tags.map { EntityTag(entityId = id, tagId = it) })
            }
        }
    }
}
