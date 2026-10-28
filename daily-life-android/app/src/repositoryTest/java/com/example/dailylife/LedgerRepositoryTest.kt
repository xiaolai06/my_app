package com.example.dailylife
import android.content.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dailylife.core.LedgerRules
import com.example.dailylife.data.files.AttachmentStore
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.feature.ledger.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.*
import java.security.MessageDigest
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class LedgerRepositoryTest {
    @Test fun deletingBudgetAlsoDeletesAlertsAndBackupStillRestores()=runBlocking {
        val ledger=repo.load().defaultLedger()!!.id
        repo.saveBudget(ledger,YearMonth.now().toString(),null,"100")
        tx(amount="100")
        assertEquals(2,repo.load().alerts.size)
        repo.removeBudget(repo.load().budgets.single().id)
        assertTrue(repo.load().budgets.isEmpty());assertTrue(repo.load().alerts.isEmpty())
        val backup=LedgerBackup(base,repo);val output=ByteArrayOutputStream();backup.export(output)
        val preview=backup.prepare(ByteArrayInputStream(output.toByteArray()))
        try {backup.restore(preview);assertEquals(1,repo.load().transactions.size)} finally {backup.discardAll()}
    }
    @Test fun permanentlyDeletedImportKeepsIdentityAndSupportsUndoAndBackup()=runBlocking {
        val ledger=repo.load().defaultLedger()!!.id
        val csv="date,amount,type,category,merchant,note,external\n$today,12,expense,,,first,DELETE-1\n$today,20,income,,,second,DELETE-2"
        val batch=repo.prepareCsv(csv,ledger,null);repo.commitImport(batch)
        val deleted=repo.load().rows.first {it.batchId==batch}.transactionId!!
        repo.manage(deleted,"trash");repo.permanentlyDelete(deleted)
        val tombstone=repo.load().rows.first {it.result=="deleted"}
        assertNull(tombstone.transactionId);assertNotNull(tombstone.dedupKey)
        repo.undoImport(batch);repo.undoImport(batch)
        assertTrue(repo.load().transactions.all {it.deletedAt!=null})
        val duplicate=repo.prepareCsv(csv+"\n",ledger,null);repo.commitImport(duplicate,true)
        assertEquals(2,repo.load().rows.count {it.batchId==duplicate&&it.result=="skipped"})
        val backup=LedgerBackup(base,repo);val output=ByteArrayOutputStream();backup.export(output)
        val preview=backup.prepare(ByteArrayInputStream(output.toByteArray()))
        try {backup.restore(preview);assertEquals(1,repo.load().transactions.size);assertEquals(1,repo.load().rows.count {it.result=="deleted"})} finally {backup.discardAll()}
    }
    @Test fun initializationRepairsLegacyOrphansWithoutRemovingImportIdentity()=runBlocking {
        val ledger=repo.load().defaultLedger()!!.id
        repo.saveBudget(ledger,YearMonth.now().toString(),null,"10");tx(amount="10")
        repo.dao.removeBudget(repo.load().budgets.single().id)
        val batch=repo.prepareCsv("date,amount,type,category,merchant,note,external\n$today,3,expense,,,,OLD-1",ledger,null)
        repo.commitImport(batch,true)
        repo.dao.removeTransaction(repo.load().rows.single().transactionId!!)
        repo.initialize();repo.initialize()
        assertTrue(repo.load().alerts.isEmpty())
        assertEquals("deleted",repo.load().rows.single().result)
        assertNull(repo.load().rows.single().transactionId)
        assertNotNull(repo.load().rows.single().dedupKey)
        LedgerBackup(base,repo).export(ByteArrayOutputStream())
    }
    private val base=ApplicationProvider.getApplicationContext<Context>();private lateinit var db:DailyLifeDatabase;private lateinit var repo:LedgerRepository;private lateinit var root:File
    private val today get()=LocalDate.now().toString()
    @Before fun setup()=runBlocking {root=File(repositoryTestRoot(base),"ledger-test-"+newId()).apply {mkdirs()};val ctx=object:ContextWrapper(base) {override fun getFilesDir()=File(root,"files").apply {mkdirs()};override fun getCacheDir()=File(root,"cache").apply {mkdirs()}}
        db=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"test.db").absolutePath).build();repo=LedgerRepository(RecordsRepository(db,AttachmentStore(ctx)));repo.initialize()}
    @After fun cleanup() {db.close();root.deleteRecursively()}
    private suspend fun account(name:String="现金",opening:String="1000"):String {repo.saveAccount(name,"cash",opening,LocalDate.now().minusYears(2).toString());return repo.load().accounts.first {it.name==name}.id}
    private suspend fun tx(kind:String="expense",amount:String="100",account:String?=null,date:String=today):LedgerForm {val f=LedgerForm(kind).set("amount",amount).set("date",date).set("account",account.orEmpty());repo.prepareDraft(f);repo.save(f);return f}
    private suspend fun fails(block:suspend ()->Unit) {assertTrue("must reject invalid financial mutation",runCatching {block()}.isFailure)}
    @Test fun expenseDraftDoesNotAffectBalanceAndReplayDoesNotDuplicate()=runBlocking {val a=account();val f=LedgerForm().set("amount","28.10").set("account",a);repo.prepareDraft(f);assertEquals(100000,repo.load().balance(a));repo.save(f);repo.save(f);assertEquals(97190,repo.load().balance(a));assertEquals(1,repo.load().transactions.size);assertEquals(1,repo.load().entries.size)}
    @Test fun editReplacesEntriesAndDeleteRestoreIsOnce()=runBlocking {val a=account();val f=tx(account=a);repo.save(repo.form(f.id).set("amount","150"));assertEquals(85000,repo.load().balance(a));repo.manage(f.id,"trash");assertEquals(100000,repo.load().balance(a));repo.manage(f.id,"restore");assertEquals(85000,repo.load().balance(a));fails {repo.manage(f.id,"restore")};assertEquals(1,repo.load().entries.size)}
    @Test fun transferAndFeeCommitOneGroupAndDoNotBecomeIncome()=runBlocking {val a=account();val b=account("银行","0");val f=LedgerForm("transfer").set("amount","200").set("account",a).set("toAccount",b).set("fee","2");repo.save(f);repo.save(f);val s=repo.load();assertEquals(79800,s.balance(a));assertEquals(20000,s.balance(b));assertEquals(200,s.summary().expense);assertEquals(0,s.summary().income);assertEquals(3,s.entries.size);repo.manage(f.id,"trash",includeFee=true);assertEquals(100000,repo.load().balance(a));assertEquals(0,repo.load().balance(b))}
    @Test fun invalidTransferRollsBackParentAndFee()=runBlocking {val a=account();fails {repo.save(LedgerForm("transfer").set("amount","50").set("account",a).set("toAccount",a).set("fee","1"))};assertTrue(repo.load().transactions.isEmpty())}
    @Test fun refundInActualMonthHasIndependentAccountAndCap()=runBlocking {val a=account();val b=account("到账","0");val last=LocalDate.now().withDayOfMonth(1).minusDays(1).toString();val original=tx(amount="300",account=a,date=last)
        val r=LedgerForm("refund").set("original",original.id).set("amount","100").set("account",b);repo.save(r);val s=repo.load();assertEquals(30000,s.summary(month=last.take(7)).expense);assertEquals(10000,s.summary().refunds);assertEquals(-10000,s.summary().netExpense);assertEquals(10000,s.balance(b));fails {repo.save(r.copy(id=newId()).set("amount","201"))};fails {repo.save(repo.form(original.id).set("amount","99"))};fails {repo.manage(original.id,"trash")}}
    @Test fun originalCategoryEditPropagatesRefundBudgetBasis()=runBlocking {val a=account();val f=tx(account=a);val r=LedgerForm("refund").set("original",f.id).set("amount","20").set("account",a);repo.save(r);val cat=repo.load().categories.first {it.name=="餐饮"};repo.save(repo.form(f.id).set("category",cat.id).set("budget","false"));val s=repo.load();assertEquals(cat.id,s.transactions.first {it.id==r.id}.categoryId);assertFalse(s.transactions.first {it.id==r.id}.includeInBudget)}
    @Test fun openingDateAndFutureTransactionsRejectAndKeepDraft()=runBlocking {repo.saveAccount("今天开户","cash","0",today);val a=repo.load().accounts.single().id;val f=LedgerForm().set("amount","1").set("account",a).set("date",LocalDate.now().minusDays(1).toString());repo.prepareDraft(f);fails {repo.save(f)};assertEquals(1,repo.load().drafts.size);fails {repo.save(f.set("date",LocalDate.now().plusDays(1).toString()))};assertTrue(repo.load().transactions.isEmpty())}
    @Test fun budgetsExcludeTransferAndReimbursementAndThresholdsOnce()=runBlocking {val a=account();val ledger=repo.load().defaultLedger()!!.id;repo.saveBudget(ledger,YearMonth.now().toString(),null,"100");tx(amount="80",account=a);assertEquals(1,repo.load().alerts.size);tx(amount="1",account=a);assertEquals(1,repo.load().alerts.size);val t=tx(amount="20",account=a);assertEquals(2,repo.load().alerts.size);repo.save(LedgerForm("refund").set("original",t.id).set("amount","20").set("account",a));assertEquals(8100,repo.load().budgetUsed(repo.load().budgets.single()))}
    @Test fun reimbursementSubmissionDoesNotChangeBalanceAndReceiptsAreNotIncome()=runBlocking {val a=account();val t=tx(account=a);val ledger=repo.load().defaultLedger()!!.id;val c=repo.saveClaim("出差",ledger,mapOf(t.id to "80"),submitDate=today);assertEquals(90000,repo.load().balance(a));repo.save(LedgerForm("reimbursement").set("claim",c).set("amount","30").set("account",a));assertEquals(93000,repo.load().balance(a));assertEquals(0,repo.load().summary().income);assertEquals(3000,repo.load().claimPaid(c));fails {repo.save(LedgerForm("reimbursement").set("claim",c).set("amount","51").set("account",a))};fails {repo.manageClaim(c,"cancel")}}
    @Test fun allocationCapRecheckedAndRefundFlagsReview()=runBlocking {val a=account();val t=tx(account=a);val ledger=repo.load().defaultLedger()!!.id;val c=repo.saveClaim("单一",ledger,mapOf(t.id to "80"));fails {repo.saveClaim("超额",ledger,mapOf(t.id to "21"))};repo.save(LedgerForm("refund").set("original",t.id).set("amount","30").set("account",a));assertTrue(repo.load().claims.first {it.id==c}.needsReview);assertEquals(3000,repo.load().refunded(t.id))}
    @Test fun calibrationUsesDatedBalanceAndIsNotOrdinaryIncome()=runBlocking {val a=account();tx(amount="50",account=a);val id=newId();repo.calibrate(a,today,"960","盘点",id);repo.calibrate(a,today,"960","盘点",id);assertEquals(96000,repo.load().balance(a));assertEquals(0,repo.load().summary().income);assertEquals(5000,repo.load().summary().expense);assertEquals(2,repo.load().transactions.size)}
    @Test fun csvExternalIdentityAndSameFileRetryAreIdempotent()=runBlocking {val ledger=repo.load().defaultLedger()!!.id;val csv="日期,金额,类型,分类,商户,备注,编号\n$today,12.10,支出,餐饮,咖啡,早餐,EX-1\n$today,20,收入,工资,公司,,EX-2";val b=repo.prepareCsv(csv,ledger,null);repo.commitImport(b);repo.commitImport(b);assertEquals(b,repo.prepareCsv(csv,ledger,null));assertEquals(2,repo.load().transactions.size);val again=repo.prepareCsv(csv+"\n",ledger,null);repo.commitImport(again);assertEquals(2,repo.load().transactions.size);assertEquals(2,repo.load().rows.count {it.batchId==again&&it.result=="skipped"})}
    @Test fun csvSuspectedDuplicatesNeedExplicitChoiceAndExportQuotesRoundTrip()=runBlocking {tx(amount="12");val ledger=repo.load().defaultLedger()!!.id;val b=repo.prepareCsv("date,amount,type,category,merchant,note\n$today,12,expense,,,\"含逗号,与换行\n第二行\"",ledger,null);repo.commitImport(b);assertEquals(1,repo.load().transactions.size);repo.commitImport(b,true);assertEquals(2,repo.load().transactions.size);val csv=repo.exportCsv();assertEquals(3,LedgerRules.csv(csv).size);repo.undoImport(b);assertEquals(1,repo.load().transactions.count {it.deletedAt==null})}
    @Test fun ledgerBackupAppendKeepsExistingFactsAndRebuildsBalances()=runBlocking {
        val a=account();val f=tx(amount="28.10",account=a);val bytes=ByteArrayOutputStream();val backup=LedgerBackup(base,repo);backup.export(bytes)
        val preview=backup.prepare(ByteArrayInputStream(bytes.toByteArray()));assertEquals(1,preview.count);backup.restore(preview);backup.discard(preview)
        assertEquals(1,repo.load().transactions.size);assertEquals(97190,repo.load().balance(a));assertEquals(f.id,repo.load().transactions.single().id)
    }
    @Test fun backupRestoresIntoDifferentOwnerAndProtectsOriginalAttachment()=runBlocking {
        val a=account();val f=LedgerForm().set("amount","5.50").set("account",a);repo.prepareDraft(f)
        val bytes="原件凭证".toByteArray();val id=newId();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val file=File(repo.records.files.root,id+".bin");file.writeBytes(bytes);repo.records.dao.putAttachment(Attachment(id,repo.records.owner(),"凭证.txt","凭证.txt","text/plain",bytes.size.toLong(),hash,"attachments/"+id+".bin",System.currentTimeMillis()))
        repo.attachExisting(f.id,id);repo.save(f)
        val backup=LedgerBackup(base,repo);val output=ByteArrayOutputStream();backup.export(output)
        val secondCtx=object:ContextWrapper(base){override fun getFilesDir()=File(root,"second-files").apply {mkdirs()};override fun getCacheDir()=File(root,"second-cache").apply {mkdirs()}}
        val secondDb=Room.databaseBuilder(base,DailyLifeDatabase::class.java,File(root,"second.db").absolutePath).build()
        try {val second=LedgerRepository(RecordsRepository(secondDb,AttachmentStore(secondCtx)));second.initialize();val restore=LedgerBackup(secondCtx,second);val preview=restore.prepare(ByteArrayInputStream(output.toByteArray()));restore.restore(preview);restore.discard(preview)
            assertNotEquals(repo.records.owner(),second.records.owner());assertEquals(second.records.owner(),second.load().transactions.single().ownerId);assertEquals(99450,second.load().balance(a));assertArrayEquals(bytes,File(second.records.files.root,id+".bin").readBytes());assertEquals(1,second.records.dao.linksFor("transaction",f.id).size)
        } finally {secondDb.close()}
    }
    @Test fun backupUnsafeZipIsRejectedBeforeAnyDatabaseMutation()=runBlocking {
        tx();val output=ByteArrayOutputStream();ZipOutputStream(output).use {zip->zip.putNextEntry(ZipEntry("../escape"));zip.write("bad".toByteArray());zip.closeEntry()}
        fails {LedgerBackup(base,repo).prepare(ByteArrayInputStream(output.toByteArray()))};assertEquals(1,repo.load().transactions.size)
    }
    @Test fun newAccountDoesNotInheritUnassignedTransactionsOrTheirDates()=runBlocking {
        tx(date=LocalDate.now().minusDays(5).toString());repo.saveAccount("新账户","cash","2",today);val a=repo.load().accounts.single();assertEquals(200,repo.load().balance(a.id));assertTrue(repo.load().entries.isEmpty())
    }
    @Test fun replayOfDeletedCommandNeverReportsSavedActiveTransaction()=runBlocking {
        val f=tx();repo.manage(f.id,"trash");fails {repo.save(f)};assertNotNull(repo.load().transactions.single().deletedAt)
    }
    @Test fun paymentCallbackFailureRollsBackTransactionAndSourceTogether()=runBlocking {
        val a=account();val f=LedgerForm().set("amount","18").set("account",a)
        fails {repo.settlePayment(f,null,1800,today) {id->db.tools().put(ToolPaymentSettlement("missing-period",1800,today,"new",id));error("模拟期间冲突")}}
        assertTrue(repo.load().transactions.isEmpty());assertTrue(db.tools().settlements().isEmpty());assertEquals(100000,repo.load().balance(a))
    }
    @Test fun linkedPaymentCorrectionsSynchronizeSourceAndDeletionNeedsDetach()=runBlocking {
        val a=account();val f=LedgerForm().set("amount","18").set("account",a);val source=newId();val period=newId()
        db.tools().put(ToolEntry(source,repo.records.owner(),"bill","物业费"));db.tools().put(ToolPaymentPeriod(period,source,today,today,today,today,1800))
        repo.settlePayment(f,null,1800,today) {id->db.tools().put(ToolPaymentSettlement(period,1800,today,"new",id));db.tools().put(db.tools().periods().single().copy(status="paid"))}
        val old=repo.form(f.id);val corrected=old.set("amount","19").set("date",LocalDate.now().minusDays(1).toString());repo.save(corrected);repo.save(corrected)
        assertEquals(1900L,db.tools().settlements().single().actualMinor);assertEquals(corrected.text("date"),db.tools().settlements().single().paidOn);assertEquals(98100,repo.load().balance(a));assertEquals(1,repo.load().transactions.size)
        fails {repo.save(old.set("amount","20"))};fails {repo.save(repo.form(f.id).copy(kind="income"))};fails {repo.manage(f.id,"trash")}
        db.tools().put(db.tools().settlements().single().copy(expenseId=null,mode="status"));repo.manage(f.id,"trash");assertEquals(100000,repo.load().balance(a))
    }
    @Test fun claimDraftAttachmentsCommitAtomicallyAndSurviveDiscardOfAnEdit()=runBlocking {
        val a=account();val expense=tx(account=a);val ledger=repo.load().defaultLedger()!!.id
        val f=LedgerForm("claim").set("name","差旅凭证").set("ledger",ledger).set("submitDate",today).set("allocations",org.json.JSONObject().put(expense.id,"80").toString())
        repo.prepareDraft(f);repo.draft(f);assertTrue(repo.load().claims.isEmpty());assertEquals(90000,repo.load().balance(a))
        val id=newId();val bytes="报销单原件".toByteArray();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val file=File(repo.records.files.root,id+".bin");file.writeBytes(bytes);repo.records.dao.putAttachment(Attachment(id,repo.records.owner(),"申请.txt","申请.txt","text/plain",bytes.size.toLong(),hash,"attachments/"+id+".bin",System.currentTimeMillis()))
        val expenseEdit=repo.form(expense.id);repo.prepareDraft(expenseEdit);repo.attachExisting(expense.id,id);repo.save(expenseEdit)
        repo.attachExisting(f.id,id);repo.saveClaimFromDraft(f);repo.saveClaimFromDraft(f)
        assertEquals(1,repo.load().claims.size);assertTrue(repo.load().drafts.isEmpty());assertEquals(1,repo.records.dao.linksFor("reimbursement",f.id).size);assertEquals(90000,repo.load().balance(a))
        val edit=repo.claimForm(f.id);repo.prepareDraft(edit);repo.removeAttachment(edit.id,id);assertTrue(file.exists());repo.discard(edit.id)
        assertEquals(1,repo.records.dao.linksFor("reimbursement",f.id).size);assertTrue(file.exists())
        val backup=LedgerBackup(base,repo);val out=ByteArrayOutputStream();backup.export(out);val preview=backup.prepare(ByteArrayInputStream(out.toByteArray()));backup.restore(preview);backup.discard(preview)
        assertEquals(1,repo.records.dao.linksFor("reimbursement",f.id).size)
        val remove=repo.claimForm(f.id);repo.prepareDraft(remove);repo.removeAttachment(remove.id,id);repo.saveClaimFromDraft(remove)
        assertTrue(repo.records.dao.linksFor("reimbursement",f.id).isEmpty());assertEquals(1,repo.records.dao.linksFor("transaction",expense.id).size);assertTrue(file.exists())
    }
    @Test fun failedClaimSubmissionRetainsDraftAndDoesNotPartiallyAllocate()=runBlocking {
        val a=account();val expense=tx(account=a);val ledger=repo.load().defaultLedger()!!.id
        val f=LedgerForm("claim").set("name","待核对").set("ledger",ledger).set("allocations",org.json.JSONObject().put(expense.id,"101").toString())
        repo.prepareDraft(f);fails {repo.saveClaimFromDraft(f)};assertEquals(f.json(),repo.load().drafts.single().payload);assertTrue(repo.load().claims.isEmpty());assertTrue(repo.load().items.isEmpty());assertEquals(90000,repo.load().balance(a))
        val fixed=f.set("allocations",org.json.JSONObject().put(expense.id,"100").toString());repo.draft(fixed);repo.saveClaimFromDraft(fixed);assertEquals(10000,repo.load().claimRequested(f.id));assertTrue(repo.load().drafts.isEmpty())
    }
    @Test fun restoringRefundRechecksChangedOriginalDateAndKeepsHistoricalType()=runBlocking {
        val a=account();val first=LocalDate.now().minusDays(3).toString();val original=tx(account=a,date=first)
        val refund=LedgerForm("refund").set("original",original.id).set("amount","20").set("account",a).set("date",LocalDate.now().minusDays(2).toString());repo.save(refund);repo.manage(refund.id,"trash")
        repo.save(repo.form(original.id).set("date",LocalDate.now().minusDays(1).toString()));fails {repo.manage(refund.id,"restore")};fails {repo.save(repo.form(original.id).copy(kind="income"))}
        assertEquals(90000,repo.load().balance(a));repo.save(repo.form(original.id).set("date",first));repo.manage(refund.id,"restore");assertEquals(92000,repo.load().balance(a))
    }
    @Test fun restoringRefundAndReceiptRechecksCapsAndDatesWithoutPartialEntries()=runBlocking {
        val a=account();val start=LocalDate.now().minusDays(3).toString();val submitted=LocalDate.now().minusDays(2).toString();val paid=LocalDate.now().minusDays(1).toString()
        val expense=tx(account=a,date=start);val oldRefund=LedgerForm("refund").set("original",expense.id).set("amount","60").set("account",a).set("date",paid);repo.save(oldRefund);repo.manage(oldRefund.id,"trash")
        val newRefund=LedgerForm("refund").set("original",expense.id).set("amount","50").set("account",a).set("date",today);repo.save(newRefund);val beforeRefund=repo.load().entries
        fails {repo.manage(oldRefund.id,"restore")};assertEquals(beforeRefund,repo.load().entries);assertEquals(95000,repo.load().balance(a));assertNotNull(repo.load().transactions.first {it.id==oldRefund.id}.deletedAt)
        repo.save(repo.form(newRefund.id).set("amount","40"));repo.manage(oldRefund.id,"restore");assertEquals(100000,repo.load().balance(a))
        val claimExpense=tx(account=a,date=start);val ledger=repo.load().defaultLedger()!!.id;val claim=repo.saveClaim("恢复核对",ledger,mapOf(claimExpense.id to "100"),submitDate=submitted)
        val oldReceipt=LedgerForm("reimbursement").set("claim",claim).set("amount","60").set("account",a).set("date",paid);repo.save(oldReceipt);repo.manage(oldReceipt.id,"trash")
        val newReceipt=LedgerForm("reimbursement").set("claim",claim).set("amount","50").set("account",a).set("date",today);repo.save(newReceipt);val beforeReceipt=repo.load().entries
        fails {repo.manage(oldReceipt.id,"restore")};assertEquals(beforeReceipt,repo.load().entries);assertEquals(95000,repo.load().balance(a))
        repo.save(repo.form(newReceipt.id).set("amount","40"));repo.manage(oldReceipt.id,"restore");assertEquals(100000,repo.load().balance(a));repo.manage(oldReceipt.id,"trash")
        var c=repo.load().claims.first {it.id==claim};repo.saveClaim(c.name,ledger,mapOf(claimExpense.id to "100"),id=claim,revision=c.revision,submitDate=today)
        val beforeDate=repo.load().entries;fails {repo.manage(oldReceipt.id,"restore")};assertEquals(beforeDate,repo.load().entries);assertEquals(94000,repo.load().balance(a))
        c=repo.load().claims.first {it.id==claim};repo.saveClaim(c.name,ledger,mapOf(claimExpense.id to "100"),id=claim,revision=c.revision,submitDate=submitted);repo.manage(oldReceipt.id,"restore");assertEquals(100000,repo.load().balance(a))
    }
    @Test fun csvExportHonorsInclusiveRangeTypesAndOrderedFields()=runBlocking {
        val date=LocalDate.now().minusDays(2).toString();val expense=tx(amount="12.10",date=date);repo.save(repo.form(expense.id).set("merchant","中文,商户"));tx("income","20")
        val selected=listOf("merchant","date","type","amount","id");val csv=repo.exportCsv(from=date,to=date,types=setOf("expense"),fields=selected);assertTrue(csv.startsWith('\uFEFF'))
        val rows=LedgerRules.csv(csv);assertEquals(listOf("商户","日期","类型","金额","交易编号"),rows.first());assertEquals(listOf("中文,商户",date,"expense","12.10",expense.id),rows[1]);assertEquals(2,rows.size)
        assertEquals(1,LedgerRules.csv(repo.exportCsv(from=today,to=today,types=setOf("expense"),fields=listOf("id"))).size)
        fails {repo.exportCsv(from=today,to=date)};fails {repo.exportCsv(fields=listOf("id","id"))};fails {repo.exportCsv(types=emptySet())};assertEquals(2,repo.load().transactions.size)
    }
    @Test fun transferFeeCorrectionsSynchronizeLinkedPaymentAndCannotRemoveSource()=runBlocking {
        val a=account();val b=account("转入","0");val transfer=LedgerForm("transfer").set("amount","50").set("account",a).set("toAccount",b).set("fee","2").set("date",today);repo.save(transfer)
        val fee=repo.load().transactions.first {it.relatedTransactionId==transfer.id};val source=newId();val period=newId();db.tools().put(ToolEntry(source,repo.records.owner(),"bill","手续费登记"));db.tools().put(ToolPaymentPeriod(period,source,today,today,today,today,200))
        repo.settlePayment(null,fee.id,200,today) {id->db.tools().put(ToolPaymentSettlement(period,200,today,"existing",id));db.tools().put(db.tools().periods().first {it.id==period}.copy(status="paid"))}
        val date=LocalDate.now().minusDays(1).toString();repo.save(repo.form(transfer.id).set("fee","3").set("date",date));assertEquals(300L,db.tools().settlements().single().actualMinor);assertEquals(date,db.tools().settlements().single().paidOn);assertEquals(94700,repo.load().balance(a))
        val entries=repo.load().entries;fails {repo.save(repo.form(transfer.id).set("fee",""))};assertEquals(entries,repo.load().entries)
        repo.save(repo.form(transfer.id).set("amount","60"));assertEquals(93700,repo.load().balance(a));db.tools().put(db.tools().settlements().single().copy(expenseId=null,mode="status"));repo.save(repo.form(transfer.id).set("fee",""));assertEquals(94000,repo.load().balance(a))
    }
    @Test fun linkedCorrectionsRollBackSourceEntriesAuditAndDraftOnLateFailure()=runBlocking {
        val a=account();val owner=repo.records.owner();val date=LocalDate.now().minusDays(1).toString()
        for(kind in listOf("period","child")) {
            val source=ToolEntry(newId(),owner,if(kind=="period") "bill" else "item","联动核对 "+kind);db.tools().put(source)
            val f=LedgerForm().set("amount","18").set("account",a).set("date",today);val relation=newId()
            repo.settlePayment(f,null,1800,today) {id->if(kind=="period") {db.tools().put(ToolPaymentPeriod(relation,source.id,today,today,today,today,1800,"paid"));db.tools().put(ToolPaymentSettlement(relation,1800,today,"new",id))} else db.tools().put(ToolChild(relation,source.id,"maintenance","换屏",date=LocalDate.now().minusDays(3).toString(),costMinor=1800,expenseId=id))}
            val original=repo.form(f.id);val bad=original.set("amount","19").set("date",date).set("tags",newId());repo.prepareDraft(bad)
            val before=repo.load();val beforeSource=db.tools().entries().first {it.id==source.id};val beforePayment=db.tools().settlements();val beforeChildren=db.tools().children()
            fails {repo.save(bad)};assertEquals(before.transactions,repo.load().transactions);assertEquals(before.entries,repo.load().entries);assertEquals(before.changes,repo.load().changes);assertEquals(beforeSource,db.tools().entries().first {it.id==source.id});assertEquals(beforePayment,db.tools().settlements());assertEquals(beforeChildren,db.tools().children());assertEquals(bad.json(),repo.load().drafts.first {it.id==f.id}.payload)
            val good=bad.set("tags","");repo.draft(good);repo.save(good);assertEquals(1900L,repo.load().transactions.first {it.id==f.id}.amountMinor)
            if(kind=="period") {assertEquals(1900L,db.tools().settlements().first {it.periodId==relation}.actualMinor);assertEquals(date,db.tools().settlements().first {it.periodId==relation}.paidOn)} else {val child=db.tools().children().first {it.id==relation};assertEquals(1900L,child.costMinor);assertEquals(LocalDate.now().minusDays(3).toString(),child.date)}
        }
    }
    @Test fun itemContextReferencesKeepOriginalFactsAndRequireExplicitDetachForDeletion()=runBlocking {
        val a=account();val expense=tx(account=a);val item=ToolEntry(newId(),repo.records.owner(),"item","原物品",payload="{\"version\":1,\"amount\":\"88\",\"date\":\"2026-01-01\"}");db.tools().put(item);db.tools().put(ToolLink(item.id,"ledger",expense.id,"item_purchase"))
        val date=LocalDate.now().minusDays(1).toString();repo.save(repo.form(expense.id).set("amount","120").set("date",date));assertEquals(88000,repo.load().balance(a));assertEquals(item,db.tools().entries().first {it.id==item.id});assertTrue(repo.form(expense.id).text("contextSource").contains("原物品"))
        fails {repo.save(repo.form(expense.id).copy(kind="income"))};fails {repo.manage(expense.id,"trash")};db.tools().unlink(item.id,"ledger",expense.id);repo.manage(expense.id,"trash");assertEquals(100000,repo.load().balance(a))
    }
    @Test fun linkedCorrectionRejectsBrokenOwnershipCurrencyAndMultipleSources()=runBlocking {
        val a=account();val source=ToolEntry(newId(),repo.records.owner(),"bill","原缴费");val period=newId();db.tools().put(source);db.tools().put(ToolPaymentPeriod(period,source.id,today,today,today,today,1800,"paid"))
        val f=LedgerForm().set("amount","18").set("account",a);repo.settlePayment(f,null,1800,today) {id->db.tools().put(ToolPaymentSettlement(period,1800,today,"new",id))}
        val edit=repo.form(f.id).set("amount","19");val original=repo.load().transactions;val entries=repo.load().entries;val settlement=db.tools().settlements().single()
        db.tools().put(settlement.copy(actualMinor=1700));fails {repo.save(edit)};assertEquals(1700L,db.tools().settlements().single().actualMinor);db.tools().put(settlement)
        val other=ToolEntry(newId(),source.ownerId,"item","另一个来源");val child=ToolChild(newId(),other.id,"maintenance","维修",date=today,costMinor=1800,expenseId=f.id);db.tools().put(other);db.tools().put(child);fails {repo.save(edit)};db.tools().removeChild(child.id)
        db.tools().put(source.copy(ownerId=newId()));fails {repo.save(edit)};db.tools().removeEntry(source.id);fails {repo.save(edit)};db.tools().put(source)
        val ledger=repo.load().defaultLedger()!!;repo.dao.put(ledger.copy(currency="USD"));fails {repo.save(edit)};repo.dao.put(ledger)
        assertEquals(original,repo.load().transactions);assertEquals(entries,repo.load().entries);assertEquals(settlement,db.tools().settlements().single());assertEquals(98200,repo.load().balance(a))
        db.tools().put(source.copy(deletedAt=System.currentTimeMillis(),state="cancelled"));repo.save(edit);assertEquals(1900L,db.tools().settlements().single().actualMinor);assertNotNull(db.tools().entries().first {it.id==source.id}.deletedAt);assertEquals("cancelled",db.tools().entries().first {it.id==source.id}.state)
    }
    @Test fun projectCreationCommitsWithTransactionAndInvalidSourceKeepsDraft()=runBlocking {
        val a=account();val project=ToolEntry(newId(),repo.records.owner(),"project","装修专题");db.tools().put(project)
        val first=LedgerForm().set("amount","12").set("account",a).set("project",project.id);repo.prepareDraft(first);repo.save(first);repo.save(first)
        val link=db.tools().links().single();assertEquals(project.id,link.entryId);assertEquals("ledger",link.entityType);assertEquals(first.id,link.entityId);assertEquals(98800,repo.load().balance(a))
        val invalid=LedgerForm().set("amount","7").set("account",a).set("project",newId());repo.prepareDraft(invalid);val before=repo.load();fails {repo.save(invalid)}
        assertEquals(before.transactions,repo.load().transactions);assertEquals(before.entries,repo.load().entries);assertEquals(invalid.json(),repo.load().drafts.single().payload);assertEquals(1,db.tools().links().size)
        val repaired=invalid.set("project",project.id);repo.draft(repaired);repo.save(repaired);assertEquals(2,db.tools().links().size);assertEquals(98100,repo.load().balance(a));assertTrue(repo.load().drafts.isEmpty())
        repo.saveTemplate("不继承专题",first);assertTrue(LedgerForm.parse(repo.load().templates.single().payload).text("project").isEmpty())
    }
}
