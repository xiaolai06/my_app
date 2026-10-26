package com.example.dailylife.feature.plans

import android.app.Application
import androidx.lifecycle.*
import com.example.dailylife.DailyLifeApplication
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PlansUi(val ready:Boolean=false,val data:PlansSnapshot=PlansSnapshot(),val busy:Boolean=false,
    val error:String?=null,val message:String?=null,val editor:PlanForm?=null,val saved:Boolean=true,
    val detailKind:String?=null,val detailId:String?=null,val instanceId:String?=null,val liveSeconds:Long=0,val clockReview:Boolean=false,
    val restore:PlanRestore?=null)
class PlansViewModel(app:Application,private val state:SavedStateHandle):AndroidViewModel(app) {
    val repository=(app as DailyLifeApplication).plans
    private val _ui=MutableStateFlow(PlansUi());val ui=_ui.asStateFlow()
    private var saving:Job?=null;private var operation:Job?=null
    private val backups=PlansBackup(app,repository)
    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                try {(getApplication<Application>() as DailyLifeApplication).planAlarms.reconcile()} catch(e:Exception) {if(e is CancellationException) throw e}
                state.get<String>("planDraft")?.let { id -> repository.dao.drafts().firstOrNull { it.id==id }?.let { d ->
                    _ui.update { it.copy(editor=PlanForm.parse(d.payload)) }
                } }
                repository.snapshot.collect { s -> _ui.update { it.copy(ready=true,data=s) } }
            } catch(e:Exception) { if(e is CancellationException) throw e; _ui.update { it.copy(error="计划读取失败："+e.message) } }
        }
        viewModelScope.launch { while(isActive) {
            val session=_ui.value.data.sessions.firstOrNull { it.state=="running" && it.deletedAt==null }
            if(session!=null) { try {
                val seconds=repository.openSeconds(session);_ui.update { it.copy(liveSeconds=seconds ?: 0,clockReview=seconds==null) }
                if(seconds==null) repository.refresh()
            } catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(clockReview=true)}}
            } else _ui.update {it.copy(liveSeconds=0,clockReview=false)}
            delay(1000)
        } }
    }
    fun run(success:String?="已保存",onSuccess:()->Unit={},block:suspend PlansRepository.()->Unit) {
        if(operation?.isActive==true) return
        operation=viewModelScope.launch {
            _ui.update { it.copy(busy=true,error=null) }
            try { repository.block(); _ui.update { it.copy(message=success) };onSuccess()
                try {(getApplication<Application>() as DailyLifeApplication).planAlarms.reconcile()} catch(e:Exception) {if(e is CancellationException) throw e;_ui.update {it.copy(message="内容已保存，系统提醒排程失败，请在提醒中心重新核对")}}
            }
            catch(e:Exception) { if(e is CancellationException) throw e
                val message=when(e) {
                    is java.time.DateTimeException->"日期、时刻或时区格式不正确，请检查输入；草稿仍保留"
                    is NumberFormatException->"请输入有效数字；草稿仍保留"
                    else->e.message ?: "操作失败，输入仍保留"
                };_ui.update { it.copy(error=message) }
            }
            finally { _ui.update { it.copy(busy=false) } }
        }
    }
    fun clearMessage() { _ui.update { it.copy(message=null) } }
    fun clearError() { _ui.update { it.copy(error=null) } }
    fun detail(kind:String?,id:String?=null,instance:String?=null) { _ui.update { it.copy(detailKind=kind,detailId=id,instanceId=instance,error=null) } }
    fun begin(kind:String,date:String?=null,id:String?=null,projectId:String?=null) =run(null) {
        saving?.cancelAndJoin()
        val existing=dao.drafts().firstOrNull { it.id==id }
        var f=if(existing!=null) PlanForm.parse(existing.payload) else if(id!=null) form(kind,id) else {
            var f=PlanForm(kind)
            date?.let { f=f.set(if(kind=="task") "planned" else "start",it).set("end",it) };f
        }
        if(projectId!=null) f=f.set("project",projectId)
        if(existing==null) prepareDraft(f,if(id!=null && kind!="focus") kind else null) else draft(f)
        state["planDraft"]=f.id;_ui.update { it.copy(editor=f,saved=true) }
    }
    fun resume(d:PlanDraft)=run(null) { val f=PlanForm.parse(d.payload);state["planDraft"]=f.id;_ui.update { it.copy(editor=f,saved=true) } }
    fun edit(f:PlanForm) {
        if(_ui.value.busy) return
        _ui.update { it.copy(editor=f,saved=false,error=null) };saving?.cancel()
        saving=viewModelScope.launch { delay(450);try {
            repository.draft(f);_ui.update { if(it.editor==f) it.copy(saved=true) else it }
        } catch(e:Exception) { if(e is CancellationException) throw e;_ui.update { it.copy(error="草稿暂存失败："+e.message) } } }
    }
    private suspend fun flush() { saving?.cancelAndJoin();_ui.value.editor?.let { repository.draft(it) } }
    fun keep()=run("草稿已保留") { flush();state["planDraft"]=null;_ui.update { it.copy(editor=null,error=null) } }
    fun discardForm()=run("草稿已丢弃") {
        saving?.cancelAndJoin();_ui.value.editor?.let { discard(it.id) };state["planDraft"]=null;_ui.update { it.copy(editor=null,error=null) }
    }
    fun save(allowWarnings:Boolean=false)=run("已保存到本机") {
        flush();val f=_ui.value.editor ?: return@run
        if(f.kind=="focus") startFocus(f) else save(f,allowWarnings)
        state["planDraft"]=null;_ui.update { it.copy(editor=null,detailKind=f.kind,detailId=f.id,instanceId=null,error=null) }
    }
    fun refresh()=run(null) { refresh() }
    private fun afterIdle(action:()->Unit) {viewModelScope.launch {operation?.join();action()}}
    fun focus(id:String,action:String)=afterIdle {
        run(if(action in setOf("finish","early","abandon")) "专注已结束" else "计时已更新") {focusAction(id,action)}
    }
    fun readNotification(id:Long)=afterIdle {run(null) {readReminder(id)}}
    fun exportBackup(uri:android.net.Uri)=afterIdle {run("计划与附件备份已导出") {backups.export(uri)}}
    fun prepareRestore(uri:android.net.Uri)=afterIdle {run(null) {
        _ui.value.restore?.let {backups.discard(it)}
        val preview=backups.prepare(uri);_ui.update {it.copy(restore=preview)}
    }}
    fun dismissRestore() {if(!_ui.value.busy) {_ui.value.restore?.let {backups.discard(it)};_ui.update {it.copy(restore=null)}}}
    fun restoreBackup()=run("计划备份已追加恢复；既有编号保留本机版本") {
        val preview=_ui.value.restore ?: return@run
        backups.restore(preview);backups.discard(preview);_ui.update {it.copy(restore=null)}
        refresh()
    }
    fun importFiles(uris:List<android.net.Uri>)=afterIdle {run("附件已导入草稿") {
        flush();val id=_ui.value.editor?.id ?: error("请先打开编辑页")
        uris.forEach {importFile(id,it)}
    }}
    fun task(row:TaskRow,action:String,children:Boolean=false)=run(if(action=="done") "已完成，可点选框撤销" else "状态已更新") { taskAction(row,action,children) }
    fun manage(kind:String,id:String,action:String)=run("已更新") { manage(kind,id,action) }
    fun duplicate(kind:String,id:String)=run("已复制为新草稿") {
        val source=form(kind,id)
        var f=source.copy(id=newId(),revision=0).set("repeat","none").set("title",source.text("title").take(116)+"（副本）")
        if(kind=="task") {
            val list=org.json.JSONArray(f.text("subtasks","[]"))
            for(i in 0 until list.length()) list.getJSONObject(i).put("id",newId())
            f=f.set("subtasks",list.toString())
        }
        prepareDraft(f,kind,id);state["planDraft"]=f.id;_ui.update { it.copy(editor=f,saved=true) }
    }
}
