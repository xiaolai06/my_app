package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import com.example.dailylife.feature.records.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassSheet(title: String, dismiss: () -> Unit, footer: (@Composable () -> Unit)? = null, scrollContent: Boolean = true, dismissEnabled:Boolean=true,
    contentHorizontalPadding:Dp=22.dp,maxHeightFraction:Float=.85f,content: @Composable ColumnScope.() -> Unit) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    if (LocalReduceMotion.current) {
        Dialog({if(canDismiss) dismiss()}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 28) {
                    SheetBody(title, dismiss, footer, scrollContent, dismissEnabled, contentHorizontalPadding, maxHeightFraction, content)
                }
            }
        }
        return
    }
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true,confirmValueChange={it!=SheetValue.Hidden || canDismiss})
    val scope=rememberCoroutineScope()
    var closing by remember {mutableStateOf(false)}
    LaunchedEffect(dismissEnabled) {
        // A late veto can leave currentValue Expanded while the drag offset heads toward Hidden.
        // Restore by target, not only isVisible, for both button and native dismiss gestures.
        if(!dismissEnabled && sheetState.targetValue==SheetValue.Hidden && sheetState.currentValue!=SheetValue.Hidden) sheetState.show()
    }
    val close:()->Unit={if(canDismiss && !closing) {closing=true;scope.launch {
        try {
            sheetState.hide()
            if(!canDismiss) sheetState.show() else if(!sheetState.isVisible) dismiss()
        } finally {closing=false}
    }}}
    val dismissOrRestore:()->Unit={
        if(canDismiss) dismiss() else if(!closing) {
            closing=true
            scope.launch {try {sheetState.show()} finally {closing=false}}
        }
    }
    val leaving=closing || sheetState.targetValue==SheetValue.Hidden
    ModalBottomSheet(onDismissRequest = dismissOrRestore, sheetState = sheetState, containerColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null) {
        GlassPanel(Modifier.fillMaxWidth().then(if(leaving) Modifier.blockGlassInput() else Modifier),radius=28) {
            SheetBody(title, close, footer, scrollContent, dismissEnabled&&!leaving, contentHorizontalPadding, maxHeightFraction, content)
        }
    }
}
@Composable private fun SheetBody(title: String, dismiss:()->Unit, footer:(@Composable ()->Unit)?, scrollContent:Boolean, dismissEnabled:Boolean,
    contentHorizontalPadding:Dp,maxHeightFraction:Float,content: @Composable ColumnScope.() -> Unit) {
    val windowHeight=androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val limit=with(LocalDensity.current) {windowHeight.toDp()}*maxHeightFraction.coerceIn(.5f,1f)
    Column(Modifier.fillMaxWidth().heightIn(max=limit).imePadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(top=10.dp),contentAlignment=Alignment.Center) {
            Box(Modifier.size(32.dp,4.dp).background(LocalGlass.current.muted.copy(alpha=.25f),androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(enabled=dismissEnabled,onClick=dismiss) {Text("关闭")}
        }
        Column(Modifier.weight(1f,fill=false).then(if(scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal=contentHorizontalPadding).padding(bottom=if(footer==null) 24.dp else 12.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
        footer?.let { HorizontalDivider(color=LocalGlass.current.accent.copy(alpha=.10f)); Box(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp)) {it()} }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun FlowChoices(options: List<Pair<String, String>>, selected: String?, select: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) -> GlassChoice(label, selected == id, onClick = { select(id) }) }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MultiChoices(options: List<Pair<String, String>>, selected: Set<String>, select: (Set<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) -> GlassChoice(label, id in selected, role=androidx.compose.ui.semantics.Role.Checkbox, onClick = {
            select(if (id in selected) selected - id else selected + id)
        }) }
    }
}
@Composable fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit, label: String = "确认") {
    GlassAlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { confirm(); dismiss() }) { Text(label) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }, containerColor = LocalGlass.current.sheet)
}

@Composable fun CalendarDialog(value: String, dismiss: () -> Unit, marked: Set<String> = emptySet(), choose: (String) -> Unit) {
    GlassDatePicker(value,dismiss,marked,choose=choose)
}

@Composable fun OrganizationSheet(ui: RecordsUi, vm: RecordsViewModel, dismiss: () -> Unit) {
    var category by remember { mutableStateOf("tag") }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf("note") }
    var subtype by remember { mutableStateOf("plain") }
    var color by remember { mutableIntStateOf(0) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var mergeId by remember { mutableStateOf<String?>(null) }
    var mergeTarget by remember { mutableStateOf<String?>(null) }
    val entries = when (category) {
        "folder" -> ui.snapshot.folders.map { it.id to it.name }
        "type" -> ui.snapshot.types.map { it.id to it.name }
        else -> ui.snapshot.tags.map { it.id to it.name }
    }
    GlassSheet("管理类型与分类", dismiss) {
        FlowChoices(listOf("tag" to "标签", "folder" to "资料夹", "type" to "内容类型"), category) {
            category = it; editing = null; name = ""; kind = "note"; subtype = "plain"
        }
        Text(when (category) {
            "folder" -> "每条记录可放入一个资料夹。删除资料夹后，记录回到未分类。"
            "type" -> "可自定义显示名称。已被使用的类型保留原内容结构，避免数据丢失。"
            else -> "每条记录可添加多个标签。同名标签会拦截，合并时自动去重。"
        }, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.muted)
        entries.forEach { (id, label) -> GlassPanel(radius = 16, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                TextButton(onClick = {
                    editing = id; name = label
                    if (category == "type") ui.snapshot.types.find { it.id == id }?.let { kind = it.kind; subtype = it.subtype }
                    if (category == "tag") color = ui.snapshot.tags.find { it.id == id }?.color ?: 0
                }) { Text("改名") }
                if (category == "tag" && entries.size > 1) TextButton(onClick = { mergeId = id }) { Text("合并") }
                TextButton(onClick = { deleteId = id }) { Text("删除") }
            }
        } }
        SectionLabel(if (editing == null) "新增" else "修改名称")
        GlassTextField(name, { name = it }, label = { Text("名称 · 最多 32 字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (category == "type" && editing == null) {
            FlowChoices(listOf("note:plain" to "Markdown", "diary:plain" to "日记", "file:plain" to "图片 / 文件",
                "bookmark:plain" to "链接收藏", "note:card" to "字段信息卡"), kind + ":" + subtype) {
                kind = it.substringBefore(':'); subtype = it.substringAfter(':')
            }
        }
        if (category == "tag") FlowChoices(listOf("0" to "雾蓝", "1" to "薄荷", "2" to "琥珀", "3" to "玫瑰"), color.toString()) { color = it.toInt() }
        Row {
            GlassButton(enabled = name.isNotBlank() && !ui.busy, onClick = {
                vm.organization(category, name, editing, color, kind, subtype)
            }) { Text("保存分类") }
            if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("取消修改") }
        }
    }
    deleteId?.let { id -> ConfirmDialog("删除分类？",
        if (category == "type") "有记录或草稿使用的类型无法删除。" else "记录正文会保留，相关分类关联将移除。",
        { deleteId = null }, { vm.deleteOrganization(category, id) }, "删除") }
    mergeId?.let { id -> GlassSheet("合并到另一个标签", { mergeId = null; mergeTarget = null }) {
        FlowChoices(entries.filter { it.first != id }, mergeTarget) { mergeTarget = it }
        GlassButton(enabled = mergeTarget != null && !ui.busy, onClick = {
            vm.deleteOrganization("tag", id, mergeTarget); mergeId = null; mergeTarget = null
        }) { Text("确认合并") }
    } }
}
