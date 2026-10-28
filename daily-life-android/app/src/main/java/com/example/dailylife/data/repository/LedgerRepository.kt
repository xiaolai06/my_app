package com.example.dailylife.data.repository

import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.core.RecordRules
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.ledger.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.*
import java.util.UUID

/** Every financial mutation, its entries and audit record commit under one shared write coordinator. */
class LedgerRepository(val records:RecordsRepository) {
    val db=records.db;val dao=db.ledger()
    val snapshot=dao.changes(SimpleSQLiteQuery("SELECT COUNT(*) FROM ledger_transactions")).map {db.withTransaction {load()}}
    private val garbage=mutableSetOf<String>()
    private suspend fun <T> write(block:suspend ()->T):T=records.writes.withLock {withContext(Dispatchers.IO) {
        garbage.clear()
        try {val result=db.withTransaction {block()};garbage.toList().forEach {id->runCatching {if(records.dao.attachmentUses(id)==0) records.dao.attachment(id)?.let {a->if(records.files.file(a).delete()||!records.files.file(a).exists()) records.dao.deleteAttachment(id)}}};result}
        finally {garbage.clear()}
    }}
    suspend fun load()=LedgerSnapshot(dao.ledgers(),dao.categories(),dao.accounts(),dao.transactions(),dao.entries(),dao.drafts(),dao.templates(),dao.budgets(),dao.alerts(),dao.claims(),dao.items(),dao.logs(),dao.batches(),dao.rows())
    suspend fun initialize() {
        records.initialize()
        write {
            // Repair only relationships left by older deletion code; keep import identities for deduplication.
            dao.removeOrphanBudgetAlerts()
            dao.repairDeletedImportRows()
            val owner=records.owner()
            if(dao.ledgers().isEmpty()) dao.put(Ledger(ownerId=owner,name="日常",normalizedName="日常",isDefault=true))
            if(dao.categories().isEmpty()) {
                listOf("expense" to listOf("未分类","餐饮","交通","购物","住房","学习","健康","娱乐","手续费"),"income" to listOf("未分类","工资","奖金","其他收入")).forEach {(direction,names)->
                    names.forEachIndexed {i,n->dao.put(LedgerCategory(ownerId=owner,name=n,direction=direction,uniqueKey=direction+"|root|"+RecordRules.normalized(n),protected=n=="未分类",sortOrder=i))}
                }
            }
        }
    }
    private suspend fun log(id:String,action:String,summary:String){dao.put(LedgerChangeLog(entityId=id,action=action,summary=summary))}
    private fun validateDraft(f:LedgerForm) {UUID.fromString(f.id);require(f.kind in setOf("expense","income","transfer","refund","reimbursement","claim")) {"草稿类型不受支持"};require(f.json().length<=100000) {"草稿内容过长"};require(f.revision>=0)}
    private fun sameDraftFamily(a:String,b:String)=a==b||(a in setOf("expense","income","transfer")&&b in setOf("expense","income","transfer"))
    suspend fun prepareDraft(f:LedgerForm)=write {
        validateDraft(f)
        val old=dao.drafts().find {it.id==f.id}
        require(old==null||sameDraftFamily(LedgerForm.parse(old.payload).kind,f.kind)) {"草稿编号已被其他类型使用"}
        if(old==null) {
            dao.put(LedgerDraft(f.id,records.owner(),f.json()))
            if(f.revision>0) records.dao.putLinks(records.dao.linksFor(if(f.kind=="claim") "reimbursement" else "transaction",f.id).map {it.copy(entityType="ledger_draft")})
        }
        old?.let {LedgerForm.parse(it.payload)} ?: f
    }
    suspend fun draft(f:LedgerForm)=write {
        validateDraft(f)
        val current=dao.drafts().firstOrNull {it.id==f.id} ?: error("未完成编辑已保存或删除，请重新打开")
        val previous=LedgerForm.parse(current.payload)
        require(sameDraftFamily(previous.kind,f.kind) && previous.revision==f.revision) {"编辑来源已变化，未完成内容仍保留，请重新核对"}
        dao.put(LedgerDraft(f.id,records.owner(),f.json()))
    }
    suspend fun discard(id:String)=write {val refs=records.dao.linksFor("ledger_draft",id).map {it.attachmentId};records.dao.clearLinks("ledger_draft",id);dao.removeDraft(id);clean(refs)}
    private suspend fun clean(ids:List<String>) {garbage.addAll(ids)}
    suspend fun importFile(id:String,uri:Uri)=records.writes.withLock {
        require(dao.drafts().any {it.id==id}) {"请先打开记账草稿"};require(records.dao.linksFor("ledger_draft",id).size<50) {"每笔最多 50 份附件"}
        val a=records.files.import(uri,records.owner());try {db.withTransaction {records.dao.putAttachment(a);records.dao.putLinks(listOf(AttachmentLink("ledger_draft",id,a.id,sortOrder=records.dao.linksFor("ledger_draft",id).size)))}} catch(e:Exception) {records.files.file(a).delete();throw e}
    }
    suspend fun attachExisting(id:String,fileId:String)=write {
        require(dao.drafts().any {it.id==id})
        records.requireAvailableAttachments(listOf(AttachmentLink("ledger_draft",id,fileId)))
        require(records.dao.linksFor("ledger_draft",id).size<50)
        records.dao.putLinks(listOf(AttachmentLink("ledger_draft",id,fileId,sortOrder=records.dao.linksFor("ledger_draft",id).size)))
    }
    suspend fun removeAttachment(id:String,fileId:String)=write {require(dao.drafts().any {it.id==id});records.dao.detach("ledger_draft",id,fileId);clean(listOf(fileId))}
    suspend fun form(id:String):LedgerForm {
        val t=dao.transactions().find {it.id==id} ?:error("账目不存在")
        var f=LedgerForm(t.type,t.id,t.revision).set("ledger",t.ledgerId).set("amount",LedgerRules.display(t.amountMinor)).set("date",t.occurredOn).set("time",t.occurredTime.orEmpty()).set("zone",t.zoneId)
            .set("category",t.categoryId.orEmpty()).set("account",t.accountId.orEmpty()).set("toAccount",t.toAccountId.orEmpty()).set("channel",t.paymentChannel).set("merchant",t.counterparty).set("location",t.location).set("subject",t.subject).set("note",t.note).set("budget",t.includeInBudget.toString()).set("original",t.originalExpenseId.orEmpty()).set("claim",t.claimId.orEmpty())
        val fee=dao.transactions().find {it.relatedTransactionId==id&&it.type=="expense"&&it.deletedAt==null}
        fee?.let {f=f.set("fee",LedgerRules.display(it.amountMinor)).set("feeAccount",it.accountId.orEmpty()).set("feeCategory",it.categoryId.orEmpty())}
        f=f.set("tags",records.dao.tagsForEntity("transaction",id).joinToString(",") {it.tagId})
        val linked=linkedSource(id);val feeLinked=fee?.let {linkedSource(it.id)}.orEmpty()
        f=f.set("linkedSource",listOf(linked,feeLinked.takeIf {it.isNotBlank()}?.let {"手续费："+it}.orEmpty()).filter {it.isNotBlank()}.joinToString("；"))
        val toolEntries=db.tools().entries();f=f.set("contextSource",itemReferences(id).joinToString("；") {r->(if(r.section=="item_purchase") "物品购买" else "物品出售")+" · "+(toolEntries.find {it.id==r.entryId}?.title ?: "来源待核对")})
        return f
    }
    private suspend fun itemReferences(id:String)=db.tools().links().filter {it.entityType=="ledger"&&it.entityId==id&&it.section in setOf("item_purchase","item_sale")}
    private suspend fun linkedSource(id:String):String {
        val tools=db.tools();val entries=tools.entries();val periods=tools.periods()
        val payments=tools.settlements().filter {it.expenseId==id}.map {s->val p=periods.find {it.id==s.periodId};val e=entries.find {it.id==p?.sourceId};(e?.title ?: "付款来源待核对")+(p?.let {" · "+it.originalKey} ?: "")}
        val children=tools.children().filter {it.expenseId==id}.map {c->val e=entries.find {it.id==c.entryId};(if(e?.kind=="item") "维修" else "清单条目")+" · "+(e?.title ?: "来源待核对")+" / "+c.title}
        return (payments+children).joinToString("；")
    }
    suspend fun save(f:LedgerForm,allowWarnings:Boolean=false):String=write {val id=writeCore(f,allowWarnings);thresholds();id}
    suspend fun claimForm(id:String):LedgerForm {
        val s=load();val c=s.claims.firstOrNull {it.id==id&&it.deletedAt==null} ?:error("报销单不存在或已删除")
        val allocations=JSONObject();s.items.filter {it.claimId==id}.forEach {allocations.put(it.expenseId,LedgerRules.display(it.requestedMinor))}
        return LedgerForm("claim",c.id,c.revision).set("name",c.name).set("ledger",c.settlementLedgerId).set("counterparty",c.counterparty).set("note",c.note).set("submitDate",c.submittedOn.orEmpty()).set("allocations",allocations.toString())
    }
    suspend fun saveClaimFromDraft(f:LedgerForm):String=write {
        require(f.kind=="claim") {"报销草稿类型不正确"};UUID.fromString(f.id)
        val allocations=JSONObject(f.text("allocations","{}"));val amounts=allocations.keys().asSequence().associateWith {allocations.getString(it)}
        val current=dao.claims().firstOrNull {it.id==f.id}
        // A retried successful command has no draft and exactly the already committed content.
        if(dao.drafts().none {it.id==f.id}&&current?.deletedAt==null&&current?.revision==f.revision+1) {
            val saved=claimForm(f.id)
            require(listOf("name","ledger","counterparty","note","submitDate").all {saved.text(it)==f.text(it)}&&load().items.filter {it.claimId==f.id}.associate {it.expenseId to it.requestedMinor}==amounts.mapValues {LedgerRules.minor(it.value)}) {"报销单已更新，请重新核对"}
            return@write f.id
        }
        require(dao.drafts().any {it.id==f.id&&LedgerForm.parse(it.payload).kind=="claim"}) {"报销草稿已关闭"}
        saveClaimCore(f.text("name"),f.text("ledger"),amounts,f.text("counterparty"),f.text("note"),f.id,f.revision,f.text("submitDate").ifBlank {null},f.text("confirmCorrection")=="true")
    }
    private suspend fun writeCore(f:LedgerForm,allowWarnings:Boolean=false,external:String?=null,batch:String?=null):String {
        UUID.fromString(f.id);val owner=records.owner();val now=System.currentTimeMillis();val s=load();val old=s.transactions.find {it.id==f.id}
        val signature=hash(f.json())
        if(old!=null&&old.deletedAt==null&&old.saveKey==signature) {dao.removeDraft(f.id);return old.id}
        require(old?.deletedAt==null) {"账目已删除，请先恢复"};require((old?.revision ?:0)==f.revision) {"账目已被修改，草稿保留，请重新核对"}
        val type=f.kind;require(type in setOf("expense","income","transfer","refund","reimbursement")) {"请选择有效账目类型"}
        val amount=LedgerRules.minor(f.text("amount"));val date=LocalDate.parse(f.text("date",LocalDate.now().toString()));require(date<=LocalDate.now()) {"未来账目只能保留草稿"}
        val time=f.text("time").ifBlank {null}?.let(LocalTime::parse);val zone=ZoneId.of(f.text("zone",ZoneId.systemDefault().id))
        val instant=time?.let {val dt=date.atTime(it);require(zone.rules.getValidOffsets(dt).isNotEmpty()) {"此时区不存在该时刻"};dt.atZone(zone).toInstant().toEpochMilli()}
        fun nullable(key:String)=f.text(key).ifBlank {null}
        var ledger=nullable("ledger") ?:s.defaultLedger()?.id ?:error("请先创建账本")
        var category=nullable("category");val account=nullable("account");val to=nullable("toAccount");var budget=type=="expense"&&f.text("budget","true")=="true"
        val original=if(type=="refund") nullable("original") else null;val claim=if(type=="reimbursement") nullable("claim") else null
        if(old!=null) {
            val dependencies=s.transactions.any {it.originalExpenseId==old.id}||s.items.any {it.expenseId==old.id}
            require(!dependencies||old.type==type) {"有关联退款或报销，请先处理依赖，再变更类型"}
            require(old.type!="transfer"||type=="transfer"||s.transactions.none {it.relatedTransactionId==old.id}) {"此转账存在手续费历史，请保留转账类型，或另建新的收支账目"}
            require(old.relatedTransactionId==null || ledger==old.ledgerId&&type==old.type) {"来源关联费用不能直接更换类型或账本"}
            val linkedPayment=paymentLinked(old.id)
            require(!linkedPayment || old.type==type&&old.ledgerId==ledger) {"账目关联生活付款，更换类型或账本前请先在功能箱解除关联；金额与付款日期更正会同步来源"}
            require(itemReferences(old.id).all {if(it.section=="item_purchase") type=="expense" else type=="income"}) {"此账目仍被物品购买或出售引用，更换收支类型前请先在物品中解除关联"}
        }
        if(type=="refund") {
            val expense=s.transactions.find {it.id==original&&it.type=="expense"&&it.deletedAt==null} ?:error("请先选择有效原支出")
            require(account!=null) {"退款请指定到账账户"};require(date.toString()>=expense.occurredOn) {"退款日期不能早于原消费"}
            require(s.transactions.filter {it.deletedAt==null&&it.type=="refund"&&it.originalExpenseId==original&&it.id!=f.id}.sumOf {it.amountMinor}+amount<=expense.amountMinor) {"累计退款不能超过原消费"}
            ledger=expense.ledgerId;category=expense.categoryId;budget=expense.includeInBudget
        } else if(type=="reimbursement") {
            val c=s.claims.find {it.id==claim&&it.deletedAt==null&&it.status=="submitted"} ?:error("报销单尚未提交或已取消")
            require(!c.needsReview) {"报销单待核对，先更正费用分配"};require(account!=null) {"回款请指定到账账户"}
            require(c.submittedOn==null||date.toString()>=c.submittedOn) {"到账日期不能早于提交日期"}
            require(s.transactions.filter {it.deletedAt==null&&it.claimId==claim&&it.id!=f.id}.sumOf {it.amountMinor}+amount<=s.claimRequested(c.id)) {"累计回款不能超过申请金额"}
            ledger=c.settlementLedgerId;category=null;budget=false
        }
        require(s.ledgers.any {it.id==ledger&&it.ownerId==owner&&(it.archivedAt==null||old?.ledgerId==ledger)}) {"账本不存在或已归档"}
        if(type in setOf("expense","income")) {
            if(category==null) category=s.categories.firstOrNull {it.direction==type&&it.protected}?.id
            require(s.categories.any {it.id==category&&it.ownerId==owner&&it.direction==type&&(it.active||old?.categoryId==category)&&s.categories.none {child->child.parentId==it.id}}) {"请选择对应方向的有效叶子分类"}
        }
        if(type=="transfer") {category=null;budget=false;require(account!=null&&to!=null&&account!=to) {"转账需要两个不同账户"}}
        fun checkAccount(id:String?) {if(id!=null) {val a=s.accounts.find {it.id==id&&it.ownerId==owner} ?:error("账户不存在");require(a.archivedAt==null || old?.accountId==id||old?.toAccountId==id) {"账户已归档"};require(date.toString()>=a.openingAsOfDate) {"账目日期早于账户期初基准日，请先核对期初"}}}
        checkAccount(account);if(type=="transfer") checkAccount(to)
        require(f.text("note").length<=50000) {"备注最多 50000 字"}
        if(old?.type=="expense") {
            require(amount>=s.refunded(old.id)) {"原消费不能小于累计退款"}
            require(s.transactions.none {it.deletedAt==null&&it.originalExpenseId==old.id&&it.occurredOn<date.toString()}) {"原消费日期不能晚于已发生的退款"}
        }
        val item=LedgerTransaction(f.id,owner,ledger,type,amount,date.toString(),time?.toString(),zone.id,instant,category,account,if(type=="transfer") to else null,
            f.text("channel"),f.text("merchant"),f.text("location"),f.text("subject"),f.text("note"),budget,original,claim,old?.relatedTransactionId,external ?: old?.externalKey,batch ?: old?.importBatchId,old?.createdAt ?:now,now,(old?.revision ?:0)+1,null,signature)
        if(old!=null) syncLinkedExpense(old,item)
        dao.put(item);rebuildEntries(item)
        if(type=="expense") s.transactions.filter {it.type=="refund"&&it.originalExpenseId==item.id}.forEach {refund->dao.put(refund.copy(ledgerId=ledger,categoryId=category,includeInBudget=budget,updatedAt=now,revision=refund.revision+1))}
        if(type=="transfer") {
            val feeAmount=if(f.text("fee").isBlank()) 0 else LedgerRules.minor(f.text("fee"),allowZero=true)
            val feeId=UUID.nameUUIDFromBytes((f.id+"/fee").toByteArray()).toString();val oldFee=s.transactions.find {it.id==feeId}
            if(oldFee!=null&&paymentLinked(feeId)) require(feeAmount>0&&ledger==oldFee.ledgerId) {"手续费已关联生活付款，取消费用或更换账本前请先解除关联；金额与付款日期更正会同步来源"}
            if(feeAmount>0) {
                val feeAccount=nullable("feeAccount") ?:account!!;checkAccount(feeAccount)
                val feeCategory=nullable("feeCategory") ?:s.categories.find {it.name=="手续费"&&it.direction=="expense"}?.id
                require(s.categories.any {it.id==feeCategory&&it.direction=="expense"&&it.active&&s.categories.none {c->c.parentId==it.id}}) {"请选择有效手续费分类"}
                if(oldFee!=null) require(s.refunded(oldFee.id)==0L&&s.items.none {it.expenseId==oldFee.id}) {"手续费已有退款或报销，请从原费用更正"}
                val fee=LedgerTransaction(feeId,owner,ledger,"expense",feeAmount,date.toString(),time?.toString(),zone.id,instant,feeCategory,feeAccount,subject="转账手续费",includeInBudget=true,relatedTransactionId=item.id,createdAt=oldFee?.createdAt ?:now,updatedAt=now,revision=(oldFee?.revision ?:0)+1)
                if(oldFee!=null) syncLinkedExpense(oldFee,fee)
                dao.put(fee);rebuildEntries(fee)
            } else if(oldFee!=null&&oldFee.deletedAt==null) {require(s.refunded(feeId)==0L&&s.items.none {it.expenseId==feeId}) {"手续费存在关联，请先处理"};noDependencies(feeId);dao.put(oldFee.copy(deletedAt=now));dao.clearEntries(feeId)}
        }
        val tags=f.text("tags").split(",").filter {it.isNotBlank()}.toSet();require(tags.all {id->records.dao.tags().any {it.id==id&&it.ownerId==owner}}) {"标签不可用"}
        records.dao.clearTags(f.id,"transaction");records.dao.putTags(tags.map {EntityTag("transaction",f.id,it)})
        bindToProject(db,f.text("project").ifBlank {null},"transaction",item.id,owner)
        val draft=dao.drafts().any {it.id==f.id}
        if(draft) {val refs=records.dao.linksFor("ledger_draft",f.id);records.requireAvailableAttachments(refs)
            val detached=records.dao.linksFor("transaction",f.id).map {it.attachmentId};records.dao.clearLinks("transaction",f.id);records.dao.putLinks(refs.map {it.copy(entityType="transaction")});records.dao.clearLinks("ledger_draft",f.id);dao.removeDraft(f.id);clean(detached)}
        reviewClaims();log(item.id,if(old==null) "create" else "edit",type+" · "+LedgerRules.display(amount)+" · "+date)
        return item.id
    }
    private suspend fun rebuildEntries(t:LedgerTransaction) {
        dao.clearEntries(t.id);if(t.deletedAt!=null)return
        if(t.accountId!=null) dao.put(AccountEntry(t.id,t.accountId,when(t.type) {"expense","transfer"->-t.amountMinor;else->t.amountMinor},"primary"))
        if(t.type=="transfer"&&t.toAccountId!=null) dao.put(AccountEntry(t.id,t.toAccountId,t.amountMinor,"incoming"))
    }
    private suspend fun reviewClaims() {
        val s=load();s.claims.filter {it.deletedAt==null&&it.status!="cancelled"}.forEach {c->
            val bad=s.items.filter {it.claimId==c.id}.any {line->val t=s.transactions.find {it.id==line.expenseId&&it.deletedAt==null&&it.type=="expense"};t==null||s.items.filter {it.expenseId==line.expenseId&&s.claims.any {claim->claim.id==it.claimId&&claim.deletedAt==null&&claim.status!="cancelled"}}.sumOf {it.requestedMinor}>(t.amountMinor-s.refunded(t.id))}
            if(c.needsReview!=bad) dao.put(c.copy(needsReview=bad))
        }
    }
    private suspend fun thresholds() {val s=load();s.budgets.filter {it.alertsEnabled}.forEach {b->listOf(80,100).forEach {p->if(s.budgetUsed(b)*100>=b.limitMinor*p&&s.alerts.none {it.budgetId==b.id&&it.threshold==p}) dao.put(BudgetAlert(b.id,p))}}}
    private suspend fun noDependencies(id:String) {
        val s=load();require(s.transactions.none {it.deletedAt==null&&(it.originalExpenseId==id)}&&s.items.none {it.expenseId==id&&s.claims.any {c->c.id==it.claimId&&c.deletedAt==null&&c.status!="cancelled"}}) {"此支出有关联退款或报销分配，请先处理关联"}
        require(!paymentLinked(id)) {"此账目关联生活付款，请先在功能箱解除付款关系"}
        require(itemReferences(id).isEmpty()) {"此账目仍被物品购买或出售引用，请先在物品中解除关联"}
    }
    private fun paymentLinked(id:String):Boolean {
        fun table(name:String)=db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?",arrayOf(name)).use {it.moveToFirst()}
        if(table("tool_payment_settlements")) db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tool_payment_settlements WHERE expenseId=?",arrayOf(id)).use {it.moveToFirst();if(it.getLong(0)>0)return true}
        if(table("tool_children")) db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM tool_children WHERE expenseId=?",arrayOf(id)).use {it.moveToFirst();if(it.getLong(0)>0)return true}
        return false
    }
    /** The source fact and financial entries are changed under the same coordinator and Room transaction. */
    private suspend fun syncLinkedExpense(old:LedgerTransaction,updated:LedgerTransaction) {
        val tools=db.tools();val payments=tools.settlements().filter {it.expenseId==old.id};val children=tools.children().filter {it.expenseId==old.id}
        if(payments.isEmpty()&&children.isEmpty()) return
        require(payments.size+children.size==1) {"此支出存在多处付款来源，请先核对并解除冲突关系"}
        require(old.type=="expense"&&updated.type=="expense"&&old.ledgerId==updated.ledgerId&&old.ownerId==updated.ownerId&&old.deletedAt==null) {"关联支出只能更正原账，不能直接更换类型或账本"}
        val ledger=dao.ledgers().firstOrNull {it.id==updated.ledgerId&&it.ownerId==updated.ownerId};require(ledger?.currency=="CNY") {"来源与账目的币种或归属不一致"}
        val persisted=dao.transactions().firstOrNull {it.id==old.id};require(persisted!=null&&persisted.revision==old.revision&&persisted.amountMinor==old.amountMinor&&persisted.occurredOn==old.occurredOn) {"账目已变化，请重新核对后更正"}
        val entries=tools.entries();val now=System.currentTimeMillis()
        payments.singleOrNull()?.let {settlement->
            val period=tools.periods().firstOrNull {it.id==settlement.periodId} ?:error("原付款期间缺失，请先核对来源")
            val source=entries.firstOrNull {it.id==period.sourceId&&it.ownerId==updated.ownerId&&it.kind in setOf("subscription","bill")} ?:error("原付款项目缺失或归属不符")
            require(period.status=="paid"&&settlement.mode in setOf("new","existing")&&settlement.actualMinor==old.amountMinor&&settlement.paidOn==old.occurredOn) {"原付款登记与账目不一致，请先核对来源"}
            tools.put(settlement.copy(actualMinor=updated.amountMinor,paidOn=updated.occurredOn))
            if(old.amountMinor!=updated.amountMinor||old.occurredOn!=updated.occurredOn) tools.put(source.copy(revision=source.revision+1,updatedAt=now))
        }
        children.singleOrNull()?.let {child->
            val source=entries.firstOrNull {it.id==child.entryId&&it.ownerId==updated.ownerId&&it.kind in setOf("checklist","item")} ?:error("原清单或物品缺失或归属不符")
            require(child.costMinor==old.amountMinor) {"原条目费用与账目不一致，请先核对来源"}
            tools.put(child.copy(costMinor=updated.amountMinor))
            if(old.amountMinor!=updated.amountMinor||old.occurredOn!=updated.occurredOn) tools.put(source.copy(revision=source.revision+1,updatedAt=now))
        }
        if(old.amountMinor!=updated.amountMinor||old.occurredOn!=updated.occurredOn) log(old.id,"source_sync","联动付款来源："+LedgerRules.display(old.amountMinor)+" / "+old.occurredOn+" → "+LedgerRules.display(updated.amountMinor)+" / "+updated.occurredOn)
    }
    suspend fun manage(id:String,action:String,includeFee:Boolean=false)=write {
        val t=dao.transactions().find {it.id==id} ?:error("账目不存在")
        when(action) {
            "trash"->{noDependencies(id);val now=System.currentTimeMillis();val fees=dao.transactions().filter {it.relatedTransactionId==id&&it.deletedAt==null};fees.forEach {fee->
                if(includeFee) {noDependencies(fee.id);dao.put(fee.copy(deletedAt=now,revision=fee.revision+1));dao.clearEntries(fee.id)} else dao.put(fee.copy(relatedTransactionId=null,revision=fee.revision+1))
            };dao.put(t.copy(deletedAt=now,revision=t.revision+1));dao.clearEntries(id)}
            "restore"->{require(t.deletedAt!=null);val s=load()
                if(t.type=="refund") {val original=s.transactions.find {it.id==t.originalExpenseId&&it.type=="expense"&&it.deletedAt==null} ?:error("原支出不可用");require(t.occurredOn>=original.occurredOn&&s.refunded(original.id)+t.amountMinor<=original.amountMinor) {"恢复后退款日期早于消费，或累计退款超出原消费"}}
                if(t.type=="reimbursement") {val c=s.claims.find {it.id==t.claimId&&it.deletedAt==null&&it.status=="submitted"} ?:error("报销单不可用");require(!c.needsReview&&s.claimPaid(c.id)+t.amountMinor<=s.claimRequested(c.id)&&(c.submittedOn==null||t.occurredOn>=c.submittedOn)&&t.ledgerId==c.settlementLedgerId) {"恢复后回款金额、日期或结算账本不一致，请先核对"}}
                listOfNotNull(t.accountId,t.toAccountId).forEach {a->require(s.accounts.any {it.id==a&&it.openingAsOfDate<=t.occurredOn}) {"账户或期初基准不支持恢复"}}
                val restored=t.copy(deletedAt=null,revision=t.revision+1,updatedAt=System.currentTimeMillis());dao.put(restored);rebuildEntries(restored)}
            else->error("不支持的账目操作")
        };reviewClaims();thresholds();log(id,action,"账目状态变更")
    }
    suspend fun permanentlyDelete(id:String)=write {val t=dao.transactions().first {it.id==id};require(t.deletedAt!=null) {"请先移入回收站"};noDependencies(id)
        require(dao.transactions().none {it.originalExpenseId==id||it.relatedTransactionId==id}&&dao.items().none {it.expenseId==id}&&dao.drafts().none {it.id==id}) {"仍有历史或草稿引用"}
        require(db.tools().links().none {it.entityType=="ledger"&&it.entityId==id}) {"仍被专题或物品引用，请先解除关联"}
        val refs=records.dao.linksFor("transaction",id).map {it.attachmentId}
        records.dao.clearLinks("transaction",id);records.dao.clearTags(id,"transaction");dao.clearEntries(id)
        dao.detachDeletedImport(id)
        dao.removeTransaction(id);clean(refs);log(id,"delete","永久删除已回收账目，保留导入去重编号")}
    suspend fun saveLedger(name:String,id:String?=null,makeDefault:Boolean=false,archive:Boolean=false)=write {
        val owner=records.owner();val n=LedgerRules.name(name);val all=dao.ledgers();val old=all.find {it.id==id};require(all.none {it.id!=id&&it.normalizedName==RecordRules.normalized(n)}) {"账本名称已存在"}
        require(!archive||old?.isDefault!=true) {"请先设置另一个默认账本"};if(makeDefault) all.forEach {if(it.isDefault) dao.put(it.copy(isDefault=false))}
        dao.put(Ledger(id ?:newId(),owner,n,RecordRules.normalized(n),isDefault=makeDefault||old?.isDefault==true||all.isEmpty(),sortOrder=old?.sortOrder ?:all.size,archivedAt=if(archive) System.currentTimeMillis() else null))
    }
    suspend fun saveCategory(name:String,direction:String,parent:String?=null,id:String?=null,active:Boolean=true)=write {
        val all=dao.categories();val old=all.find {it.id==id};val n=LedgerRules.name(name);require(direction in setOf("income","expense"));require(old?.protected!=true||n==old.name&&active&&parent==null&&direction==old.direction) {"未分类不能改名或停用"}
        require(old==null||old.direction==direction) {"已有分类不能更换收支方向"}
        if(parent!=null) require(parent!=id&&all.any {it.id==parent&&it.parentId==null&&it.direction==direction&&it.active}&&dao.transactions().none {it.categoryId==parent}) {"父分类需同方向且未直接使用，分类最多两层"}
        require(old==null||parent==old.parentId||all.none {it.parentId==old.id}) {"带子分类的分类不能成为子级"}
        val key=direction+"|"+(parent ?:"root")+"|"+RecordRules.normalized(n);require(all.none {it.id!=id&&it.uniqueKey==key}) {"同级分类名称已存在"}
        dao.put(LedgerCategory(id ?:newId(),records.owner(),n,direction,parent,key,old?.icon ?:"category",active,old?.protected ?:false,old?.sortOrder ?:all.size))
    }
    suspend fun saveAccount(name:String,type:String,opening:String,asOf:String,note:String="",tail:String="",id:String?=null,confirmOpeningChange:Boolean=false,archive:Boolean=false)=write {
        val all=dao.accounts();val old=all.find {it.id==id};val n=LedgerRules.name(name);val amount=LedgerRules.minor(opening,allowZero=true,signed=true);LocalDate.parse(asOf)
        require(type in setOf("cash","bank","payment","other")) {"请选择账户类型"};require(all.none {it.id!=id&&it.normalizedName==RecordRules.normalized(n)}) {"账户名称已存在"}
        val tx=dao.transactions().filter {id!=null&&(it.accountId==id||it.toAccountId==id)};require(tx.none {it.occurredOn<asOf}) {"基准日不能晚于已有流水日期"}
        if(old!=null&&(old.openingBalanceMinor!=amount||old.openingAsOfDate!=asOf)&&tx.isNotEmpty()) require(confirmOpeningChange) {"修改期初会改变历史余额，请确认影响后再保存"}
        val a=MoneyAccount(id ?:newId(),records.owner(),n,RecordRules.normalized(n),type,amount,asOf,note,tail,old?.sortOrder ?:all.size,old?.hidden ?:false,if(archive) System.currentTimeMillis() else null);dao.put(a);log(a.id,"account","账户期初 "+LedgerRules.display(amount)+"，基准日 "+asOf)
    }
    suspend fun calibrate(accountId:String,date:String,target:String,reason:String,commandId:String=newId())=write {
        val s=load();val a=s.accounts.find {it.id==accountId&&it.archivedAt==null} ?:error("请选择有效账户");LocalDate.parse(date);require(date>=a.openingAsOfDate&&date<=LocalDate.now().toString());require(reason.isNotBlank()) {"请填写余额校准原因"}
        val signature=hash(listOf(accountId,date,target,reason).joinToString("|"))
        s.transactions.find {it.id==commandId}?.let {require(it.type=="adjustment"&&it.deletedAt==null&&it.saveKey==signature) {"校准编号已被使用，请重新核对"};return@write}
        val desired=LedgerRules.minor(target,allowZero=true,signed=true);val before=s.balance(accountId,date);val diff=desired-before
        require(diff in -LedgerRules.MAX_MINOR..LedgerRules.MAX_MINOR) {"本次校准差额超过单笔金额范围，请先核对账户流水"}
        require(diff!=0L) {"当前余额与目标相同，无需校准"};val t=LedgerTransaction(commandId,records.owner(),s.defaultLedger()!!.id,"adjustment",diff,date,accountId=accountId,subject="余额校准",note=reason,includeInBudget=false,saveKey=signature)
        dao.put(t);rebuildEntries(t);log(commandId,"calibrate",LedgerRules.display(before)+" → "+LedgerRules.display(desired)+"，原因："+reason)
    }
    suspend fun saveBudget(ledgerId:String,month:String,categoryId:String?,amount:String,alerts:Boolean=true,id:String?=null)=write {
        val s=load();YearMonth.parse(month);require(s.ledgers.any {it.id==ledgerId&&it.archivedAt==null});if(categoryId!=null) require(s.categories.any {it.id==categoryId&&it.direction=="expense"&&s.categories.none {c->c.parentId==it.id}}) {"分类预算需使用支出叶子分类"}
        val key=categoryId ?:"total";val existing=s.budgets.find {it.ledgerId==ledgerId&&it.month==month&&it.scopeKey==key}
        require(existing==null||id==existing.id) {"该月份已有此范围预算，请编辑原预算"};dao.put(MonthlyBudget(id ?:newId(),records.owner(),ledgerId,month,categoryId,key,LedgerRules.minor(amount),alerts));thresholds()
    }
    suspend fun removeBudget(id:String)=write {
        dao.clearBudgetAlerts(id)
        dao.removeBudget(id)
    }
    suspend fun readBudgetAlert(id:String,threshold:Int)=write {dao.alerts().find {it.budgetId==id&&it.threshold==threshold}?.let {dao.put(it.copy(readAt=System.currentTimeMillis()))}}
    suspend fun saveTemplate(name:String,f:LedgerForm,id:String?=null)=write {require(f.kind in setOf("expense","income","transfer"));val data=JSONObject(f.payload);listOf("original","claim","tags","date","time","external","fee","linkedSource","contextSource","project").forEach(data::remove);dao.put(TransactionTemplate(id ?:newId(),records.owner(),LedgerRules.name(name),f.copy(id=newId(),revision=0,payload=data.toString()).json()))}
    suspend fun removeTemplate(id:String)=write {dao.removeTemplate(id)}
    suspend fun saveClaim(name:String,ledgerId:String,allocations:Map<String,String>,counterparty:String="",note:String="",id:String?=null,revision:Long=0,submitDate:String?=null,confirmCorrection:Boolean=false):String=write {saveClaimCore(name,ledgerId,allocations,counterparty,note,id,revision,submitDate,confirmCorrection)}
    private suspend fun saveClaimCore(name:String,ledgerId:String,allocations:Map<String,String>,counterparty:String,note:String,id:String?,revision:Long,submitDate:String?,confirmCorrection:Boolean):String {
        require(note.length<=50000&&counterparty.length<=120) {"报销备注最多五万字，对方称呼最多 120 字"}
        val s=load();val old=s.claims.find {it.id==id};require(old?.deletedAt==null) {"报销单已删除，请先恢复"};require((old?.revision ?:0)==revision) {"报销单已更新，请重新打开"};val key=id ?:newId();UUID.fromString(key);require(s.ledgers.any {it.id==ledgerId&&it.archivedAt==null})
        require(old==null||old.settlementLedgerId==ledgerId||s.transactions.none {it.claimId==key}) {"已有回款历史，不能更换报销结算账本"}
        require(old?.status!="cancelled") {"已取消报销单不能修改"};if(submitDate!=null) require(LocalDate.parse(submitDate)<=LocalDate.now()) {"提交日期不能在未来"}
        require(submitDate==null||s.transactions.none {it.deletedAt==null&&it.claimId==key&&it.occurredOn<submitDate}) {"提交日期不能晚于已有回款日期"}
        val lines=allocations.map {(expense,amount)->ReimbursementItem(key,expense,LedgerRules.minor(amount))};require(submitDate==null||lines.isNotEmpty()) {"提交报销前请至少分配一笔支出"}
        if(old?.status=="submitted"&&lines.toSet()!=s.items.filter {it.claimId==key}.toSet()) require(confirmCorrection) {"已提交的费用调整需要明确确认更正"}
        lines.forEach {line->val t=s.transactions.find {it.id==line.expenseId&&it.type=="expense"&&it.deletedAt==null} ?:error("支出不存在或已删除")
            val other=s.items.filter {it.expenseId==t.id&&it.claimId!=key&&s.claims.any {c->c.id==it.claimId&&c.deletedAt==null&&c.status!="cancelled"}}.sumOf {it.requestedMinor}
            require(line.requestedMinor+other<=t.amountMinor-s.refunded(t.id)) {"费用已被分配或退款，可报额度不足"}
        }
        require(lines.sumOf {it.requestedMinor}>=s.claimPaid(key)) {"申请额不能低于已回款额"}
        dao.put(ReimbursementClaim(key,records.owner(),LedgerRules.name(name),ledgerId,submitDate ?:old?.submittedOn,if(submitDate!=null||old?.status=="submitted") "submitted" else "draft",false,counterparty,note,(old?.revision ?:0)+1))
        dao.clearItems(key);lines.forEach {dao.put(it)}
        if(s.drafts.any {it.id==key&&LedgerForm.parse(it.payload).kind=="claim"}) {
            val refs=records.dao.linksFor("ledger_draft",key);records.requireAvailableAttachments(refs)
            val detached=records.dao.linksFor("reimbursement",key).map {it.attachmentId};records.dao.clearLinks("reimbursement",key);records.dao.putLinks(refs.map {it.copy(entityType="reimbursement")});records.dao.clearLinks("ledger_draft",key);dao.removeDraft(key);clean(detached)
        }
        reviewClaims();log(key,"claim","报销申请 "+LedgerRules.display(lines.sumOf {it.requestedMinor}));return key
    }
    suspend fun manageClaim(id:String,action:String)=write {val s=load();val c=s.claims.first {it.id==id};when(action) {
        "cancel"->{require(s.claimPaid(id)==0L) {"已有实际回款，请先更正回款账目"};dao.put(c.copy(status="cancelled",revision=c.revision+1))}
        "trash"->{require(s.transactions.none {it.claimId==id}) {"存在回款历史，不能删除报销单"};dao.put(c.copy(deletedAt=System.currentTimeMillis(),revision=c.revision+1))}
        "restore"->{require(c.deletedAt!=null);dao.put(c.copy(deletedAt=null,revision=c.revision+1));reviewClaims()}
        else->error("不支持的报销操作")};log(id,action,"报销状态变更")}
    /** Caller rechecks its business idempotency and relationship limits inside [settle], never outside the transaction. */
    suspend fun settlePayment(form:LedgerForm?,existingExpenseId:String?,actualMinor:Long,paidOn:String,settle:suspend (String)->Unit):String=write {
        require((form!=null) xor (existingExpenseId!=null)) {"请选择新建支出或关联已有支出"};val id=if(form!=null) {require(form.kind=="expense");writeCore(form)} else existingExpenseId!!
        val t=dao.transactions().firstOrNull {it.id==id&&it.deletedAt==null&&it.type=="expense"} ?:error("只能关联有效支出")
        require(t.amountMinor==actualMinor&&t.occurredOn==paidOn) {"支出金额或日期与实际付款不一致"};settle(id);thresholds();id
    }
    suspend fun prepareCsv(text:String,ledgerId:String,accountId:String?,source:String="CSV",sourceAccount:String="",mapping:Map<String,Int> = mapOf("date" to 0,"amount" to 1,"type" to 2,"category" to 3,"merchant" to 4,"note" to 5,"external" to 6)):String=write {
        val s=load();require(s.ledgers.any {it.id==ledgerId&&it.archivedAt==null});val parsed=LedgerRules.csv(text);require(parsed.size>=2) {"CSV 需包含表头及至少一行内容"};require(listOf("date","amount","type").all {it in mapping}) {"请映射日期、金额和类型"}
        val digest=hash(text);s.batches.find {it.fileHash==digest&&it.source==source&&it.sourceAccount==sourceAccount&&it.ledgerId==ledgerId}?.let {return@write it.id}
        val batch=LedgerImportBatch(ownerId=records.owner(),source=source,sourceAccount=sourceAccount,fileHash=digest,ledgerId=ledgerId,accountId=accountId,mapping=JSONObject(mapping).toString());dao.put(batch)
        parsed.drop(1).forEachIndexed {index,cells->
            fun cell(k:String)=mapping[k]?.let {cells.getOrNull(it)}.orEmpty().trim()
            var f=LedgerForm(when(cell("type").lowercase()) {"expense","支出"->"expense";"income","收入"->"income";else->cell("type")}).set("ledger",ledgerId).set("amount",cell("amount")).set("date",cell("date")).set("account",accountId.orEmpty()).set("merchant",cell("merchant")).set("note",cell("note"))
            val category=s.categories.find {it.name==cell("category")&&it.direction==f.kind&&it.active&&s.categories.none {c->c.parentId==it.id}};if(category!=null) f=f.set("category",category.id)
            val external=cell("external").ifBlank {null}?.let {JSONArray(listOf(source,sourceAccount,it)).toString()};var error=""
            runCatching {require(f.kind in setOf("expense","income")) {"首版 CSV 仅导入普通收支；退款、回款及转账需在关联业务中登记"};LedgerRules.minor(cell("amount"));require(LocalDate.parse(cell("date"))<=LocalDate.now()) {"不能导入未来正式账目"}}.exceptionOrNull()?.let {error=it.message ?:"日期或金额格式错误"}
            val suspected=s.transactions.any {it.deletedAt==null&&it.type==f.kind&&it.occurredOn==cell("date")&&it.counterparty==cell("merchant")&&runCatching {LedgerRules.minor(cell("amount"))==it.amountMinor}.getOrDefault(false)}
            dao.put(LedgerImportRow(batch.id,index+2,JSONArray(cells).toString(),f.json(),external,result=if(error.isEmpty()) "pending" else "error",error=error,suspectedDuplicate=suspected))
        };batch.id
    }
    suspend fun commitImport(batchId:String,allowSuspected:Boolean=false)=write {
        val b=dao.batches().first {it.id==batchId}
        val rows=dao.rows()
        val identities=(dao.transactions().mapNotNull {it.externalKey}+
            rows.filter {it.result in setOf("imported","deleted")}.mapNotNull {it.dedupKey}).toMutableSet()
        rows.filter {it.batchId==batchId&&it.result in setOf("pending","error")}.forEach {row->
            if(row.error.isNotEmpty()) return@forEach
            if(row.dedupKey!=null&&row.dedupKey in identities) {dao.put(row.copy(result="skipped",error="可靠外部编号已导入过（含已删除账目）"));return@forEach}
            if(row.suspectedDuplicate&&!allowSuspected) return@forEach
            try {val id=writeCore(LedgerForm.parse(row.parsedValues),external=row.dedupKey,batch=batchId);dao.put(row.copy(result="imported",transactionId=id));row.dedupKey?.let(identities::add)}
            catch(e:Exception) {throw IllegalArgumentException("第 "+row.rowNumber+" 行未通过业务校验，本批次尚未新增；"+e.message,e)}
        };dao.put(b.copy(status=if(dao.rows().filter {it.batchId==batchId}.all {it.result in setOf("imported","skipped","deleted")}) "completed" else "partial"));thresholds()
    }
    suspend fun undoImport(batchId:String)=write {
        val b=dao.batches().first {it.id==batchId}
        dao.repairDeletedImportRows()
        val ids=dao.rows().filter {it.batchId==batchId&&it.result=="imported"}.mapNotNull {it.transactionId}.toSet()
        val transactions=dao.transactions().filter {it.id in ids&&it.deletedAt==null}
        transactions.forEach {noDependencies(it.id)}
        transactions.forEach {t->
            dao.put(t.copy(deletedAt=System.currentTimeMillis(),revision=t.revision+1));dao.clearEntries(t.id)
            log(t.id,"undo_import","撤销来源批次")
        }
        dao.put(b.copy(status="undone"));reviewClaims();thresholds()
    }
    suspend fun exportCsv(ledgerId:String?=null,from:String?=null,to:String?=null,types:Set<String>?=null,fields:List<String>?=null):String {
        from?.let(LocalDate::parse);to?.let(LocalDate::parse);require(from==null||to==null||from<=to) {"开始日期不能晚于结束日期"}
        val labels=linkedMapOf("date" to "日期","amount" to "金额","type" to "类型","category" to "分类","merchant" to "商户","note" to "备注","external" to "外部编号","time" to "时刻","ledger" to "账本","account" to "账户","id" to "交易编号")
        val selected=fields ?:labels.keys.toList();require(selected.isNotEmpty()&&selected.size==selected.toSet().size&&selected.all {it in labels}) {"请选择有效且不重复的导出字段"}
        require(types==null||types.isNotEmpty()&&types.all {it in setOf("expense","income","transfer","refund","reimbursement","adjustment")}) {"请选择至少一种有效账目类型"}
        val s=records.writes.withLock {db.withTransaction {load()}};require(ledgerId==null||s.ledgers.any {it.id==ledgerId}) {"导出账本不存在"}
        val rows=s.transactions.filter {it.deletedAt==null&&(ledgerId==null||it.ledgerId==ledgerId)&&(from==null||it.occurredOn>=from)&&(to==null||it.occurredOn<=to)&&(types==null||it.type in types)}.map {t->
            val values=mapOf("date" to t.occurredOn,"amount" to LedgerRules.display(t.amountMinor),"type" to t.type,"category" to s.categories.find {it.id==t.categoryId}?.name.orEmpty(),"merchant" to t.counterparty,"note" to t.note,"external" to t.externalKey.orEmpty(),"time" to t.occurredTime.orEmpty(),"ledger" to s.ledgers.find {it.id==t.ledgerId}?.name.orEmpty(),"account" to s.accounts.find {it.id==t.accountId}?.name.orEmpty(),"id" to t.id)
            selected.map {values.getValue(it)}
        }
        return "\uFEFF"+(listOf(selected.map {labels.getValue(it)})+rows).joinToString("\r\n") {row->row.joinToString(",",transform=LedgerRules::csvCell)}
    }
    private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
}
