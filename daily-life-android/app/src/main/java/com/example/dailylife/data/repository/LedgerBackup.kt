package com.example.dailylife.data.repository
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.LedgerForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.zip.*
data class LedgerRestore(val folder:File,val manifest:JSONObject,val count:Int,val files:Int)
/** Ledger-scoped append backup. Fixed table/field allowlist; imported account entries are rebuilt from transaction facts. */
class LedgerBackup(private val context:Context,private val repo:LedgerRepository) {
    private val tables=listOf("ledgers","ledger_categories","money_accounts","ledger_transactions","account_entries","ledger_drafts","transaction_templates","monthly_budgets","budget_alerts","reimbursement_claims","reimbursement_items","ledger_change_logs","ledger_import_batches","ledger_import_rows","tags","attachments","entity_tags","attachment_links")
    private val cap=256L*1024*1024
    private fun rows(data:JSONObject,t:String)=data.getJSONArray(t).let {a->(0 until a.length()).map {a.getJSONObject(it)}}
    private fun id(v:String){require(UUID.fromString(v).toString()==v) {"备份包含无效标识"}}
    private fun hash(file:File)=file.inputStream().use {input->val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)};d.digest().joinToString("") {"%02x".format(it)}}
    private fun read(t:String):JSONArray {require(t in tables);val where=if(t in setOf("entity_tags","attachment_links")) " WHERE entityType IN ('transaction','ledger_draft','reimbursement')" else ""
        return JSONArray().also {a->repo.db.openHelper.writableDatabase.query("SELECT * FROM "+t+where).use {c->while(c.moveToNext()){val row=JSONObject();for(i in 0 until c.columnCount)row.put(c.getColumnName(i),when(c.getType(i)){Cursor.FIELD_TYPE_NULL->JSONObject.NULL;Cursor.FIELD_TYPE_INTEGER->c.getLong(i);else->c.getString(i)});a.put(row)}}}}
    suspend fun export(uri:Uri)=withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {export(it)} ?:error("无法写入备份文件")}
    suspend fun export(out:OutputStream)=withContext(Dispatchers.IO) {repo.records.writes.withLock {
        val data=JSONObject();repo.db.withTransaction {tables.forEach {data.put(it,read(it))};val refs=rows(data,"attachment_links").map {it.getString("attachmentId")}.toSet();data.put("attachments",JSONArray(rows(data,"attachments").filter {it.getString("id") in refs}))}
        validate(data);val manifest=JSONObject().put("format","daily-life-ledger").put("version",1).put("schema",3).put("data",data);val bytes=manifest.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=10*1024*1024) {"账本清单超过 10 MB"}
        ZipOutputStream(out).use {zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(bytes);zip.closeEntry();rows(data,"attachments").forEach {a->val file=File(repo.records.files.root,a.getString("id")+".bin");require(file.isFile&&file.length()==a.getLong("size")&&hash(file)==a.getString("sha256")) {"附件缺失或损坏，备份未完成"};zip.putNextEntry(ZipEntry("attachments/"+a.getString("id")+".bin"));file.inputStream().use {it.copyTo(zip)};zip.closeEntry()}}
    }}
    suspend fun prepare(uri:Uri)=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {prepare(it)} ?:error("无法读取账本备份")}
    suspend fun prepare(input:InputStream):LedgerRestore=withContext(Dispatchers.IO) {
        val folder=File(context.cacheDir,"ledger-restore-"+newId()).apply {mkdirs()}
        try {val names=mutableSetOf<String>();var total=0L
            ZipInputStream(BufferedInputStream(input)).use {zip->while(true) {val e=zip.nextEntry ?:break;require(!e.isDirectory&&names.add(e.name)&&names.size<=1001) {"备份文件重复或数量过多"};require(e.name=="manifest.json"||e.name.matches(Regex("attachments/[0-9a-f-]{36}\\.bin"))) {"备份路径不安全"}
                val f=File(folder,e.name);f.parentFile?.mkdirs();var size=0L;f.outputStream().use {out->val b=ByteArray(65536);while(true){val n=zip.read(b);if(n<0)break;size+=n;total+=n;require(total<=cap+10*1024*1024&&size<=if(e.name=="manifest.json")10*1024*1024 else 100L*1024*1024) {"备份解压容量超限"};out.write(b,0,n)}};zip.closeEntry()}}
            val m=JSONObject(File(folder,"manifest.json").readText(Charsets.UTF_8));require(m.getString("format")=="daily-life-ledger"&&m.getInt("version")==1&&m.getInt("schema")==3) {"账本备份格式不支持"};val data=m.getJSONObject("data");validate(data)
            val files=rows(data,"attachments");require(names==files.map {"attachments/"+it.getString("id")+".bin"}.toSet()+"manifest.json") {"附件清单不一致"}
            files.forEach {a->val f=File(folder,"attachments/"+a.getString("id")+".bin");require(f.length()==a.getLong("size")&&hash(f)==a.getString("sha256")) {"附件校验失败"}}
            LedgerRestore(folder,m,rows(data,"ledger_transactions").size,files.size)
        } catch(e:Exception){folder.deleteRecursively();throw e}
    }
    fun discard(preview:LedgerRestore){preview.folder.deleteRecursively()}
    private fun validate(data:JSONObject) {
        require(data.keys().asSequence().toSet()==tables.toSet()) {"备份表结构不兼容"};require(tables.sumOf {rows(data,it).size}<=50000&&rows(data,"ledger_transactions").size<=10000) {"账本备份上限一万笔 / 五万明细"}
        val files=rows(data,"attachments");require(files.size<=1000&&files.sumOf {it.getLong("size")}<=cap)
        tables.forEach {t->val definitions=repo.db.openHelper.writableDatabase.query("PRAGMA table_info("+t+")").use {c->buildMap {while(c.moveToNext())put(c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("type")) to (c.getInt(c.getColumnIndexOrThrow("notnull"))!=0))}}
            val unique=mutableSetOf<String>();rows(data,t).forEach {r->require(r.keys().asSequence().toSet()==definitions.keys) {"备份字段不兼容："+t};if(r.has("id")){id(r.getString("id"));require(unique.add(r.getString("id"))) {"备份标识重复"}}
                definitions.forEach {(k,d)->val v=r.opt(k);require(v==JSONObject.NULL||v is Number||v is String);require(v!=JSONObject.NULL||!d.second);if(v!=JSONObject.NULL) {if(d.first=="INTEGER")require(v is Number&&v.toString().toLongOrNull()!=null) {"整数精度错误"};if(d.first=="TEXT")require(v is String&&v.length<=100000) {"文字字段错误"}}}
            }}
        files.forEach {a->id(a.getString("id"));require(a.getString("relativePath")=="attachments/"+a.getString("id")+".bin"&&a.getString("sha256").matches(Regex("[0-9a-f]{64}"))&&a.getLong("size") in 0..100L*1024*1024) {"附件信息不安全"}}
        val ledgerIds=rows(data,"ledgers").map {it.getString("id")}.toSet();val categoryIds=rows(data,"ledger_categories").map {it.getString("id")}.toSet();val accountIds=rows(data,"money_accounts").map {it.getString("id")}.toSet();val txIds=rows(data,"ledger_transactions").map {it.getString("id")}.toSet();val claimIds=rows(data,"reimbursement_claims").map {it.getString("id")}.toSet()
        rows(data,"ledgers").forEach {require(it.getString("name").isNotBlank()&&it.getString("currency")=="CNY")}
        rows(data,"money_accounts").forEach {LocalDate.parse(it.getString("openingAsOfDate"));require(it.getLong("openingBalanceMinor") in -99_999_999_999L..99_999_999_999L&&it.getString("accountType") in setOf("cash","bank","payment","other"))}
        rows(data,"ledger_categories").forEach {c->require(c.getString("direction") in setOf("expense","income"));if(!c.isNull("parentId")){val p=rows(data,"ledger_categories").firstOrNull {it.getString("id")==c.getString("parentId")} ?:error("分类父级缺失");require(p.isNull("parentId")&&p.getString("direction")==c.getString("direction"))}}
        rows(data,"ledger_transactions").forEach {t->require(t.getString("ledgerId") in ledgerIds);require(t.getString("type") in setOf("expense","income","transfer","refund","reimbursement","adjustment"));LocalDate.parse(t.getString("occurredOn"));require(t.getLong("amountMinor") in -99_999_999_999L..99_999_999_999L)
            require(LocalDate.parse(t.getString("occurredOn"))<=LocalDate.now()&&t.getLong("revision")>0);ZoneId.of(t.getString("zoneId"));if(!t.isNull("occurredTime"))LocalTime.parse(t.getString("occurredTime"))
            if(t.getString("type")!="adjustment")require(t.getLong("amountMinor")>0);if(!t.isNull("categoryId"))require(t.getString("categoryId") in categoryIds);listOf("accountId","toAccountId").forEach {k->if(!t.isNull(k))require(t.getString(k) in accountIds)};if(!t.isNull("originalExpenseId"))require(t.getString("originalExpenseId") in txIds);if(!t.isNull("claimId"))require(t.getString("claimId") in claimIds)
            if(!t.isNull("relatedTransactionId"))require(t.getString("relatedTransactionId") in txIds)
        }
        rows(data,"reimbursement_claims").forEach {c->require(c.getString("settlementLedgerId") in ledgerIds&&c.getString("status") in setOf("draft","submitted","cancelled"));if(!c.isNull("submittedOn"))require(LocalDate.parse(c.getString("submittedOn"))<=LocalDate.now());require(c.getString("status")!="submitted"||!c.isNull("submittedOn"))}
        val budgets=rows(data,"monthly_budgets").map {it.getString("id")}.toSet();rows(data,"monthly_budgets").forEach {b->YearMonth.parse(b.getString("month"));require(b.getString("ledgerId") in ledgerIds&&b.getLong("limitMinor") in 1..99_999_999_999L);if(!b.isNull("categoryId"))require(b.getString("categoryId") in categoryIds)}
        rows(data,"budget_alerts").forEach {require(it.getString("budgetId") in budgets&&it.getInt("threshold") in setOf(80,100))}
        val batches=rows(data,"ledger_import_batches").map {it.getString("id")}.toSet();rows(data,"ledger_import_batches").forEach {b->require(b.getString("ledgerId") in ledgerIds&&(b.isNull("accountId")||b.getString("accountId") in accountIds));JSONObject(b.getString("mapping"))}
        rows(data,"ledger_import_rows").forEach {r->require(r.getString("batchId") in batches&&r.getInt("rowNumber")>=2&&r.getString("result") in setOf("pending","error","skipped","imported"));if(!r.isNull("transactionId"))require(r.getString("transactionId") in txIds);LedgerForm.parse(r.getString("parsedValues"));JSONArray(r.getString("rawValues"))}
        rows(data,"account_entries").forEach {require(it.getString("transactionId") in txIds&&it.getString("accountId") in accountIds)}
        rows(data,"reimbursement_items").forEach {require(it.getString("claimId") in claimIds&&it.getString("expenseId") in txIds&&it.getLong("requestedMinor")>0)}
        rows(data,"ledger_drafts").forEach {val f=LedgerForm.parse(it.getString("payload"));require(f.id==it.getString("id")&&f.revision>=0&&f.kind in setOf("expense","income","transfer","refund","reimbursement","claim")) {"草稿身份或类型与内容不一致"}}
        rows(data,"transaction_templates").forEach {require(LedgerForm.parse(it.getString("payload")).kind in setOf("expense","income","transfer")) {"模板类型不支持"}}
        val draftIds=rows(data,"ledger_drafts").map {it.getString("id")}.toSet();val attachmentIds=files.map {it.getString("id")}.toSet();val tagIds=rows(data,"tags").map {it.getString("id")}.toSet()
        listOf("attachment_links","entity_tags").forEach {table->rows(data,table).forEach {r->val reference=r.getString("entityId");require(when(r.getString("entityType")){"transaction"->reference in txIds;"ledger_draft"->reference in draftIds;"reimbursement"->reference in claimIds;else->false}) {"备份包含无效对象引用"};if(table=="attachment_links")require(r.getString("attachmentId") in attachmentIds) else require(r.getString("tagId") in tagIds)}}
    }
    suspend fun restore(preview:LedgerRestore)=withContext(Dispatchers.IO) {repo.records.writes.withLock {
        val data=JSONObject(preview.manifest.getJSONObject("data").toString());validate(data);val published=mutableListOf<File>();val before=repo.load();val owner=repo.records.owner()
        val wantedFiles=rows(data,"attachment_links").filter {r->when(r.getString("entityType")){"transaction"->before.transactions.none {it.id==r.getString("entityId")};"ledger_draft"->before.drafts.none {it.id==r.getString("entityId")};"reimbursement"->before.claims.none {it.id==r.getString("entityId")};else->false}}.map {it.getString("attachmentId")}.toSet()
        try {
            rows(data,"attachments").filter {it.getString("id") in wantedFiles}.forEach {a->val target=File(repo.records.files.root,a.getString("id")+".bin");val source=File(preview.folder,"attachments/"+a.getString("id")+".bin");require(source.isFile&&source.length()==a.getLong("size")&&hash(source)==a.getString("sha256")) {"恢复原件校验失败"}
                if(target.exists()) {val old=repo.records.dao.attachment(a.getString("id"));require(old!=null&&old.sha256==a.getString("sha256")&&hash(target)==old.sha256) {"原件标识冲突或残留，请先处理存储"}}
                else {published+=target;source.inputStream().use {input->FileOutputStream(target).use {out->input.copyTo(out);out.fd.sync()}}}
            }
            repo.db.withTransaction {
                val ledgerMap=mutableMapOf<String,String>();val categoryMap=mutableMapOf<String,String>();val accountMap=mutableMapOf<String,String>();val tagMap=mutableMapOf<String,String>()
                fun insert(t:String,r:JSONObject):Long {require(t in tables);val v=ContentValues();r.keys().forEach {k->val x=r.get(k);when(x){JSONObject.NULL->v.putNull(k);is Number->v.put(k,x.toLong());else->v.put(k,x.toString())}};return repo.db.openHelper.writableDatabase.insert(t,SQLiteDatabase.CONFLICT_IGNORE,v)}
                rows(data,"ledgers").forEach {r->val key=r.getString("id");val existing=before.ledgers.find {it.id==key} ?:before.ledgers.find {it.normalizedName==r.getString("normalizedName")};ledgerMap[key]=existing?.id ?:key;if(existing==null){r.put("ownerId",owner).put("isDefault",if(before.ledgers.isEmpty()) r.getLong("isDefault") else 0);insert("ledgers",r)}}
                val categoryRows=rows(data,"ledger_categories").sortedBy {if(it.isNull("parentId"))0 else 1};val addedCategories=before.categories.toMutableList()
                categoryRows.forEach {r->val key=r.getString("id");val parent=if(r.isNull("parentId")) null else categoryMap[r.getString("parentId")] ?:error("分类父级不完整");val direction=r.getString("direction");val unique=direction+"|"+(parent ?:"root")+"|"+com.example.dailylife.core.RecordRules.normalized(r.getString("name"));val existing=addedCategories.find {it.id==key} ?:addedCategories.find {it.uniqueKey==unique};categoryMap[key]=existing?.id ?:key
                    if(existing==null){r.put("ownerId",owner).put("parentId",parent ?:JSONObject.NULL).put("uniqueKey",unique);insert("ledger_categories",r);addedCategories+=LedgerCategory(key,owner,r.getString("name"),direction,parent,unique)}}
                val accountNames=before.accounts.map {it.normalizedName}.toMutableSet();rows(data,"money_accounts").forEach {r->val key=r.getString("id");val existing=before.accounts.find {it.id==key};accountMap[key]=key
                    if(existing!=null) require(existing.openingBalanceMinor==r.getLong("openingBalanceMinor")&&existing.openingAsOfDate==r.getString("openingAsOfDate")) {"同编号账户的期初不同，不能直接追加"}
                    else {var name=r.getString("name");var i=1;while(com.example.dailylife.core.RecordRules.normalized(name) in accountNames){name=r.getString("name")+" · 恢复 "+i++};val normalized=com.example.dailylife.core.RecordRules.normalized(name);accountNames+=normalized;r.put("name",name).put("normalizedName",normalized).put("ownerId",owner);insert("money_accounts",r)}}
                rows(data,"tags").forEach {r->val existing=repo.records.dao.tags().find {it.id==r.getString("id")||it.normalizedName==r.getString("normalizedName")};tagMap[r.getString("id")]=existing?.id ?:r.getString("id");if(existing==null){r.put("ownerId",owner);insert("tags",r)}}
                val skipTx=before.transactions.map {it.id}.toSet();val skipClaim=before.claims.map {it.id}.toSet();val skipDraft=before.drafts.map {it.id}.toSet();val skipBatch=before.batches.map {it.id}.toSet()
                fun remap(r:JSONObject){if(r.has("ownerId"))r.put("ownerId",owner);listOf("ledgerId","settlementLedgerId").forEach {k->if(r.has(k)&&!r.isNull(k))r.put(k,ledgerMap[r.getString(k)] ?:error("账本关系缺失"))};if(r.has("categoryId")&&!r.isNull("categoryId"))r.put("categoryId",categoryMap[r.getString("categoryId")] ?:error("分类关系缺失"));listOf("accountId","toAccountId").forEach {k->if(r.has(k)&&!r.isNull(k))r.put(k,accountMap[r.getString(k)] ?:error("账户关系缺失"))}}
                rows(data,"reimbursement_claims").filter {it.getString("id") !in skipClaim}.forEach {remap(it);insert("reimbursement_claims",it)}
                rows(data,"ledger_transactions").filter {it.getString("id") !in skipTx}.forEach {r->remap(r);if(!r.isNull("externalKey"))require(before.transactions.none {it.externalKey==r.getString("externalKey")}) {"可靠外部编号与已有账目冲突"};require(insert("ledger_transactions",r)!=-1L) {"交易编号或可靠外部编号冲突"}}
                rows(data,"reimbursement_items").filter {it.getString("claimId") !in skipClaim}.forEach {insert("reimbursement_items",it)}
                rows(data,"ledger_import_batches").filter {it.getString("id") !in skipBatch}.forEach {remap(it);require(insert("ledger_import_batches",it)!=-1L) {"导入来源批次冲突，不能追加混合来源"}}
                rows(data,"ledger_import_rows").filter {it.getString("batchId") !in skipBatch}.forEach {insert("ledger_import_rows",it)}
                rows(data,"monthly_budgets").forEach {r->remap(r);r.put("scopeKey",if(r.isNull("categoryId"))"total" else r.getString("categoryId"));insert("monthly_budgets",r)}
                rows(data,"budget_alerts").filter {r->before.budgets.none {it.id==r.getString("budgetId")}&&repo.dao.budgets().any {it.id==r.getString("budgetId")}}.forEach {insert("budget_alerts",it)}
                fun formMapping(json:String):String {var f=LedgerForm.parse(json);listOf("ledger" to ledgerMap,"category" to categoryMap,"account" to accountMap,"toAccount" to accountMap,"feeAccount" to accountMap,"feeCategory" to categoryMap).forEach {(k,map)->val v=f.text(k);if(v.isNotBlank())f=f.set(k,map[v] ?:v)};return f.json()}
                rows(data,"ledger_drafts").filter {it.getString("id") !in skipDraft}.forEach {r->r.put("ownerId",owner).put("payload",formMapping(r.getString("payload")));insert("ledger_drafts",r)}
                rows(data,"transaction_templates").forEach {r->r.put("ownerId",owner).put("payload",formMapping(r.getString("payload")));insert("transaction_templates",r)}
                rows(data,"ledger_change_logs").forEach {insert("ledger_change_logs",it)}
                rows(data,"attachments").filter {it.getString("id") in wantedFiles}.forEach {r->r.put("ownerId",owner);insert("attachments",r)}
                fun keepRef(r:JSONObject)=when(r.getString("entityType")){"transaction"->r.getString("entityId") !in skipTx;"ledger_draft"->r.getString("entityId") !in skipDraft;"reimbursement"->r.getString("entityId") !in skipClaim;else->false}
                rows(data,"entity_tags").filter(::keepRef).forEach {r->r.put("tagId",tagMap[r.getString("tagId")] ?:error("标签关系缺失"));insert("entity_tags",r)}
                rows(data,"attachment_links").filter(::keepRef).forEach {insert("attachment_links",it)}
                verifyAndRebuild()
            }
        } catch(e:Exception){published.forEach {it.delete()};throw e}
    }}
    private suspend fun verifyAndRebuild() {
        val s=repo.load();s.transactions.forEach {t->require(s.ledgers.any {it.id==t.ledgerId}) {"交易账本缺失"};listOfNotNull(t.accountId,t.toAccountId).forEach {a->require(s.accounts.any {it.id==a&&t.occurredOn>=it.openingAsOfDate}) {"账户或期初基准关系错误"}}
            if(t.type=="transfer")require(t.accountId!=null&&t.toAccountId!=null&&t.accountId!=t.toAccountId)
            if(t.type in setOf("expense","income"))require(s.categories.any {it.id==t.categoryId&&it.direction==t.type&&s.categories.none {c->c.parentId==it.id}}) {"账目分类关系错误"}
            if(t.type=="refund"&&t.deletedAt==null){val original=s.transactions.find {it.id==t.originalExpenseId&&it.type=="expense"&&it.deletedAt==null} ?:error("退款原消费缺失");require(t.accountId!=null&&t.occurredOn>=original.occurredOn&&s.refunded(original.id)<=original.amountMinor&&t.ledgerId==original.ledgerId&&t.categoryId==original.categoryId&&t.includeInBudget==original.includeInBudget) {"退款累计或口径不一致"}}
            if(t.type=="reimbursement"&&t.deletedAt==null){val c=s.claims.find {it.id==t.claimId&&it.deletedAt==null&&it.status=="submitted"} ?:error("回款报销单缺失");require(t.accountId!=null&&s.claimPaid(c.id)<=s.claimRequested(c.id)&&t.ledgerId==c.settlementLedgerId&&(c.submittedOn==null||t.occurredOn>=c.submittedOn)) {"回款金额、日期或结算账本不一致"}}
            if(t.relatedTransactionId!=null)require(t.type=="expense"&&s.transactions.any {it.id==t.relatedTransactionId&&it.type=="transfer"}) {"手续费的转账关系不完整"}
            repo.dao.clearEntries(t.id);if(t.deletedAt==null){if(t.accountId!=null)repo.dao.put(AccountEntry(t.id,t.accountId,if(t.type in setOf("expense","transfer"))-t.amountMinor else t.amountMinor,"primary"));if(t.type=="transfer")repo.dao.put(AccountEntry(t.id,t.toAccountId!!,t.amountMinor,"incoming"))}
        }
        s.items.forEach {i->require(s.claims.any {it.id==i.claimId}&&s.transactions.any {it.id==i.expenseId&&it.type=="expense"}) {"报销分配关系错误"}}
        s.claims.filter {it.deletedAt==null&&it.status!="cancelled"}.forEach {c->val conflict=s.items.filter {it.claimId==c.id}.any {i->val t=s.transactions.first {it.id==i.expenseId};t.deletedAt!=null||s.items.filter {it.expenseId==i.expenseId&&s.claims.any {cl->cl.id==it.claimId&&cl.deletedAt==null&&cl.status!="cancelled"}}.sumOf {it.requestedMinor}>t.amountMinor-s.refunded(t.id)};if(conflict&&!c.needsReview)repo.dao.put(c.copy(needsReview=true))}
    }
}
