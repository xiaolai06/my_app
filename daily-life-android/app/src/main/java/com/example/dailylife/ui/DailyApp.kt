package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.data.repository.DashboardPreferences
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*

@Composable fun DailyApp(ui:RecordsUi,vm:RecordsViewModel,plans:PlansUi,planVm:PlansViewModel,
    ledger:LedgerUi,ledgerVm:LedgerViewModel,tools:ToolsUi,toolsVm:ToolsViewModel,
    home:DashboardPreferences,updateHome:(DashboardPreferences)->Unit,requestNotifications:()->Unit,
    addPlanFiles:()->Unit,backupPlans:()->Unit,restorePlans:()->Unit,
    addLedgerFiles:()->Unit,backupLedger:()->Unit,restoreLedger:()->Unit,exportCsv:()->Unit,importCsv:()->Unit,
    addToolFiles:()->Unit,backupTools:()->Unit,restoreTools:()->Unit,
    backupRecords:()->Unit,restoreRecords:()->Unit,addPictures:()->Unit,addFiles:()->Unit,
    openAttachment:(Attachment)->Unit,exportAttachment:(Attachment)->Unit,exportRecord:(RecordBundle)->Unit,shareRecord:(RecordBundle)->Unit) {
    val p=LocalGlass.current
    val snackbar=remember {SnackbarHostState()}
    val rootStates=rememberSaveableStateHolder()
    var page by rememberSaveable {mutableStateOf("今天")}
    var rootReturn by rememberSaveable {mutableStateOf<String?>(null)}
    var planLanding by rememberSaveable {mutableStateOf<String?>(null)}
    var settings by rememberSaveable {mutableStateOf(false)}
    var search by rememberSaveable {mutableStateOf(false)}
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
        "expense_amount"->ledgerVm.begin("expense",amount=id)
        "task_date"->planVm.begin("task",date=id)
        "anniversary_date"->toolsVm.begin("anniversary",date=id)
        "ledger_month"->{ledgerVm.month(id);ledgerVm.page("overview");page="账本"}
        "feature"->{when(id) {
            "habit","focus","goal"->{rootReturn=page;planLanding=mapOf("habit" to "习惯","focus" to "专注","goal" to "目标")[id];page="计划"}
            "card"->{rootReturn=page;ui.snapshot.types.firstOrNull {it.subtype=="card"}?.let {vm.filter(RecordFilter(typeId=it.id))};page="记录"}
            "settings","backup"->settings=true
            "notifications"->requestNotifications()
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
    val fullLedger=ledger.editor!=null || ledger.detailId!=null || page=="账本" && ledger.page in setOf("claims","imports","drafts","trash","changes","accountFlow","cashFlow")
    val fullTools=tools.editor!=null || tools.detailId!=null || page=="功能箱" && tools.page!="home"
    val busy=ui.busy || plans.busy || ledger.busy || tools.busy
    BackHandler(enabled=ui.editor==null && ui.detailId==null && plans.editor==null && plans.detailId==null && !fullLedger && !fullTools && page!="今天") {page=rootReturn?.takeIf {it!=page} ?: "今天";rootReturn=null}
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(p.background,lerp(p.background,p.selected,.26f),p.background)))) {
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
            ToolsScreen(tools,toolsVm,ledger.data,plans.data,::linked,addToolFiles,viewAttachment,{toolsBackupConfirm=true},restoreTools)
        }
        else Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={DailyNavigation(page) {rootReturn=null;page=it}}) {padding->
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom=padding.calculateBottomPadding())) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={search=true},modifier=Modifier.testTag("globalSearch")) {Icon(Icons.Default.Search,"搜索所有内容")}
                    IconButton(onClick={settings=true},modifier=Modifier.testTag("appSettings")) {Icon(Icons.Default.Person,"外观与设置")}
                }
                if(!ui.ready || !plans.ready || !ledger.ready || !tools.ready) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val failure=plans.error ?: ledger.error ?: tools.error
                    if(failure!=null) Text(failure,Modifier.padding(24.dp)) else CircularProgressIndicator()
                }
                else Crossfade(targetState=page,animationSpec=tween(if(LocalReduceMotion.current) 0 else 160),label="main destination") {current->
                    rootStates.SaveableStateProvider(current) {when(current) {
                        "今天"->HomeScreen(ui,vm,plans,planVm,ledger.data,tools.data,home,updateHome,::quick,{page=it},::linked)
                        "记录"->RecordsScreen(ui,vm,openAttachment)
                        "计划"->PlansPane(plans,planVm,planLanding,tools.data,{id->focusDetail("tools");toolsVm.detail(id)},{planLanding=null})
                        "账本"->LedgerScreen(ledger,ledgerVm,addLedgerFiles,viewAttachment,exportCsv,importCsv,backupLedger,restoreLedger)
                        "功能箱"->ToolsScreen(tools,toolsVm,ledger.data,plans.data,::linked,addToolFiles,viewAttachment,{toolsBackupConfirm=true},restoreTools)
                    }}
                }
            }
        }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal=12.dp).padding(bottom=100.dp)) {data->
            Snackbar(dismissAction={TextButton(onClick={data.dismiss()},modifier=Modifier.testTag("dismissMessage")) {Text("关闭")}}) {Text(data.visuals.message)}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding().align(Alignment.TopCenter).testTag("appBusy"))
    }
    if(search) GlobalSearch(ui.snapshot,plans.data,ledger.data,tools.data,home.hideAmounts,{search=false}) {kind,id->search=false;linked(kind,id)}
    if(plans.error!=null && plans.editor==null) AlertDialog(onDismissRequest={planVm.clearError()},title={Text("操作未完成")},
        text={Text(plans.error)},confirmButton={TextButton(onClick={planVm.clearError()}) {Text("知道了，继续编辑")}},containerColor=p.sheet)
    if(planBackupConfirm) ConfirmDialog("导出计划备份","包含任务、日程、重复实例、习惯、专注、目标、标签、草稿、回收站和引用的附件原件。ZIP 未加密；不含记录正文、系统通知历史或外观设置。上限 5000 主项、50000 明细、1000 份附件 / 256 MB。",
        {planBackupConfirm=false},backupPlans,"选择保存位置")
    if(ledgerBackupConfirm) ConfirmDialog("导出账本备份","包含账本、账户、分类、账目、报销、预算、模板、草稿、日志和附件。ZIP 未加密，金额隐藏不会移除备份中的真实金额；请存放在可信位置。不同领域需分别备份。",
        {ledgerBackupConfirm=false},backupLedger,"选择保存位置")
    if(toolsBackupConfirm) ConfirmDialog("导出功能箱备份","包含生活事项、子项、付款登记、专题引用、草稿与附件，包括私密序列号。ZIP 未加密，不包含原账目、记录和计划，也不包含计算历史；请分别备份相关领域。",
        {toolsBackupConfirm=false},backupTools,"选择保存位置")
    plans.restore?.let {preview->GlassSheet("恢复计划前确认",{planVm.dismissRestore()}) {
        Text("备份内有 "+preview.count+" 个主项与草稿，"+preview.files+" 份附件。")
        Text("按原编号追加；已有编号及其明细保留本机版本，同名分组和标签合并。原记录正文不在本包，当前不存在的来源记录会解除关联。活动计时恢复为待核对。")
        Button(enabled=!plans.busy,onClick={planVm.restoreBackup()}) {Text("确认追加恢复")}
        TextButton(enabled=!plans.busy,onClick={planVm.dismissRestore()}) {Text("取消")}
    }}
    if (settings) GlassSheet("外观与动效", { settings = false }) {
        var nickname by remember {mutableStateOf(home.nickname)}
        OutlinedTextField(nickname,{nickname=it.take(30)},label={Text("称呼（可选）")},singleLine=true,modifier=Modifier.fillMaxWidth())
        TextButton(onClick={updateHome(home.copy(nickname=nickname))}) {Text("保存称呼")}
        Text("选择主题", style = MaterialTheme.typography.titleMedium)
        FlowChoices(listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"), ui.appearance.theme) {
            vm.appearance(ui.appearance.copy(theme = it))
        }
        SettingRow("减少动态效果", "关闭选中颜色渐变与页面装饰动效", ui.appearance.reducedMotion) {
            vm.appearance(ui.appearance.copy(reducedMotion = it))
        }
        SettingRow("增强磨砂底色", "提高表面不透明度，适合更清晰的阅读", ui.appearance.frosted) {
            vm.appearance(ui.appearance.copy(frosted = it))
        }
        Text("外观偏好仅保存在这台设备。开启增强磨砂底色，可以让文字和控件更清晰。",
            style = MaterialTheme.typography.bodySmall, color = p.muted)
        HorizontalDivider()
        Text("本地资料与备份",style=MaterialTheme.typography.titleMedium)
        Text("内容保存在这台设备，卸载前请导出备份。各领域备份分别导出，附件原件包含在 ZIP 中。",color=p.muted)
        TextButton(enabled=!busy,onClick={settings=false;backupConfirm=true}) {Text("备份记录与附件")}
        TextButton(enabled=!busy,onClick=restoreRecords) {Text("恢复记录备份")}
        TextButton(enabled=!busy,onClick={settings=false;planBackupConfirm=true}) {Text("备份计划与附件")}
        TextButton(enabled=!busy,onClick=restorePlans) {Text("恢复计划备份")}
        TextButton(enabled=!busy,onClick={settings=false;ledgerBackupConfirm=true}) {Text("备份账本与附件")}
        TextButton(enabled=!busy,onClick={settings=false;page="账本";restoreLedger()}) {Text("恢复账本备份")}
        TextButton(enabled=!busy,onClick={settings=false;toolsBackupConfirm=true}) {Text("备份功能箱与附件")}
        TextButton(enabled=!busy,onClick={settings=false;page="功能箱";restoreTools()}) {Text("恢复功能箱备份")}
        Text("开发版 0.3.0 · 本地存储 · 无账号与云同步",color=p.muted,style=MaterialTheme.typography.bodySmall)
    }
    if (backupConfirm) ConfirmDialog("导出记录备份", "包含正式记录、回收站、标签、模板和附件原件，不含草稿及外观设置。ZIP 未加密，包含信息卡私密字段，请存放在可信位置。本次上限：2000 条记录、1000 份附件，共 256 MB。",
        { backupConfirm = false }, backupRecords, "选择保存位置")
    ui.restorePreview?.let { preview -> GlassSheet("恢复前确认", { if (!ui.busy) vm.dismissRestore() }) {
        Text("备份内有 " + preview.records + " 条记录、" + preview.attachments + " 份附件；其中 " + preview.existing + " 条记录标识已存在。")
        Text("内容会导入当前本地资料。同标识记录保留本机版本；已有同一天的日记会跳过。同名分类按名称合并，结构冲突会中止恢复。现有数据不会被覆盖。")
        Button(enabled = !ui.busy, onClick = { vm.restoreBackup() }) { Text("确认合并恢复") }
        TextButton(enabled = !ui.busy, onClick = { vm.dismissRestore() }) { Text("取消") }
    } }
    attachment?.let { a -> AttachmentViewer(a, vm.repository.files, { attachment = null },
        { openAttachment(a) }, { exportAttachment(a) }) }
}
@Composable fun SettingRow(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(description, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.muted) }
        Switch(checked, change)
    }
}
