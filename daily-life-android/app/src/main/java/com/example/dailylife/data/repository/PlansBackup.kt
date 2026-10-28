package com.example.dailylife.data.repository

import com.example.dailylife.data.files.RestoreWorkspaceOwner
import android.content.Context
import android.net.Uri
import android.database.Cursor
import androidx.room.withTransaction
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.plans.PlanForm
import com.example.dailylife.core.PlanRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.zip.*

data class PlanRestore(val folder:File,val manifest:JSONObject,val count:Int,val files:Int)
/** Scoped append restore. No SQL or file path from the archive is executed. Existing facts are never replaced. */
class PlansBackup(private val context:Context,private val repo:PlansRepository) {
    private val workspaces = RestoreWorkspaceOwner(context.cacheDir)
    fun discardAll() = workspaces.close()
    private val tables=listOf("task_groups","tasks","subtasks","events","repeat_rules","occurrences","habits","habit_rules","habit_logs",
        "habit_pauses","focus_sessions","focus_segments","goals","goal_progress","goal_links","milestones","plan_drafts","tags","attachments","entity_tags","attachment_links")
    private val rootTypes=mapOf("task" to "tasks","event" to "events","habit" to "habits","focus" to "focus_sessions","goal" to "goals","plan_draft" to "plan_drafts")
    private val roots=setOf("task_groups","tasks","events","habits","focus_sessions","goals","plan_drafts")
    private val maxBytes=256L*1024*1024
    private fun rows(json:JSONObject,table:String)=json.getJSONArray(table).let {a->(0 until a.length()).map {a.getJSONObject(it)}}
    private fun ids(json:JSONObject,table:String)=rows(json,table).map {it.getString("id")}.toSet()
    private fun uuid(id:String) {require(UUID.fromString(id).toString()==id) {"备份标识格式错误"}}
    private fun hash(f:File)=f.inputStream().use {s->val digest=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536)
        while(true) {val n=s.read(b);if(n<0) break;digest.update(b,0,n)};digest.digest().joinToString("") {"%02x".format(it)}}
    private fun readTable(name:String,where:String=""):JSONArray {
        require(name in tables)
        return JSONArray().also {a->repo.db.openHelper.writableDatabase.query("SELECT * FROM "+name+where).use {c->
            while(c.moveToNext()) {val row=JSONObject();for(i in 0 until c.columnCount) row.put(c.getColumnName(i),when(c.getType(i)) {
                Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);Cursor.FIELD_TYPE_FLOAT->c.getDouble(i);else->c.getString(i)
            });a.put(row)}
        }}
    }
    suspend fun export(uri:Uri)=withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri,"wt")?.use {export(it)} ?: error("无法写入备份位置")
    }
    suspend fun export(output:OutputStream)=withContext(Dispatchers.IO) {repo.writes.withLock {
        val data=JSONObject()
        repo.db.withTransaction {
            tables.forEach {table->val where=when(table) {
                "entity_tags","attachment_links"->" WHERE entityType IN ('task','event','habit','focus','goal','plan_draft')"
                else->""
            };data.put(table,readTable(table,where))}
            val refs=rows(data,"attachment_links").map {it.getString("attachmentId")}.toSet()
            data.put("attachments",JSONArray(rows(data,"attachments").filter {it.getString("id") in refs}))
        }
        val count=roots.sumOf {rows(data,it).size};require(count<=5000 && tables.sumOf {rows(data,it).size}<=50000) {"计划备份上限 5000 主项 / 50000 明细"}
        val files=rows(data,"attachments");require(files.size<=1000 && files.sumOf {it.getLong("size")}<=maxBytes) {"附件备份上限 1000 份 / 256 MB"}
        val manifest=JSONObject().put("format","daily-life-plans").put("version",1).put("schema",2).put("data",data)
        val bytes=manifest.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=10*1024*1024) {"计划清单过大"}
        ZipOutputStream(output).use {zip->
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(bytes);zip.closeEntry()
            files.forEach {a->val id=a.getString("id");uuid(id);val f=File(repo.records.files.root,id+".bin")
                require(f.isFile && f.length()==a.getLong("size") && hash(f)==a.getString("sha256")) {"附件缺失或损坏，未完成备份"}
                zip.putNextEntry(ZipEntry("attachments/"+id+".bin"));f.inputStream().use {it.copyTo(zip)};zip.closeEntry()
            }
        }
    }}
    suspend fun prepare(uri:Uri)=withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use {prepare(it)} ?: error("无法读取备份")
    }
    suspend fun prepare(input:InputStream):PlanRestore=withContext(Dispatchers.IO) {
        val folder=workspaces.create("plan-restore-")
        try {
            val names=mutableSetOf<String>();var total=0L
            ZipInputStream(BufferedInputStream(input)).use {zip->
                while(true) {val entry=zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name) && names.size<=1001) {"备份包含重复或多余文件"}
                    val relative=entry.name
                    require(relative=="manifest.json" || relative.matches(Regex("attachments/[0-9a-f-]{36}\\.bin"))) {"备份包含不安全路径"}
                    if(relative!="manifest.json") uuid(relative.substringAfter("/").removeSuffix(".bin"))
                    val target=File(folder,relative);target.parentFile?.mkdirs()
                    var size=0L
                    target.outputStream().use {out->val buffer=ByteArray(65536)
                        while(true) {val n=zip.read(buffer);if(n<0) break;size+=n;total+=n
                            require(total<=maxBytes+10L*1024*1024 && size<=if(relative=="manifest.json") 10L*1024*1024 else 100L*1024*1024) {"备份解压后超过容量上限"}
                            out.write(buffer,0,n)
                        }
                    };zip.closeEntry()
                }
            }
            val manifest=JSONObject(File(folder,"manifest.json").readText())
            require(manifest.getString("format")=="daily-life-plans" && manifest.getInt("version")==1 && manifest.getInt("schema")==2) {"备份格式或版本不支持"}
            val data=manifest.getJSONObject("data");validate(data)
            val files=rows(data,"attachments")
            val expected=files.map {"attachments/"+it.getString("id")+".bin"}.toSet()+"manifest.json"
            require(names==expected) {"备份原件清单不一致"}
            files.forEach {a->val f=File(folder,"attachments/"+a.getString("id")+".bin")
                require(f.length()==a.getLong("size") && hash(f)==a.getString("sha256")) {"附件校验失败"}
            }
            PlanRestore(folder,manifest,roots.sumOf {rows(data,it).size},files.size)
        } catch(e:Exception) {workspaces.discard(folder);throw e}
    }
    private fun validate(data:JSONObject) {
        require(data.keys().asSequence().toSet()==tables.toSet()) {"备份表结构不完整"}
        require(tables.sumOf {rows(data,it).size}<=50000 && roots.sumOf {rows(data,it).size}<=5000)
        require(rows(data,"attachments").size<=1000 && rows(data,"attachments").sumOf {it.getLong("size")}<=maxBytes)
        tables.forEach {table->
            val definitions=repo.db.openHelper.writableDatabase.query("PRAGMA table_info("+table+")").use {c->buildMap {
                while(c.moveToNext()) put(c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("type")) to (c.getInt(c.getColumnIndexOrThrow("notnull"))!=0))
            }}
            val columns=definitions.keys
            val seen=mutableSetOf<String>()
            rows(data,table).forEach {row->
                require(row.keys().asSequence().toSet()==columns) {"备份字段不兼容："+table}
                if(row.has("id")) {uuid(row.getString("id"));require(seen.add(row.getString("id"))) {"重复标识"}}
                row.keys().forEach {key->
                    val v=row.opt(key)
                    require(v==JSONObject.NULL || v is String || v is Number) {"备份字段类型错误"}
                    val definition=definitions.getValue(key)
                    require(v!=JSONObject.NULL || !definition.second) {"必填字段缺失"}
                    if(v!=JSONObject.NULL && definition.first=="TEXT") require(v is String) {"文字字段格式错误"}
                    if(v!=JSONObject.NULL && definition.first=="INTEGER") require(v is Number && v.toString().toLongOrNull()!=null) {"整数精度错误"}
                    if(v is String) require(v.length<=100000) {"备份单字段过大"}
                    if(v is String && (key.endsWith("Date") || key in setOf("endDateExclusive","effectiveFrom","originalKey","scheduledDate","stoppedFrom"))) LocalDate.parse(v)
                }
                if(row.has("ownerId")) uuid(row.getString("ownerId"))
            }
        }
        fun ref(table:String,column:String,target:String,nullable:Boolean=false) {
            val values=ids(data,target);rows(data,table).forEach {r->require(nullable && r.isNull(column) || r.getString(column) in values) {"备份关联缺失："+table+"."+column}}
        }
        ref("tasks","groupId","task_groups",true);ref("subtasks","taskId","tasks");ref("events","taskId","tasks",true)
        listOf("habit_rules","habit_logs","habit_pauses").forEach {ref(it,"habitId","habits")}
        ref("focus_segments","sessionId","focus_sessions");ref("focus_sessions","taskId","tasks",true)
        listOf("goal_progress","goal_links","milestones").forEach {ref(it,"goalId","goals")}
        rows(data,"repeat_rules").forEach {r->
            val type=r.getString("entityType");require(type in setOf("task","event") && r.getString("entityId") in ids(data,rootTypes.getValue(type)))
            PlanRules.dates(RepeatRule(r.getString("id"),r.getString("ownerId"),type,r.getString("entityId"),r.getString("anchorDate"),r.getString("frequency"),
                r.getInt("interval"),r.getString("weekdays"),r.optString("endDate").takeUnless {r.isNull("endDate")},r.optInt("count").takeUnless {r.isNull("count")},r.getString("invalidPolicy")),LocalDate.parse(r.getString("anchorDate")),1)
        }
        val occurrenceKeys=mutableSetOf<String>()
        rows(data,"occurrences").forEach {o->val r=rows(data,"repeat_rules").firstOrNull {it.getString("id")==o.getString("ruleId")} ?: error("重复规则缺失")
            require(r.getString("entityId")==o.getString("entityId") && r.getString("entityType")==o.getString("entityType") && occurrenceKeys.add(o.getString("ruleId")+":"+o.getString("originalKey")))
        }
        rows(data,"goal_links").forEach {l->val type=l.getString("sourceType");require(type in setOf("task","focus") && l.getString("sourceId") in ids(data,rootTypes.getValue(type)))}
        rows(data,"entity_tags").forEach {l->require(l.getString("entityType") in rootTypes && l.getString("entityId") in ids(data,rootTypes.getValue(l.getString("entityType"))) && l.getString("tagId") in ids(data,"tags"))}
        rows(data,"attachment_links").forEach {l->require(l.getString("entityType") in rootTypes && l.getString("entityId") in ids(data,rootTypes.getValue(l.getString("entityType"))) && l.getString("attachmentId") in ids(data,"attachments"))}
        rows(data,"attachments").forEach {a->require(a.getString("relativePath")=="attachments/"+a.getString("id")+".bin" && a.getLong("size")>0 && a.getString("sha256").matches(Regex("[0-9a-f]{64}")))}
        rows(data,"plan_drafts").forEach {d->val f=PlanForm.parse(d.getString("payload"));require(f.id==d.getString("id") && f.kind==d.getString("kind") && f.kind in setOf("task","event","habit","goal","focus"))}
        rows(data,"tasks").forEach {require(it.getString("status") in setOf("todo","doing","done","cancelled") && it.getInt("priority") in 0..2);PlanRules.title(it.getString("title"))}
        rows(data,"events").forEach {r->
            PlanRules.title(r.getString("title"));require(r.getString("status") in setOf("scheduled","cancelled") && r.getString("endDateExclusive")>r.getString("startDate"));ZoneId.of(r.getString("zoneId"))
        }
        rows(data,"habit_rules").forEach {require(it.getLong("targetMilli")>0 && it.getInt("weeklyDays") in 1..7 && it.getString("scheduleType") in setOf("daily","weekdays","weekly"))}
        rows(data,"habit_logs").forEach {require(it.getLong("valueMilli")>0)}
        rows(data,"goals").forEach {require(it.getLong("targetMilli")>0 && it.getString("progressSource") in setOf("manual","tasks","focus","milestones"))}
        rows(data,"focus_segments").forEach {require(it.getLong("seconds") in 0..604800 && it.getString("phase") in setOf("work","break"))}
        require(rows(data,"focus_sessions").count {it.getString("state") in setOf("running","paused","awaiting","review") && it.isNull("deletedAt")}<=1) {"备份有多个活动计时"}
    }
    suspend fun restore(preview:PlanRestore):Int=withContext(Dispatchers.IO) {repo.writes.withLock {
        val data=preview.manifest.getJSONObject("data");validate(data)
        val published=mutableListOf<File>();var inserted=0
        try {repo.db.withTransaction {
            val sql=repo.db.openHelper.writableDatabase;val owner=repo.records.owner()
            val existing=tables.filter {it !in setOf("goal_links","entity_tags","attachment_links")}.associateWith {table->
                readTable(table).let {a->(0 until a.length()).map {a.getJSONObject(it).getString("id")}.toSet()}
            }
            val fresh=roots.associateWith {ids(data,it)-existing.getValue(it)}
            val hasNewActive=rows(data,"focus_sessions").any {it.getString("id") in fresh.getValue("focus_sessions") && it.getString("state") in setOf("running","paused","awaiting","review") && it.isNull("deletedAt")}
            require(!hasNewActive || repo.dao.sessions().none {it.deletedAt==null && it.state in setOf("running","paused","awaiting","review")}) {"请先结束本机活动计时，再恢复备份中的活动专注"}
            val groupMap=rows(data,"task_groups").associate {r->r.getString("id") to (repo.dao.groups().firstOrNull {it.normalizedName==r.getString("normalizedName")}?.id ?: r.getString("id"))}
            val tagMap=rows(data,"tags").associate {r->r.getString("id") to (repo.records.dao.tags().firstOrNull {it.normalizedName==r.getString("normalizedName")}?.id ?: r.getString("id"))}
            val acceptedRules=rows(data,"repeat_rules").filter {it.getString("entityId") in fresh.getValue(rootTypes.getValue(it.getString("entityType")))}.map {it.getString("id")}.toSet()
            fun accepted(table:String,r:JSONObject):Boolean=when(table) {
                in roots->r.getString("id") in fresh.getValue(table) && (table!="task_groups" || groupMap[r.getString("id")]==r.getString("id"))
                "tags"->r.getString("id") !in existing.getValue(table) && tagMap[r.getString("id")]==r.getString("id")
                "attachments"->r.getString("id") !in existing.getValue(table)
                "subtasks"->r.getString("taskId") in fresh.getValue("tasks")
                "repeat_rules"->r.getString("id") in acceptedRules
                "occurrences"->r.getString("ruleId") in acceptedRules
                "habit_rules","habit_logs","habit_pauses"->r.getString("habitId") in fresh.getValue("habits")
                "focus_segments"->r.getString("sessionId") in fresh.getValue("focus_sessions")
                "goal_progress","goal_links","milestones"->r.getString("goalId") in fresh.getValue("goals")
                "entity_tags","attachment_links"->r.getString("entityId") in fresh.getValue(rootTypes.getValue(r.getString("entityType")))
                else->false
            }
            rows(data,"attachments").forEach {r->val id=r.getString("id");val staged=File(preview.folder,"attachments/"+id+".bin")
                require(staged.isFile && staged.length()==r.getLong("size") && hash(staged)==r.getString("sha256")) {"恢复原件已变化"}
                val old=repo.records.dao.attachment(id)
                if(old!=null) require(old.sha256==r.getString("sha256") && old.size==r.getLong("size") && repo.records.files.file(old).exists()) {"同编号附件冲突"}
                else {val target=File(repo.records.files.root,id+".bin");require(!target.exists()) {"存在同编号文件残留，请先检查存储"}
                    staged.inputStream().use {source->FileOutputStream(target).use {out->published+=target;source.copyTo(out);out.fd.sync()}}
                }
            }
            tables.forEach {table->rows(data,table).filter {accepted(table,it)}.forEach {original->
                val r=JSONObject(original.toString());if(r.has("ownerId")) r.put("ownerId",owner)
                if(table=="tasks") {if(!r.isNull("groupId")) r.put("groupId",groupMap.getValue(r.getString("groupId")))
                    if(!r.isNull("sourceRecordId") && repo.records.dao.record(r.getString("sourceRecordId"))==null) r.put("sourceRecordId",JSONObject.NULL)}
                if(table=="entity_tags") r.put("tagId",tagMap.getValue(r.getString("tagId")))
                if(table=="focus_sessions" && r.getString("state") in setOf("running","paused","awaiting","review")) r.put("state","review")
                val columns=r.keys().asSequence().toList()
                val args=columns.map {key->r.opt(key).takeUnless {it==JSONObject.NULL}}.toTypedArray()
                sql.execSQL("INSERT INTO "+table+" ("+columns.joinToString(",")+") VALUES ("+columns.joinToString(",") {"?"}+")",args)
                if(table in roots) inserted++
            }}
        };inserted} catch(e:Exception) {repo.records.cleanupUncommittedOriginals(published);throw e}
    }}
    fun discard(preview:PlanRestore) {workspaces.discard(preview.folder)}
}
