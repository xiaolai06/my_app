package com.example.dailylife.feature.ledger

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import org.json.JSONArray
import org.json.JSONObject

val ledgerCsvFields=linkedMapOf("date" to "日期","amount" to "金额","type" to "类型代码","category" to "分类","merchant" to "商户","note" to "备注","external" to "外部编号","time" to "具体时刻","ledger" to "账本","account" to "账户","id" to "交易编号")
val ledgerCsvTypes=setOf("expense","income","transfer","refund","reimbursement","adjustment")
data class LedgerCsvExport(val ledgerId:String?=null,val from:String="",val to:String="",val types:Set<String> = ledgerCsvTypes,val fields:List<String> = ledgerCsvFields.keys.toList()) {
    fun json()=JSONObject().put("ledger",ledgerId.orEmpty()).put("from",from).put("to",to).put("types",JSONArray(types.toList())).put("fields",JSONArray(fields)).toString()
    companion object {
        fun parse(text:String):LedgerCsvExport {
            val o=JSONObject(text)
            fun values(key:String):List<String> {
                val array=o.getJSONArray(key)
                return (0 until array.length()).map {array.getString(it)}
            }
            return LedgerCsvExport(o.optString("ledger").ifBlank {null},o.optString("from"),o.optString("to"),values("types").toSet(),values("fields"))
        }
    }
}

data class LedgerTransactionFilter(val query:String="",val type:String="all",val category:String="",val account:String="",val onlyBudget:Boolean=false,val from:String="",val to:String="",val min:String="",val max:String="",val advanced:Boolean=false) {
    fun json()=JSONObject().put("query",query).put("type",type).put("category",category).put("account",account).put("budget",onlyBudget).put("from",from).put("to",to).put("min",min).put("max",max).put("advanced",advanced).toString()
    companion object {
        fun parse(text:String):LedgerTransactionFilter {
            val o=JSONObject(text)
            return LedgerTransactionFilter(o.optString("query"),o.optString("type","all"),o.optString("category"),o.optString("account"),o.optBoolean("budget"),o.optString("from"),o.optString("to"),o.optString("min"),o.optString("max"),o.optBoolean("advanced"))
        }
    }
}

data class LedgerUi(val ready:Boolean=false,val data:LedgerSnapshot=LedgerSnapshot(),val records:RecordsSnapshot=RecordsSnapshot(),
    val busy:Boolean=false,val error:String?=null,val message:String?=null,val editor:LedgerForm?=null,val saved:Boolean=true,
    val detailId:String?=null,val page:String="overview",val ledgerId:String?=null,val month:String=YearMonth.now().toString(),
    val hideAmounts:Boolean=false,val csvHeaders:List<String> = emptyList(),val csvSample:List<List<String>> = emptyList(),val csvPending:Boolean=false,
    val restore:LedgerRestore?=null,val accountId:String?=null,val csvExport:LedgerCsvExport?=null,
    val transactionFilter:LedgerTransactionFilter=LedgerTransactionFilter(),val accountFrom:String?=null,val accountTo:String?=null,val cashFrom:String?=null,val cashTo:String?=null)

class LedgerViewModel(app:Application,private val state:SavedStateHandle):AndroidViewModel(app) {
    private val application=app as DailyLifeApplication
    val repository=application.ledger
    val files=application.records.files
    private val _ui=MutableStateFlow(LedgerUi(page=state["ledgerPage"] ?: "overview",ledgerId=state["ledgerId"],month=state["ledgerMonth"] ?: YearMonth.now().toString(),accountId=state["ledgerAccount"],
        csvExport=state.get<String>("ledgerCsvExport")?.let {runCatching {LedgerCsvExport.parse(it)}.getOrNull()},
        transactionFilter=state.get<String>("ledgerFilter")?.let {runCatching {LedgerTransactionFilter.parse(it)}.getOrNull()} ?: LedgerTransactionFilter(),
        accountFrom=state["ledgerAccountFrom"],accountTo=state["ledgerAccountTo"],cashFrom=state["ledgerCashFrom"],cashTo=state["ledgerCashTo"]))
    val ui=_ui.asStateFlow()
    private var pending:Job?=null;private var saving:Job?=null;private var csvText:String?=null
    private val backups=LedgerBackup(application,repository)
    init {
        viewModelScope.launch {try {
            repository.initialize()
            state.get<String>("ledgerDraft")?.let {id->repository.load().drafts.find {it.id==id}?.let {d->val f=refreshSource(LedgerForm.parse(d.payload));_ui.update {it.copy(editor=f)}}}
            repository.snapshot.collect {s->_ui.update {it.copy(ready=true,data=s,ledgerId=it.ledgerId?.takeIf {id->s.ledgers.any {l->l.id==id}} ?: s.defaultLedger()?.id)}}
        } catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error="账本读取失败："+e.message)}}}
        viewModelScope.launch {application.records.snapshot.collect {s->_ui.update {it.copy(records=s)}}}
        viewModelScope.launch {application.preferences.dashboard.collect {p->_ui.update {it.copy(hideAmounts=p.hideAmounts)}}}
    }
    fun page(value:String) {state["ledgerPage"]=value;_ui.update {it.copy(page=value,detailId=null,error=null)}}
    fun selectLedger(id:String) {state["ledgerId"]=id;_ui.update {it.copy(ledgerId=id)}}
    fun openAccount(id:String) {
        if(id!=_ui.value.accountId) {state["ledgerAccountFrom"]=null;state["ledgerAccountTo"]=null;_ui.update {it.copy(accountFrom=null,accountTo=null)}}
        state["ledgerAccount"]=id;_ui.update {it.copy(accountId=id)};page("accountFlow")
    }
    fun accountRange(from:String,to:String) {state["ledgerAccountFrom"]=from;state["ledgerAccountTo"]=to;_ui.update {it.copy(accountFrom=from,accountTo=to)}}
    fun cashRange(from:String,to:String) {state["ledgerCashFrom"]=from;state["ledgerCashTo"]=to;_ui.update {it.copy(cashFrom=from,cashTo=to)}}
    fun filter(value:LedgerTransactionFilter) {state["ledgerFilter"]=value.json();_ui.update {it.copy(transactionFilter=value)}}
    fun configureCsv(value:LedgerCsvExport) {state["ledgerCsvExport"]=value.json();_ui.update {it.copy(csvExport=value)}}
    fun month(value:String) {runCatching {YearMonth.parse(value)}.onSuccess {state["ledgerMonth"]=value;_ui.update {it.copy(month=value)}}}
    fun detail(id:String?) {_ui.update {it.copy(detailId=id,error=null)}}
    fun clearError() {_ui.update {it.copy(error=null)}}
    fun clearMessage() {_ui.update {it.copy(message=null)}}
    fun toggleHidden() {viewModelScope.launch {try {
        val p=application.preferences.dashboard.first();application.preferences.setDashboard(p.copy(hideAmounts=!p.hideAmounts))
    } catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error="金额显示偏好保存失败，请重试")}}}}
    private suspend fun refreshSource(f:LedgerForm):LedgerForm {
        if(f.revision==0L || f.kind !in setOf("expense","income","transfer")) return f
        val current=repository.form(f.id)
        return f.set("linkedSource",current.text("linkedSource")).set("contextSource",current.text("contextSource"))
    }
    fun run(message:String?="已保存",onSuccess:()->Unit={},block:suspend LedgerRepository.()->Unit) {
        if(pending?.isActive==true) return
        pending=viewModelScope.launch {
            _ui.update {it.copy(busy=true,error=null)}
            try {repository.block();_ui.update {it.copy(message=message)};onSuccess()}
            catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error=when(e) {
                is DateTimeException->"日期或时刻不正确，请检查输入；未保存的内容仍保留"
                is NumberFormatException->"请输入有效数字；未保存的内容仍保留"
                else->e.message ?: "操作未完成，输入仍保留"
            })}}
            finally {_ui.update {it.copy(busy=false)}}
        }
    }
    fun begin(kind:String="expense",id:String?=null,original:String?=null,claim:String?=null,template:TransactionTemplate?=null,amount:String?=null,projectId:String?=null)=run(null) {
        saving?.cancelAndJoin()
        val existing=id?.let {v->load().drafts.find {it.id==v}}
        var f=if(existing!=null) LedgerForm.parse(existing.payload) else if(id!=null) form(id) else if(template!=null) {
            val source=LedgerForm.parse(template.payload);source.copy(id=newId(),revision=0).set("date",LocalDate.now().toString())
        } else LedgerForm(kind).set("ledger",_ui.value.ledgerId ?: load().defaultLedger()?.id.orEmpty()).set("date",LocalDate.now().toString())
        original?.let {f=f.set("original",it)};claim?.let {f=f.set("claim",it)};amount?.let {f=f.set("amount",it)};projectId?.let {f=f.set("project",it)}
        f=refreshSource(f)
        prepareDraft(f);state["ledgerDraft"]=f.id;_ui.update {it.copy(editor=f,saved=true)}
    }
    fun beginClaim(id:String?=null)=run(null) {
        saving?.cancelAndJoin()
        val existing=id?.let {value->load().drafts.find {it.id==value}}
        val f=existing?.let {LedgerForm.parse(it.payload)} ?: if(id!=null) claimForm(id) else LedgerForm("claim")
            .set("ledger",_ui.value.ledgerId ?: load().defaultLedger()?.id.orEmpty()).set("allocations","{}")
        require(f.kind=="claim") {"请从报销申请重新打开"}
        prepareDraft(f);state["ledgerDraft"]=f.id;state["ledgerPage"]="claims"
        _ui.update {it.copy(editor=f,page="claims",saved=true)}
    }
    fun edit(f:LedgerForm) {
        if(_ui.value.busy) return
        _ui.update {it.copy(editor=f,saved=false,error=null)};saving?.cancel()
        saving=viewModelScope.launch {delay(450);try {repository.draft(f);_ui.update {if(it.editor==f) it.copy(saved=true) else it}}
            catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error="草稿暂存失败："+e.message)}}}
    }
    private suspend fun flush() {saving?.cancelAndJoin();_ui.value.editor?.let {repository.draft(it)}}
    fun save(keepAdding:Boolean=false,allowWarnings:Boolean=false)=run("已保存到本机") {
        flush();val f=_ui.value.editor ?: return@run
        val id=save(f,allowWarnings);state["ledgerDraft"]=null
        if(keepAdding) {val fresh=LedgerForm(f.kind).set("ledger",f.text("ledger")).set("date",LocalDate.now().toString());prepareDraft(fresh);state["ledgerDraft"]=fresh.id;_ui.update {it.copy(editor=fresh,saved=true)}}
        else _ui.update {it.copy(editor=null,detailId=id,saved=true)}
    }
    fun saveClaimDraft()=run("报销申请已保存") {
        flush();val f=_ui.value.editor ?: return@run;require(f.kind=="claim")
        saveClaimFromDraft(f);state["ledgerDraft"]=null;state["ledgerPage"]="claims"
        _ui.update {it.copy(editor=null,detailId=null,page="claims",saved=true)}
    }
    fun keep()=run("草稿已保留") {flush();state["ledgerDraft"]=null;_ui.update {it.copy(editor=null)}}
    fun discardEditor()=run("草稿已丢弃") {saving?.cancelAndJoin();_ui.value.editor?.let {discard(it.id)};state["ledgerDraft"]=null;_ui.update {it.copy(editor=null)}}
    fun resume(d:LedgerDraft)=run(null) {val f=refreshSource(LedgerForm.parse(d.payload));state["ledgerDraft"]=f.id;_ui.update {it.copy(editor=f,saved=true)}}
    fun duplicate(t:LedgerTransaction)=run("已复制为新草稿，请确认日期和金额") {
        val source=form(t.id);val f=source.copy(id=newId(),revision=0).set("date",LocalDate.now().toString()).set("original","").set("claim","").set("fee","").set("linkedSource","").set("contextSource","").set("project","")
        require(f.kind in setOf("expense","income","transfer")) {"退款、回款和余额校准不能复制成普通账目"}
        prepareDraft(f);state["ledgerDraft"]=f.id;_ui.update {it.copy(editor=f,saved=true)}
    }
    fun importFiles(uris:List<Uri>) {viewModelScope.launch {pending?.join();run("附件已导入草稿") {flush();val id=_ui.value.editor?.id ?: error("请先打开编辑页");uris.forEach {importFile(id,it)}}}}
    fun exportBackup(uri:Uri) {viewModelScope.launch {pending?.join();run("账本与附件备份已导出") {backups.export(uri)}}}
    fun prepareRestore(uri:Uri) {viewModelScope.launch {pending?.join();run(null) {
        _ui.value.restore?.let {backups.discard(it)};val preview=backups.prepare(uri);_ui.update {it.copy(restore=preview)}
    }}}
    fun dismissRestore() {if(!_ui.value.busy) {_ui.value.restore?.let {backups.discard(it)};_ui.update {it.copy(restore=null)}}}
    fun restoreBackup()=run("账本备份已追加恢复；已有编号保留本机版本") {
        val p=_ui.value.restore ?: error("请先选择备份");backups.restore(p);backups.discard(p);_ui.update {it.copy(restore=null)}
    }
    fun prepareCsv(uri:Uri) {viewModelScope.launch {pending?.join();run(null) {
        val text=withContext(Dispatchers.IO) {application.contentResolver.openInputStream(uri)?.use {s->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536)
            while(true) {val count=s.read(buffer);if(count<0) break;require(out.size()+count<=10_000_000) {"CSV 不能超过 10 MB"};out.write(buffer,0,count)}
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString()
        } ?: error("无法打开文件")}
        val rows=com.example.dailylife.core.LedgerRules.csv(text);require(rows.size>1) {"文件需有表头和至少一行账单"}
        csvText=text;_ui.update {it.copy(csvHeaders=rows.first(),csvSample=rows.drop(1).take(3),csvPending=true,page="imports")}
    }}}
    fun dismissCsv() {csvText=null;_ui.update {it.copy(csvPending=false)}}
    fun stageCsv(ledger:String,account:String?,source:String,sourceAccount:String,mapping:Map<String,Int>)=run("预览已生成，请核对后导入",onSuccess={dismissCsv()}) {prepareCsv(csvText ?: error("请重新选择 CSV"),ledger,account,source,sourceAccount,mapping)}
    fun exportCsv(uri:Uri) {viewModelScope.launch {pending?.join();run("CSV 明细已导出") {
        val month=YearMonth.parse(_ui.value.month);val options=_ui.value.csvExport ?: LedgerCsvExport(_ui.value.ledgerId,month.atDay(1).toString(),month.atEndOfMonth().toString())
        val text=exportCsv(options.ledgerId,options.from.ifBlank {null},options.to.ifBlank {null},options.types,options.fields)
        withContext(Dispatchers.IO) {application.contentResolver.openOutputStream(uri,"wt")?.use {it.write(text.toByteArray(Charsets.UTF_8))} ?: error("无法写入文件")}
    }}}
}
