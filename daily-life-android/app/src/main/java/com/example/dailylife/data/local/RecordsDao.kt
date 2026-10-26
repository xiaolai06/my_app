package com.example.dailylife.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordsDao {
    @Query("SELECT * FROM profiles LIMIT 1") suspend fun profile(): LocalProfile?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProfile(value: LocalProfile)
    @Transaction @Query("SELECT * FROM records ORDER BY pinOrder IS NULL, pinOrder DESC, updatedAt DESC")
    fun observeRecords(): Flow<List<RecordBundle>>
    @Transaction @Query("SELECT * FROM records WHERE id=:id") suspend fun record(id: String): RecordBundle?
    @Transaction @Query("SELECT * FROM records") suspend fun allRecords(): List<RecordBundle>
    @Upsert suspend fun putRecord(value: Record)
    @Upsert suspend fun putNote(value: Note)
    @Upsert suspend fun putDiary(value: Diary)
    @Upsert suspend fun putBookmark(value: Bookmark)
    @Upsert suspend fun putFile(value: FileRecord)
    @Upsert suspend fun putFields(values: List<CardField>)
    @Query("SELECT * FROM card_fields WHERE id=:id") suspend fun field(id: String): CardField?
    @Query("DELETE FROM card_fields WHERE recordId=:id") suspend fun clearFields(id: String)
    @Query("SELECT * FROM diaries WHERE ownerId=:owner AND activeDate=:date AND recordId!=:exceptId")
    suspend fun diaryConflict(owner: String, date: String, exceptId: String): Diary?
    @Query("SELECT * FROM bookmarks WHERE url=:url AND recordId!=:exceptId")
    suspend fun duplicateBookmarks(url: String, exceptId: String): List<Bookmark>
    @Query("DELETE FROM records WHERE id=:id") suspend fun deleteRecord(id: String)
    @Query("SELECT * FROM record_types ORDER BY sortOrder,name") fun observeTypes(): Flow<List<RecordType>>
    @Query("SELECT * FROM record_types ORDER BY sortOrder,name") suspend fun types(): List<RecordType>
    @Query("SELECT * FROM record_types WHERE id=:id") suspend fun type(id: String): RecordType?
    @Upsert suspend fun putType(value: RecordType)
    @Query("SELECT count(*) FROM records WHERE typeId=:id") suspend fun typeUses(id: String): Int
    @Query("DELETE FROM record_types WHERE id=:id") suspend fun deleteType(id: String)
    @Query("SELECT * FROM folders ORDER BY sortOrder,name") fun observeFolders(): Flow<List<Folder>>
    @Query("SELECT * FROM folders ORDER BY sortOrder,name") suspend fun folders(): List<Folder>
    @Upsert suspend fun putFolder(value: Folder)
    @Query("DELETE FROM folders WHERE id=:id") suspend fun deleteFolder(id: String)
    @Query("SELECT * FROM tags ORDER BY sortOrder,name") fun observeTags(): Flow<List<Tag>>
    @Query("SELECT * FROM tags ORDER BY sortOrder,name") suspend fun tags(): List<Tag>
    @Upsert suspend fun putTag(value: Tag)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putTags(values: List<EntityTag>)
    @Query("DELETE FROM entity_tags WHERE entityType=:type AND entityId=:id")
    suspend fun clearTags(id: String, type: String = "record")
    @Query("SELECT * FROM entity_tags WHERE entityType=:type AND entityId=:id")
    suspend fun tagsForEntity(type: String, id: String): List<EntityTag>
    @Query("SELECT * FROM entity_tags WHERE tagId=:id") suspend fun tagLinks(id: String): List<EntityTag>
    @Query("DELETE FROM tags WHERE id=:id") suspend fun deleteTag(id: String)
    @Query("SELECT * FROM attachments") fun observeAttachments(): Flow<List<Attachment>>
    @Query("SELECT * FROM attachments") suspend fun attachments(): List<Attachment>
    @Query("SELECT * FROM attachments WHERE id=:id") suspend fun attachment(id: String): Attachment?
    @Upsert suspend fun putAttachment(value: Attachment)
    @Query("DELETE FROM attachments WHERE id=:id") suspend fun deleteAttachment(id: String)
    @Query("SELECT * FROM attachment_links") fun observeAttachmentLinks(): Flow<List<AttachmentLink>>
    @Query("SELECT * FROM attachment_links") suspend fun links(): List<AttachmentLink>
    @Query("SELECT * FROM attachment_links WHERE entityType=:type AND entityId=:id ORDER BY sortOrder")
    suspend fun linksFor(type: String, id: String): List<AttachmentLink>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putLinks(values: List<AttachmentLink>)
    @Query("DELETE FROM attachment_links WHERE entityType=:type AND entityId=:id") suspend fun clearLinks(type: String, id: String)
    @Query("DELETE FROM attachment_links WHERE entityType=:type AND entityId=:id AND attachmentId=:attachment")
    suspend fun detach(type: String, id: String, attachment: String)
    @Query("SELECT count(*) FROM attachment_links WHERE attachmentId=:id") suspend fun attachmentUses(id: String): Int
    @Query("SELECT * FROM drafts ORDER BY savedAt DESC") fun observeDrafts(): Flow<List<Draft>>
    @Query("SELECT * FROM drafts ORDER BY savedAt DESC") suspend fun drafts(): List<Draft>
    @Query("SELECT * FROM drafts WHERE id=:id") suspend fun draft(id: String): Draft?
    @Upsert suspend fun putDraft(value: Draft)
    @Query("DELETE FROM drafts WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("SELECT * FROM templates ORDER BY name") fun observeTemplates(): Flow<List<ContentTemplate>>
    @Query("SELECT * FROM templates") suspend fun templates(): List<ContentTemplate>
    @Upsert suspend fun putTemplate(value: ContentTemplate)
    @Query("DELETE FROM templates WHERE id=:id") suspend fun deleteTemplate(id: String)
}
