package com.example.dailylife.feature.records

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class RecordFilter(
    val query: String = "", val typeId: String? = null, val folderId: String? = null,
    val tags: Set<String> = emptySet(), val state: String = "active", val inbox: Boolean = false,
    val sort: String = "updated", val from: String? = null, val to: String? = null, val anniversary: Boolean = false
)
data class EditorSession(val draftId: String, val data: EditorData, val saved: Boolean = true)
data class RecordsUi(
    val ready: Boolean = false, val loadError: String? = null, val snapshot: RecordsSnapshot = RecordsSnapshot(),
    val filter: RecordFilter = RecordFilter(), val editor: EditorSession? = null,
    val detailId: String? = null, val selected: Set<String> = emptySet(),
    val busy: Boolean = false, val message: String? = null, val editorError: String? = null,
    val appearance: Appearance = Appearance(), val restorePreview: RestorePreview? = null
)
fun RecordsSnapshot.filtered(filter: RecordFilter): List<RecordBundle> {
    val q = filter.query.trim().lowercase()
    return records.asSequence().filter { b ->
        val r = b.record
        val businessDate = b.diary?.diaryDate ?: Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        when (filter.state) {
            "trash" -> r.deletedAt != null
            "archived" -> r.deletedAt == null && r.archivedAt != null
            else -> r.deletedAt == null && r.archivedAt == null
        } &&
        (filter.typeId == null || r.typeId == filter.typeId) &&
        (filter.folderId == null || r.folderId == filter.folderId || filter.folderId == "none" && r.folderId == null) &&
        (!filter.inbox || r.organizationStatus == "inbox") &&
        filter.tags.all { tag -> b.tagLinks.any { it.entityType == "record" && it.tagId == tag } } &&
        (filter.from == null || businessDate >= filter.from) &&
        (filter.to == null || businessDate <= filter.to) &&
        (!filter.anniversary || b.diary?.diaryDate?.takeLast(5) == LocalDate.now().toString().takeLast(5)) &&
        (q.isBlank() || (listOf(r.title, b.body, b.bookmark?.url ?: "") +
            b.fields.filterNot { it.isPrivate }.flatMap { listOf(it.name, it.value) } +
            attachmentsFor("record", r.id).map { it.displayName } +
            tags.filter { t -> b.tagLinks.any { it.entityType == "record" && it.tagId == t.id } }.map { it.name })
            .any { it.lowercase().contains(q) })
    }.sortedWith(compareBy<RecordBundle> { it.record.pinOrder == null }
        .thenComparator { a, b -> when (filter.sort) {
            "title" -> title(a).compareTo(title(b), ignoreCase = true)
            "created" -> b.record.createdAt.compareTo(a.record.createdAt)
            else -> b.record.updatedAt.compareTo(a.record.updatedAt)
        } }).toList()
}

class RecordsViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    val repository = (application as DailyLifeApplication).records
    private val preferences = (application as DailyLifeApplication).preferences
    private val backup = RecordsBackup(application, repository)
    private val _ui = MutableStateFlow(RecordsUi())
    val ui = _ui.asStateFlow()
    private var autoSave: Job? = null
    private var operation: Job? = null
    private var loading: Job? = null
    init {
        viewModelScope.launch { preferences.appearance.retryWhen { e, attempt ->
            if (e is CancellationException) throw e
            message("外观偏好读取失败，正在重试")
            delay(((attempt + 1) * 1000).coerceAtMost(30000)); true
        }.collect { a -> _ui.update { it.copy(appearance = a) } } }
        retryLoad()
    }
    fun retryLoad() {
        if (loading?.isActive == true) return
        _ui.update { it.copy(loadError = null) }
        loading = viewModelScope.launch {
            try {
                repository.initialize()
                if (_ui.value.editor == null) savedState.get<String>("draft")?.let { id ->
                    repository.dao.draft(id)?.let { draft -> _ui.update { it.copy(editor = EditorSession(id, EditorData.fromJson(draft.payload))) } }
                }
                repository.snapshot.collect { s -> _ui.update { it.copy(ready = true, loadError = null, snapshot = s) } }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(ready = false, loadError = "记录读取失败：" + (e.message ?: "请重试")) }
            }
        }
    }
    fun message(value: String?) { _ui.update { it.copy(message = value) } }
    fun filter(value: RecordFilter) { _ui.update { it.copy(filter = value, selected = emptySet()) } }
    fun detail(id: String?) { _ui.update { it.copy(detailId = id, selected = emptySet()) } }
    fun select(id: String) { _ui.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) } }
    fun clearSelection() { _ui.update { it.copy(selected = emptySet()) } }
    private fun work(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try { block() } catch (e: Exception) {
                if (e is CancellationException) throw e
                val text = e.message ?: "操作失败，请重试"
                _ui.update { it.copy(message = text, editorError = if (it.editor != null) text else null) }
            } finally { _ui.update { it.copy(busy = false) } }
        }
    }
    fun begin(typeId: String, target: String? = null, template: ContentTemplate? = null,projectId:String?=null) = work {
        flush()
        val d = repository.newDraft(typeId, target, template)
        var data = EditorData.fromJson(d.payload)
        if(projectId!=null && target==null) {data=data.copy(projectId=projectId);repository.saveDraft(d.id,data)}
        if (target == null && _ui.value.snapshot.types.find { it.id == typeId }?.kind == "diary" &&
            _ui.value.filter.from != null && _ui.value.filter.from == _ui.value.filter.to) {
            data = data.copy(diaryDate = _ui.value.filter.from!!)
            repository.saveDraft(d.id, data)
        }
        savedState["draft"] = d.id
        _ui.update { it.copy(editor = EditorSession(d.id, data), editorError = null) }
    }
    fun resume(draft: Draft) = work {
        flush()
        val current = repository.dao.draft(draft.id) ?: error("未完成编辑已保存或删除，请刷新列表")
        savedState["draft"] = current.id
        _ui.update { it.copy(editor = EditorSession(current.id, EditorData.fromJson(current.payload)), editorError = null) }
    }
    fun edit(data: EditorData) {
        if (_ui.value.busy || operation?.isActive == true) return
        val session = _ui.value.editor ?: return
        _ui.update { it.copy(editor = session.copy(data = data, saved = false), editorError = null) }
        autoSave?.cancel()
        autoSave = viewModelScope.launch {
            delay(450)
            try {
                repository.saveDraft(session.draftId, data)
                _ui.update { if (it.editor?.draftId == session.draftId && it.editor.data == data) it.copy(editor = it.editor.copy(saved = true)) else it }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(editorError = "草稿暂存失败：" + (e.message ?: "请重试")) }
            }
        }
    }
    private suspend fun flush() {
        autoSave?.cancelAndJoin()
        _ui.value.editor?.let { repository.saveDraft(it.draftId, it.data) }
    }
    fun leaveEditor() = work {
        flush(); savedState["draft"] = null
        _ui.update { it.copy(editor = null, editorError = null) }; message("未完成内容已保留，可在“我的”继续")
    }
    fun keepDraft() = leaveEditor()
    fun discard(draftId: String? = null) = work {
        autoSave?.cancelAndJoin()
        val id = draftId ?: _ui.value.editor?.draftId ?: return@work
        repository.discardDraft(id)
        if (_ui.value.editor?.draftId == id) {
            savedState["draft"] = null; _ui.update { it.copy(editor = null, editorError = null) }
        }
    }
    fun save() = work {
        flush()
        val session = _ui.value.editor ?: return@work
        val id = repository.commit(session.draftId, session.data)
        savedState["draft"] = null
        _ui.update { it.copy(editor = null, detailId = id, editorError = null) }; message("已保存到本机")
    }
    fun importFiles(uris: List<Uri>) = work {
        flush()
        val id = _ui.value.editor?.draftId ?: return@work
        var success = 0; val failed = mutableListOf<String>()
        uris.take(50).forEach { uri ->
            try { repository.importAttachment(uri, id); success++ }
            catch (e: Exception) { if (e is CancellationException) throw e; failed += e.message ?: "导入失败" }
        }
        message("已导入 " + success + " 份" + if (failed.isEmpty()) "" else "；" + failed.joinToString("；"))
    }
    fun removeAttachment(id: String) = work {
        _ui.value.editor?.let { repository.removeAttachment(it.draftId, id) }
    }
    fun attachExisting(id: String, onSuccess: () -> Unit = {}) = work {
        _ui.value.editor?.let { repository.attachExisting(it.draftId, id); onSuccess() }
    }
    fun exportOriginal(uri: Uri, id: String) = work {
        repository.exportOriginal(getApplication<Application>(), uri, id); message("附件原件已导出")
    }
    fun exportMarkdown(uri: Uri, id: String) = work {
        val snapshot = repository.snapshot.first()
        val record = snapshot.records.firstOrNull { it.record.id == id } ?: error("记录已不存在，未导出")
        val content = record.markdown(snapshot)
        withContext(Dispatchers.IO) {
            val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选位置")
            output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
        }
        message("Markdown 已导出")
    }
    fun mutate(ids: Set<String>, action: String, folderId: String? = null, tags: Set<String> = emptySet()) = work {
        repository.mutate(ids, action, folderId, tags)
        _ui.update { it.copy(selected = emptySet(), detailId = if (action == "trash") null else it.detailId) }
        message("已完成")
    }
    fun destroy(ids: Set<String>) = work {
        repository.permanentlyDelete(ids); _ui.update { it.copy(selected = emptySet(), detailId = null) }; message("已永久删除")
    }
    fun bulkClassify(ids: Set<String>, folderId: String?, tags: Set<String>) = work {
        repository.classify(ids, folderId, tags)
        clearSelection(); message("已完成归类")
    }
    fun duplicate(id: String) = work { val copy = repository.duplicate(id); detail(copy); message("已创建副本") }
    fun organization(category: String, name: String, id: String? = null, color: Int = 0, kind: String = "note", subtype: String = "plain") = work {
        repository.putOrganization(category, name, id, color, kind, subtype); message("已保存分类")
    }
    fun deleteOrganization(category: String, id: String, mergeInto: String? = null) = work {
        repository.deleteOrganization(category, id, mergeInto); message("已更新分类")
    }
    fun template(name: String) = work {
        _ui.value.editor?.let { repository.saveTemplate(name, it.data); message("模板已保存；隐私字段的值不会写入模板") }
    }
    fun appearance(value: Appearance) = work { preferences.set(value) }
    fun exportBackup(uri: Uri) = work { backup.export(uri); message("记录备份已导出，包含附件原件；不包含草稿") }
    fun prepareRestore(uri: Uri) = work {
        withContext(Dispatchers.IO) { backup.discardAll() }
        _ui.update { it.copy(restorePreview = null) }
        val preview = backup.prepare(uri)
        _ui.update { it.copy(restorePreview = preview) }
    }
    fun restoreBackup() = work {
        val preview = _ui.value.restorePreview ?: return@work
        val (inserted, skipped) = backup.restore(preview)
        _ui.update { it.copy(restorePreview = null) }
        message("已恢复 " + inserted + " 条；跳过 " + skipped + " 条已有记录或日记日期冲突")
    }
    fun dismissRestore() = work {
        _ui.value.restorePreview?.let { withContext(Dispatchers.IO) { backup.discard(it) } }
        _ui.update { it.copy(restorePreview = null) }
    }
    override fun onCleared() {
        super.onCleared()
        (getApplication<Application>() as DailyLifeApplication).backgroundScope.launch {
            operation?.join()
            runCatching { backup.discardAll() }
        }
    }
    fun acceptShared(text: String?, uris: List<Uri>) = work {
        flush()
        val type = _ui.value.snapshot.types.firstOrNull { it.kind == if (uris.isEmpty()) "note" else "file" } ?: return@work
        val draft = repository.newDraft(type.id)
        val data = EditorData.fromJson(draft.payload).copy(body = text.orEmpty())
        repository.saveDraft(draft.id, data)
        savedState["draft"] = draft.id
        _ui.update { it.copy(editor = EditorSession(draft.id, data), detailId = null, editorError = null) }
        uris.take(50).forEach { repository.importAttachment(it, draft.id) }
        message("分享内容已放入草稿，请检查后保存")
    }
}
