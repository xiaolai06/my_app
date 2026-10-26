package com.example.dailylife.data.local
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
/** Called before the single Room instance is opened; never substitutes a destructive migration. */
object DatabaseSafety {
    fun beforeOpen(context:Context,name:String) {
        val file=context.getDatabasePath(name)
        if(!file.exists()) return
        SQLiteDatabase.openDatabase(file.absolutePath,null,SQLiteDatabase.OPEN_READWRITE).use {sqlite->
            val oldVersion=sqlite.version
            if(oldVersion !in 1..2) return
            sqlite.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null).use {c->
                require(c.moveToFirst() && c.getInt(0)==0) {"旧数据库正忙，暂缓升级；原数据保留"}
            }
            val folder=File(context.noBackupFilesDir,"pre-upgrade-v"+oldVersion).apply {mkdirs()}
            val destination=File(folder,"daily-life-"+System.currentTimeMillis()+".db")
            try {file.inputStream().use {source->FileOutputStream(destination).use {out->source.copyTo(out);out.fd.sync()}}}
            catch(e:Exception) {destination.delete();throw e}
        }
    }
}
