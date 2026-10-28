package com.example.dailylife

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dailylife.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LifestyleMigrationTest {
    @Test fun versionThreeMigratesWithoutChangingRecordOrReminderHistory()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val folder=File(repositoryTestRoot(context),"migration34-"+newId()).apply {mkdirs()};val file=File(folder,"old.db")
        val schema=JSONObject(RepositoryTestSchema.read(3)).getJSONObject("database")
        val owner=newId();val type=newId();val record=newId();val task=newId()
        SQLiteDatabase.openOrCreateDatabase(file,null).use {sql->
            val entities=schema.getJSONArray("entities");val token="$"+"{TABLE_NAME}"
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);sql.execSQL(e.getString("createSql").replace(token,e.getString("tableName")))
                val indexes=e.optJSONArray("indices");if(indexes!=null) for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace(token,e.getString("tableName")))
            }
            sql.execSQL("CREATE TABLE room_master_table(id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
            sql.execSQL("INSERT INTO profiles VALUES(?,?)",arrayOf(owner,"迁移前"))
            sql.execSQL("INSERT INTO record_types VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(type,owner,"随记","随记","note","plain","note",0))
            sql.execSQL("INSERT INTO records(id,ownerId,kind,typeId,title,organizationStatus,createdAt,updatedAt,revision) VALUES(?,?,?,?,?,?,?,?,?)",arrayOf<Any>(record,owner,"note",type,"原有资料","inbox",1000L,2000L,5L))
            sql.execSQL("INSERT INTO notes VALUES(?,?,?,?)",arrayOf(record,"不会丢失的正文","markdown","plain"))
            sql.execSQL("INSERT INTO plan_reminders(id,sourceType,sourceId,instanceKey,title,triggerAt,state,postedAt,readAt) VALUES(?,?,?,?,?,?,?,?,?)",arrayOf<Any>(1L,"task",task,"single","原有提醒",1000L,"posted",1100L,1200L))
            sql.version=3
        }
        val db=Room.databaseBuilder(context,DailyLifeDatabase::class.java,file.absolutePath).build()
        try {
            val b=db.records().record(record)!!;assertEquals("不会丢失的正文",b.body);assertEquals(5L,b.record.revision);assertEquals("{}",b.record.presentation)
            val reminder=db.plans().reminders().single();assertEquals(1100L,reminder.postedAt);assertEquals(1200L,reminder.readAt);assertNull(reminder.deferredUntil)
            assertEquals(4,db.openHelper.writableDatabase.version)
        } finally {db.close();folder.deleteRecursively()}
    }
}
