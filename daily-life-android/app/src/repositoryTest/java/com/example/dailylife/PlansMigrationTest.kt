package com.example.dailylife
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class PlansMigrationTest {
    @Test fun realVersionOneSchemaMigratesWithoutLosingChineseRecord()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File(repositoryTestRoot(context),"migration-"+newId()+".db")
        val schema=JSONObject(RepositoryTestSchema.read(1)).getJSONObject("database")
        val owner=newId();val type=newId();val record=newId()
        SQLiteDatabase.openOrCreateDatabase(file,null).use {sql->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val token="$"+"{TABLE_NAME}"
                sql.execSQL(e.getString("createSql").replace(token,e.getString("tableName")))
                val indexes=e.optJSONArray("indices")
                if(indexes!=null) for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace(token,e.getString("tableName")))
            }
            sql.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            sql.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
            sql.execSQL("INSERT INTO profiles VALUES(?,?)",arrayOf(owner,"本机"))
            sql.execSQL("INSERT INTO record_types VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(type,owner,"随记","随记","note","plain","note",0))
            val row=schema.getJSONArray("entities").let {a->(0 until a.length()).map {a.getJSONObject(it)}.first {it.getString("tableName")=="records"}}
            val fields=row.getJSONArray("fields");val columns=(0 until fields.length()).map {fields.getJSONObject(it).getString("columnName")}
            val values=columns.map<String,Any?> {k->when(k) {
                "id"->record;"ownerId"->owner;"kind"->"note";"typeId"->type;"title"->"升级前的中文";"organizationStatus"->"inbox"
                "revision"->1L;"createdAt","updatedAt"->1000L;else->null
            }}.toTypedArray()
            sql.execSQL("INSERT INTO records ("+columns.joinToString(",")+") VALUES ("+columns.joinToString(",") {"?"}+")",values)
            sql.execSQL("INSERT INTO notes(recordId,body,bodyFormat,noteSubtype) VALUES(?,?,?,?)",arrayOf(record,"## 原文\n\n升级后完整保留。","markdown","plain"))
            sql.version=1
        }
        val copies=File(repositoryTestRoot(context),"migration-copy-"+newId())
        val safety=object:ContextWrapper(context) {
            override fun getDatabasePath(name:String)=file
            override fun getNoBackupFilesDir()=copies.apply {mkdirs()}
        }
        DatabaseSafety.beforeOpen(safety,"old.db")
        val copy=File(copies,"pre-upgrade-v1").listFiles()!!.single()
        SQLiteDatabase.openDatabase(copy.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use {sqlite->
            assertEquals(1,sqlite.version)
            sqlite.rawQuery("SELECT body FROM notes WHERE recordId=?",arrayOf(record)).use {c->assertTrue(c.moveToFirst());assertEquals("## 原文\n\n升级后完整保留。",c.getString(0))}
        }
        val db=Room.databaseBuilder(context,DailyLifeDatabase::class.java,file.absolutePath).build()
        try {
            assertEquals("## 原文\n\n升级后完整保留。",db.records().record(record)!!.body)
            assertEquals("升级前的中文",db.records().record(record)!!.record.title)
            assertTrue(db.plans().tasks().isEmpty())
            assertEquals(4,db.openHelper.writableDatabase.version)
        } finally {db.close();file.delete();File(file.absolutePath+"-wal").delete();File(file.absolutePath+"-shm").delete();copies.deleteRecursively()}
    }
}
