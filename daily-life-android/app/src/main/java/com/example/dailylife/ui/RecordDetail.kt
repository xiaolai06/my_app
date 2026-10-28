package com.example.dailylife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.example.dailylife.data.local.*
import com.example.dailylife.feature.records.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun RecordDetail(ui: RecordsUi, vm: RecordsViewModel, openAttachment: (Attachment) -> Unit,
    exportRecord: (RecordBundle) -> Unit, shareRecord: (RecordBundle) -> Unit) {
    val b = ui.snapshot.records.find { it.record.id == ui.detailId }
    var actions by remember { mutableStateOf(false) }
    var trash by remember { mutableStateOf(false) }
    var destroy by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    val p = LocalGlass.current
    BackHandler { vm.detail(null) }
    if (b == null) {
        Column(Modifier.statusBarsPadding().padding(24.dp)) {
            Text("正在读取记录…"); TextButton(onClick = { vm.detail(null) }) { Text("返回记录") }
        }; return
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { GlassPanel(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), radius = 24) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { actions = true }) { Text("更多操作") }
                if (b.record.deletedAt == null) GlassButton(enabled = !ui.busy, onClick = { vm.begin(b.record.typeId, b.record.id) }) { Text("编辑记录") }
                else GlassButton(enabled = !ui.busy, onClick = { vm.mutate(setOf(b.record.id), "restore") }) { Text("恢复记录") }
            }
        } }) { padding ->
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding())) {
            GlassPageHeader(ui.snapshot.types.find {it.id==b.record.typeId}?.name.orEmpty(),back={vm.detail(null)})
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 23.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                JournalStory(ui.snapshot.title(b),b.body,b.diary?.diaryDate,b.diary?.mood,runCatching {JournalStyle.parse(b.record.presentation)}.getOrDefault(JournalStyle()),ui.snapshot.attachmentsFor("record",b.record.id).filter {it.mime.startsWith("image/")},vm.repository.files,openAttachment)
                Text(Instant.ofEpochMilli(b.record.updatedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                    if (b.record.deletedAt != null) " · 回收站" else if (b.record.archivedAt != null) " · 已归档" else "", style = MaterialTheme.typography.bodySmall, color = p.muted)
                b.diary?.let { Text(it.diaryDate + if (it.mood.isNotBlank()) " · " + it.mood else "", color = p.accent) }
                b.bookmark?.let {
                    MarkdownBody("[" + it.url.replace("]", "%5D") + "](" + it.url + ")")
                    Text(if (it.readAt == null) "未读收藏" else "已读收藏", color = p.muted)
                }

                b.fields.sortedBy { it.sortOrder }.forEach { f -> GlassPanel(Modifier.fillMaxWidth(), radius = 18) {
                    Column(Modifier.padding(16.dp)) {
                        Text(f.name, style = MaterialTheme.typography.labelMedium, color = p.muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectionContainer(Modifier.weight(1f)) { Text(if (f.isPrivate && f.id !in revealed) "••••••" else f.value, Modifier.padding(top = 6.dp)) }
                            if (f.isPrivate) TextButton(onClick = { revealed = if (f.id in revealed) revealed - f.id else revealed + f.id }) { Text(if (f.id in revealed) "隐藏" else "显示") }
                        }
                    }
                } }
                val tags = ui.snapshot.tags.filter { t -> b.tagLinks.any { it.entityType == "record" && it.tagId == t.id } }
                Text((ui.snapshot.folders.find { it.id == b.record.folderId }?.name ?: "未分类") + " · " +
                    (if (b.record.organizationStatus == "inbox") "待整理" else "已整理") +
                    if (tags.isNotEmpty()) "\n" + tags.joinToString("  ") { "#" + it.name } else "", color = p.muted)
                val attachments = ui.snapshot.attachmentsFor("record", b.record.id).filterNot {it.mime.startsWith("image/")}
                if (attachments.isNotEmpty()) {
                    SectionLabel("附件原件")
                    AttachmentList(attachments, vm.repository.files, openAttachment)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if(actions) GlassSheet("记录操作",{actions=false}) {
        if(b.record.deletedAt==null) {
            listOf("pin" to if(b.record.pinOrder==null) "置顶" else "取消置顶",
                "archive" to if(b.record.archivedAt==null) "归档" else "取消归档",
                "organize" to if(b.record.organizationStatus=="inbox") "标为已整理" else "放回待整理").forEach {(key,label)->
                GlassActionRow(label,enabled=!ui.busy) {vm.mutate(setOf(b.record.id),key);actions=false}
            }
            if(b.bookmark!=null) GlassActionRow("切换已读 / 未读",enabled=!ui.busy) {vm.mutate(setOf(b.record.id),"read");actions=false}
            GlassActionRow(if(b.diary!=null) "复制为普通随记" else "创建副本",enabled=!ui.busy) {vm.duplicate(b.record.id);actions=false}
            GlassActionRow("分享文字（排除私密字段）",enabled=!ui.busy) {shareRecord(b);actions=false}
            GlassActionRow("移入回收站",enabled=!ui.busy,destructive=true) {trash=true;actions=false}
        } else {
            GlassActionRow("恢复到原位置",enabled=!ui.busy) {vm.mutate(setOf(b.record.id),"restore");actions=false}
            GlassActionRow("永久删除",enabled=!ui.busy,destructive=true) {destroy=true;actions=false}
        }
    }
    if (trash) ConfirmDialog("移入回收站？", "记录和附件原件仍会保留，可以稍后恢复。", { trash = false }, { vm.mutate(setOf(b.record.id), "trash") }, "移入回收站")
    if (destroy) ConfirmDialog("永久删除记录？", "此操作无法撤销。仍被其他记录或草稿引用的附件会保留。", { destroy = false }, { vm.destroy(setOf(b.record.id)) }, "永久删除")
}
