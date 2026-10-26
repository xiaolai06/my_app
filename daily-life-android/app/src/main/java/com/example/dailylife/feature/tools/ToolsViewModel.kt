package com.example.dailylife.feature.tools
import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import com.example.dailylife.core.ToolRules
import com.example.dailylife.feature.ledger.LedgerForm
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
data class ToolsUi(val ready:Boolean=false,val data:ToolsSnapshot=ToolsSnapshot(),val records:RecordsSnapshot=RecordsSnapshot(),
    val busy:Boolean=false,val error:String?=null,val message:String?=null,val page:String="home",val editor:ToolForm?=null,
    val detailId:String?=null,val saved:Boolean=true,val backupPreview:ToolsRestore?=null,val hideAmounts:Boolean=false)
class ToolsViewModel(app:Application,private val state:SavedStateHandle):AndroidViewModel(app) {
    private val application=app as DailyLifeApplication
    val repository=application.tools
    private val _ui=MutableStateFlow(ToolsUi(page=state["toolsPage"] ?: "home"));val ui=_ui.asStateFlow()
    private var pending:Job?=null;private var saving:Job?=null
    private val backups=ToolsBackup(app,repository)
    init {
        viewModelScope.launch {try {repository.initialize();state.get<String>("toolDraft")?.let {id->repository.dao.drafts().find {it.id==id}?.let {d->_ui.update {it.copy(editor=ToolForm.parse(d.payload))}}};repository.snapshot.collect {s->_ui.update {it.copy(ready=true,data=s)}}} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error="功能箱读取失败："+e.message)}}}
        viewModelScope.launch {application.records.snapshot.collect {s->_ui.update {it.copy(records=s)}}}
        viewModelScope.launch {application.preferences.dashboard.collect {p->_ui.update {it.copy(hideAmounts=p.hideAmounts)}}}
    }
    fun page(value:String) {state["toolsPage"]=value;_ui.update {it.copy(page=value,detailId=null,error=null)};if(value!="home") run(null){feature(value,"open")}}
    fun detail(id:String?) {_ui.update {it.copy(detailId=id,error=null)}}
    fun clearError() {_ui.update {it.copy(error=null)}}
    fun clearMessage() {_ui.update {it.copy(message=null)}}
    fun run(message:String?="已保存",onSuccess:()->Unit={},block:suspend ToolsRepository.()->Unit) {
        if(pending?.isActive==true) return
        pending=viewModelScope.launch {_ui.update {it.copy(busy=true,error=null)};try {repository.block();_ui.update {it.copy(message=message)};onSuccess();try {application.planAlarms.reconcile()} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(message="内容已保存，提醒排程需稍后重新核对")}}} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error=when(e) {is java.time.DateTimeException->"日期格式不正确，请检查输入";is NumberFormatException->"请输入有效数字";else->e.message ?: "操作未完成，输入仍保留"})}} finally {_ui.update {it.copy(busy=false)}}}
    }
    fun begin(kind:String,id:String?=null,projectId:String?=null,date:String?=null)=run(null) {
        saving?.cancelAndJoin();val existing=dao.drafts().find {it.id==id};val f=existing?.let {ToolForm.parse(it.payload)} ?: if(id==null) ToolForm(kind).set("date",LocalDate.now().toString()).set("effective",LocalDate.now().toString()) else form(id)
        var initial=if(id==null && existing==null) when(kind) {"bill"->f.set("cycle","once").set("interval","1");"subscription"->f.set("cycle","monthly").set("interval","1").set("amount","0");"loan"->f.set("quantity","1").set("unit","件");else->f} else f
        if(projectId!=null) initial=initial.set("_project",projectId)
        if(id==null && date!=null) initial=initial.set("date",date)
        if(existing==null) prepare(initial);state["toolDraft"]=initial.id;_ui.update {it.copy(editor=initial,saved=true)}
    }
    fun resume(d:ToolDraft) {_ui.update {it.copy(editor=ToolForm.parse(d.payload),saved=true)};state["toolDraft"]=d.id}
    fun edit(f:ToolForm) {if(_ui.value.busy) return;_ui.update {it.copy(editor=f,saved=false,error=null)};saving?.cancel();saving=viewModelScope.launch {delay(450);try {repository.draft(f);_ui.update {if(it.editor==f) it.copy(saved=true) else it}} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error="草稿暂存失败："+e.message)}}}}
    private suspend fun flush() {saving?.cancelAndJoin();_ui.value.editor?.let {repository.draft(it)}}
    fun keep()=run("草稿已保留") {flush();state["toolDraft"]=null;_ui.update {it.copy(editor=null)}}
    fun discardForm()=run("草稿已丢弃") {saving?.cancelAndJoin();_ui.value.editor?.let {discard(it.id)};state["toolDraft"]=null;_ui.update {it.copy(editor=null)}}
    fun save() {val active=pending;viewModelScope.launch {active?.join();run("已保存到本机") {flush();val f=_ui.value.editor ?: return@run;save(f);state["toolDraft"]=null;_ui.update {it.copy(editor=null,detailId=f.text("_project").ifBlank {f.id})}}}}
    fun refresh()=run(null) {refresh()}
    fun importFiles(uris:List<Uri>) {viewModelScope.launch {pending?.join();run("附件已添加到草稿") {flush();val id=_ui.value.editor?.id ?: error("请先打开编辑页");uris.forEach {importFile(id,it)}}}}
    fun settleExpense(periodId:String,amount:String,date:String,existing:String?=null,ledger:String,account:String?=null,category:String?=null) {
        if(pending?.isActive==true) return
        pending=viewModelScope.launch {_ui.update {it.copy(busy=true,error=null)};try {
            val actual=ToolRules.minor(amount);require(actual>0) {"零元期间请仅登记状态"}
            val p=repository.dao.periods().first {it.id==periodId};val source=repository.dao.entries().first {it.id==p.sourceId}
            val form=if(existing==null) LedgerForm("expense").set("amount",amount).set("date",date).set("ledger",ledger).set("account",account.orEmpty()).set("category",category.orEmpty()).set("subject",source.title).set("note","来自功能箱付款期间 "+p.originalKey) else null
            application.ledger.settlePayment(form,existing,actual,date) {expense->repository.commitSettlement(periodId,actual,date,if(existing==null) "new" else "existing",expense)}
            _ui.update {it.copy(message="付款与账目已一并保存")}
            try {application.planAlarms.reconcile()} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(message="付款与账目已保存，提醒刷新未完成，请稍后重新核对")}}
        } catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error=e.message ?: "付款未保存")}} finally {_ui.update {it.copy(busy=false)}}}
    }
    fun backup(uri:Uri) {viewModelScope.launch {pending?.join();run("功能箱备份已导出") {backups.export(uri)}}}
    fun settleChildExpense(childId:String,amount:String,date:String,existing:String?=null,ledger:String,account:String?=null,category:String?=null) {
        if(pending?.isActive==true) return
        pending=viewModelScope.launch {_ui.update {it.copy(busy=true,error=null)};try {
            val actual=ToolRules.minor(amount);require(actual>0) {"零元费用无需创建支出"}
            val child=repository.dao.children().first {it.id==childId};val source=repository.dao.entries().first {it.id==child.entryId}
            val form=if(existing==null) LedgerForm("expense").set("amount",amount).set("date",date).set("ledger",ledger).set("account",account.orEmpty()).set("category",category.orEmpty()).set("subject",child.title).set("note","来自功能箱"+(if(source.kind=="item") "维修" else "清单")+"「"+source.title+"」 · 条目 "+child.id) else null
            application.ledger.settlePayment(form,existing,actual,date) {expense->repository.commitChildExpense(childId,actual,date,expense)}
            _ui.update {it.copy(message="支出与条目来源已一并保存")}
        } catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(error=e.message ?: "支出未保存，输入仍保留")}} finally {_ui.update {it.copy(busy=false)}}}
    }
    fun exportBackup(uri:Uri)=backup(uri)
    fun prepareRestore(uri:Uri) {viewModelScope.launch {pending?.join();run(null) {val p=backups.prepare(uri);_ui.update {it.copy(backupPreview=p)}}}}
    fun restore()=run("功能箱内容已追加恢复") {val p=_ui.value.backupPreview ?: return@run;backups.restore(p);_ui.update {it.copy(backupPreview=null)}}
    fun dismissRestore() {_ui.update {it.copy(backupPreview=null)}}
}
