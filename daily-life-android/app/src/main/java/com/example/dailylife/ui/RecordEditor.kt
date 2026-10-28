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
    var stylePicker by remember {mutableStateOf(false)}
    var preview by rememberSaveable(session.draftId) { mutableStateOf(false) }
    var classification by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) }
    var typePicker by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var fieldEdit by remember { mutableStateOf<CardField?>(null) }
    var existingFiles by remember { mutableStateOf(false) }
    var bodyValue by remember(session.draftId) { mutableStateOf(TextFieldValue(data.body)) }
    LaunchedEffect(data.body) { if (bodyValue.text != data.body) bodyValue = TextFieldValue(data.body, TextRange(data.body.length)) }
    BackHandler { if (!ui.busy) vm.leaveEditor() }
    fun insert(prefix: String, suffix: String = "") {
        if (ui.busy) return
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
                        TextButton(enabled = !ui.busy, onClick = { insert("## ") }) { Text("标题") }
                        TextButton(enabled = !ui.busy, onClick = { insert("**", "**") }) { Text("加粗") }
                        TextButton(enabled = !ui.busy, onClick = { insert("\n- ") }) { Text("列表") }
                        TextButton(enabled = !ui.busy, onClick = { insert("\n- [ ] ") }) { Text("待办") }
                        TextButton(enabled = !ui.busy, onClick = { insert("[", "](https://)") }) { Text("链接") }
                    }
                    TextButton(enabled = !ui.busy, onClick = {stylePicker=true}) {Text("手账样式")}
                    TextButton(enabled = !ui.busy, onClick = addPictures) { Text("图片") }
                    TextButton(enabled = !ui.busy, onClick = addFiles) { Text("文件") }
                    TextButton(enabled = !ui.busy, onClick = { existingFiles = true }) { Text("已有附件") }
                    TextButton(enabled = !ui.busy, onClick = { classification = true }) { Text("归类") }
                    TextButton(enabled = !ui.busy, onClick = { template = true }) { Text("存模板") }
                }
                ui.editorError?.let { Text(it, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Box(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                    GlassSaveActions(if(session.saved) "草稿已保存" else "正在保存草稿…",!ui.busy,
                        {vm.leaveEditor()},{vm.save()},"保存记录","saveRecord","稍后继续")
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding())) {
            GlassPageHeader(if(data.baseRevision==0L) "新记录" else "编辑记录",back=vm::leaveEditor,backTag="backRecordEditor",enabled=!ui.busy) {
                TextButton(enabled = !ui.busy, onClick = { typePicker = true }) { Text((type?.name ?: "选择类型")+" ▾") }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if(data.projectId!=null) {
                    Text("保存后加入原专题",color=p.muted)
                    TextButton(enabled=!ui.busy,onClick={vm.edit(data.copy(projectId=null))}) {Text("取消加入专题")}
                }
                if (type?.kind == "diary") GlassPanel(Modifier.fillMaxWidth(), radius = 18) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !ui.busy, onClick = { date = true }, modifier = Modifier.weight(1f)) { Text("日期  " + data.diaryDate + "  ▾") }
                        TextButton(enabled = !ui.busy, onClick = {
                            val moods = listOf("", "晴朗", "平静", "充实", "疲惫", "低落")
                            vm.edit(data.copy(mood = moods[(moods.indexOf(data.mood) + 1).mod(moods.size)]))
                        }) { Text(data.mood.ifBlank { "选心情" }) }
                    }
                }
                if (preview) {
                    JournalStory(data.title.ifBlank {"内容预览"},data.body,data.diaryDate.takeIf {type?.kind=="diary"},data.mood,runCatching {JournalStyle.parse(data.presentation)}.getOrDefault(JournalStyle()),ui.snapshot.attachmentsFor("draft",session.draftId).filter {it.mime.startsWith("image/")},vm.repository.files,openAttachment)
                    data.fields.forEach { f -> Text(f.name + "：" + if (f.isPrivate) "••••（私密）" else f.value) }
                } else {
                    GlassTextField(data.title, { vm.edit(data.copy(title = it)) },
                        label = { Text(if (type?.subtype == "card") "信息卡名称" else "标题（可留空）") },
                        modifier = Modifier.fillMaxWidth().testTag("recordTitle"), singleLine = true, readOnly = ui.busy,
                        isError = data.title.length > 120,
                        supportingText = { if (data.title.length > 120) Text("标题最多 120 字，请缩短；输入仍保留在草稿") },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    if (type?.kind == "bookmark") {
                        GlassTextField(data.url, { vm.edit(data.copy(url = it)) }, label = { Text("网页链接") },
                            placeholder = { Text("https://…") }, singleLine = true, readOnly = ui.busy, modifier = Modifier.fillMaxWidth())
                        SettingRow("允许另存相同链接", "默认提醒重复收藏，不会擅自删掉网址参数", data.allowDuplicateUrl) { vm.edit(data.copy(allowDuplicateUrl = it)) }
                    }
                    if (type?.subtype == "card") {
                        Text("字段信息卡", style = MaterialTheme.typography.titleMedium)
                        Text("私密字段默认遮挡，搜索和分享不包含字段值。请勿用来保存密码。",
                            style = MaterialTheme.typography.bodySmall, color = p.muted)
                        data.fields.forEachIndexed { index, f -> GlassPanel(Modifier.fillMaxWidth(), radius = 18) {
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable(enabled = !ui.busy) { fieldEdit = f }.padding(vertical = 12.dp)) {
                                    Text(f.name.ifBlank { "未命名字段" } + if (f.isPrivate) " · 私密" else "")
                                    Text(if (f.isPrivate) "••••" else f.value.take(80), color = p.muted)
                                }
                                if (index > 0) TextButton(enabled = !ui.busy, onClick = {
                                    val fields = data.fields.toMutableList(); fields[index] = fields[index - 1]; fields[index - 1] = f
                                    vm.edit(data.copy(fields = fields))
                                }) { Text("上移") }
                                TextButton(enabled = !ui.busy, onClick = { vm.edit(data.copy(fields = data.fields.filterNot { it.id == f.id })) }) { Text("移除") }
                            }
                        } }
                        GlassOutlinedButton(onClick = { fieldEdit = CardField(newId(), data.recordId, "", "") }, enabled = !ui.busy && data.fields.size < 50) { Text("添加字段") }
                    }
                    GlassTextField(bodyValue, {
                        if (!ui.busy) { bodyValue = it; vm.edit(data.copy(body = it.text)) }
                    }, label = { Text(if (type?.subtype == "card") "补充说明" else "正文") },
                        placeholder = { Text("写下此刻的想法…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp).testTag("recordBody"),
                        isError = bodyValue.text.length > 50000,
                        supportingText = { Text(bodyValue.text.length.toString() + " / 50,000 字" + if (bodyValue.text.length > 50000) " · 请拆分正文，草稿仍保留" else "") },
                        readOnly = ui.busy, minLines = 9, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                }
                val folder = ui.snapshot.folders.find { it.id == data.folderId }?.name ?: "未分类"
                val tags = ui.snapshot.tags.filter { it.id in data.tags }.joinToString(" ") { "#" + it.name }
                TextButton(enabled = !ui.busy, onClick = { classification = true }) { Text(folder + " · " + (if (data.organized) "已整理" else "待整理") + if (tags.isNotBlank()) " · " + tags else "") }
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
    if(stylePicker) JournalStylePicker(data.presentation,ui.busy,{vm.edit(data.copy(presentation=it))},{stylePicker=false})
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
        GlassTextField(templateName, { templateName = it }, label = { Text("模板名称 · 最多 32 字") }, modifier = Modifier.fillMaxWidth())
        GlassButton(enabled = templateName.isNotBlank() && !ui.busy, onClick = { vm.template(templateName); template = false }) { Text("保存模板") }
    }
    fieldEdit?.let { field -> FieldEditor(field, { fieldEdit = null }) { value ->
        val fields = data.fields.toMutableList(); val i = fields.indexOfFirst { it.id == value.id }
        if (i >= 0) fields[i] = value else fields += value
        vm.edit(data.copy(fields = fields)); fieldEdit = null
    } }
    if(existingFiles) {
        val linked=ui.snapshot.attachmentsFor("draft",session.draftId).map {it.id}.toSet()
        val options=ui.snapshot.attachments.filter {a->a.id !in linked && ui.snapshot.links.any {it.attachmentId==a.id && it.entityType=="record"}}
            .map {it.id to (it.displayName+" · "+formatBytes(it.size))}
        GlassSelectionSheet("复用已有附件",options,null,{existingFiles=false}) {id->vm.attachExisting(id) {existingFiles=false}}
    }
}
@Composable private fun FieldEditor(initial: CardField, dismiss: () -> Unit, apply: (CardField) -> Unit) {
    var field by remember(initial.id) { mutableStateOf(initial) }
    GlassSheet("信息卡字段", dismiss) {
        GlassTextField(field.name, { field = field.copy(name = it) }, label = { Text("字段名称 · 最多 40 字") }, modifier = Modifier.fillMaxWidth())
        FlowChoices(listOf("text" to "文字", "number" to "数字", "date" to "日期", "link" to "链接", "phone" to "电话"), field.valueType) { field = field.copy(valueType = it) }
        GlassTextField(field.value, { field = field.copy(value = it) }, label = { Text(if (field.valueType == "date") "内容 · YYYY-MM-DD" else "字段内容 · 最多 5000 字") }, modifier = Modifier.fillMaxWidth())
        SettingRow("私密字段", "详情中默认遮挡，不参与普通搜索", field.isPrivate) { field = field.copy(isPrivate = it) }
        GlassButton(enabled = field.name.isNotBlank() && field.value.isNotBlank(), onClick = { apply(field) }) { Text("确认字段") }
    }
}
