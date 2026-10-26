package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import com.example.dailylife.feature.records.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassSheet(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (LocalReduceMotion.current) {
        Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 28) {
                    SheetBody(title, content)
                }
            }
        }
        return
    }
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = LocalGlass.current.sheet,
        dragHandle = { Box(Modifier.padding(12.dp).size(36.dp, 4.dp).background(LocalGlass.current.muted.copy(alpha = .3f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp))) }) {
        SheetBody(title, content)
    }
}
@Composable private fun SheetBody(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
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
        options.forEach { (id, label) -> GlassChoice(label, id in selected, onClick = {
            select(if (id in selected) selected - id else selected + id)
        }) }
    }
}
@Composable fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit, label: String = "确认") {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { confirm(); dismiss() }) { Text(label) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }, containerColor = LocalGlass.current.sheet)
}

@Composable fun CalendarDialog(value: String, dismiss: () -> Unit, marked: Set<String> = emptySet(), choose: (String) -> Unit) {
    val initial = remember { runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now()) }
    var selected by remember { mutableStateOf(initial) }
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var dateText by remember { mutableStateOf(initial.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss) {
        GlassPanel(Modifier.fillMaxWidth().heightIn(max = 720.dp), radius = 28) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("选择日期", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹ 上月") }
                    Text(month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")), Modifier.weight(1f))
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("下月 ›") }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 348.dp || LocalDensity.current.fontScale > 1.3f) {
                        var week by remember(month) { mutableIntStateOf((if (YearMonth.from(selected) == month) selected.dayOfMonth - 1 else 0) / 7) }
                        val end = (week * 7 + 7).coerceAtMost(month.lengthOfMonth())
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(enabled = week > 0, onClick = { week-- }) { Text("上一组") }
                                TextButton(enabled = end < month.lengthOfMonth(), onClick = { week++ }) { Text("下一组") }
                            }
                            for (number in week * 7 + 1..end) {
                                val candidate = month.atDay(number)
                                GlassChoice(candidate.format(DateTimeFormatter.ofPattern("M 月 d 日  E", java.util.Locale.CHINA)) +
                                    if (candidate.toString() in marked) " · 有事项" else "", candidate == selected,
                                    Modifier.fillMaxWidth(), onClick = { selected = candidate; dateText = candidate.toString(); error = null })
                            }
                        }
                    } else Column {
                        Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(vertical = 8.dp)) } }
                        val offset = month.atDay(1).dayOfWeek.value - 1
                        for (week in 0 until (offset + month.lengthOfMonth() + 6) / 7) Row(Modifier.fillMaxWidth()) {
                            for (day in 0..6) {
                                val number = week * 7 + day - offset + 1
                                if (number in 1..month.lengthOfMonth()) {
                                    val candidate = month.atDay(number)
                                    Surface(onClick = { selected = candidate; dateText = candidate.toString(); error = null },
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = if (candidate == selected) LocalGlass.current.selected else androidx.compose.ui.graphics.Color.Transparent,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text(number.toString() + if (candidate.toString() in marked) "·" else "",
                                            color = if (candidate == selected) LocalGlass.current.accent else LocalGlass.current.ink) }
                                    }
                                } else Spacer(Modifier.weight(1f).height(48.dp))
                            }
                        }
                    }
                }
                Text("也可直接输入日期，方便选择较早的年份", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(dateText, { dateText = it; error = null }, label = { Text("YYYY-MM-DD") }, singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { month = YearMonth.now(); selected = LocalDate.now(); dateText = selected.toString() }) { Text("今天") }
                    TextButton(onClick = dismiss) { Text("取消") }
                    TextButton(onClick = {
                        val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                        if (date == null) error = "请输入有效日期，例如 2026-10-24"
                        else { choose(date.toString()); dismiss() }
                    }) { Text("确定") }
                }
            }
        }
    }
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
        OutlinedTextField(name, { name = it }, label = { Text("名称 · 最多 32 字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (category == "type" && editing == null) {
            FlowChoices(listOf("note:plain" to "Markdown", "diary:plain" to "日记", "file:plain" to "图片 / 文件",
                "bookmark:plain" to "链接收藏", "note:card" to "字段信息卡"), kind + ":" + subtype) {
                kind = it.substringBefore(':'); subtype = it.substringAfter(':')
            }
        }
        if (category == "tag") FlowChoices(listOf("0" to "雾蓝", "1" to "薄荷", "2" to "琥珀", "3" to "玫瑰"), color.toString()) { color = it.toInt() }
        Row {
            Button(enabled = name.isNotBlank() && !ui.busy, onClick = {
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
        Button(enabled = mergeTarget != null && !ui.busy, onClick = {
            vm.deleteOrganization("tag", id, mergeTarget); mergeId = null; mergeTarget = null
        }) { Text("确认合并") }
    } }
}
