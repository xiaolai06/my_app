package com.example.dailylife
import android.content.*
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dailylife.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class LedgerMigrationTest {
    @Test fun actualSchemaTwoUpgradesWithChineseRecordAndTaskIntact()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<Context>();val folder=File(repositoryTestRoot(ctx),"migration23-"+newId()).apply {mkdirs()};val file=File(folder,"old.db")
        val schema=JSONObject(RepositoryTestSchema.read(2)).getJSONObject("database")
        val owner=newId();val type=newId();val record=newId();val task=newId()
        SQLiteDatabase.openOrCreateDatabase(file,null).use {sql->val list=schema.getJSONArray("entities");val token="$"+"{TABLE_NAME}"
            for(i in 0 until list.length()){val e=list.getJSONObject(i);sql.execSQL(e.getString("createSql").replace(token,e.getString("tableName")));val indexes=e.optJSONArray("indices");if(indexes!=null)for(j in 0 until indexes.length())sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace(token,e.getString("tableName")))}
            sql.execSQL("CREATE TABLE room_master_table(id INTEGER PRIMARY KEY,identity_hash TEXT)");sql.execSQL("INSERT INTO room_master_table VALUES(42,?)",arrayOf(schema.getString("identityHash")))
            sql.execSQL("INSERT INTO profiles VALUES(?,?)",arrayOf(owner,"迁移测试"));sql.execSQL("INSERT INTO record_types VALUES(?,?,?,?,?,?,?,?)",arrayOf<Any>(type,owner,"随记","随记","note","plain","note",0))
            fun insert(name:String,values:Map<String,Any?>){val e=(0 until list.length()).map {list.getJSONObject(it)}.first {it.getString("tableName")==name};val fields=e.getJSONArray("fields");val columns=(0 until fields.length()).map {fields.getJSONObject(it).getString("columnName")};sql.execSQL("INSERT INTO "+name+" ("+columns.joinToString(",")+") VALUES("+columns.joinToString(","){"?"}+")",columns.map {values[it]}.toTypedArray())}
            insert("records",mapOf("id" to record,"ownerId" to owner,"kind" to "note","typeId" to type,"title" to "升级前资料","organizationStatus" to "inbox","createdAt" to 1000L,"updatedAt" to 1000L,"revision" to 3L))
            sql.execSQL("INSERT INTO notes VALUES(?,?,?,?)",arrayOf(record,"## 原始记录内容","markdown","plain"))
            insert("tasks",mapOf("id" to task,"ownerId" to owner,"title" to "不丢失的任务","note" to "来自原记录","priority" to 2,"status" to "doing","sourceRecordId" to record,"createdAt" to 1000L,"updatedAt" to 1001L,"revision" to 4L))
            sql.version=2
        }
        val safe=object:ContextWrapper(ctx){override fun getDatabasePath(name:String)=file;override fun getNoBackupFilesDir()=File(folder,"no-backup").apply {mkdirs()}}
        DatabaseSafety.beforeOpen(safe,"old.db");val copy=File(safe.noBackupFilesDir,"pre-upgrade-v2").listFiles()!!.single()
        SQLiteDatabase.openDatabase(copy.absolutePath,null,SQLiteDatabase.OPEN_READONLY).use {sql->assertEquals(2,sql.version);sql.rawQuery("SELECT title FROM tasks WHERE id=?",arrayOf(task)).use {assertTrue(it.moveToFirst());assertEquals("不丢失的任务",it.getString(0))}}
        val db=Room.databaseBuilder(ctx,DailyLifeDatabase::class.java,file.absolutePath).build()
        try {assertEquals("## 原始记录内容",db.records().record(record)!!.body);val t=db.plans().tasks().single();assertEquals(task,t.id);assertEquals(record,t.sourceRecordId);assertEquals(4L,t.revision);assertEquals("doing",t.status);assertTrue(db.ledger().transactions().isEmpty());assertTrue(db.tools().entries().isEmpty());assertEquals(4,db.openHelper.writableDatabase.version)}
        finally {db.close();folder.deleteRecursively()}
    }
}
