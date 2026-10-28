package com.example.dailylife
import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.ledger.LedgerForm
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.*
import java.time.LocalDate
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ToolsRepositoryTest {
    @Test fun wardrobeTagsValidateNormalizeAndRoundTripWithOutfitMembership()=runBlocking {
        val shirt=form("item").set("wardrobe","true").set("styleTags"," 通勤，简约,通勤 ")
        save(shirt)
        val outfit=form("outfit").set("items",shirt.id).set("styleTags","通勤,正式");save(outfit)
        assertEquals(listOf("通勤","简约"),repo.load().entries.first {it.id==shirt.id}.styleTags())
        assertEquals(outfit.id,WardrobeCatalog(repo.load()).memberships[shirt.id]!!.single().id)
        val bad=repo.form(shirt.id).set("styleTags","字".repeat(13));repo.prepare(bad)
        assertTrue(runCatching {repo.save(bad)}.isFailure)
        assertEquals(listOf("通勤","简约"),repo.load().entries.first {it.id==shirt.id}.styleTags())
        repo.discard(shirt.id)
        val backup=ToolsBackup(context,repo);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray()
        repo.manage(outfit.id,"trash");repo.deleteForever(outfit.id)
        assertTrue(WardrobeCatalog(repo.load()).memberships[shirt.id].isNullOrEmpty())
        backup.restore(backup.prepare(ByteArrayInputStream(bytes)))
        assertEquals(listOf("通勤","正式"),repo.load().entries.first {it.id==outfit.id}.styleTags())
        assertEquals(outfit.id,WardrobeCatalog(repo.load()).memberships[shirt.id]!!.single().id)
    }
    @Test fun wardrobeFiltersUseRealRelationsAndPinnedOrderWithoutDuplicatingItems()=runBlocking {
        val first=form("item").set("wardrobe","true").set("title","蓝衬衫").set("occasion","上班").set("styleTags","通勤").set("clothingType","top")
        val second=form("item").set("wardrobe","true").set("title","运动鞋").set("laundry","wash").set("clothingType","shoes")
        save(first);save(second);repo.manage(second.id,"pin");repo.manage(first.id,"favorite")
        val outfit=form("outfit").set("title","周一通勤").set("items",first.id).set("occasion","上班");save(outfit)
        val catalog=WardrobeCatalog(repo.load())
        assertEquals(second.id,catalog.filter(false,WardrobeFilter()).first().id)
        assertEquals(listOf(first.id),catalog.filter(false,WardrobeFilter(collection="favorites",tag="通勤",membership="paired",occasion="上班")).map {it.id})
        assertEquals(listOf(second.id),catalog.filter(false,WardrobeFilter(membership="single",laundry="wash",category="shoes")).map {it.id})
        assertEquals(listOf(first.id),catalog.filter(false,WardrobeFilter(query="周一通勤")).map {it.id})
        assertEquals(listOf(outfit.id),catalog.filter(true,WardrobeFilter()).map {it.id})
        repo.manage(outfit.id,"archive")
        assertEquals(2,WardrobeCatalog(repo.load()).filter(false,WardrobeFilter(membership="single")).size)
        assertEquals(3,repo.load().entries.size)
    }
    @Test fun photoCoverIsIsolatedUntilCommitAndDiscardPreservesOriginals()=runBlocking {
        val f=form("item").set("wardrobe","true");repo.prepare(f)
        suspend fun picture(name:String):Attachment {
            val a=records.files.importStream(ByteArrayInputStream(name.toByteArray()),records.owner(),name,"image/jpeg")
            records.dao.putAttachment(a);repo.attach(f.id,a.id);return a
        }
        val first=picture("first.jpg");val second=picture("second.jpg");repo.save(f)
        val edit=repo.form(f.id);repo.prepare(edit);repo.photoCover(f.id,second.id)
        assertEquals(first.id,records.dao.linksFor("tool",f.id).first().attachmentId)
        assertEquals(second.id,records.dao.linksFor("tool_draft",f.id).first().attachmentId)
        repo.discard(f.id)
        assertEquals(first.id,records.dao.linksFor("tool",f.id).first().attachmentId)
        assertTrue(records.files.file(first).exists());assertTrue(records.files.file(second).exists())
        repo.prepare(edit);repo.photoCover(f.id,second.id);repo.save(edit)
        assertEquals(listOf(second.id,first.id),records.dao.linksFor("tool",f.id).map {it.attachmentId})
        assertEquals("second.jpg",records.files.file(second).readText())
        assertTrue(runCatching {repo.photoCover(f.id,first.id)}.isFailure)
    }
    @Test fun photoOrderingSurvivesAppendBackupAndSharedReferenceRemoval()=runBlocking {
        val f=form("outfit");repo.prepare(f)
        val a=records.files.importStream(ByteArrayInputStream("photo one".toByteArray()),records.owner(),"one.jpg","image/jpeg")
        val b=records.files.importStream(ByteArrayInputStream("photo two".toByteArray()),records.owner(),"two.jpg","image/jpeg")
        records.dao.putAttachment(a);records.dao.putAttachment(b);repo.attach(f.id,a.id);repo.attach(f.id,b.id);repo.photoCover(f.id,b.id);repo.save(f)
        val other=form("item");repo.prepare(other);repo.attach(other.id,b.id);repo.save(other)
        val bytes=ByteArrayOutputStream().also {ToolsBackup(context,repo).export(it)}.toByteArray()
        val restoredContext=object:ContextWrapper(context) {
            override fun getFilesDir()=File(root,"restored-files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"restored-cache").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"restored-private").apply {mkdirs()}
        }
        val restoredDb=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"restored.db").absolutePath).build()
        try {
            val restoredRecords=RecordsRepository(restoredDb,AttachmentStore(restoredContext))
            val restoredRepo=ToolsRepository(restoredDb,restoredRecords);restoredRepo.initialize()
            val backup=ToolsBackup(restoredContext,restoredRepo);backup.restore(backup.prepare(ByteArrayInputStream(bytes)))
            assertEquals(listOf(b.id,a.id),restoredRecords.dao.linksFor("tool",f.id).map {it.attachmentId})
            val edit=restoredRepo.form(f.id);restoredRepo.prepare(edit);restoredRepo.detach(f.id,b.id)
            val c=restoredRecords.files.importStream(ByteArrayInputStream("third".toByteArray()),restoredRecords.owner(),"three.jpg","image/jpeg")
            restoredRecords.dao.putAttachment(c);restoredRepo.attach(f.id,c.id);restoredRepo.save(edit)
            val links=restoredRecords.dao.linksFor("tool",f.id)
            assertEquals(listOf(a.id,c.id),links.map {it.attachmentId});assertEquals(links.size,links.map {it.sortOrder}.distinct().size)
            assertTrue(restoredRecords.files.file(b).exists());assertEquals(1,restoredRecords.dao.attachmentUses(b.id))
            assertEquals("photo two",restoredRecords.files.file(b).readText())
        } finally {restoredDb.close()}
    }
    @Test fun unavailableOriginalCannotBeAttachedAndSaveKeepsOriginalAndDraft()=runBlocking {
        val f=form("checklist");save(f)
        val changed=repo.form(f.id).set("title","修改后的名称");repo.prepare(changed)
        val attachment=Attachment(newId(),records.owner(),"原件.txt","原件.txt","text/plain",4,"0".repeat(64),"",System.currentTimeMillis()).let {it.copy(relativePath="attachments/"+it.id+".bin")}
        records.dao.putAttachment(attachment)
        assertTrue(runCatching {repo.attach(f.id,attachment.id)}.isFailure)
        assertTrue(records.dao.linksFor("tool_draft",f.id).isEmpty())
        records.files.file(attachment).writeText("test")
        repo.attach(f.id,attachment.id)
        records.files.file(attachment).writeText("truncated")
        assertTrue(runCatching {repo.save(changed)}.isFailure)
        assertEquals(f.text("title"),repo.dao.entries().single().title)
        assertEquals(1,repo.dao.drafts().size);assertEquals(1,records.dao.linksFor("tool_draft",f.id).size)
        records.files.file(attachment).writeText("test");repo.save(changed)
        assertEquals("修改后的名称",repo.dao.entries().single().title)
        assertTrue(repo.dao.drafts().isEmpty())
    }
    @Test fun subscriptionMonthEndPeriodsRemainContinuousAndPaidOverrideIsPreserved()=runBlocking {
        val anchor=LocalDate.now().withMonth(1).withDayOfMonth(31)
        val f=form("subscription").set("date",anchor.toString()).set("amount","10").set("cycle","monthly").set("interval","1")
        save(f)
        val periods=repo.dao.periods().sortedBy {it.originalKey}
        periods.zipWithNext().forEach {(a,b)->assertEquals(LocalDate.parse(b.periodStart).minusDays(1).toString(),a.periodEnd)}
        val first=periods.first();val customStart=anchor.minusDays(2).toString();val customEnd=anchor.plusDays(3).toString()
        repo.adjustPeriod(first.id,anchor.toString(),"9",customStart,customEnd)
        repo.settleOnly(first.id,"9",LocalDate.now().toString());repo.refresh()
        val saved=repo.dao.periods().first {it.id==first.id}
        assertEquals("paid",saved.status);assertEquals(customStart,saved.periodStart);assertEquals(customEnd,saved.periodEnd)
    }
    @Test fun refreshRepairsLegacyUnpaidShortMonthButKeepsItsAmount()=runBlocking {
        val anchor=LocalDate.now().minusYears(1).withMonth(1).withDayOfMonth(31)
        save(form("subscription").set("date",anchor.toString()).set("amount","10").set("cycle","monthly").set("interval","1"))
        val key=anchor.plusMonths(1).toString()
        val p=repo.dao.periods().first {it.originalKey==key}
        val legacyEnd=LocalDate.parse(key).plusMonths(1).minusDays(1).toString()
        repo.dao.put(p.copy(periodEnd=legacyEnd,expectedMinor=1234L))
        repo.refresh()
        val repaired=repo.dao.periods().first {it.id==p.id}
        assertEquals(anchor.plusMonths(2).minusDays(1).toString(),repaired.periodEnd)
        assertEquals(1234L,repaired.expectedMinor)
    }
    @Test fun cancellingAndReplacingToolsBackupPreviewsRemovesExtractedFiles()=runBlocking {
        save(form("checklist"));val backup=ToolsBackup(context,repo);val output=ByteArrayOutputStream();backup.export(output)
        repeat(3) {
            val preview=backup.prepare(ByteArrayInputStream(output.toByteArray()))
            assertTrue(preview.directory.isDirectory);backup.discard(preview);assertFalse(preview.directory.exists())
        }
        val first=backup.prepare(ByteArrayInputStream(output.toByteArray()))
        val second=backup.prepare(ByteArrayInputStream(output.toByteArray()))
        backup.discardAll();assertFalse(first.directory.exists());assertFalse(second.directory.exists())
    }
    private val base=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root:File;private lateinit var context:Context;private lateinit var db:DailyLifeDatabase
    private lateinit var records:RecordsRepository;private lateinit var repo:ToolsRepository;private lateinit var ledger:LedgerRepository
    @Before fun setup()=runBlocking {root=File(repositoryTestRoot(base),"tool-tests-"+newId()).apply {mkdirs()};context=object:ContextWrapper(base) {override fun getFilesDir()=File(root,"files").apply {mkdirs()};override fun getCacheDir()=File(root,"cache").apply {mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply {mkdirs()}};db=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"test.db").absolutePath).build();records=RecordsRepository(db,AttachmentStore(context));ledger=LedgerRepository(records);ledger.initialize();repo=ToolsRepository(db,records);repo.initialize()}
    @After fun close() {db.close();root.deleteRecursively()}
    private suspend fun save(f:ToolForm) {repo.prepare(f);repo.save(f)}
    private fun form(kind:String)=ToolForm(kind).set("title","测试"+kind).set("date",LocalDate.now().toString())
    @Test fun checklistCopyTemplateHasIndependentIdsAndResetDoesNotChangeTemplate()=runBlocking {val f=form("checklist");save(f);repo.child(f.id,"雨伞","1","把");val child=repo.dao.children().single();repo.childAction(child.id,"toggle");val template=repo.copy(f.id,true);val copy=repo.copy(template);assertEquals("checklist_template",repo.dao.entries().first {it.id==template}.kind);assertEquals(3,repo.dao.children().map {it.id}.toSet().size);repo.reset(copy);assertNotNull(repo.dao.children().first {it.id==child.id}.completedAt);assertNull(repo.dao.children().first {it.entryId==copy}.completedAt)}
    @Test fun checklistOrderingActuallySwapsAndCompletingThenAddingReopens()=runBlocking {val f=form("checklist");save(f);repo.child(f.id,"一");repo.child(f.id,"二");val c=repo.dao.children().first {it.title=="二"};repo.childAction(c.id,"up");assertEquals("二",repo.dao.children().first().title);repo.manage(f.id,"complete");repo.child(f.id,"三");assertEquals("active",repo.dao.entries().single().state)}
    @Test fun loanPartialReturnEditReplacesAndOverReturnFails()=runBlocking {val f=form("loan").set("person","朋友").set("quantity","3.5").set("unit","本");save(f);repo.returned(f.id,LocalDate.now().toString(),"1","首批");val log=repo.dao.logs().single();repo.returned(f.id,log.date,"2","改为2本",log.id);assertEquals(1500L,repo.load().remaining(repo.dao.entries().single()));assertTrue(runCatching {repo.returned(f.id,log.date,"2","超量")}.isFailure);assertEquals(1,repo.dao.logs().size);repo.removeLog(log.id);assertEquals(3500L,repo.load().remaining(repo.dao.entries().single()))}
    @Test fun itemSaleAndTrashAreBlockedByOutstandingLoan()=runBlocking {val item=form("item");save(item);val loan=form("loan").set("person","朋友").set("quantity","1").set("item",item.id);save(loan);assertTrue(runCatching {repo.manage(item.id,"trash")}.isFailure);assertTrue(runCatching {save(repo.form(item.id).set("status","sold"))}.isFailure);repo.returned(loan.id,LocalDate.now().toString(),"1","");repo.manage(item.id,"trash");assertNotNull(repo.dao.entries().first {it.id==item.id}.deletedAt)}
    @Test fun subscriptionPeriodKeyStablePriceSnapshotsAndStatusSettlementIdempotent()=runBlocking {val date=LocalDate.now().minusMonths(1).toString();val f=form("subscription").set("date",date).set("amount","9.90").set("cycle","monthly").set("interval","1");save(f);val before=repo.dao.periods().first();repo.adjustPeriod(before.id,LocalDate.now().toString(),"8.80");repo.settleOnly(before.id,"8.80",LocalDate.now().toString());assertTrue(runCatching {repo.settleOnly(before.id,"8.80",LocalDate.now().toString())}.isFailure);assertEquals(1,repo.dao.settlements().size);assertEquals(before.originalKey,repo.dao.periods().first {it.id==before.id}.originalKey);assertTrue(ledger.load().transactions.isEmpty())}
    @Test fun paymentAndExpenseAreOneTransactionAndRetryCannotDuplicate()=runBlocking {val f=form("bill").set("cycle","once").set("amount","25.00");save(f);val p=repo.dao.periods().single();val ledgerId=ledger.load().defaultLedger()!!.id;val command=LedgerForm("expense").set("ledger",ledgerId).set("amount","25.00").set("date",LocalDate.now().toString());ledger.settlePayment(command,null,2500,LocalDate.now().toString()) {id->repo.commitSettlement(p.id,2500,LocalDate.now().toString(),"new",id)};assertEquals(1,ledger.load().transactions.size);assertTrue(runCatching {ledger.settlePayment(command.copy(id=newId()),null,2500,LocalDate.now().toString()) {id->repo.commitSettlement(p.id,2500,LocalDate.now().toString(),"new",id)}}.isFailure);assertEquals(1,ledger.load().transactions.size)}
    @Test fun deletedSourceRollsBackExpenseAtFinalCommit()=runBlocking {val f=form("bill").set("cycle","once");save(f);val p=repo.dao.periods().single();repo.manage(f.id,"trash");val command=LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","1").set("date",LocalDate.now().toString());assertTrue(runCatching {ledger.settlePayment(command,null,100,LocalDate.now().toString()) {id->repo.commitSettlement(p.id,100,LocalDate.now().toString(),"new",id)}}.isFailure);assertTrue(ledger.load().transactions.isEmpty());assertTrue(repo.dao.settlements().isEmpty())}
    @Test fun settlementRejectsInconsistentModeAndWrongExpenseFacts()=runBlocking {
        val f=form("bill").set("cycle","once");save(f);val p=repo.dao.periods().single();val today=LocalDate.now().toString()
        assertTrue(runCatching {repo.commitSettlement(p.id,100,today,"new",null)}.isFailure)
        val command=LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","1").set("date",today)
        assertTrue(runCatching {ledger.settlePayment(command,null,100,today) {id->repo.commitSettlement(p.id,200,today,"new",id)}}.isFailure)
        assertTrue(ledger.load().transactions.isEmpty());assertTrue(repo.dao.settlements().isEmpty())
    }
    @Test fun maintenanceAndShoppingExpensesPreserveSourceFactsAndProtectBothSides()=runBlocking {
        val today=LocalDate.now().toString();val serviced=LocalDate.now().minusDays(2).toString();val item=form("item");save(item)
        repo.child(item.id,"换电池",date=serviced,cost="9");val child=repo.dao.children().single()
        val command=LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","10").set("date",today)
        val expense=ledger.settlePayment(command,null,1000,today) {id->repo.commitChildExpense(child.id,1000,today,id)}
        assertEquals(serviced,repo.dao.children().single().date);assertEquals(1000L,repo.dao.children().single().costMinor)
        assertTrue(runCatching {ledger.settlePayment(command.copy(id=newId()),null,1000,today) {id->repo.commitChildExpense(child.id,1000,today,id)}}.isFailure)
        assertEquals(1,ledger.load().transactions.size)
        assertTrue(runCatching {repo.child(item.id,"换电池",id=child.id,date=serviced,cost="11")}.isFailure)
        assertTrue(runCatching {repo.childAction(child.id,"delete")}.isFailure)
        ledger.save(ledger.form(expense).set("amount","11"));assertEquals(1100L,repo.dao.children().first {it.id==child.id}.costMinor);assertEquals(serviced,repo.dao.children().first {it.id==child.id}.date)
        ledger.save(ledger.form(expense).set("amount","10"))
        repo.manage(item.id,"trash");assertTrue(runCatching {repo.deleteForever(item.id)}.isFailure);repo.manage(item.id,"restore")
        val list=form("checklist");save(list);repo.child(list.id,"电池");val shopping=repo.dao.children().first {it.entryId==list.id}
        assertTrue(runCatching {ledger.settlePayment(null,expense,1000,today) {id->repo.commitChildExpense(shopping.id,1000,today,id)}}.isFailure)
        repo.detachChildExpense(child.id);assertEquals(1,ledger.load().transactions.size)
        ledger.settlePayment(null,expense,1000,today) {id->repo.commitChildExpense(shopping.id,1000,today,id)}
        assertNull(repo.dao.children().first {it.id==shopping.id}.date)
        val bill=form("bill").set("cycle","once");save(bill);val period=repo.dao.periods().single()
        assertTrue(runCatching {ledger.settlePayment(null,expense,1000,today) {id->repo.commitSettlement(period.id,1000,today,"existing",id)}}.isFailure)
        repo.detachChildExpense(shopping.id);repo.childAction(shopping.id,"delete")
        assertEquals(1,ledger.load().transactions.size)
    }
    @Test fun toolboxBackupKeepsVerifiedMaintenanceExpenseAndSeparateServiceDate()=runBlocking {
        val today=LocalDate.now().toString();val serviced=LocalDate.now().minusDays(2).toString();val item=form("item");save(item)
        repo.child(item.id,"维修屏幕",date=serviced,cost="20");val child=repo.dao.children().single()
        val command=LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","20").set("date",today)
        val expense=ledger.settlePayment(command,null,2000,today) {id->repo.commitChildExpense(child.id,2000,today,id)}
        val backup=ToolsBackup(context,repo);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray()
        repo.detachChildExpense(child.id);repo.manage(item.id,"trash");repo.deleteForever(item.id)
        backup.restore(backup.prepare(ByteArrayInputStream(bytes)))
        assertEquals(expense,repo.dao.children().single().expenseId);assertEquals(serviced,repo.dao.children().single().date)
    }
    @Test fun malformedBackupChildAndUnsupportedProjectLinkAreRejectedBeforeMutation()=runBlocking {
        val list=form("checklist");val project=form("project");save(list);save(project);repo.child(list.id,"雨伞");repo.link(project.id,"tool",list.id)
        val backup=ToolsBackup(context,repo);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray()
        val original=java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use {zip->assertEquals("manifest.json",zip.nextEntry.name);zip.readBytes().toString(Charsets.UTF_8)}
        suspend fun rejected(change:(org.json.JSONObject)->Unit) {
            val manifest=org.json.JSONObject(original);change(manifest.getJSONObject("data"))
            val invalid=ByteArrayOutputStream().also {out->java.util.zip.ZipOutputStream(out).use {zip->zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray());zip.closeEntry()}}.toByteArray()
            assertTrue(runCatching {backup.prepare(ByteArrayInputStream(invalid))}.isFailure)
            assertEquals(2,repo.dao.entries().size);assertEquals("check",repo.dao.children().single().kind);assertEquals("tool",repo.dao.links().single().entityType)
        }
        rejected {data->data.getJSONArray("tool_children").getJSONObject(0).put("kind","maintenance").put("date",org.json.JSONObject.NULL)}
        rejected {data->data.getJSONArray("tool_links").getJSONObject(0).put("entityType","unknown_source")}
    }
    @Test fun itemContextReferencesKeepIndependentFactsAndRequireExplicitImport()=runBlocking {
        val today=LocalDate.now().toString();val original=LocalDate.now().minusDays(3).toString();val first=form("item").set("date",original).set("amount","12");val second=form("item");save(first);save(second)
        val expense=ledger.save(LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","20").set("date",today))
        repo.itemTransaction(first.id,"item_purchase",expense);repo.itemTransaction(second.id,"item_purchase",expense)
        assertEquals("12",repo.dao.entries().first {it.id==first.id}.field("amount"));assertEquals(original,repo.dao.entries().first {it.id==first.id}.field("date"))
        assertTrue(runCatching {ledger.manage(expense,"trash")}.isFailure)
        repo.manage(first.id,"trash");assertTrue(runCatching {repo.deleteForever(first.id)}.isFailure);repo.manage(first.id,"restore")
        repo.itemTransaction(first.id,"item_purchase",expense,true);assertEquals("20.00",repo.dao.entries().first {it.id==first.id}.field("amount"));assertEquals(today,repo.dao.entries().first {it.id==first.id}.field("date"))
        val income=ledger.save(LedgerForm("income").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","30").set("date",today))
        assertTrue(runCatching {repo.itemTransaction(first.id,"item_purchase",income)}.isFailure)
        repo.itemTransaction(first.id,"item_sale",income);assertEquals("",repo.dao.entries().first {it.id==first.id}.field("soldAmount"))
        repo.unlink(first.id,"ledger",expense);repo.unlink(second.id,"ledger",expense);ledger.manage(expense,"trash");assertNotNull(ledger.load().transactions.first {it.id==expense}.deletedAt)
    }
    @Test fun projectCreationAndGroupedBatchAreAtomicAndSectionOrderingWorks()=runBlocking {
        val project=form("project");save(project);val list=form("checklist").set("_project",project.id);save(list)
        assertEquals("checklist",repo.dao.links().single().section);assertEquals("",repo.dao.entries().first {it.id==list.id}.field("_project"))
        repo.batchChildren(list.id,"雨伞\n水杯","随身");assertEquals(2,repo.dao.children().size);assertTrue(repo.dao.children().all {it.groupName=="随身"})
        assertTrue(runCatching {repo.batchChildren(list.id,"正常条目\n"+"很".repeat(121))}.isFailure);assertEquals(2,repo.dao.children().size)
        val item=form("item").set("_project",project.id);save(item);repo.moveProjectLink(project.id,"tool",item.id,section="checklist");repo.moveProjectLink(project.id,"tool",item.id,direction=-1,scope="checklist")
        assertEquals(item.id,repo.dao.links().filter {it.entryId==project.id}.minBy {it.sortOrder}.entityId)
        val next=form("anniversary").set("_project",project.id);repo.prepare(next);repo.manage(project.id,"trash");assertTrue(runCatching {repo.save(next)}.isFailure)
        assertTrue(repo.dao.entries().none {it.id==next.id});assertTrue(repo.dao.drafts().any {it.id==next.id})
    }
    @Test fun adjustedPeriodSurvivesRefreshAndStatusPaymentCanBeSupplementedOnce()=runBlocking {
        val today=LocalDate.now();val f=form("bill").set("cycle","once").set("amount","10").set("billingStart",today.withDayOfMonth(1).toString()).set("billingEnd",today.toString());save(f)
        val initial=repo.dao.periods().single();assertEquals(today.withDayOfMonth(1).toString(),initial.periodStart)
        repo.adjustPeriod(initial.id,today.plusDays(4).toString(),"7.50",today.minusDays(1).toString(),today.toString());repo.refresh()
        val adjusted=repo.dao.periods().single();assertEquals(750L,adjusted.expectedMinor);assertEquals(today.minusDays(1).toString(),adjusted.periodStart)
        repo.settleOnly(initial.id,"7.50",today.toString());val createdAt=repo.dao.settlements().single().createdAt
        val form=LedgerForm("expense").set("ledger",ledger.load().defaultLedger()!!.id).set("amount","7.50").set("date",today.toString())
        ledger.settlePayment(form,null,750,today.toString()) {id->repo.commitSettlement(initial.id,750,today.toString(),"new",id)}
        assertEquals(createdAt,repo.dao.settlements().single().createdAt);assertEquals(1,repo.dao.settlements().size)
        assertTrue(runCatching {ledger.settlePayment(form.copy(id=newId()),null,750,today.toString()) {id->repo.commitSettlement(initial.id,750,today.toString(),"new",id)}}.isFailure)
        assertEquals(1,ledger.load().transactions.size)
    }
    @Test fun projectLinksAreUniqueAndRemovalPreservesOriginal()=runBlocking {val project=form("project");val item=form("item");save(project);save(item);repo.link(project.id,"tool",item.id);repo.link(project.id,"tool",item.id);assertEquals(1,repo.dao.links().size);repo.unlink(project.id,"tool",item.id);assertEquals(2,repo.dao.entries().size)}
    @Test fun anniversaryLeapAndReminderAreNaturalDateRules()=runBlocking {val f=form("anniversary").set("date","2000-02-29").set("repeat","yearly").set("leap","mar1").set("reminderOn","true").set("reminderTime","09:00").set("reminderDays","1");save(f);val s=repo.load();assertEquals("2027-03-01",s.nextDate(s.entries.single(),LocalDate.of(2027,1,1)).toString());assertEquals(1,s.reminders().size);repo.manage(f.id,"trash");assertTrue(repo.load().reminders().isEmpty())}
    @Test fun scopedBackupPreservesExistingIdsAndRejectsUnsafeZip()=runBlocking {val f=form("checklist");save(f);repo.child(f.id,"条目");val backup=ToolsBackup(context,repo);val bytes=ByteArrayOutputStream().also {backup.export(it)}.toByteArray();repo.manage(f.id,"archive");backup.restore(backup.prepare(ByteArrayInputStream(bytes)));assertEquals(1,repo.dao.entries().size);assertNotNull(repo.dao.entries().single().archivedAt);assertEquals(1,repo.dao.children().size);val corrupt=ByteArrayOutputStream().also {out->java.util.zip.ZipOutputStream(out).use {z->z.putNextEntry(java.util.zip.ZipEntry("../unsafe"));z.write(byteArrayOf(1));z.closeEntry()}}.toByteArray();assertTrue(runCatching {backup.prepare(ByteArrayInputStream(corrupt))}.isFailure);assertEquals(1,repo.dao.entries().size)}
    @Test fun futureCycleVersionRetainsPastAndStopsSupersededFuture()=runBlocking {
        val today=LocalDate.now();val anchor=today.minusMonths(2).withDayOfMonth(1)
        val f=form("subscription").set("date",anchor.toString()).set("amount","10").set("cycle","monthly").set("interval","1");save(f)
        val old=repo.dao.periods();val historical=old.first {it.originalKey<today.toString()}
        val effective=today.plusDays(2);val changed=repo.form(f.id).set("date",effective.toString()).set("cycle","weekly").set("interval","1").set("effective",effective.toString()).set("amount","15")
        save(changed);val periods=repo.dao.periods();assertEquals(historical,periods.first {it.id==historical.id})
        assertTrue(periods.any {it.originalKey==effective.toString() && it.expectedMinor==1500L && it.status=="pending"})
        assertTrue(old.filter {it.originalKey>=effective.toString() && java.time.temporal.ChronoUnit.DAYS.between(effective,LocalDate.parse(it.originalKey))%7!=0L}.all {p->periods.first {it.id==p.id}.status=="superseded"})
    }
}
