package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.records.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun RecordsScreen(ui: RecordsUi, vm: RecordsViewModel, openAttachment: (Attachment) -> Unit) {
    var add by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var organize by rememberSaveable { mutableStateOf(false) }
    var bulk by rememberSaveable { mutableStateOf(false) }
    var destroy by remember { mutableStateOf(false) }
    var diaryCalendar by remember { mutableStateOf(false) }
    val records = remember(ui.snapshot, ui.filter) { ui.snapshot.filtered(ui.filter) }
    val p = LocalGlass.current
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val showAdd = ui.selected.isEmpty() && ui.filter.state != "trash"
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).testTag("recordsContent")) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(ui.filter.query, { vm.filter(ui.filter.copy(query = it)) }, placeholder = { Text(if(largeFont) "搜索记录" else "搜索正文、标签或附件名",maxLines=1,overflow=TextOverflow.Ellipsis) },
                    singleLine = true, shape = androidx.compose.foundation.shape.RoundedCornerShape(19.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.weight(1f).testTag("recordSearch"))
                IconButton(onClick = { filters = true }) { Icon(Icons.Default.MoreVert, "筛选与排序") }
            }
            LazyRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
                item { GlassChoice("全部", ui.filter.typeId == null, onClick = { vm.filter(ui.filter.copy(typeId = null)) }) }
                items(ui.snapshot.types, key = { it.id }) { t ->
                    GlassChoice(t.name, ui.filter.typeId == t.id, onClick = { vm.filter(ui.filter.copy(typeId = t.id)) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((when (ui.filter.state) { "trash" -> "回收站"; "archived" -> "归档"; else -> if (ui.filter.inbox) "待整理" else "我的记录" }) +
                    " · " + records.size, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.muted)
                TextButton(onClick = { organize = true }) { Text("管理分类") }
                if (ui.snapshot.types.find { it.id == ui.filter.typeId }?.kind == "diary")
                    TextButton(onClick = { diaryCalendar = true }) { Text("日历") }
            }
            if (records.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text(if (ui.snapshot.records.isEmpty()) "从一条随记开始" else "这里还没有记录", style = MaterialTheme.typography.headlineSmall)
                Text(if (ui.snapshot.records.isEmpty()) "写下想法，放入照片，也可以收藏一个链接。\n点击"+(if(largeFont) "下方" else "右下方")+"「写记录」。" else "试试调整筛选，或搜索其他关键词。",
                    Modifier.padding(top = 12.dp), color = p.muted)
                if (ui.filter != RecordFilter()) TextButton(onClick = { vm.filter(RecordFilter()) }) { Text("清除筛选") }
            } else LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().testTag("recordsList"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 106.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.record.id }) { b -> RecordListItem(b, ui, vm, b.record.id in ui.selected) }
            }
        }
        if(largeFont && showAdd) FilledTonalButton(onClick={add=true},enabled=!ui.busy,
            modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).heightIn(min=56.dp).testTag("addRecord"),
            colors=ButtonDefaults.filledTonalButtonColors(containerColor=p.selected,contentColor=p.accent)) {Text("＋ 写记录")}
        }
        if (!largeFont && showAdd) FloatingActionButton(onClick = { add = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("addRecord"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            containerColor = p.selected, contentColor = p.accent) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("写记录", fontWeight = FontWeight.SemiBold)
            }
        }
        if (ui.selected.isNotEmpty()) GlassPanel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), radius = 24) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.clearSelection() }) { Text("取消 " + ui.selected.size) }
                if (ui.filter.state == "trash") {
                    TextButton(onClick = { vm.mutate(ui.selected, "restore") }) { Text("恢复") }
                    TextButton(onClick = { destroy = true }) { Text("永久删除") }
                } else {
                    TextButton(onClick = { bulk = true }) { Text("归类") }
                    TextButton(onClick = { vm.mutate(ui.selected, "archive") }) { Text("切换归档") }
                    TextButton(onClick = { vm.mutate(ui.selected, "trash") }) { Text("回收站") }
                }
            }
        }
    }
    if (add) GlassSheet("新建记录", { add = false }) {
        Text("选择内容结构，保存到本机", color = p.muted)
        ui.snapshot.types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> GlassPanel(Modifier.weight(1f), radius = 20) {
                    Column(Modifier.fillMaxWidth().testTag("newRecordType:" + t.id).clickable(enabled = !ui.busy) { add = false; vm.begin(t.id) }.padding(18.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(typeDescription(t.kind, t.subtype), style = MaterialTheme.typography.bodySmall, color = p.muted)
                    }
                } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (ui.snapshot.templates.isNotEmpty()) {
            SectionLabel("我的模板")
            ui.snapshot.templates.forEach { t -> TextButton(onClick = { add = false; vm.begin(t.typeId, template = t) }) { Text(t.name) } }
        }
        if (ui.snapshot.drafts.isNotEmpty()) {
            SectionLabel("继续草稿", "未正式提交的内容")
            ui.snapshot.drafts.forEach { d ->
                val data = runCatching { EditorData.fromJson(d.payload) }.getOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(modifier = Modifier.weight(1f), enabled = data != null, onClick = { add = false; vm.resume(d) }) {
                        Text(data?.title?.ifBlank { data.body.take(28).ifBlank { "未命名草稿" } } ?: "无法读取的草稿", maxLines = 2)
                    }
                    TextButton(onClick = { vm.discard(d.id) }) { Text("丢弃") }
                }
            }
        }
    }
    if (filters) FilterSheet(ui, { filters = false }) { vm.filter(it); filters = false }
    if (organize) OrganizationSheet(ui, vm) { organize = false }
    if (bulk) ClassificationSheet(ui, null, { bulk = false }) { folder, tags, _ ->
        vm.bulkClassify(ui.selected, folder, tags); bulk = false
    }
    if (destroy) ConfirmDialog("永久删除 " + ui.selected.size + " 条记录？", "此操作无法撤销。仍被其他记录或草稿引用的附件会保留。",
        { destroy = false }, { vm.destroy(ui.selected) }, "永久删除")
    if (diaryCalendar) CalendarDialog(ui.filter.from ?: LocalDate.now().toString(), { diaryCalendar = false },
        ui.snapshot.records.filter { it.record.deletedAt == null }.mapNotNull { it.diary?.diaryDate }.toSet()) {
        vm.filter(ui.filter.copy(from = it, to = it))
    }
}
fun typeDescription(kind: String, subtype: String): String = when {
    subtype == "card" -> "自定义字段"
    kind == "diary" -> "按日期记录"
    kind == "file" -> "图片与原文件"
    kind == "bookmark" -> "链接与笔记"
    else -> "支持 Markdown"
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun RecordListItem(b: RecordBundle, ui: RecordsUi, vm: RecordsViewModel, selected: Boolean) {
    val p = LocalGlass.current
    val attachments = ui.snapshot.attachmentsFor("record", b.record.id)
    GlassPanel(Modifier.fillMaxWidth().combinedClickable(
        onClick = { if (ui.selected.isNotEmpty()) vm.select(b.record.id) else vm.detail(b.record.id) },
        onLongClick = { vm.select(b.record.id) }), selected = selected) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.snapshot.types.find { it.id == b.record.typeId }?.name.orEmpty() +
                    if (b.record.pinOrder != null) " · 置顶" else "", style = MaterialTheme.typography.labelMedium, color = p.accent, modifier = Modifier.weight(1f))
                Text(if (selected) "✓" else Instant.ofEpochMilli(b.record.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    style = MaterialTheme.typography.labelSmall, color = p.muted)
            }
            Text(ui.snapshot.title(b), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.body.isNotBlank()) Text(b.body.replace(Regex("[#*\\u0060>]"), "").replace("\n", " ").take(180),
                maxLines = 3, overflow = TextOverflow.Ellipsis, color = p.muted, style = MaterialTheme.typography.bodyMedium)
            attachments.firstOrNull { it.mime.startsWith("image/") }?.let { AttachmentPreview(it, vm.repository.files, Modifier.fillMaxWidth().height(126.dp)) }
            val tags = ui.snapshot.tags.filter { t -> b.tagLinks.any { it.entityType == "record" && it.tagId == t.id } }
            val folder = ui.snapshot.folders.find { it.id == b.record.folderId }?.name
            Text(listOfNotNull(folder, if (b.record.organizationStatus == "inbox") "待整理" else null,
                tags.take(3).joinToString(" ") { "#" + it.name }.ifBlank { null },
                if (attachments.isNotEmpty()) attachments.size.toString() + " 份附件" else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
    }
}

@Composable fun FilterSheet(ui: RecordsUi, dismiss: () -> Unit, apply: (RecordFilter) -> Unit) {
    var filter by remember { mutableStateOf(ui.filter) }
    var calendar by remember { mutableStateOf<String?>(null) }
    GlassSheet("筛选与排序", dismiss) {
        SectionLabel("显示范围")
        FlowChoices(listOf("active" to "日常", "archived" to "已归档", "trash" to "回收站"), filter.state) { filter = filter.copy(state = it) }
        SettingRow("仅待整理", "添加标签不会自动改变整理状态", filter.inbox) { filter = filter.copy(inbox = it) }
        SectionLabel("资料夹")
        FlowChoices(listOf("all" to "全部", "none" to "未分类") + ui.snapshot.folders.map { it.id to it.name }, filter.folderId ?: "all") {
            filter = filter.copy(folderId = it.takeUnless { it == "all" })
        }
        SectionLabel("标签", "同时满足选中的标签")
        MultiChoices(ui.snapshot.tags.map { it.id to it.name }, filter.tags) { filter = filter.copy(tags = it) }
        SectionLabel("日期范围", "日记按日记日期；其他记录按创建日期")
        Row {
            TextButton(onClick = { calendar = "from" }) { Text(filter.from ?: "开始日期") }
            TextButton(onClick = { calendar = "to" }) { Text(filter.to ?: "结束日期") }
            TextButton(onClick = { filter = filter.copy(from = null, to = null) }) { Text("清除") }
        }
        SettingRow("往年同一天", "仅显示与今天月日相同的日记", filter.anniversary) { filter = filter.copy(anniversary = it) }
        FlowChoices(listOf("updated" to "最近修改", "created" to "最近创建", "title" to "按标题"), filter.sort) { filter = filter.copy(sort = it) }
        Row {
            TextButton(onClick = { filter = RecordFilter() }) { Text("重置") }
            Button(enabled = filter.from == null || filter.to == null || filter.from!! <= filter.to!!, onClick = { apply(filter) }) { Text("应用筛选") }
        }
    }
    calendar?.let { part -> CalendarDialog(if (part == "from") filter.from.orEmpty() else filter.to.orEmpty(), { calendar = null }) {
        filter = if (part == "from") filter.copy(from = it) else filter.copy(to = it)
    } }
}

@Composable fun ClassificationSheet(ui: RecordsUi, data: EditorData?, dismiss: () -> Unit,
    apply: (String?, Set<String>, Boolean) -> Unit) {
    var folder by remember { mutableStateOf(data?.folderId) }
    var tags by remember { mutableStateOf(data?.tags ?: emptySet()) }
    var organized by remember { mutableStateOf(data?.organized ?: false) }
    GlassSheet(if (data == null) "批量归类" else "整理这条记录", dismiss) {
        SectionLabel("资料夹")
        FlowChoices(listOf("none" to "未分类") + ui.snapshot.folders.map { it.id to it.name }, folder ?: "none") { folder = it.takeUnless { it == "none" } }
        SectionLabel("标签", if (data == null) "批量操作会追加标签，保留原有标签" else "可多选，点击选中项可取消")
        MultiChoices(ui.snapshot.tags.map { it.id to it.name }, tags) { tags = it }
        if (ui.snapshot.tags.isEmpty()) Text("还没有标签，可返回列表的「管理分类」创建。", style = MaterialTheme.typography.bodySmall)
        if (data != null) SettingRow("已整理", "单独控制整理状态", organized) { organized = it }
        Button(onClick = { apply(folder, tags, organized) }) { Text("确认归类") }
    }
}
