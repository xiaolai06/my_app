package com.example.dailylife

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.records.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class RecordsRepositoryTest {
    @Test fun rollbackCleanupNeverDeletesOriginalWhoseMetadataCommitted()=runBlocking {
        val committed=repo.files.importStream("已提交原件".byteInputStream(),repo.owner(),"kept.txt","text/plain")
        repo.dao.putAttachment(committed)
        val orphan=File(repo.files.root,newId()+".bin").apply {writeText("未提交复制")}
        repo.cleanupUncommittedOriginals(listOf(repo.files.file(committed),orphan))
        assertTrue(repo.files.file(committed).isFile);assertFalse(orphan.exists())
        assertNotNull(repo.dao.attachment(committed.id))
    }
    @Test fun closedDraftFailsExplicitlyInsteadOfReportingAutosaved()=runBlocking {
        val d=draft();val data=EditorData.fromJson(d.payload).copy(body="正式内容")
        repo.commit(d.id,data)
        assertTrue(runCatching {repo.saveDraft(d.id,data.copy(body="已关闭后输入"))}.isFailure)
        assertEquals("正式内容",repo.dao.record(data.recordId)!!.body)
    }
    @Test fun initializationCanRetryAndPreOpenWorkRunsOffMainThread()=runBlocking {
        val next=database("retry")
        try {
            var attempts=0
            val candidate=RecordsRepository(next,AttachmentStore(contextAt("retry"))) {
                assertNotEquals(android.os.Looper.getMainLooper(),android.os.Looper.myLooper())
                attempts++
                if(attempts==1) error("模拟首次文件读取失败")
            }
            assertTrue(runCatching {candidate.initialize()}.isFailure)
            candidate.initialize();val owner=candidate.owner();candidate.initialize()
            assertEquals(owner,candidate.owner());assertEquals(2,attempts);assertEquals(9,candidate.dao.types().size)
        } finally {next.close()}
    }
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private lateinit var db: DailyLifeDatabase
    private lateinit var repo: RecordsRepository
    private fun contextAt(name: String): Context = object : ContextWrapper(base) {
        override fun getFilesDir() = File(root, name + "/files").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, name + "/nobackup").apply { mkdirs() }
        override fun getCacheDir() = File(root, name + "/cache").apply { mkdirs() }
    }
    private fun database(name: String) = Room.databaseBuilder(base, DailyLifeDatabase::class.java, File(root, name + ".db").absolutePath).build()
    @Before fun setup() = runBlocking {
        root = File(repositoryTestRoot(base), "records-test-" + newId()).apply { mkdirs() }
        db = database("primary"); repo = RecordsRepository(db, AttachmentStore(contextAt("primary"))); repo.initialize()
    }
    @After fun cleanup() { db.close(); root.deleteRecursively() }
    private suspend fun draft(kind: String = "note", subtype: String = "plain") =
        repo.newDraft(repo.dao.types().first { it.kind == kind && it.subtype == subtype }.id)
    private suspend fun note(body: String): String { val d = draft(); return repo.commit(d.id, EditorData.fromJson(d.payload).copy(body = body)) }
    @Test fun projectCreationCommitsOriginalAndLinkTogetherAndRetainsDraftOnInvalidSource()=runBlocking {
        val project=ToolEntry(ownerId=repo.owner(),kind="project",title="周末旅行")
        db.tools().put(project)
        val d=draft();val record=EditorData.fromJson(d.payload).copy(body="车票与行程",projectId=project.id)
        repo.saveDraft(d.id,record);repo.commit(d.id,record)
        assertEquals(record.recordId,db.tools().links().single().entityId)
        assertEquals("record",db.tools().links().single().entityType)
        val plans=PlansRepository(repo,AndroidFocusClock(base));plans.initialize()
        val task=com.example.dailylife.feature.plans.PlanForm("task").set("title","整理行李").set("project",project.id)
        plans.prepareDraft(task);plans.save(task)
        assertEquals(2,db.tools().links().size);assertTrue(db.tools().links().any {it.entityType=="task"&&it.entityId==task.id})
        db.tools().put(project.copy(deletedAt=1))
        val failed=draft();val input=EditorData.fromJson(failed.payload).copy(body="输入不能丢失",projectId=project.id)
        repo.saveDraft(failed.id,input)
        assertTrue(runCatching {repo.commit(failed.id,input)}.isFailure)
        assertNull(repo.dao.record(input.recordId));assertEquals("输入不能丢失",EditorData.fromJson(repo.dao.draft(failed.id)!!.payload).body)
        val blocked=com.example.dailylife.feature.plans.PlanForm("task").set("title","暂存任务").set("project",project.id)
        plans.prepareDraft(blocked);assertTrue(runCatching {plans.save(blocked)}.isFailure)
        assertTrue(plans.dao.tasks().none {it.id==blocked.id});assertNotNull(plans.dao.drafts().find {it.id==blocked.id})
        assertEquals(2,db.tools().links().size)
        val saved=repo.dao.record(record.recordId)!!.record;repo.dao.putRecord(saved.copy(deletedAt=1))
        assertTrue(runCatching {repo.permanentlyDelete(setOf(saved.id))}.isFailure)
        assertNotNull(repo.dao.record(saved.id))
    }
    private fun expectFailure(block: suspend () -> Unit) = runBlocking {
        assertTrue("Expected a guarded validation failure", runCatching { block() }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun chineseMarkdownPersistsAcrossDatabaseReopen() = runBlocking {
        val text = ("## 今天\n\n中文 **加粗** 和 emoji ☀️\n\n| 项目 | 数量 |\n|---|---|\n| 苹果 | 2 |\n\n" +
            "中文长文保存与再次读取。\n".repeat(5000)).take(50_000)
        assertEquals(50_000, text.length)
        val id = note(text)
        db.close(); db = database("primary"); repo = RecordsRepository(db, AttachmentStore(contextAt("primary")))
        assertEquals(text, repo.dao.record(id)!!.body)
        assertEquals("", repo.dao.record(id)!!.record.title)
        assertEquals("今天", repo.dao.record(id)!!.displayTitle())
    }
    @Test fun draftsNeverModifyOriginalUntilCommitAndDetectConcurrentChanges() = runBlocking {
        val id = note("原始内容")
        val edit = repo.newDraft(repo.dao.record(id)!!.record.typeId, id)
        val data = EditorData.fromJson(edit.payload).copy(body = "草稿改动")
        repo.saveDraft(edit.id, data)
        assertEquals("原始内容", repo.dao.record(id)!!.body)
        assertEquals(edit.id, repo.newDraft(data.typeId, id).id)
        repo.mutate(setOf(id), "pin")
        expectFailure { repo.commit(edit.id, data) }
        assertNotNull(repo.dao.draft(edit.id))
        assertEquals("原始内容", repo.dao.record(id)!!.body)
    }
    @Test fun diaryUniquenessTrashAndRestoreConflictAreAtomic() = runBlocking {
        val a = draft("diary"); val value = EditorData.fromJson(a.payload).copy(body = "第一篇", diaryDate = "2026-10-24")
        val id = repo.commit(a.id, value)
        val b = draft("diary"); val second = EditorData.fromJson(b.payload).copy(body = "第二篇", diaryDate = value.diaryDate)
        expectFailure { repo.commit(b.id, second) }
        repo.mutate(setOf(id), "trash")
        val replacement = repo.commit(b.id, second)
        expectFailure { repo.mutate(setOf(id), "restore") }
        assertNotNull(repo.dao.record(id)!!.record.deletedAt)
        assertNull(repo.dao.record(replacement)!!.record.deletedAt)
    }
    @Test fun sharedAttachmentsSurviveDraftRemovalAndDeletingOneRecord() = runBlocking {
        val d = draft()
        val attachment = repo.files.importStream("保留原件".byteInputStream(), repo.owner(), "照片.txt", "text/plain")
        repo.dao.putAttachment(attachment); repo.attachExisting(d.id, attachment.id)
        val id = repo.commit(d.id, EditorData.fromJson(d.payload))
        val copy = repo.duplicate(id)
        val edit = repo.newDraft(repo.dao.record(id)!!.record.typeId, id)
        repo.removeAttachment(edit.id, attachment.id); repo.discardDraft(edit.id)
        assertTrue(repo.files.file(attachment).exists())
        repo.mutate(setOf(id), "trash"); repo.permanentlyDelete(setOf(id))
        assertTrue(repo.files.file(attachment).exists())
        assertEquals(1, repo.dao.attachmentUses(attachment.id))
        repo.mutate(setOf(copy), "trash"); repo.permanentlyDelete(setOf(copy))
        assertFalse(repo.files.file(attachment).exists())
        assertNull(repo.dao.attachment(attachment.id))
    }
    @Test fun removedAttachmentIsCollectedOnlyAfterSuccessfulEditCommit() = runBlocking {
        val d = draft(); val a = repo.files.importStream("附件".byteInputStream(), repo.owner(), "资料.txt", "text/plain")
        repo.dao.putAttachment(a); repo.attachExisting(d.id, a.id)
        val id = repo.commit(d.id, EditorData.fromJson(d.payload).copy(body = "有正文"))
        val edit = repo.newDraft(repo.dao.record(id)!!.record.typeId, id)
        repo.removeAttachment(edit.id, a.id); assertTrue(repo.files.file(a).exists())
        repo.commit(edit.id, EditorData.fromJson(edit.payload).copy(body = "只保留正文"))
        assertFalse(repo.files.file(a).exists())
    }
    @Test fun tagMergeDeduplicatesAndDoesNotChangeInboxStatus() = runBlocking {
        repo.putOrganization("tag", "生活"); repo.putOrganization("tag", "读书")
        val tags = repo.dao.tags(); val id = note("内容")
        repo.mutate(setOf(id), "tags", tagIds = tags.map { it.id }.toSet())
        repo.deleteOrganization("tag", tags[0].id, tags[1].id)
        val b = repo.dao.record(id)!!
        assertEquals(1, b.tagLinks.size); assertEquals(tags[1].id, b.tagLinks.single().tagId)
        assertEquals("inbox", b.record.organizationStatus)
        expectFailure { repo.putOrganization("tag", "  " + tags[1].name + "  ") }
    }
    @Test fun bookmarkDuplicateIsExplicitAndQueryIsPreserved() = runBlocking {
        val a = draft("bookmark"); val data = EditorData.fromJson(a.payload).copy(url = "example.com/?id=1&view=full")
        repo.commit(a.id, data)
        val b = draft("bookmark"); val second = EditorData.fromJson(b.payload).copy(url = data.url)
        expectFailure { repo.commit(b.id, second) }
        val id = repo.commit(b.id, second.copy(allowDuplicateUrl = true))
        assertEquals("https://example.com/?id=1&view=full", repo.dao.record(id)!!.bookmark!!.url)
    }
    @Test fun templateCreatesIndependentFieldIdsAndPrivateFieldsStayOutOfSearchAndExport() = runBlocking {
        val a = draft("note", "card")
        val data = EditorData.fromJson(a.payload).copy(title = "我的信息卡", fields = listOf(
            CardField(newId(), "", "公开字段", "图书证"),
            CardField(newId(), "", "证件备注", "secret-unique-938", isPrivate = true)))
        val id = repo.commit(a.id, data)
        repo.saveTemplate("图书证模板", data)
        val template = repo.dao.templates().single()
        val next = repo.newDraft(data.typeId, template = template)
        val nextData = EditorData.fromJson(next.payload)
        assertTrue(nextData.fields.all { it.value.isEmpty() })
        assertTrue(nextData.fields.none { f -> data.fields.any { it.id == f.id } })
        val b = repo.dao.record(id)!!
        val snapshot = RecordsSnapshot(records = listOf(b))
        assertTrue(snapshot.filtered(RecordFilter(query = "secret-unique")).isEmpty())
        assertFalse(b.markdown(snapshot).contains("secret-unique"))
        assertEquals(2, repo.dao.record(id)!!.fields.size)
    }
    private fun archiveUri(name: String): android.net.Uri {
        val file = File(root, "exports/" + name + "-" + newId() + ".zip").apply { parentFile!!.mkdirs() }
        return android.net.Uri.fromFile(file)
    }
    @Test fun scopedBackupRestoresRecordsAndFilesAndSkipsExistingIds() = runBlocking {
        val a = draft(); val data = EditorData.fromJson(a.payload).copy(body = "备份中文")
        val attachment = repo.files.importStream("原始字节".byteInputStream(), repo.owner(), "原件.txt", "text/plain")
        repo.dao.putAttachment(attachment); repo.attachExisting(a.id, attachment.id)
        val id = repo.commit(a.id, data)
        val uri = archiveUri("roundtrip")
        RecordsBackup(base, repo).export(uri)
        val otherDb = database("restored")
        try {
            val other = RecordsRepository(otherDb, AttachmentStore(contextAt("restored"))); other.initialize()
            val backup = RecordsBackup(base, other)
            val preview = backup.prepare(uri); assertEquals(1, preview.records)
            assertEquals(1 to 0, backup.restore(preview))
            assertEquals("备份中文", other.dao.record(id)!!.body)
            assertEquals("原始字节", other.files.file(other.dao.attachment(attachment.id)!!).readText())
            assertEquals(0 to 1, backup.restore(backup.prepare(uri)))
        } finally { otherDb.close() }
    }
    @Test fun unsafeArchivePathsAreRejectedBeforeAnyDatabaseWrite() = runBlocking {
        val uri = archiveUri("invalid")
        base.contentResolver.openOutputStream(uri)!!.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.txt")); zip.write("bad".toByteArray()); zip.closeEntry()
            }
        }
        val error = runCatching { RecordsBackup(base, repo).prepare(uri) }.exceptionOrNull()
        assertTrue("Unsafe ZIP path was not rejected: " + error, error is IllegalArgumentException || error is java.util.zip.ZipException)
        assertTrue(repo.dao.allRecords().isEmpty())
    }
}
