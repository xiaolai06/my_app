package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.records.*

@Composable fun RecordEditor(ui: RecordsUi, vm: RecordsViewModel, addPictures: () -> Unit,
    addFiles: () -> Unit, openAttachment: (Attachment) -> Unit) {
    val session = ui.editor ?: return
    val data = session.data
    val type = ui.snapshot.types.find { it.id == data.typeId }
    val p = LocalGlass.current
    var preview by rememberSaveable(session.draftId) { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var classification by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) }
    var typePicker by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var fieldEdit by remember { mutableStateOf<CardField?>(null) }
    var existingFiles by remember { mutableStateOf(false) }
    var bodyValue by remember(session.draftId) { mutableStateOf(TextFieldValue(data.body)) }
    LaunchedEffect(data.body) { if (bodyValue.text != data.body) bodyValue = TextFieldValue(data.body, TextRange(data.body.length)) }
    BackHandler { if (!ui.busy) leave = true }
    fun insert(prefix: String, suffix: String = "") {
        val start = bodyValue.selection.min
        val end = bodyValue.selection.max
        val result = bodyValue.text.substring(0, start) + prefix + bodyValue.text.substring(start, end) + suffix + bodyValue.text.substring(end)
        bodyValue = TextFieldValue(result, TextRange(start + prefix.length, end + prefix.length))
        vm.edit(data.copy(body = result))
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(10.dp), radius = 24) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !ui.busy, onClick = { preview = !preview }) { Text(if (preview) "继续编辑" else "预览") }
                    if (!preview) {
                        TextButton(onClick = { insert("## ") }) { Text("标题") }
                        TextButton(onClick = { insert("**", "**") }) { Text("加粗") }
                        TextButton(onClick = { insert("\n- ") }) { Text("列表") }
                        TextButton(onClick = { insert("\n- [ ] ") }) { Text("待办") }
                        TextButton(onClick = { insert("[", "](https://)") }) { Text("链接") }
                    }
                    TextButton(enabled = !ui.busy, onClick = addPictures) { Text("图片") }
                    TextButton(enabled = !ui.busy, onClick = addFiles) { Text("文件") }
                    TextButton(enabled = !ui.busy, onClick = { existingFiles = true }) { Text("已有附件") }
                    TextButton(onClick = { classification = true }) { Text("归类") }
                    TextButton(onClick = { template = true }) { Text("存模板") }
                }
                ui.editorError?.let { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (session.saved) "草稿已暂存本机" else "正在暂存草稿…",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = p.muted)
                    TextButton(enabled = !ui.busy, onClick = { vm.keepDraft() }) { Text("稍后写") }
                    Button(enabled = !ui.busy, onClick = { vm.save() }, modifier = Modifier.testTag("saveRecord")) { Text("保存记录") }
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !ui.busy, onClick = { leave = true }) { Icon(Icons.Default.ArrowBack, "返回并处理草稿") }
                Column(Modifier.weight(1f)) {
                    Text(if (data.baseRevision == 0L) "写一条记录" else "编辑记录", style = MaterialTheme.typography.titleLarge)
                    Text(type?.name ?: "类型不存在", style = MaterialTheme.typography.bodySmall, color = p.muted)
                }
                TextButton(enabled = !ui.busy, onClick = { typePicker = true }) { Text("类型") }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if(data.projectId!=null) {
                    Text("保存后加入原专题",color=p.muted)
                    TextButton(enabled=!ui.busy,onClick={vm.edit(data.copy(projectId=null))}) {Text("取消加入专题")}
                }
                if (type?.kind == "diary") GlassPanel(Modifier.fillMaxWidth(), radius = 18) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { date = true }, modifier = Modifier.weight(1f)) { Text("日期  " + data.diaryDate + "  ▾") }
                        TextButton(onClick = {
                            val moods = listOf("", "晴朗", "平静", "充实", "疲惫", "低落")
                            vm.edit(data.copy(mood = moods[(moods.indexOf(data.mood) + 1).mod(moods.size)]))
                        }) { Text(data.mood.ifBlank { "选心情" }) }
                    }
                }
                if (preview) {
                    Text(data.title.ifBlank { "内容预览" }, style = MaterialTheme.typography.headlineMedium)
                    if (data.body.isBlank()) Text("尚无正文", color = p.muted) else MarkdownBody(data.body)
                    data.fields.forEach { f -> Text(f.name + "：" + if (f.isPrivate) "••••（私密）" else f.value) }
                } else {
                    OutlinedTextField(data.title, { vm.edit(data.copy(title = it)) },
                        label = { Text(if (type?.subtype == "card") "信息卡名称" else "标题（可留空）") },
                        modifier = Modifier.fillMaxWidth().testTag("recordTitle"), singleLine = true,
                        isError = data.title.length > 120,
                        supportingText = { if (data.title.length > 120) Text("标题最多 120 字，请缩短；输入仍保留在草稿") },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    if (type?.kind == "bookmark") {
                        OutlinedTextField(data.url, { vm.edit(data.copy(url = it)) }, label = { Text("网页链接") },
                            placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        SettingRow("允许另存相同链接", "默认提醒重复收藏，不会擅自删掉网址参数", data.allowDuplicateUrl) { vm.edit(data.copy(allowDuplicateUrl = it)) }
                    }
                    if (type?.subtype == "card") {
                        Text("字段信息卡", style = MaterialTheme.typography.titleMedium)
                        Text("私密字段默认遮挡，搜索和分享不包含字段值。请勿用来保存密码。",
                            style = MaterialTheme.typography.bodySmall, color = p.muted)
                        data.fields.forEachIndexed { index, f -> GlassPanel(Modifier.fillMaxWidth(), radius = 18) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { fieldEdit = f }.padding(vertical = 12.dp)) {
                                    Text(f.name.ifBlank { "未命名字段" } + if (f.isPrivate) " · 私密" else "")
                                    Text(if (f.isPrivate) "••••" else f.value.take(80), color = p.muted)
                                }
                                if (index > 0) TextButton(onClick = {
                                    val fields = data.fields.toMutableList(); fields[index] = fields[index - 1]; fields[index - 1] = f
                                    vm.edit(data.copy(fields = fields))
                                }) { Text("上移") }
                                TextButton(onClick = { vm.edit(data.copy(fields = data.fields.filterNot { it.id == f.id })) }) { Text("移除") }
                            }
                        } }
                        OutlinedButton(onClick = { fieldEdit = CardField(newId(), data.recordId, "", "") }, enabled = data.fields.size < 50) { Text("添加字段") }
                    }
                    OutlinedTextField(bodyValue, {
                        bodyValue = it; vm.edit(data.copy(body = it.text))
                    }, label = { Text(if (type?.subtype == "card") "补充说明 · Markdown" else "正文 · Markdown") },
                        placeholder = { Text("从这里写下今天的想法…\n\n支持 # 标题、**加粗**、列表和表格。\n也可以通过底部工具栏插入格式。") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp).testTag("recordBody"),
                        isError = bodyValue.text.length > 50000,
                        supportingText = { Text(bodyValue.text.length.toString() + " / 50,000 字" + if (bodyValue.text.length > 50000) " · 请拆分正文，草稿仍保留" else "") },
                        minLines = 9, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                }
                val folder = ui.snapshot.folders.find { it.id == data.folderId }?.name ?: "未分类"
                val tags = ui.snapshot.tags.filter { it.id in data.tags }.joinToString(" ") { "#" + it.name }
                TextButton(onClick = { classification = true }) { Text(folder + " · " + (if (data.organized) "已整理" else "待整理") + if (tags.isNotBlank()) " · " + tags else "") }
                val attachments = ui.snapshot.attachmentsFor("draft", session.draftId)
                if (attachments.isNotEmpty()) {
                    SectionLabel("附件 · " + attachments.size, "移除只影响当前草稿，原记录在保存前保持完整。")
                    AttachmentList(attachments, vm.repository.files, openAttachment) { vm.removeAttachment(it.id) }
                } else if (type?.kind == "file") {
                    GlassPanel(Modifier.fillMaxWidth(), radius = 20) {
                        Column(Modifier.padding(18.dp)) {
                            Text("把资料留在手边", style = MaterialTheme.typography.titleMedium)
                            Text("从底部添加图片或文件。图片单份不超过 20 MB，其他文件不超过 100 MB。", Modifier.padding(top = 8.dp), color = p.muted)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if (leave) GlassSheet("保存这次编辑？", { leave = false }) {
        Text("内容已作为草稿暂存，正式保存前不会修改原记录。")
        Button(enabled = !ui.busy, onClick = { leave = false; vm.keepDraft() }) { Text("保留草稿并返回") }
        TextButton(enabled = !ui.busy, onClick = { leave = false; vm.discard() }) { Text("丢弃这份草稿") }
        TextButton(onClick = { leave = false }) { Text("继续编辑") }
    }
    if (classification) ClassificationSheet(ui, data, { classification = false }) { folder, tags, organized ->
        vm.edit(data.copy(folderId = folder, tags = tags, organized = organized)); classification = false
    }
    if (date) CalendarDialog(data.diaryDate, { date = false }) { vm.edit(data.copy(diaryDate = it)) }
    if (typePicker) GlassSheet("选择内容类型", { typePicker = false }) {
        Text(if (data.baseRevision > 0) "已有记录可以更换相同内容结构的显示类型。" else "切换内容结构前，请先保存或保留草稿，避免内容丢失。",
            style = MaterialTheme.typography.bodySmall)
        FlowChoices(ui.snapshot.types.filter { it.kind == type?.kind && it.subtype == type.subtype }.map { it.id to it.name }, data.typeId) {
            vm.edit(data.copy(typeId = it)); typePicker = false
        }
    }
    if (template) GlassSheet("保存为模板", { template = false }) {
        Text("保留正文结构和字段名称，字段值与附件不会写入模板。正文中若有个人信息，请先移除。")
        OutlinedTextField(templateName, { templateName = it }, label = { Text("模板名称 · 最多 32 字") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = templateName.isNotBlank() && !ui.busy, onClick = { vm.template(templateName); template = false }) { Text("保存模板") }
    }
    fieldEdit?.let { field -> FieldEditor(field, { fieldEdit = null }) { value ->
        val fields = data.fields.toMutableList(); val i = fields.indexOfFirst { it.id == value.id }
        if (i >= 0) fields[i] = value else fields += value
        vm.edit(data.copy(fields = fields)); fieldEdit = null
    } }
    if (existingFiles) GlassSheet("复用已有附件", { existingFiles = false }) {
        Text("引用同一份原件；移除一处引用不会删除其他记录使用的附件。", style = MaterialTheme.typography.bodySmall)
        val linked = ui.snapshot.attachmentsFor("draft", session.draftId).map { it.id }.toSet()
        ui.snapshot.attachments.filter { a -> a.id !in linked && ui.snapshot.links.any { it.attachmentId == a.id && it.entityType == "record" } }.forEach { a ->
            TextButton(enabled = !ui.busy, onClick = { vm.attachExisting(a.id); existingFiles = false }) { Text(a.displayName + " · " + formatBytes(a.size)) }
        }
    }
}
@Composable private fun FieldEditor(initial: CardField, dismiss: () -> Unit, apply: (CardField) -> Unit) {
    var field by remember(initial.id) { mutableStateOf(initial) }
    GlassSheet("信息卡字段", dismiss) {
        OutlinedTextField(field.name, { field = field.copy(name = it) }, label = { Text("字段名称 · 最多 40 字") }, modifier = Modifier.fillMaxWidth())
        FlowChoices(listOf("text" to "文字", "number" to "数字", "date" to "日期", "link" to "链接", "phone" to "电话"), field.valueType) { field = field.copy(valueType = it) }
        OutlinedTextField(field.value, { field = field.copy(value = it) }, label = { Text(if (field.valueType == "date") "内容 · YYYY-MM-DD" else "字段内容 · 最多 5000 字") }, modifier = Modifier.fillMaxWidth())
        SettingRow("私密字段", "详情中默认遮挡，默认不参与搜索与导出", field.isPrivate) { field = field.copy(isPrivate = it) }
        Button(enabled = field.name.isNotBlank() && field.value.isNotBlank(), onClick = { apply(field) }) { Text("确认字段") }
    }
}
