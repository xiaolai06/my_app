package com.example.dailylife

import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.example.dailylife.core.RecordRules
import com.example.dailylife.feature.records.*
import com.example.dailylife.feature.plans.*
import com.example.dailylife.feature.ledger.*
import com.example.dailylife.feature.tools.*
import com.example.dailylife.feature.home.HomeViewModel
import com.example.dailylife.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var model: RecordsViewModel
    private lateinit var plans: PlansViewModel
    private lateinit var ledger:LedgerViewModel
    private lateinit var tools:ToolsViewModel
    private lateinit var home:HomeViewModel
    private var incoming by mutableStateOf<Intent?>(null)
    override fun onStart() {super.onStart();plans.setVisible(true)}
    override fun onStop() {plans.setVisible(false);super.onStop()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[RecordsViewModel::class.java]
        plans = ViewModelProvider(this)[PlansViewModel::class.java]
        ledger=ViewModelProvider(this)[LedgerViewModel::class.java]
        tools=ViewModelProvider(this)[ToolsViewModel::class.java]
        home=ViewModelProvider(this)[HomeViewModel::class.java]
        if (savedInstanceState == null) incoming = intent.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE || it.action=="com.example.dailylife.OPEN_PLAN" || it.action=="com.example.dailylife.OPEN_TOOL" }
        setContent {
            val ui by model.ui.collectAsStateWithLifecycle()
            val planUi by plans.ui.collectAsStateWithLifecycle()
            val ledgerUi by ledger.ui.collectAsStateWithLifecycle()
            val toolsUi by tools.ui.collectAsStateWithLifecycle()
            val homeUi by home.state.collectAsStateWithLifecycle()
            val homeError by home.error.collectAsStateWithLifecycle()
            var exportRecordId by rememberSaveable { mutableStateOf<String?>(null) }
            var exportAttachmentId by rememberSaveable { mutableStateOf<String?>(null) }
            val webLinks = remember {
                object : UriHandler {
                    override fun openUri(uri: String) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RecordRules.url(uri))))
                        }.onFailure { model.message("无法打开链接，请检查网址或安装浏览器") }
                    }
                }
            }
            val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importFiles(it) }
            val pictures = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.importFiles(it) }
            val planFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { plans.importFiles(it) }
            val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let { model.exportBackup(it) } }
            val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { model.prepareRestore(it) } }
            val planBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {it?.let {plans.exportBackup(it)}}
            val planRestore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {plans.prepareRestore(it)}}
            val ledgerFiles=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {ledger.importFiles(it)}
            val ledgerBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {it?.let {ledger.exportBackup(it)}}
            val ledgerRestore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {ledger.prepareRestore(it)}}
            val csvExport=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {it?.let {ledger.exportCsv(it)}}
            val csvImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {ledger.prepareCsv(it)}}
            val toolPictures=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) {tools.importFiles(it,imagesOnly=true)}
            val toolFiles=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {tools.importFiles(it)}
            val toolBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {it?.let {tools.backup(it)}}
            val toolRestore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {it?.let {tools.prepareRestore(it)}}
            val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->
                model.message(if(allowed) "通知权限已允许，正在核对提醒" else "通知未启用，事项及提醒配置仍保留");plans.refresh();tools.refresh()
            }
            val original = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data
                val id = exportAttachmentId; exportAttachmentId = null
                if (result.resultCode == RESULT_OK && uri != null && id != null) model.exportOriginal(uri, id)
            }
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
                val id = exportRecordId; exportRecordId = null
                if (uri != null && id != null) model.exportMarkdown(uri, id)
            }
            LaunchedEffect(ui.ready, planUi.ready,toolsUi.ready, incoming) {
                val shared = incoming
                if (ui.ready && shared != null) {
                    if(shared.action=="com.example.dailylife.OPEN_TOOL") {
                        if(toolsUi.ready) {
                            incoming=null;val id=shared.getStringExtra("toolId")
                            if(toolsUi.data.entries.any {it.id==id}) {tools.detail(id);shared.getLongExtra("jobId",-1).takeIf {it>0}?.let {plans.readNotification(it)}} else model.message("这项生活内容已不存在")
                        }
                        return@LaunchedEffect
                    }
                    if(shared.action=="com.example.dailylife.OPEN_PLAN") {
                        if(planUi.ready) {
                            incoming=null
                            val kind=shared.getStringExtra("planKind");val id=shared.getStringExtra("planId")
                            val exists=when(kind) {
                                "task"->planUi.data.tasks.any {it.id==id};"event"->planUi.data.events.any {it.id==id}
                                "habit"->planUi.data.habits.any {it.id==id};"focus"->planUi.data.sessions.any {it.id==id};"goal"->planUi.data.goals.any {it.id==id};else->false
                            }
                            if(exists) {
                                plans.detail(kind,id,shared.getStringExtra("instanceId"))
                                shared.getLongExtra("jobId",-1).takeIf {it>0}?.let {plans.readNotification(it)}
                            } else model.message("该计划内容已不存在，请从计划页面查找")
                        }
                        return@LaunchedEffect
                    }
                    incoming = null
                    if(shared.action!=Intent.ACTION_SEND && shared.action!=Intent.ACTION_SEND_MULTIPLE) return@LaunchedEffect
                    val uris = buildList<Uri> {
                        if (shared.action == Intent.ACTION_SEND) {
                            @Suppress("DEPRECATION") val uri = shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            uri?.let { add(it) }
                        } else {
                            @Suppress("DEPRECATION") val list = shared.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                            list?.let { addAll(it) }
                        }
                        shared.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { add(it) } }
                    }.distinct()
                    model.acceptShared(shared.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(), uris)
                }
            }
            DailyTheme(ui.appearance) {
                val dark = LocalGlass.current.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                CompositionLocalProvider(LocalUriHandler provides webLinks) {
                LaunchedEffect(homeError) {homeError?.let {model.message(it);home.clearError()}}
                DailyApp(ui, model, planUi, plans,ledgerUi.copy(records=ui.snapshot,hideAmounts=false),ledger,
                    toolsUi.copy(records=ui.snapshot,hideAmounts=homeUi.hideAmounts),tools,homeUi,home,home::update,
                    requestNotifications={
                        if(android.os.Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                            notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else runCatching {startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))}.onFailure {model.message("无法打开系统通知设置")}
                    },
                    addLedgerFiles={ledgerFiles.launch(arrayOf("*/*"))},backupLedger={ledgerBackup.launch("My Life-账本-"+java.time.LocalDate.now()+".zip")},restoreLedger={ledgerRestore.launch(arrayOf("application/zip","application/octet-stream"))},
                    exportCsv={csvExport.launch("My Life-账目-"+java.time.LocalDate.now()+".csv")},importCsv={csvImport.launch(arrayOf("text/*","application/octet-stream","application/vnd.ms-excel"))},
                    addToolPictures={toolPictures.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                    addToolFiles={toolFiles.launch(arrayOf("*/*"))},backupTools={toolBackup.launch("My Life-工具-"+java.time.LocalDate.now()+".zip")},restoreTools={toolRestore.launch(arrayOf("application/zip","application/octet-stream"))},
                    addPlanFiles={planFiles.launch(arrayOf("*/*"))},
                    backupPlans={planBackup.launch("My Life-计划-"+java.time.LocalDate.now()+".zip")},restorePlans={planRestore.launch(arrayOf("application/zip","application/octet-stream"))},
                    backupRecords = { backup.launch("My Life-记录-" + java.time.LocalDate.now() + ".zip") },
                    restoreRecords = { restore.launch(arrayOf("application/zip", "application/octet-stream")) },
                    addPictures = { pictures.launch(arrayOf("image/*")) },
                    addFiles = { files.launch(arrayOf("*/*")) },
                    openAttachment = { a ->
                        runCatching {
                            val file = model.repository.files.file(a)
                            require(file.exists()) { "附件原文件已缺失" }
                            val uri = FileProvider.getUriForFile(this, packageName + ".files", file)
                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { model.message("无法打开附件：" + (it.message ?: "请安装支持此格式的应用")) }
                    },
                    exportAttachment = { a ->
                        exportAttachmentId = a.id
                        original.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(a.mime).putExtra(Intent.EXTRA_TITLE, a.originalName))
                    },
                    exportRecord = { record ->
                        exportRecordId = record.record.id
                        export.launch(ui.snapshot.title(record).replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60) + ".md")
                    },
                    shareRecord = { record ->
                        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, record.markdown(ui.snapshot)), "分享记录")) }
                            .onFailure { model.message("当前没有可用的分享应用") }
                    })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming = intent }
    override fun onResume() {super.onResume();if(::plans.isInitialized) plans.refresh();if(::tools.isInitialized) tools.refresh()}
}
