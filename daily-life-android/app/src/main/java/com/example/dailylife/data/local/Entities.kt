package com.example.dailylife.data.local

import androidx.room.*
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "profiles")
data class LocalProfile(@PrimaryKey val id: String, val name: String = "我")

@Entity(tableName = "record_types", indices = [Index(value = ["ownerId", "normalizedName"], unique = true)])
data class RecordType(
    @PrimaryKey val id: String, val ownerId: String, val name: String, val normalizedName: String,
    val kind: String = "note", val subtype: String = "plain", val icon: String = "note",
    val sortOrder: Int = 0
)
@Entity(tableName = "folders", indices = [Index(value = ["ownerId", "normalizedName"], unique = true)])
data class Folder(@PrimaryKey val id: String, val ownerId: String, val name: String,
    val normalizedName: String, val sortOrder: Int = 0)
@Entity(tableName = "tags", indices = [Index(value = ["ownerId", "normalizedName"], unique = true)])
data class Tag(@PrimaryKey val id: String, val ownerId: String, val name: String,
    val normalizedName: String, val color: Int = 0, val sortOrder: Int = 0)

@Entity(tableName = "records", indices = [Index("ownerId"), Index("typeId"), Index("folderId"), Index("deletedAt"), Index("updatedAt")],
    foreignKeys = [
        ForeignKey(entity = LocalProfile::class, parentColumns = ["id"], childColumns = ["ownerId"]),
        ForeignKey(entity = RecordType::class, parentColumns = ["id"], childColumns = ["typeId"]),
        ForeignKey(entity = Folder::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)
    ])
data class Record(
    @PrimaryKey val id: String, val ownerId: String, val kind: String, val typeId: String,
    val title: String = "", val folderId: String? = null, val organizationStatus: String = "inbox",
    val pinOrder: Long? = null, val archivedAt: Long? = null, val deletedAt: Long? = null,
    val createdAt: Long, val updatedAt: Long, val revision: Long = 1,
    @ColumnInfo(defaultValue = "'{}'") val presentation: String = "{}"
)
@Entity(tableName = "notes", foreignKeys = [ForeignKey(entity = Record::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class Note(@PrimaryKey val recordId: String, val body: String,
    val bodyFormat: String = "markdown", val noteSubtype: String = "plain")
@Entity(tableName = "diaries", indices = [Index(value = ["ownerId", "activeDate"], unique = true)],
    foreignKeys = [ForeignKey(entity = Record::class, parentColumns = ["id"],
        childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class Diary(@PrimaryKey val recordId: String, val ownerId: String, val diaryDate: String,
    val body: String, val mood: String = "", val activeDate: String? = diaryDate)
@Entity(tableName = "bookmarks", indices = [Index("url")], foreignKeys = [ForeignKey(entity = Record::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class Bookmark(@PrimaryKey val recordId: String, val url: String, val note: String,
    val readAt: Long? = null)
@Entity(tableName = "file_records", foreignKeys = [ForeignKey(entity = Record::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class FileRecord(@PrimaryKey val recordId: String, val attachmentId: String, val note: String)
@Entity(tableName = "card_fields", indices = [Index("recordId")], foreignKeys = [ForeignKey(entity = Record::class,
    parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)])
data class CardField(@PrimaryKey val id: String, val recordId: String, val name: String,
    val value: String, val valueType: String = "text", val isPrivate: Boolean = false, val sortOrder: Int = 0)
@Entity(tableName = "entity_tags", primaryKeys = ["entityType", "entityId", "tagId"],
    indices = [Index("tagId")], foreignKeys = [ForeignKey(entity = Tag::class, parentColumns = ["id"],
        childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)])
data class EntityTag(val entityType: String = "record", val entityId: String, val tagId: String)
@Entity(tableName = "attachments", indices = [Index("sha256")])
data class Attachment(@PrimaryKey val id: String, val ownerId: String, val originalName: String,
    val displayName: String, val mime: String, val size: Long, val sha256: String,
    val relativePath: String, val createdAt: Long, val status: String = "available")
@Entity(tableName = "attachment_links", primaryKeys = ["entityType", "entityId", "attachmentId"],
    indices = [Index("attachmentId")], foreignKeys = [ForeignKey(entity = Attachment::class,
        parentColumns = ["id"], childColumns = ["attachmentId"], onDelete = ForeignKey.CASCADE)])
data class AttachmentLink(val entityType: String, val entityId: String, val attachmentId: String,
    val role: String = "attachment", val sortOrder: Int = 0)
@Entity(tableName = "drafts", indices = [Index("targetId")])
data class Draft(@PrimaryKey val id: String, val ownerId: String, val targetId: String?,
    val schemaVersion: Int = 1, val payload: String, val savedAt: Long)
@Entity(tableName = "templates")
data class ContentTemplate(@PrimaryKey val id: String, val ownerId: String, val name: String,
    val typeId: String, val schemaVersion: Int = 1, val body: String = "", val fieldsJson: String = "[]")

data class RecordBundle(
    @Embedded val record: Record,
    @Relation(parentColumn = "id", entityColumn = "recordId") val note: Note?,
    @Relation(parentColumn = "id", entityColumn = "recordId") val diary: Diary?,
    @Relation(parentColumn = "id", entityColumn = "recordId") val bookmark: Bookmark?,
    @Relation(parentColumn = "id", entityColumn = "recordId") val file: FileRecord?,
    @Relation(parentColumn = "id", entityColumn = "recordId") val fields: List<CardField>,
    @Relation(parentColumn = "id", entityColumn = "entityId", entity = EntityTag::class) val tagLinks: List<EntityTag>
) {
    val body: String get() = note?.body ?: diary?.body ?: bookmark?.note ?: file?.note ?: ""
    fun displayTitle(attachmentName: String? = null): String = record.title.ifBlank {
        body.lineSequence().firstOrNull { it.isNotBlank() }?.replace(Regex("^[#>*\\-\\s]+"), "")?.take(80)?.ifBlank { null }
            ?: attachmentName ?: bookmark?.url ?: "未命名资料"
    }
}
