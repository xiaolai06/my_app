package com.example.dailylife.data.repository
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.ToolForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.*
import java.util.UUID
import java.security.MessageDigest
import java.util.zip.*
data class ToolsRestore(val directory:File,val manifest:JSONObject,val count:Int,val files:Int)
class ToolsBackup(private val context:Context,private val repo:ToolsRepository) {
    private val tables=listOf("tool_entries","tool_children","tool_logs","tool_links","tool_drafts","tool_payment_periods","tool_payment_settlements","feature_preferences","attachments","attachment_links")
    private val max=256L*1024*1024
    private fun rows(data:JSONObject,table:String)=data.getJSONArray(table).let {a->(0 until a.length()).map {a.getJSONObject(it)}}
    private fun hash(file:File)=file.inputStream().use {input->val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true) {val n=input.read(b);if(n<0) break;d.update(b,0,n)};d.digest().joinToString("") {"%02x".format(it)}}
    private fun uuid(id:String) {require(UUID.fromString(id).toString()==id) {"备份标识不正确"}}
    private fun table(name:String)=JSONArray().also {a->repo.db.openHelper.writableDatabase.query("SELECT * FROM "+name+(if(name=="attachment_links") " WHERE entityType IN ('tool','tool_draft')" else "")).use {c->while(c.moveToNext()) {val r=JSONObject();for(i in 0 until c.columnCount) r.put(c.getColumnName(i),when(c.getType(i)) {Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)});a.put(r)}}}
    suspend fun export(uri:Uri)=withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {export(it)} ?: error("无法写入备份")}
    suspend fun export(output:OutputStream)=withContext(Dispatchers.IO) {repo.records.writes.withLock {
        val data=JSONObject();repo.db.withTransaction {tables.forEach {data.put(it,table(it))};val refs=rows(data,"attachment_links").map {it.getString("attachmentId")}.toSet();data.put("attachments",JSONArray(rows(data,"attachments").filter {it.getString("id") in refs}))}
        require(rows(data,"tool_entries").size<=5000 && tables.sumOf {rows(data,it).size}<=50000) {"备份最多 5000 主项 / 50000 明细"};val files=rows(data,"attachments");require(files.size<=1000 && files.sumOf {it.getLong("size")}<=max)
        val manifest=JSONObject().put("format","daily-life-tools").put("version",1).put("data",data);val bytes=manifest.toString().toByteArray();require(bytes.size<=10*1024*1024)
        ZipOutputStream(output).use {zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(bytes);zip.closeEntry();files.forEach {a->val id=a.getString("id");uuid(id);val f=File(repo.records.files.root,"$id.bin");require(f.isFile && f.length()==a.getLong("size") && hash(f)==a.getString("sha256")) {"附件缺失或损坏，备份未完成"};zip.putNextEntry(ZipEntry("attachments/$id.bin"));f.inputStream().use {it.copyTo(zip)};zip.closeEntry()}}
    }}
    suspend fun prepare(uri:Uri)=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {prepare(it)} ?: error("无法读取备份")}
    suspend fun prepare(input:InputStream):ToolsRestore=withContext(Dispatchers.IO) {
        val directory=File(context.cacheDir,"tools-restore-"+newId()).apply {mkdirs()}
        try {val names=mutableSetOf<String>();var total=0L
            ZipInputStream(BufferedInputStream(input)).use {zip->while(true) {val entry=zip.nextEntry ?: break;val n=entry.name;require(!entry.isDirectory && names.add(n) && names.size<=1001 && (n=="manifest.json" || n.matches(Regex("attachments/[0-9a-f-]{36}\\.bin")))) {"备份文件或路径不安全"};val f=File(directory,n);f.parentFile?.mkdirs();var size=0L;f.outputStream().use {out->val b=ByteArray(65536);while(true) {val read=zip.read(b);if(read<0) break;size+=read;total+=read;require(total<=max+10*1024*1024 && size<=if(n=="manifest.json") 10L*1024*1024 else 100L*1024*1024) {"备份解压容量超限"};out.write(b,0,read)}};zip.closeEntry()}}
            val manifest=JSONObject(File(directory,"manifest.json").readText());require(manifest.getString("format")=="daily-life-tools" && manifest.getInt("version")==1) {"功能箱备份格式不支持"};val data=manifest.getJSONObject("data");validate(data)
            val files=rows(data,"attachments");require(names==files.map {"attachments/"+it.getString("id")+".bin"}.toSet()+"manifest.json") {"附件清单不一致"};files.forEach {a->val f=File(directory,"attachments/"+a.getString("id")+".bin");require(f.length()==a.getLong("size") && hash(f)==a.getString("sha256")) {"附件校验失败"}}
            ToolsRestore(directory,manifest,rows(data,"tool_entries").size,files.size)
        } catch(e:Exception) {directory.deleteRecursively();throw e}
    }
    private fun validate(data:JSONObject) {
        require(data.keys().asSequence().toSet()==tables.toSet());require(rows(data,"tool_entries").size<=5000 && tables.sumOf {rows(data,it).size}<=50000)
        require(rows(data,"attachments").size<=1000 && rows(data,"attachments").sumOf {it.getLong("size")}<=max)
        for(table in tables) {
            val columns=repo.db.openHelper.writableDatabase.query("PRAGMA table_info($table)").use {c->buildMap {while(c.moveToNext()) put(c.getString(1),c.getString(2) to (c.getInt(3)!=0))}}
            val ids=mutableSetOf<String>()
            for(row in rows(data,table)) {require(row.keys().asSequence().toSet()==columns.keys) {"备份字段不兼容"};for((key,type) in columns) {val v=row.opt(key);require(v!=JSONObject.NULL || !type.second);if(v!=JSONObject.NULL) {require(if(type.first=="INTEGER") v is Number && v.toString().toLongOrNull()!=null else v is String);if(v is String) require(v.length<=100000)}}
                if(row.has("id")) {uuid(row.getString("id"));require(ids.add(row.getString("id")))}
            }
        }
        val entries=rows(data,"tool_entries").map {it.getString("id")}.toSet();val drafts=rows(data,"tool_drafts").map {it.getString("id")}.toSet();val periods=rows(data,"tool_payment_periods").map {it.getString("id")}.toSet();val files=rows(data,"attachments").map {it.getString("id")}.toSet()
        rows(data,"tool_entries").forEach {r->val p=JSONObject(r.getString("payload"));require(p.getInt("version")==1);val f=ToolForm(r.getString("kind"),r.getString("id"),r.getLong("revision"),p.keys().asSequence().filter {it!="version"}.associateWith {p.get(it).toString()}).set("title",r.getString("title")).set("note",r.getString("note"));repo.validate(f);require(r.getString("state") in listOf("active","trial","paused","cancelled","done","idle","sold","scrapped"))}
        listOf("tool_children","tool_logs","tool_links").forEach {table->rows(data,table).forEach {require(it.getString("entryId") in entries)}}
        rows(data,"tool_drafts").forEach {val form=ToolForm.parse(it.getString("payload"));require(form.id==it.getString("id") && form.kind==it.getString("kind") && form.kind in com.example.dailylife.feature.tools.toolKinds.map {it.first}+"checklist_template")}
        val sourceRows=rows(data,"tool_entries").associateBy {it.getString("id")}
        val linkKeys=mutableSetOf<String>();val itemRoles=mutableSetOf<String>();rows(data,"tool_links").forEach {r->val kind=sourceRows.getValue(r.getString("entryId")).getString("kind");require(kind in listOf("project","item")) {"此来源不支持对象关联"};if(kind=="project") {require(r.getString("entityType") in listOf("record","task","event","habit","focus","goal","tool","ledger")) {"专题来源类型不支持"};require(r.getString("section") in listOf("overview","task","record","material","checklist","ledger","goal")) {"专题分区不支持"}} else require(r.getString("entityType")=="ledger" && r.getString("section") in listOf("item_purchase","item_sale") && itemRoles.add(r.getString("entryId")+":"+r.getString("section"))) {"物品购买出售引用无效或重复"};uuid(r.getString("entityId"));require(r.getString("entityId")!=r.getString("entryId"));require(linkKeys.add(r.getString("entryId")+":"+r.getString("entityType")+":"+r.getString("entityId"))) {"对象关联重复"}}
        val periodKeys=mutableSetOf<String>();rows(data,"tool_payment_periods").forEach {r->require(r.getString("sourceId") in entries && sourceRows.getValue(r.getString("sourceId")).getString("kind") in listOf("subscription","bill") && periodKeys.add(r.getString("sourceId")+":"+r.getString("originalKey")));listOf("periodStart","periodEnd","dueDate","originalKey").forEach {java.time.LocalDate.parse(r.getString(it))};require(r.getString("periodEnd")>=r.getString("periodStart"));require(r.isNull("expectedMinor") || r.getLong("expectedMinor") in 0..99_999_999_999L)}
        val settled=mutableSetOf<String>();rows(data,"tool_payment_settlements").forEach {r->val id=r.getString("periodId");require(id in periods && settled.add(id) && r.getLong("actualMinor") in 0..99_999_999_999L);require(java.time.LocalDate.parse(r.getString("paidOn"))<=java.time.LocalDate.now());require(rows(data,"tool_payment_periods").first {it.getString("id")==id}.getString("status")=="paid");require(r.getString("mode") in listOf("status","new","existing"));require((r.getString("mode")=="status")==r.isNull("expenseId"))}
        rows(data,"tool_payment_periods").forEach {r->require(r.getString("status") in listOf("pending","paid","cancelled","superseded"));require((r.getString("status")=="paid")==settled.contains(r.getString("id")))}
        rows(data,"attachments").forEach {r->val id=r.getString("id");uuid(id);require(r.getString("sha256").matches(Regex("[0-9a-f]{64}")) && r.getLong("size") in 0..100L*1024*1024);require(r.getString("relativePath")=="attachments/$id.bin") {"附件路径不正确"}}
        rows(data,"tool_children").forEach {r->val kind=sourceRows.getValue(r.getString("entryId")).getString("kind");require(kind in listOf("checklist","checklist_template","item") && r.getString("kind")==if(kind=="item") "maintenance" else "check") {"子项类型与来源不一致"};com.example.dailylife.core.PlanRules.title(r.getString("title"));require(r.getString("note").length<=50000 && r.getString("groupName").length<=120 && r.getString("unit").length<=40);require(r.isNull("quantityMilli") || r.getLong("quantityMilli") in 1..1_000_000_000_000L && r.getString("unit").isNotBlank());require(r.isNull("costMinor") || r.getLong("costMinor") in 0..99_999_999_999L);if(!r.isNull("date")) java.time.LocalDate.parse(r.getString("date"));require(kind!="item" || !r.isNull("date")) {"维修日期必填"}}
        val expenseRefs=mutableSetOf<String>();(rows(data,"tool_children")+rows(data,"tool_payment_settlements")).filter {!it.isNull("expenseId")}.forEach {r->uuid(r.getString("expenseId"));require(expenseRefs.add(r.getString("expenseId"))) {"同一支出不能关联多个条目或付款期间"}}
        rows(data,"tool_logs").forEach {r->java.time.LocalDate.parse(r.getString("date"));require(r.getString("kind") in listOf("return","postpone","payment_version","period_override"));val e=sourceRows.getValue(r.getString("entryId"));if(r.getString("kind") in listOf("return","postpone")) require(e.getString("kind")=="loan");if(r.getString("kind")=="return") {require(r.getLong("quantityMilli") in 1..1_000_000_000_000L);require(r.getString("date")>=JSONObject(e.getString("payload")).getString("date") && r.getString("date")<=java.time.LocalDate.now().toString())};if(r.getString("kind")=="period_override") {val p=JSONObject(r.getString("payload"));require(p.getInt("version")==1 && rows(data,"tool_payment_periods").any {it.getString("id")==p.getString("periodId") && it.getString("sourceId")==r.getString("entryId")})};if(r.getString("kind")=="payment_version") {require(e.getString("kind") in listOf("subscription","bill"));val p=JSONObject(r.getString("payload"));repo.validate(ToolForm(e.getString("kind"),e.getString("id"),fields=p.keys().asSequence().filter {it!="version"}.associateWith {p.get(it).toString()}).set("title",e.getString("title")))}}
        rows(data,"tool_entries").filter {it.getString("kind")=="loan"}.forEach {e->val p=JSONObject(e.getString("payload"));require(rows(data,"tool_logs").filter {it.getString("entryId")==e.getString("id") && it.getString("kind")=="return"}.sumOf {it.getLong("quantityMilli")}<=com.example.dailylife.core.ToolRules.milli(p.optString("quantity","1"))) {"归还数量超过原数量"}}
        rows(data,"attachment_links").forEach {r->require(r.getString("entityType") in listOf("tool","tool_draft"));require(r.getString("entityId") in if(r.getString("entityType")=="tool") entries else drafts);require(r.getString("attachmentId") in files)}
    }
    suspend fun restore(preview:ToolsRestore)=withContext(Dispatchers.IO) {repo.records.writes.withLock {
        val data=preview.manifest.getJSONObject("data");validate(data);val published=mutableListOf<File>()
        try {repo.db.withTransaction {
            val owner=repo.records.owner();val old=repo.dao.entries().map {it.id}.toSet();val added=rows(data,"tool_entries").filter {it.getString("id") !in old}.map {it.getString("id")}.toSet();val oldDrafts=repo.dao.drafts().map {it.id}.toSet();val addDrafts=rows(data,"tool_drafts").filter {it.getString("id") !in oldDrafts}.map {it.getString("id")}.toSet()
            val addPeriods=rows(data,"tool_payment_periods").filter {it.getString("sourceId") in added}.map {it.getString("id")}.toSet()
            val selectedLinks=rows(data,"attachment_links").filter {it.getString("entityId") in if(it.getString("entityType")=="tool") added else addDrafts};val wanted=selectedLinks.map {it.getString("attachmentId")}.toSet()
            fun insert(table:String,row:JSONObject) {val names=row.keys().asSequence().toList();repo.db.openHelper.writableDatabase.execSQL("INSERT INTO $table ("+names.joinToString(",")+") VALUES ("+names.joinToString(",") {"?"}+")",names.map {row.get(it).takeIf {v->v!=JSONObject.NULL}}.toTypedArray())}
            rows(data,"attachments").filter {it.getString("id") in wanted}.forEach {r->val id=r.getString("id");val oldFile=repo.records.dao.attachment(id);val target=File(repo.records.files.root,"$id.bin");if(oldFile!=null) require(oldFile.sha256==r.getString("sha256") && target.isFile && hash(target)==oldFile.sha256) {"原件编号冲突"} else {require(!target.exists()) {"已有未登记原件，请先维护存储"};val source=File(preview.directory,"attachments/$id.bin");require(hash(source)==r.getString("sha256"));published+=target;source.inputStream().use {input->FileOutputStream(target).use {out->input.copyTo(out);out.fd.sync()}};r.put("ownerId",owner);insert("attachments",r)}}
            for(table in tables.filter {it !in listOf("attachments","attachment_links","feature_preferences")}) for(source in rows(data,table)) {val r=JSONObject(source.toString());val include=when(table) {"tool_entries"->r.getString("id") in added;"tool_drafts"->r.getString("id") in addDrafts;"tool_payment_periods"->r.getString("id") in addPeriods;"tool_payment_settlements"->r.getString("periodId") in addPeriods;else->r.getString("entryId") in added};if(!include) continue
                if(r.has("ownerId")) r.put("ownerId",owner)
                if(table=="tool_links" && r.getString("section") in listOf("item_purchase","item_sale")) {val expected=if(r.getString("section")=="item_purchase") "expense" else "income";if(repo.db.ledger().transactions().none {it.id==r.getString("entityId") && it.ownerId==owner && it.deletedAt==null && it.type==expected && repo.db.ledger().ledgers().any {ledger->ledger.id==it.ledgerId && ledger.currency=="CNY"}}) continue}
                if(table=="tool_children" && !r.isNull("expenseId")) {val tx=repo.db.ledger().transactions().find {it.id==r.getString("expenseId") && it.deletedAt==null && it.type=="expense" && it.ownerId==owner};if(tx==null || r.isNull("costMinor") || tx.amountMinor!=r.getLong("costMinor") || repo.dao.children().any {it.expenseId==tx.id} || repo.dao.settlements().any {it.expenseId==tx.id}) r.put("expenseId",JSONObject.NULL)}
                if(table=="tool_payment_settlements" && !r.isNull("expenseId")) {val tx=repo.db.ledger().transactions().find {it.id==r.getString("expenseId") && it.deletedAt==null && it.type=="expense" && it.ownerId==owner};if(tx==null || tx.amountMinor!=r.getLong("actualMinor") || tx.occurredOn!=r.getString("paidOn") || repo.dao.settlements().any {it.expenseId==tx.id} || repo.dao.children().any {it.expenseId==tx.id}) {r.put("expenseId",JSONObject.NULL);r.put("mode","status")}}
                insert(table,r)
            }
            selectedLinks.forEach {insert("attachment_links",it)}
            val localFeatures=repo.dao.preferences().map {it.featureKey}.toSet()
            rows(data,"feature_preferences").filter {it.getString("featureKey") !in localFeatures}.forEach {insert("feature_preferences",it)}
        };preview.directory.deleteRecursively()} catch(e:Exception) {published.forEach {it.delete()};throw e}
    }}
}
