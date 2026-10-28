package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.DashboardPreferences
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*

@Composable fun DailyApp(ui:RecordsUi,vm:RecordsViewModel,plans:PlansUi,planVm:PlansViewModel,
    ledger:LedgerUi,ledgerVm:LedgerViewModel,tools:ToolsUi,toolsVm:ToolsViewModel,
    home:DashboardPreferences,homeVm:HomeViewModel,updateHome:(DashboardPreferences)->Unit,requestNotifications:()->Unit,
    addPlanFiles:()->Unit,backupPlans:()->Unit,restorePlans:()->Unit,
    addLedgerFiles:()->Unit,backupLedger:()->Unit,restoreLedger:()->Unit,exportCsv:()->Unit,importCsv:()->Unit,
    addToolFiles:()->Unit,backupTools:()->Unit,restoreTools:()->Unit,
    backupRecords:()->Unit,restoreRecords:()->Unit,addPictures:()->Unit,addFiles:()->Unit,
    openAttachment:(Attachment)->Unit,exportAttachment:(Attachment)->Unit,exportRecord:(RecordBundle)->Unit,shareRecord:(RecordBundle)->Unit,addToolPictures:()->Unit=addToolFiles) {
    val p=LocalGlass.current
    val backdrop=rememberHazeState()
    val snackbar=remember {SnackbarHostState()}
    val rootStates=rememberSaveableStateHolder()
    var page by rememberSaveable {mutableStateOf("今天")}
    var rootReturn by rememberSaveable {mutableStateOf<String?>(null)}
    var planLanding by rememberSaveable {mutableStateOf<String?>(null)}
    val weather by homeVm.weather.collectAsState()
    val focusNotifications by homeVm.focusNotifications.collectAsState()
    var reminderCenter by rememberSaveable {mutableStateOf(false)}
    var settings by rememberSaveable {mutableStateOf(false)}
    var recovery by rememberSaveable {mutableStateOf(false)}
    var resumeEditing by remember {mutableStateOf<UnfinishedEditSummary?>(null)}
    var transfer by rememberSaveable {mutableStateOf<String?>(null)}
    var settingsSection by rememberSaveable {mutableStateOf("appearance")}
    var backupConfirm by remember {mutableStateOf(false)}
    var planBackupConfirm by remember {mutableStateOf(false)}
    var ledgerBackupConfirm by remember {mutableStateOf(false)}
    var toolsBackupConfirm by remember {mutableStateOf(false)}
    var attachment by remember {mutableStateOf<Attachment?>(null)}
    var detailOrder by rememberSaveable {mutableStateOf(emptyList<String>())}
    var seenDetails by rememberSaveable {mutableStateOf(emptyMap<String,String>())}
    val activeDetails=mapOf("record" to ui.detailId,"plan" to plans.detailId,"ledger" to ledger.detailId,"tools" to tools.detailId).mapNotNull {(kind,id)->id?.let {kind to it}}.toMap()
    LaunchedEffect(activeDetails) {
        val opened=activeDetails.keys.filter {seenDetails[it]!=activeDetails[it]}
        detailOrder=detailOrder.filter {it in activeDetails && it !in opened}+opened
        seenDetails=activeDetails
    }
    val activeDetail=activeDetails.keys.lastOrNull {seenDetails[it]!=activeDetails[it]} ?: detailOrder.lastOrNull {it in activeDetails} ?: activeDetails.keys.lastOrNull()
    fun focusDetail(kind:String) {detailOrder=(detailOrder-kind)+kind}
    val viewAttachment:(Attachment)->Unit={a->if(a.mime.startsWith("image/") || a.mime=="application/pdf") attachment=a else openAttachment(a)}
    fun quick(kind:String) {when(kind) {
        "task","event"->planVm.begin(kind)
        "expense"->ledgerVm.begin("expense")
        else->ui.snapshot.types.firstOrNull {it.kind=="note" && it.subtype=="plain"}?.let {vm.begin(it.id)}
    }}
    fun linked(kind:String,id:String) {when(kind) {
        "project_new"->{
            val parts=id.split('|',limit=2);val project=parts.firstOrNull();val target=parts.getOrNull(1)
            if(project!=null && tools.data.entries.any {it.id==project && it.kind=="project" && it.deletedAt==null && it.archivedAt==null}) when(target) {
                "note"->ui.snapshot.types.firstOrNull {it.kind=="note" && it.subtype=="plain"}?.let {vm.begin(it.id,projectId=project)}
                "task","event","goal"->planVm.begin(target,projectId=project)
                "expense"->ledgerVm.begin("expense",projectId=project)
            }
        }
        "tool_feature"->{toolsVm.page(id);page="功能箱"}
        "expense_amount"->ledgerVm.begin("expense",amount=id)
        "task_date"->planVm.begin("task",date=id)
        "anniversary_date"->toolsVm.begin("anniversary",date=id)
        "ledger_month"->{ledgerVm.month(id);ledgerVm.page("overview");page="账本"}
        "feature"->{when(id) {
            "habit","focus","goal"->{rootReturn=page;planLanding=mapOf("habit" to "习惯","focus" to "专注","goal" to "目标")[id];page="计划"}
            "card"->{rootReturn=page;ui.snapshot.types.firstOrNull {it.subtype=="card"}?.let {vm.filter(RecordFilter(typeId=it.id))};page="记录"}
            "settings","backup"->{settingsSection=if(id=="backup") "backup" else "appearance";settings=true}
            "recovery"->recovery=true
            "notifications"->reminderCenter=true
        }}
        "record"->{focusDetail("record");vm.detail(id)}
        "transaction","expense","ledger"->{focusDetail("ledger");ledgerVm.detail(id)}
        "task","event","habit","focus","goal"->{focusDetail("plan");planVm.detail(kind,id)}
        else->{focusDetail("tools");toolsVm.detail(id)}
    }}
    LaunchedEffect(ui.message) {ui.message?.let {snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it);vm.message(null)}}
    LaunchedEffect(plans.message) {plans.message?.let {snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it);planVm.clearMessage()}}
    LaunchedEffect(ledger.message) {ledger.message?.let {snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it);ledgerVm.clearMessage()}}
    LaunchedEffect(tools.message) {tools.message?.let {snackbar.currentSnackbarData?.dismiss();snackbar.showSnackbar(it);toolsVm.clearMessage()}}
    val fullLedger=ledger.editor!=null || ledger.detailId!=null || page=="账本" && ledger.page in setOf("claims","imports","trash","changes","accountFlow","budgets","manage")
    val fullTools=tools.editor!=null || tools.detailId!=null || page=="功能箱" && tools.page!="home"
    val busy=ui.busy || plans.busy || ledger.busy || tools.busy
    val unfinished=remember(ui.snapshot,plans.data,ledger.data,tools.data,home.hideAmounts) {
        unfinishedEdits(ui.snapshot,plans.data,ledger.data,tools.data,home.hideAmounts)
    }
    fun continueEditing(item:UnfinishedEditSummary) {if(!busy) {resumeEditing=item;recovery=true}}
    BackHandler(enabled=ui.editor==null && ui.detailId==null && plans.editor==null && plans.detailId==null && !fullLedger && !fullTools && page!="今天") {page=rootReturn?.takeIf {it!=page} ?: "今天";rootReturn=null}
    Box(Modifier.fillMaxSize().glassBackdrop(p)) {
        if(plans.editor!=null) PlanEditor(plans,planVm,ui.snapshot,addPlanFiles,viewAttachment)
        else if(ui.editor!=null) RecordEditor(ui,vm,addPictures,addFiles,viewAttachment)
        else if(activeDetail=="record" && ledger.editor==null && tools.editor==null) RecordDetail(ui,vm,viewAttachment,exportRecord,shareRecord)
        else if(activeDetail=="plan" && ledger.editor==null && tools.editor==null) PlanDetail(plans,planVm,ui.snapshot,viewAttachment,{id->focusDetail("record");vm.detail(id)})
        else if(fullLedger && (ledger.editor!=null || tools.editor==null && (activeDetail==null || activeDetail=="ledger"))) rootStates.SaveableStateProvider("ledgerFull") {
            Box(Modifier.fillMaxSize().then(if(ledger.editor==null && ledger.detailId==null) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)) {
                LedgerScreen(ledger,ledgerVm,addLedgerFiles,viewAttachment,exportCsv,importCsv,backupLedger,restoreLedger)
            }
        }
        else if(fullTools) rootStates.SaveableStateProvider("toolsFull") {
            ToolsScreen(tools,toolsVm,ledger.data,plans.data,::linked,addToolFiles,viewAttachment,{toolsBackupConfirm=true},restoreTools,home.nickname,unfinished.size,onAddPhotos=addToolPictures,unfinished=unfinished,continueEditing=::continueEditing)
        }
        else Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={DailyNavigation(page,backdrop) {rootReturn=null;page=it}}) {padding->
            Column(Modifier.fillMaxSize().hazeSource(backdrop).glassBackdrop(p).statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
                val needsPlans=page in setOf("今天","计划","功能箱")
                val needsLedger=page in setOf("今天","账本","功能箱")
                val needsTools=page in setOf("今天","计划","功能箱")
                if(!ui.ready || needsPlans && !plans.ready || needsLedger && !ledger.ready || needsTools && !tools.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val failures=listOfNotNull(ui.loadError,plans.loadError?.takeIf {needsPlans},ledger.loadError?.takeIf {needsLedger},tools.loadError?.takeIf {needsTools})
                    if(failures.isNotEmpty()) Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(failures.joinToString("\n"))
                        Text("本机内容仍保留。可重试读取，或切换到已经加载的页面。",color=p.muted)
                        GlassButton(onClick={vm.retryLoad();planVm.retryLoad();ledgerVm.retryLoad();toolsVm.retryLoad()},modifier=Modifier.testTag("retryLoading")) {Text("重新加载")}
                    } else CircularProgressIndicator()
                }
                else GlassPageTransition(targetState=page,label="main destination") {current->
                    rootStates.SaveableStateProvider(current) {Column(Modifier.fillMaxSize()) {
                        if(current !in setOf("今天","账本","计划")) GlassPageHeader(rootDestinations.firstOrNull {it.first==current}?.second ?: current)
                        Box(Modifier.weight(1f)) {when(current) {
                        "今天"->HomeScreen(ui,vm,plans,planVm,ledger.data,tools.data,home,weather,homeVm,updateHome,{page=it},::linked)
                        "记录"->RecordsScreen(ui,vm,openAttachment)
                        "计划"->PlansPane(plans,planVm,planLanding,tools.data,{id->focusDetail("tools");toolsVm.detail(id)},{planLanding=null})
                        "账本"->LedgerScreen(ledger,ledgerVm,addLedgerFiles,viewAttachment,exportCsv,importCsv,backupLedger,restoreLedger)
                        "功能箱"->ToolsScreen(tools,toolsVm,ledger.data,plans.data,::linked,addToolFiles,viewAttachment,{toolsBackupConfirm=true},restoreTools,home.nickname,unfinished.size,onAddPhotos=addToolPictures,unfinished=unfinished,continueEditing=::continueEditing)
                    }}}}
                }
            }
        }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal=12.dp).padding(bottom=100.dp)) {data->
            Snackbar(dismissAction={TextButton(onClick={data.dismiss()},modifier=Modifier.testTag("dismissMessage")) {Text("关闭")}}) {Text(data.visuals.message)}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter).testTag("appBusy"))
    }
    if(reminderCenter) ReminderCenter(plans,planVm,focusNotifications.first,homeVm::setFocusNotifications,requestNotifications,{reminderCenter=false}) {j->linked(j.sourceType,j.sourceId)}
    if(recovery) RecoverEditing(ui,vm,plans,planVm,ledger,ledgerVm,tools,toolsVm,initialResume=resumeEditing) {recovery=false;resumeEditing=null}
    if(plans.error!=null && plans.editor==null) GlassAlertDialog(onDismissRequest={planVm.clearError()},title={Text("操作未完成")},
        text={Text(plans.error)},confirmButton={TextButton(onClick={planVm.clearError()}) {Text("知道了，继续编辑")}},containerColor=p.sheet)
    if(ledger.error!=null && page!="账本" && !fullLedger && !recovery) GlassAlertDialog(onDismissRequest=ledgerVm::clearError,
        title={Text("数据操作未完成")},text={Text(ledger.error)},
        confirmButton={TextButton(onClick=ledgerVm::clearError) {Text("知道了")}},containerColor=p.sheet)
    if(planBackupConfirm) ConfirmDialog("导出计划备份","包含任务、日程、重复实例、习惯、专注、目标、标签、未完成编辑、回收站和引用的附件原件。ZIP 未加密；不含记录正文、系统通知历史或外观设置。上限 5000 主项、50000 明细、1000 份附件 / 256 MB。",
        {planBackupConfirm=false},backupPlans,"选择保存位置")
    if(ledgerBackupConfirm) ConfirmDialog("导出账本备份","包含账本、账户、分类、账目、报销、预算、模板、未完成编辑、日志和附件。ZIP 未加密，金额隐藏不会移除备份中的真实金额；请存放在可信位置。不同领域需分别备份。",
        {ledgerBackupConfirm=false},backupLedger,"选择保存位置")
    if(toolsBackupConfirm) ConfirmDialog("导出工具备份","包含生活事项、子项、付款登记、专题引用、未完成编辑与附件，包括私密序列号。ZIP 未加密，不包含原账目、记录和计划，也不包含计算历史；请分别备份相关领域。",
        {toolsBackupConfirm=false},backupTools,"选择保存位置")
    plans.restore?.let {preview->GlassSheet("恢复计划前确认",{planVm.dismissRestore()},dismissEnabled=!plans.busy) {
        Text("备份内有 "+preview.count+" 个主项与未完成编辑，"+preview.files+" 份附件。")
        Text("按原编号追加；已有编号及其明细保留本机版本，同名分组和标签合并。原记录正文不在本包，当前不存在的来源记录会解除关联。活动计时恢复为待核对。")
        GlassButton(enabled=!plans.busy,onClick={planVm.restoreBackup()}) {Text("确认追加恢复")}
        TextButton(enabled=!plans.busy,onClick={planVm.dismissRestore()}) {Text("取消")}
    }}
    if(settings) {
        if(settingsSection=="appearance") AppearanceSettings(ui.appearance,vm::appearance,home.nickname,
            {name,result->homeVm.saveDashboard(home.copy(nickname=name),result)},{settings=false})
        else BackupSettings(busy,{settings=false},backup={domain->
            settings=false
            when(domain) {"records"->backupConfirm=true;"plans"->planBackupConfirm=true;"ledger"->ledgerBackupConfirm=true;else->toolsBackupConfirm=true}
        },restore={domain->
            settings=false
            when(domain) {"records"->restoreRecords();"plans"->restorePlans();"ledger"->{page="账本";restoreLedger()};else->{page="功能箱";restoreTools()}}
        },transfer={kind->
            settings=false
            if(kind=="importCsv") {page="账本";ledgerVm.page("imports");importCsv()} else transfer=kind
        })
    }
    if (backupConfirm) ConfirmDialog("导出记录备份", "包含正式记录、回收站、标签、模板和附件原件，不含草稿及外观设置。ZIP 未加密，包含信息卡私密字段，请存放在可信位置。本次上限：2000 条记录、1000 份附件，共 256 MB。",
        { backupConfirm = false }, backupRecords, "选择保存位置")
    ui.restorePreview?.let { preview -> GlassSheet("恢复前确认", { if (!ui.busy) vm.dismissRestore() },dismissEnabled=!ui.busy) {
        Text("备份内有 " + preview.records + " 条记录、" + preview.attachments + " 份附件；其中 " + preview.existing + " 条记录标识已存在。")
        Text("内容会导入当前本地资料。同标识记录保留本机版本；已有同一天的日记会跳过。同名分类按名称合并，结构冲突会中止恢复。现有数据不会被覆盖。")
        GlassButton(enabled = !ui.busy, onClick = { vm.restoreBackup() }) { Text("确认合并恢复") }
        TextButton(enabled = !ui.busy, onClick = { vm.dismissRestore() }) { Text("取消") }
    } }
    attachment?.let { a -> AttachmentViewer(a, vm.repository.files, { attachment = null },
        { openAttachment(a) }, { exportAttachment(a) }) }
    transfer?.let {kind->DataTransferSheet(kind,ui.snapshot,ledger,ledgerVm,{transfer=null},exportCsv,exportRecord,exportAttachment)}
}
@Composable fun SettingRow(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp)
        .toggleable(value=checked,role=androidx.compose.ui.semantics.Role.Switch,onValueChange=change).padding(vertical=10.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title);if(description.isNotBlank()) Text(description,style=MaterialTheme.typography.bodySmall,color=LocalGlass.current.muted)
        }
        GlassSwitch(checked,null)
    }
}
