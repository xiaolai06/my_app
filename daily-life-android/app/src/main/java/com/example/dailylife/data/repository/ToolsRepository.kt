package com.example.dailylife.data.repository
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.dailylife.core.*
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.tools.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit

class ToolsRepository(val db:DailyLifeDatabase,val records:RecordsRepository) {
    val dao=db.tools()
    private val garbage=mutableSetOf<String>()
    private suspend fun <T> write(block:suspend ()->T):T=records.writes.withLock {withContext(Dispatchers.IO) {
        garbage.clear();try {val result=db.withTransaction {block()};garbage.toList().forEach {id->runCatching {if(records.dao.attachmentUses(id)==0) records.dao.attachment(id)?.let {a->if(records.files.file(a).delete() || !records.files.file(a).exists()) records.dao.deleteAttachment(id)}}};result} finally {garbage.clear()}
    }}
    val snapshot=dao.changes(SimpleSQLiteQuery("SELECT 1")).map {db.withTransaction {load()}}
    suspend fun load()=ToolsSnapshot(dao.entries(),dao.children(),dao.logs(),dao.links(),dao.drafts(),dao.calculations(),dao.periods(),dao.settlements(),dao.preferences())
    suspend fun initialize() {records.initialize();records.writes.withLock {db.withTransaction {expand()}}}
    suspend fun draft(f:ToolForm)=records.writes.withLock {
        require(f.json().length<=100000) {"内容过长"}
        val current=dao.drafts().firstOrNull {it.id==f.id} ?: error("未完成编辑已保存或删除，请重新打开")
        val previous=ToolForm.parse(current.payload)
        require(previous.kind==f.kind && previous.revision==f.revision) {"编辑来源已变化，未完成内容仍保留，请重新核对"}
        dao.put(ToolDraft(f.id,f.kind,f.json()))
    }
    suspend fun prepare(f:ToolForm)=records.writes.withLock {db.withTransaction {
        dao.drafts().firstOrNull {it.id==f.id}?.let {current->
            val existing=ToolForm.parse(current.payload)
            require(existing.kind==f.kind) {"未完成编辑编号已被其他类型使用"}
            return@withTransaction existing
        }
        dao.put(ToolDraft(f.id,f.kind,f.json()))
        if(dao.entries().any {it.id==f.id}) records.dao.putLinks(records.dao.linksFor("tool",f.id).map {it.copy(entityType="tool_draft")})
        f
    }}
    suspend fun form(id:String):ToolForm {val e=dao.entries().first {it.id==id};val p=JSONObject(e.payload);return ToolForm(e.kind,e.id,e.revision,p.keys().asSequence().filter {it!="version"}.associateWith {p.get(it).toString()}+("title" to e.title)+("note" to e.note)+("pinned" to e.pinned.toString())+("items" to dao.links().filter {it.entryId==id && it.section=="outfit"}.joinToString(",") {it.entityId}))}
    internal fun validate(f:ToolForm) {
        require(f.kind in toolKinds.map {it.first}+"checklist_template") {"不支持的功能"};PlanRules.title(f.text("title"));require(f.text("note").length<=50000) {"备注最多五万字"}
        fun date(k:String)=f.text(k).takeIf {it.isNotBlank()}?.let(ToolRules::date)
        val start=date("date");val end=date("end");date("warranty");date("expiry");date("careDate");date("trialEnd");date("effective");val billingStart=date("billingStart");val billingEnd=date("billingEnd")
        require(billingStart==null && billingEnd==null || billingStart!=null && billingEnd!=null && billingEnd>=billingStart) {"所属期间的起止日期需完整且结束不早于开始"}
        if(f.text("reminderOn")=="true") {LocalTime.parse(f.text("reminderTime","09:00"));require(f.text("reminderDays","0").toInt() in 0..366) {"提醒提前天数为 0 至 366"}}
        require(start==null || end==null || end>=start) {"结束日期不能早于开始日期"}
        require(f.fields.size<=80 && f.fields.all {it.key.length<=60 && it.value.length<=50000}) {"字段内容过长"}
        if(f.kind in setOf("item","outfit")) {
            val tags=wardrobeTags(f.text("styleTags"))
            require(tags.size<=10 && tags.all {it.length<=12}) {"最多 10 个风格标签，每个不超过 12 字"}
        }
        if(f.kind=="item" && f.text("wardrobe")=="true") {
            require(f.text("clothingType","top") in clothingCategories && f.text("warmth","all") in clothingWarmth) {"请选择有效衣物分类和温度偏好"}
            require(f.text("laundry","ready") in setOf("ready","wash","stored")) {"衣物状态不支持"}
            listOf("color","size","material","occasion").forEach {require(f.text(it).length<=80) {"衣物字段最多 80 字"}}
        }
        if(f.kind=="outfit") {
            require(f.text("occasion").length<=80) {"场景最多 80 字"}
            val ids=f.text("items").split(",").filter {it.isNotBlank()}
            require(ids.size<=30 && ids.distinct().size==ids.size) {"一套穿搭最多 30 件不同衣物"}
            ids.forEach {java.util.UUID.fromString(it)}
        }
        when(f.kind) {
            "anniversary"->{require(start!=null) {"请选择原始日期"};require(f.text("repeat","once") in listOf("once","yearly"));require(f.text("leap","feb28") in listOf("feb28","mar1","skip"))}
            "subscription","bill"->{require(start!=null) {"请选择首期日期"};if(f.kind=="subscription") {ToolRules.minor(f.text("amount","0"));require(f.text("status","active")!="trial" || date("trialEnd")!=null) {"试用中需要试用结束日"}}
                else f.text("amount").takeIf {it.isNotBlank()}?.let(ToolRules::minor)
                require(f.text("cycle","monthly") in listOf("once","daily","weekly","monthly","yearly"));require(f.text("interval","1").toInt() in 1..120) {"周期间隔为 1 至 120"}
                if(start!=null) {val span=when(f.text("cycle","monthly")) {"daily"->ChronoUnit.DAYS.between(LocalDate.parse(start),LocalDate.now().plusMonths(18));"weekly"->ChronoUnit.WEEKS.between(LocalDate.parse(start),LocalDate.now().plusMonths(18));"yearly"->ChronoUnit.YEARS.between(LocalDate.parse(start),LocalDate.now().plusMonths(18));"once"->0L;else->ChronoUnit.MONTHS.between(LocalDate.parse(start),LocalDate.now().plusMonths(18))};require(span/f.text("interval","1").toInt()<10000) {"此锚点会生成超过一万期，请缩短范围或调整周期间隔"}}
                f.text("url").takeIf {it.isNotBlank()}?.let {require(it.startsWith("https://") || it.startsWith("http://")) {"管理链接只支持 http/https"};RecordRules.url(it)}
            }
            "loan"->{require(start!=null && start<=LocalDate.now().toString()) {"借还日期不能晚于今天"};require(f.text("person").isNotBlank()) {"请填写对方称呼"};ToolRules.milli(f.text("quantity","1"));require(f.text("unit","件").isNotBlank());require(f.text("direction","out") in listOf("out","in"))}
            "item"->{f.text("amount").takeIf {it.isNotBlank()}?.let(ToolRules::minor);f.text("soldAmount").takeIf {it.isNotBlank()}?.let(ToolRules::minor);date("soldDate");val warranty=date("warranty");require(start==null || warranty==null || warranty>=start) {"保修截止日不能早于购买日"}}
        }
    }
    suspend fun save(f:ToolForm)=write {
        validate(f);val old=dao.entries().find {it.id==f.id};require((old?.revision ?: 0)==f.revision) {"内容已变化，请保留草稿后重新打开"};require(old==null || old.deletedAt==null) {"请先恢复此内容"}
        val draftRefs=records.dao.linksFor("tool_draft",f.id)
        records.requireAvailableAttachments(draftRefs)
        require(old==null || old.kind==f.kind) {"已有内容不能更换类型，请新建另一项"}
        val now=System.currentTimeMillis();val p=JSONObject(f.fields).put("version",1).apply {remove("title");remove("note");remove("pinned");remove("_project");remove("items");if(f.kind=="item") {put("category",f.text("category").trim());put("location",f.text("location").trim())}}
        val state=if(f.kind=="subscription") f.text("status",old?.state ?: "active") else if(f.kind=="item") f.text("status",old?.state ?: "active") else old?.state ?: "active"
        if(f.kind in setOf("item","outfit") && f.fields.containsKey("styleTags")) p.put("styleTags",wardrobeTags(f.text("styleTags")).joinToString(","))
        if(f.kind=="loan") {val returned=dao.logs().filter {it.entryId==f.id && it.kind=="return"}.sumOf {it.quantityMilli};require(ToolRules.milli(f.text("quantity","1"))>=returned) {"原数量不能小于已归还数量"};require(old==null || old.field("date")==f.text("date")) {"已有借还日期不能改写"}}
        if(f.kind=="item" && state in listOf("sold","scrapped")) require(dao.entries().none {it.kind=="loan" && it.deletedAt==null && it.state!="cancelled" && it.field("item")==f.id && it.field("direction","out")=="out" && load().remaining(it)>0}) {"此物品仍有未归还借出，请先处理借还"}
        dao.put(ToolEntry(f.id,records.owner(),f.kind,PlanRules.title(f.text("title")),f.text("note"),p.toString(),state,f.text("pinned")=="true",old?.createdAt ?: now,now,(old?.revision ?: 0)+1,old?.archivedAt,old?.deletedAt))
        if(f.kind=="outfit") {
            val ids=f.text("items").split(",").filter {it.isNotBlank()}
            val available=dao.entries().filter {it.isClothing() && it.deletedAt==null && it.archivedAt==null && it.state !in setOf("sold","scrapped")}.map {it.id}.toSet()
            require(ids.all {it in available}) {"衣物已归档或移除，请重新选择；草稿仍保留"}
            dao.removeLinks(f.id)
            ids.forEachIndexed {index,id->dao.put(ToolLink(f.id,"tool",id,"outfit",index))}
        }
        if(f.kind in listOf("subscription","bill")) {
            val changed=old==null || listOf("amount","cycle","interval","date","end","billingStart","billingEnd").any {old.field(it)!=f.text(it)}
            val effective=if(old==null) f.text("date") else f.text("effective",LocalDate.now().toString());if(changed) {require(old==null || effective>=LocalDate.now().toString()) {"新费用规则只能从今天或未来生效"};require(dao.periods().none {it.sourceId==f.id && it.originalKey>=effective && it.status=="paid"}) {"未来已有结算，请选择更晚的生效日期"};dao.put(ToolLog(entryId=f.id,kind="payment_version",date=effective,payload=p.toString()))}
        }
        val oldRefs=records.dao.linksFor("tool",f.id).map {it.attachmentId};records.dao.clearLinks("tool",f.id)
        records.dao.putLinks(draftRefs.map {it.copy(entityType="tool")});records.dao.clearLinks("tool_draft",f.id)
        bindToProject(db,f.text("_project").ifBlank {null},f.kind,f.id,records.owner())
        dao.removeDraft(f.id);expand();clean(oldRefs)
    }
    suspend fun manage(id:String,action:String)=records.writes.withLock {db.withTransaction {
        val e=dao.entries().first {it.id==id};val now=System.currentTimeMillis()
        if(action=="trash" && e.kind=="item") require(dao.entries().none {it.kind=="loan" && it.field("item")==id && it.field("direction","out")=="out" && it.deletedAt==null && it.state!="cancelled" && load().remaining(it)>0}) {"还有未归还物品，请先处理借还"}
        dao.put(when(action) {"favorite"->e.copy(payload=JSONObject(e.payload).put("favorite",(!e.isFavorite()).toString()).toString());"trash"->e.copy(deletedAt=now);"restore"->e.copy(deletedAt=null);"archive"->e.copy(archivedAt=now);"unarchive"->e.copy(archivedAt=null);"pause"->e.copy(state=if(e.state=="paused") "active" else "paused");"cancel"->e.copy(state=if(e.state=="cancelled") "active" else "cancelled");"complete"->e.copy(state=if(e.state=="done") "active" else "done");"pin"->e.copy(pinned=!e.pinned);else->error("未知操作")}.copy(revision=e.revision+1,updatedAt=now))
        expand()
    }}
    suspend fun deleteForever(id:String)=write {
        val s=load();val e=s.entries.first {it.id==id};require(e.deletedAt!=null) {"请先移入回收站"};require(s.drafts.none {it.id==id}) {"请先处理此项草稿"}
        require(s.settlements.none {settle->settle.expenseId!=null && s.periods.any {it.id==settle.periodId && it.sourceId==id}}) {"仍有付款账目关联，请先在详情解除"}
        require(s.children.none {it.entryId==id && it.expenseId!=null}) {"条目仍有关联支出，请恢复后先解除关联"}
        require(s.links.none {it.entryId==id && it.section in listOf("item_purchase","item_sale")}) {"物品仍有购买或出售账目引用，请恢复后先解除"}
        require(s.links.none {it.entityId==id && it.entryId!=id}) {"仍被专题或穿搭引用，请先解除"}
        s.periods.filter {it.sourceId==id}.forEach {dao.removeSettlement(it.id)}
        dao.removePeriods(id);dao.removeChildren(id);dao.removeLogs(id);dao.removeLinks(id);dao.removeEntry(id)
        val refs=records.dao.linksFor("tool",id);records.dao.clearLinks("tool",id);clean(refs.map {it.attachmentId})
    }
    suspend fun discard(id:String)=write {dao.removeDraft(id);val refs=records.dao.linksFor("tool_draft",id);records.dao.clearLinks("tool_draft",id);clean(refs.map {it.attachmentId})}
    private fun clean(ids:List<String>) {garbage+=ids}
    suspend fun importFile(id:String,uri:Uri,imagesOnly:Boolean=false)=records.writes.withLock {
        require(dao.drafts().any {it.id==id});val refs=records.dao.linksFor("tool_draft",id);require(refs.size<50) {"最多 50 份附件"}
        val a=records.files.import(uri,records.owner())
        try {require(!imagesOnly || a.mime.startsWith("image/")) {"请选择照片文件"}
            db.withTransaction {records.dao.putAttachment(a);records.dao.putLinks(listOf(AttachmentLink("tool_draft",id,a.id,sortOrder=(refs.maxOfOrNull {it.sortOrder} ?: -1)+1)))}
        } catch(e:Exception) {records.files.file(a).delete();throw e}
    }
    suspend fun attach(id:String,file:String)=records.writes.withLock {
        require(dao.drafts().any {it.id==id}) {"编辑已关闭，请重新打开"}
        val ref=AttachmentLink("tool_draft",id,file)
        records.requireAvailableAttachments(listOf(ref))
        val refs=records.dao.linksFor("tool_draft",id);require(refs.size<50) {"最多 50 份附件"}
        if(refs.none {it.attachmentId==file}) records.dao.putLinks(listOf(ref.copy(sortOrder=(refs.maxOfOrNull {it.sortOrder} ?: -1)+1)))
    }
    /** Cover is the first image reference; draft ordering never mutates the saved item. */
    suspend fun photoCover(id:String,file:String)=records.writes.withLock {db.withTransaction {
        require(dao.drafts().any {it.id==id}) {"编辑已关闭，请重新打开"}
        val refs=records.dao.linksFor("tool_draft",id)
        val selected=refs.firstOrNull {it.attachmentId==file} ?: error("照片已移除")
        require(records.dao.attachment(file)?.mime?.startsWith("image/")==true) {"只能选择照片作为封面"}
        records.requireAvailableAttachments(listOf(selected))
        records.dao.clearLinks("tool_draft",id)
        records.dao.putLinks((listOf(selected)+refs.filter {it.attachmentId!=file}).mapIndexed {index,ref->ref.copy(sortOrder=index)})
    }}
    suspend fun detach(id:String,file:String)=write {records.dao.detach("tool_draft",id,file);clean(listOf(file))}
    suspend fun child(entryId:String,title:String,quantity:String="",unit:String="",group:String="",note:String="",id:String?=null,date:String?=null,cost:String="")=records.writes.withLock {db.withTransaction {
        val e=dao.entries().first {it.id==entryId && it.deletedAt==null};require(e.kind in listOf("checklist","item","checklist_template"));val old=dao.children().find {it.id==id};require(old==null || old.entryId==entryId)
        val q=quantity.takeIf {it.isNotBlank()}?.let(ToolRules::milli);require(q==null || unit.isNotBlank()) {"填写数量后请填写单位"};date?.let(ToolRules::date)
        require(note.length<=50000 && group.length<=120 && unit.length<=40) {"备注最多五万字，分组最多120字，单位最多40字"}
        if(e.kind=="item") require(date!=null) {"维修日期必填"}
        val amount=cost.takeIf {it.isNotBlank()}?.let(ToolRules::minor)
        require(old?.expenseId==null || old.costMinor==amount && old.date==date) {"条目已关联支出，请先解除关联后修改金额或日期"}
        dao.put(ToolChild(id ?: newId(),entryId,if(e.kind=="item") "maintenance" else "check",PlanRules.title(title),q,unit,group,note,date,amount,old?.expenseId,old?.completedAt,old?.sortOrder ?: dao.children().count {it.entryId==entryId}))
        if(e.state=="done" && old?.completedAt==null) dao.put(e.copy(state="active",revision=e.revision+1))
    }}
    suspend fun batchChildren(id:String,text:String,group:String="")=records.writes.withLock {db.withTransaction {
        val names=text.lines().filter {it.isNotBlank()}.map(PlanRules::title);require(names.size in 1..100) {"每次可添加 1 至 100 项，每行一项"};require(group.trim().length<=120) {"分组名称最多120字"}
        val entry=dao.entries().first {it.id==id && it.deletedAt==null && it.kind in listOf("checklist","checklist_template")};val offset=dao.children().count {it.entryId==id}
        names.forEachIndexed {index,name->dao.put(ToolChild(entryId=id,title=name,groupName=group.trim(),sortOrder=offset+index))};if(entry.state=="done") dao.put(entry.copy(state="active",revision=entry.revision+1))
    }}
    suspend fun childAction(id:String,action:String)=records.writes.withLock {db.withTransaction {val c=dao.children().first {it.id==id};val e=dao.entries().first {it.id==c.entryId && it.deletedAt==null};when(action) {"delete"->{require(c.expenseId==null) {"此条目仍有关联支出，请先解除关联"};dao.removeChild(id)};"up","down"->{val all=dao.children().filter {it.entryId==e.id}.toMutableList();val from=all.indexOfFirst {it.id==id};val to=(from+if(action=="up") -1 else 1).coerceIn(all.indices);val swap=all[from];all[from]=all[to];all[to]=swap;all.forEachIndexed {i,v->dao.put(v.copy(sortOrder=i))}};else->{dao.put(c.copy(completedAt=if(c.completedAt==null) System.currentTimeMillis() else null));if(c.completedAt!=null && e.state=="done") dao.put(e.copy(state="active"))}}}}
    /** Called only inside LedgerRepository.settlePayment's existing mutex and transaction. */
    internal suspend fun commitChildExpense(childId:String,actual:Long,date:String,expenseId:String) {
        val child=dao.children().find {it.id==childId} ?: error("条目已不存在，请刷新后核对")
        val source=dao.entries().find {it.id==child.entryId && it.deletedAt==null && it.archivedAt==null && it.kind in listOf("checklist","item")} ?: error("原清单或物品已删除、归档，请恢复后操作")
        require(child.expenseId==null) {"此条目已有支出关联，请勿重复记账"}
        require(actual in 1..99_999_999_999L) {"实际金额须大于零"};ToolRules.date(date);require(date<=LocalDate.now().toString()) {"付款日期不能晚于今天"}
        val expense=db.ledger().transactions().find {it.id==expenseId && it.deletedAt==null && it.type=="expense" && it.ownerId==source.ownerId}
        require(expense!=null && expense.amountMinor==actual && expense.occurredOn==date) {"请关联金额与日期一致的有效支出"}
        require(dao.children().none {it.expenseId==expenseId} && dao.settlements().none {it.expenseId==expenseId}) {"此支出已关联其他条目或付款期间"}
        dao.put(child.copy(expenseId=expenseId,costMinor=actual))
    }
    suspend fun detachChildExpense(childId:String)=records.writes.withLock {db.withTransaction {
        val child=dao.children().first {it.id==childId};require(dao.entries().any {it.id==child.entryId && it.deletedAt==null}) {"请先恢复原内容"};dao.put(child.copy(expenseId=null))
    }}
    suspend fun reset(id:String)=records.writes.withLock {db.withTransaction {dao.children().filter {it.entryId==id}.forEach {dao.put(it.copy(completedAt=null))};dao.entries().find {it.id==id}?.let {dao.put(it.copy(state="active"))}}}
    suspend fun copy(id:String,template:Boolean=false):String=records.writes.withLock {db.withTransaction {val e=dao.entries().first {it.id==id};val next=newId();dao.put(e.copy(id=next,title=e.title+(if(template) "模板" else "副本"),kind=if(template) "checklist_template" else if(e.kind=="checklist_template") "checklist" else e.kind,state="active",revision=1,createdAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis(),deletedAt=null,archivedAt=null));dao.children().filter {it.entryId==id}.forEach {dao.put(it.copy(id=newId(),entryId=next,completedAt=null,expenseId=null))};if(!template) records.dao.putLinks(records.dao.linksFor("tool",id).map {it.copy(entityId=next)});next}}
    suspend fun returned(id:String,date:String,quantity:String,note:String,logId:String?=null)=records.writes.withLock {db.withTransaction {
        val s=load();val e=s.entries.first {it.id==id && it.kind=="loan" && it.deletedAt==null};require(e.state!="cancelled") {"请先恢复借还"};ToolRules.date(date);require(date>=e.field("date") && date<=LocalDate.now().toString()) {"归还日期须在借还日至今天之间"}
        val q=ToolRules.milli(quantity);val old=s.logs.find {it.id==logId};require(old==null || old.entryId==id && old.kind=="return")
        require(q<=s.remaining(e)+(old?.quantityMilli ?: 0)) {"归还数量不能超过剩余数量"};dao.put(ToolLog(logId ?: newId(),id,"return",date,q,note))
    }}
    suspend fun wear(id:String,date:String)=records.writes.withLock {
        require(dao.entries().any {it.id==id && it.kind=="outfit" && it.deletedAt==null && it.archivedAt==null}) {"穿搭已移除"}
        ToolRules.date(date);require(date<=LocalDate.now().toString()) {"不能提前登记穿着"}
        val key=java.util.UUID.nameUUIDFromBytes(("wear:"+id+":"+date).toByteArray()).toString()
        if(dao.logs().none {it.id==key || it.entryId==id && it.kind=="wear" && it.date==date}) dao.put(ToolLog(id=key,entryId=id,kind="wear",date=date))
    }
    suspend fun removeWear(id:String)=records.writes.withLock {
        require(dao.logs().any {it.id==id && it.kind=="wear"});dao.removeLog(id)
    }
    suspend fun removeLog(id:String)=records.writes.withLock {val log=dao.logs().first {it.id==id};require(log.kind=="return") {"历史版本不可直接删除"};dao.removeLog(id)}
    suspend fun postpone(id:String,date:String)=records.writes.withLock {db.withTransaction {val e=dao.entries().first {it.id==id};ToolRules.date(date);require(date>=e.field("date")) {"归还日不能早于借还日"};dao.put(ToolLog(entryId=id,kind="postpone",date=LocalDate.now().toString(),note=e.field("end")+" → "+date));dao.put(e.copy(payload=JSONObject(e.payload).put("end",date).toString(),revision=e.revision+1))}}
    suspend fun link(id:String,type:String,target:String,section:String="overview")=records.writes.withLock {db.withTransaction {
        require(dao.entries().any {it.id==id && it.kind=="project" && it.deletedAt==null});require(target!=id)
        val valid=when(type) {"record"->records.dao.record(target)?.record?.deletedAt==null && records.dao.record(target)!=null;"task"->db.plans().tasks().any {it.id==target && it.deletedAt==null};"event"->db.plans().events().any {it.id==target && it.deletedAt==null};"habit"->db.plans().habits().any {it.id==target && it.deletedAt==null};"focus"->db.plans().sessions().any {it.id==target && it.deletedAt==null};"goal"->db.plans().goals().any {it.id==target && it.deletedAt==null};"tool"->dao.entries().any {it.id==target && it.deletedAt==null};"ledger"->db.ledger().transactions().any {it.id==target && it.deletedAt==null};else->false}
        require(valid) {"关联内容已不存在或类型不支持"};require(section in listOf("overview","task","record","material","checklist","ledger","goal")) {"不支持的专题分区"};if(dao.links().none {it.entryId==id && it.entityType==type && it.entityId==target}) dao.put(ToolLink(id,type,target,section,dao.links().count {it.entryId==id}))
    }}
    suspend fun unlink(id:String,type:String,target:String)=records.writes.withLock {dao.unlink(id,type,target)}
    suspend fun moveProjectLink(id:String,type:String,target:String,section:String?=null,direction:Int=0,scope:String?=null)=records.writes.withLock {db.withTransaction {
        require(dao.entries().any {it.id==id && it.kind=="project" && it.deletedAt==null});val all=dao.links().filter {it.entryId==id}.sortedBy {it.sortOrder}.toMutableList();val index=all.indexOfFirst {it.entityType==type && it.entityId==target};require(index>=0)
        if(section!=null) {require(section in listOf("overview","task","record","material","checklist","ledger","goal"));dao.put(all[index].copy(section=section))}
        else {val entries=dao.entries().associateBy {it.id};fun effective(link:ToolLink)=if(link.section!="overview") link.section else when(link.entityType) {"task","event","habit","focus"->"task";"record"->"record";"ledger"->"ledger";"goal"->"goal";"tool"->if(entries[link.entityId]?.kind in listOf("checklist","checklist_template")) "checklist" else "material";else->"material"}
            val indices=all.indices.filter {scope==null || effective(all[it])==scope};val position=indices.indexOf(index);require(position>=0);val next=indices[(position+direction.coerceIn(-1,1)).coerceIn(indices.indices)];val swap=all[index];all[index]=all[next];all[next]=swap;all.forEachIndexed {order,link->dao.put(link.copy(sortOrder=order))}}
    }}
    suspend fun itemTransaction(id:String,role:String,target:String,importFacts:Boolean=false)=records.writes.withLock {db.withTransaction {
        require(role in listOf("item_purchase","item_sale"));val item=dao.entries().first {it.id==id && it.kind=="item" && it.deletedAt==null};val type=if(role=="item_purchase") "expense" else "income"
        val transaction=db.ledger().transactions().find {it.id==target && it.ownerId==item.ownerId && it.deletedAt==null && it.type==type} ?: error("请选择有效的"+if(type=="expense") "购买支出" else "出售收入")
        require(db.ledger().ledgers().any {it.id==transaction.ledgerId && it.ownerId==item.ownerId && it.currency=="CNY"}) {"原账目币种不匹配"}
        if(importFacts) {val payload=JSONObject(item.payload);payload.put(if(role=="item_purchase") "amount" else "soldAmount",ToolRules.money(transaction.amountMinor));payload.put(if(role=="item_purchase") "date" else "soldDate",transaction.occurredOn)
            val next=item.copy(payload=payload.toString(),revision=item.revision+1,updatedAt=System.currentTimeMillis());val f=ToolForm(next.kind,next.id,next.revision,payload.keys().asSequence().filter {it!="version"}.associateWith {payload.get(it).toString()}).set("title",next.title);validate(f);dao.put(next)}
        else dao.put(item.copy(revision=item.revision+1,updatedAt=System.currentTimeMillis()))
        dao.links().filter {it.entryId==id && it.section==role}.forEach {dao.unlink(id,it.entityType,it.entityId)};dao.put(ToolLink(id,"ledger",target,role))
    }}
    suspend fun feature(key:String,action:String)=records.writes.withLock {db.withTransaction {
        val old=dao.preferences().find {it.featureKey==key} ?: FeaturePreference(key,sortOrder=dao.preferences().size)
        if(action in listOf("up","down")) {val saved=dao.preferences().associateBy {it.featureKey};val keys=listOf("checklist","anniversary","subscription","bill","wardrobe","outfit","item","loan","project","habit","focus","goal","card","calculator","dates","aa");val all=keys.mapIndexed {i,k->saved[k] ?: FeaturePreference(k,sortOrder=i)}.sortedBy {it.sortOrder}.toMutableList();val from=all.indexOfFirst {it.featureKey==key};if(from>=0) {val to=(from+if(action=="up") -1 else 1).coerceIn(all.indices);val swap=all[from];all[from]=all[to];all[to]=swap;all.forEachIndexed {i,v->dao.put(v.copy(sortOrder=i))}}}
        else dao.put(when(action) {"favorite"->old.copy(favorite=!old.favorite);"hide"->old.copy(hidden=!old.hidden);else->old.copy(lastOpenedAt=System.currentTimeMillis())})
    }}
    suspend fun calculation(kind:String,input:String,result:String,save:Boolean=true)=records.writes.withLock {if(save) {dao.put(ToolCalculation(kind=kind,input=JSONObject().put("version",1).put("value",input).toString(),result=JSONObject().put("version",1).put("value",result).toString()));dao.calculations().drop(200).forEach {dao.removeCalculation(it.id)}}}
    suspend fun clearCalculation(id:String?=null)=records.writes.withLock {if(id==null) dao.clearCalculations() else dao.removeCalculation(id)}
    /** Bounded expansion from stable anchor, never advances from a late payment. */
    internal suspend fun expand() {
        val today=LocalDate.now();val existing=dao.periods().associateBy {it.sourceId+":"+it.originalKey}
        val logs=dao.logs()
        val versionsBySource=logs.filter {it.kind=="payment_version"}.groupBy {it.entryId}
        val overrides=logs.filter {it.kind=="period_override"}.map {JSONObject(it.payload).getString("periodId")}.toSet()
        for(e in dao.entries().filter {it.kind in listOf("subscription","bill") && it.deletedAt==null && it.state in listOf("active","trial")}) {
            var versions=versionsBySource[e.id].orEmpty().sortedWith(compareBy<ToolLog> {it.date}.thenBy {it.createdAt}).groupBy {it.date}.values.map {it.last()}.sortedBy {it.date}
            if(versions.isEmpty()) versions=listOf(ToolLog(entryId=e.id,kind="payment_version",date=e.field("date"),payload=e.payload))
            val wanted=mutableSetOf<String>()
            versions.forEachIndexed {index,v->
                val payload=JSONObject(v.payload);val anchor=LocalDate.parse(payload.getString("date"));val cycle=payload.optString("cycle","monthly");val interval=payload.optString("interval","1").toInt();val end=payload.optString("end").takeIf {it.isNotBlank()}?.let(LocalDate::parse);val until=versions.getOrNull(index+1)?.date
                for(i in 0..9999) {
                    if(cycle=="once" && i>0) break
                    val d=if(cycle=="once") anchor else PaymentPeriodRules.start(anchor,cycle,interval,i)
                    if(d>today.plusMonths(18) || end!=null && d>end || until!=null && d.toString()>=until) break
                    if(d.toString()<v.date) continue
                    val key=d.toString();wanted+=key;val old=existing[e.id+":"+key]
                    val overridden=old!=null && old.id in overrides
                    val expected=if(overridden) old?.expectedMinor else payload.optString("amount").takeIf {it.isNotBlank()}?.let(ToolRules::minor)
                    val periodStart=if(overridden) old!!.periodStart else if(cycle=="once") payload.optString("billingStart").ifBlank {key} else key
                    val periodEnd=if(overridden) old!!.periodEnd else if(cycle=="once") payload.optString("billingEnd").ifBlank {(end ?: d).toString()} else PaymentPeriodRules.end(anchor,cycle,interval,i).toString()
                    if(old==null || old.status in listOf("pending","superseded") && d>=today) dao.put(ToolPaymentPeriod(old?.id ?: newId(),e.id,key,periodStart,periodEnd,old?.dueDate ?: key,expected,if(old?.status=="superseded") "pending" else old?.status ?: "pending"))
                    else if(old.status=="pending" && !overridden && cycle!="once" && old.periodStart==key &&
                        old.periodEnd==PaymentPeriodRules.end(d,cycle,interval,0).toString() && old.periodEnd!=periodEnd) {
                        // Repair the old short-month calculation without rewriting settled or explicitly adjusted history.
                        dao.put(old.copy(periodEnd=periodEnd))
                    }
                }
            }
            existing.values.filter {it.sourceId==e.id && it.status=="pending" && it.originalKey>=today.toString() && it.originalKey !in wanted}.forEach {dao.put(it.copy(status="superseded"))}
        }
    }
    suspend fun refresh()=records.writes.withLock {db.withTransaction {expand()}}
    suspend fun adjustPeriod(id:String,date:String,amount:String,start:String?=null,end:String?=null)=records.writes.withLock {db.withTransaction {val p=dao.periods().first {it.id==id};require(p.status=="pending");ToolRules.date(date);val a=start ?: p.periodStart;val b=end ?: p.periodEnd;ToolRules.date(a);ToolRules.date(b);require(b>=a) {"所属期结束不能早于开始"};dao.put(p.copy(dueDate=date,expectedMinor=amount.takeIf {it.isNotBlank()}?.let(ToolRules::minor),periodStart=a,periodEnd=b));dao.put(ToolLog(id=java.util.UUID.nameUUIDFromBytes(("period_override:"+id).toByteArray()).toString(),entryId=p.sourceId,kind="period_override",date=LocalDate.now().toString(),payload=JSONObject().put("version",1).put("periodId",id).toString()))}}
    internal suspend fun commitSettlement(periodId:String,actual:Long,date:String,mode:String,expenseId:String?) {
        val p=dao.periods().first {it.id==periodId};val previous=dao.settlements().find {it.periodId==periodId}
        val supplement=p.status=="paid" && previous!=null && previous.mode=="status" && previous.expenseId==null && mode!="status"
        require(p.status=="pending" && previous==null || supplement) {"此期间已处理，请刷新后核对"}
        require(dao.entries().any {it.id==p.sourceId && it.deletedAt==null && (supplement || it.archivedAt==null && it.state in listOf("active","trial"))}) {"原项目已删除、归档或停止，请先恢复后付款"}
        if(supplement) require(previous!!.actualMinor==actual && previous.paidOn==date) {"补记账需沿用原付款金额和日期；更正请先撤销原处理"}
        require(actual in 0..99_999_999_999L && mode in listOf("status","new","existing")) {"付款金额或方式无效"};ToolRules.date(date);require(date<=LocalDate.now().toString()) {"付款日期不能晚于今天"}
        require((mode=="status")== (expenseId==null)) {"付款方式与账目关联不一致"}
        if(expenseId!=null) {val source=dao.entries().first {it.id==p.sourceId};val expense=db.ledger().transactions().find {it.id==expenseId && it.deletedAt==null && it.type=="expense" && it.ownerId==source.ownerId}
            require(expense!=null && expense.amountMinor==actual && expense.occurredOn==date) {"请关联金额和日期一致的有效支出"}}
        require(expenseId==null || dao.settlements().none {it.expenseId==expenseId} && dao.children().none {it.expenseId==expenseId}) {"此账目已关联其他付款期间或条目"};dao.put(ToolPaymentSettlement(periodId,actual,date,mode,expenseId,previous?.createdAt ?: System.currentTimeMillis()));dao.put(p.copy(status="paid"))
    }
    suspend fun settleOnly(id:String,actual:String,date:String)=records.writes.withLock {db.withTransaction {commitSettlement(id,ToolRules.minor(actual),date,"status",null)}}
    suspend fun skipPeriod(id:String)=records.writes.withLock {val p=dao.periods().first {it.id==id};require(p.status=="pending");dao.put(p.copy(status="cancelled"))}
    suspend fun reopenPeriod(id:String)=records.writes.withLock {db.withTransaction {val s=dao.settlements().find {it.periodId==id};require(s?.expenseId==null) {"请先解除此期的付款关联"};dao.removeSettlement(id);val p=dao.periods().first {it.id==id};dao.put(p.copy(status="pending"))}}
    suspend fun detachSettlement(id:String,keepPaid:Boolean)=records.writes.withLock {db.withTransaction {
        val s=dao.settlements().first {it.periodId==id};val p=dao.periods().first {it.id==id}
        if(keepPaid) dao.put(s.copy(expenseId=null,mode="status")) else {dao.removeSettlement(id);dao.put(p.copy(status="pending"))}
    }}
}
