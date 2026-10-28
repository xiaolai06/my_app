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
import com.example.dailylife.feature.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LifestyleRepositoryTest {
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root:File
    private lateinit var context:Context
    private lateinit var db:DailyLifeDatabase
    private lateinit var records:RecordsRepository
    private lateinit var tools:ToolsRepository
    @Before fun setup()=runBlocking {
        root=File(repositoryTestRoot(base),"lifestyle-"+newId()).apply {mkdirs()}
        context=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        db=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"source.db").absolutePath).build()
        records=RecordsRepository(db,AttachmentStore(context));records.initialize()
        tools=ToolsRepository(db,records)
    }
    @After fun close() {db.close();root.deleteRecursively()}
    private suspend fun save(f:ToolForm) {tools.prepare(f);tools.save(f)}
    private fun clothing()=ToolForm("item").set("title","蓝色衬衫").set("wardrobe","true").set("clothingType","top").set("warmth","layer").set("color","蓝").set("favorite","true")

    @Test fun outfitReferencesOriginalClothesWearIsIdempotentAndDeleteIsProtected()=runBlocking {
        val shirt=clothing();save(shirt)
        val outfit=ToolForm("outfit").set("title","周一通勤").set("items",shirt.id).set("occasion","上班");save(outfit)
        assertEquals(2,tools.dao.entries().size);assertEquals(shirt.id,tools.dao.links().single().entityId)
        assertEquals(shirt.id,tools.form(outfit.id).text("items"))
        tools.wear(outfit.id,LocalDate.now().toString());tools.wear(outfit.id,LocalDate.now().toString())
        assertEquals(1,tools.dao.logs().count {it.kind=="wear"})
        tools.manage(shirt.id,"trash");assertTrue(runCatching {tools.deleteForever(shirt.id)}.isFailure)
        val changed=tools.form(outfit.id).set("title","更改穿搭")
        tools.prepare(changed);assertTrue(runCatching {tools.save(changed)}.isFailure)
        assertEquals("周一通勤",tools.dao.entries().first {it.id==outfit.id}.title)
        assertTrue(tools.dao.drafts().any {it.id==outfit.id})
        tools.save(changed.set("items",""));tools.deleteForever(shirt.id)
        assertEquals(1,tools.dao.entries().size)
    }
    @Test fun clothingAndOutfitBackupRestoreIntoAnotherDatabaseKeepsReferencesAndFavorites()=runBlocking {
        val shirt=clothing();save(shirt)
        val outfit=ToolForm("outfit").set("title","秋日搭配").set("items",shirt.id);save(outfit)
        tools.wear(outfit.id,LocalDate.now().toString())
        val backup=ToolsBackup(context,tools);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray()
        val target=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"target.db").absolutePath).build()
        try {
            val targetRecords=RecordsRepository(target,AttachmentStore(context));targetRecords.initialize()
            val next=ToolsRepository(target,targetRecords);val restore=ToolsBackup(context,next)
            restore.restore(restore.prepare(ByteArrayInputStream(bytes)))
            assertTrue(next.dao.entries().first {it.id==shirt.id}.isFavorite())
            assertEquals(shirt.id,next.dao.links().single().entityId)
            assertEquals("wear",next.dao.logs().single().kind)
            assertEquals(shirt.id,next.form(outfit.id).text("items"))
        } finally {target.close()}
    }
    @Test fun diaryPresentationRoundTripsThroughDraftCommitAndBackup()=runBlocking {
        val type=records.dao.types().first {it.kind=="diary"}
        val draft=records.newDraft(type.id)
        val style=JournalStyle("dots","rose","film","♡","一页好心情")
        val data=EditorData.fromJson(draft.payload).copy(body="保留原本的文字",presentation=style.json())
        records.saveDraft(draft.id,data);records.commit(draft.id,data)
        val original=records.dao.record(data.recordId)!!
        assertEquals(style,JournalStyle.parse(original.record.presentation));assertEquals(data.body,original.body)
        val reopened=records.newDraft(type.id,data.recordId)
        assertEquals(style,JournalStyle.parse(EditorData.fromJson(reopened.payload).presentation))
        records.discardDraft(reopened.id)
        val backup=RecordsBackup(context,records)
        val uri=android.net.Uri.fromFile(File(root,"journal-export.zip"));backup.export(uri)
        val target=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"journal.db").absolutePath).build()
        try {
            val next=RecordsRepository(target,AttachmentStore(context));next.initialize()
            val restore=RecordsBackup(context,next);restore.restore(restore.prepare(uri))
            assertEquals(style,JournalStyle.parse(next.dao.record(data.recordId)!!.record.presentation))
        } finally {target.close()}
    }
    @Test fun malformedBackupCannotDuplicateWearDaysOrUseAnOutfitAsClothing()=runBlocking {
        val shirt=clothing();save(shirt)
        val outfit=ToolForm("outfit").set("title","散步").set("items",shirt.id);save(outfit)
        tools.wear(outfit.id,LocalDate.now().toString())
        val backup=ToolsBackup(context,tools);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray()
        val preview=backup.prepare(ByteArrayInputStream(bytes))
        val logs=preview.manifest.getJSONObject("data").getJSONArray("tool_logs")
        logs.put(org.json.JSONObject(logs.getJSONObject(0).toString()).put("id",newId()))
        assertTrue(runCatching {backup.restore(preview)}.isFailure)
        val again=backup.prepare(ByteArrayInputStream(bytes))
        again.manifest.getJSONObject("data").getJSONArray("tool_links").getJSONObject(0).put("entityId",outfit.id)
        assertTrue(runCatching {backup.restore(again)}.isFailure)
        assertEquals(1,tools.dao.logs().count {it.kind=="wear"})
    }
    @Test fun recommendationsExplainTheirRulesAndNeverSuggestDirtyOrRemovedClothes()=runBlocking {
        val shirt=clothing();save(shirt)
        val dirty=clothing().set("title","待洗外套").set("laundry","wash").set("favorite","false");save(dirty)
        var snapshot=tools.load()
        assertTrue(snapshot.suggestions(LocalDate.now(),20.0,80).any {it.id==shirt.id && it.reason.contains("20")})
        assertFalse(snapshot.suggestions(LocalDate.now(),20.0,80).any {it.id==dirty.id})
        tools.manage(shirt.id,"trash");snapshot=tools.load()
        assertFalse(snapshot.suggestions(LocalDate.now(),20.0,80).any {it.id==shirt.id})
    }
    @Test fun itemExpiryCareAndWarrantyCreateDifferentStableReminderInstances()=runBlocking {
        val day=LocalDate.now().plusDays(10).toString()
        save(clothing().set("warranty",day).set("expiry",day).set("careDate",day).set("reminderOn","true").set("reminderDays","2").set("reminderTime","08:30"))
        val reminders=tools.load().reminders()
        assertEquals(setOf("warranty","expiry","care"),reminders.map {it.instanceKey}.toSet())
        assertEquals(1,reminders.map {it.triggerAt}.distinct().size)
    }
}
