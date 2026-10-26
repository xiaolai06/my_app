package com.example.dailylife.feature.records

import com.example.dailylife.data.local.RecordBundle
import com.example.dailylife.data.repository.RecordsSnapshot

fun RecordBundle.markdown(snapshot: RecordsSnapshot): String = buildString {
    append("# ").append(snapshot.title(this@markdown)).append("\n\n")
    diary?.let { append("日期：").append(it.diaryDate).append("\n\n") }
    bookmark?.let { append(it.url).append("\n\n") }
    append(body).append("\n\n")
    fields.sortedBy { it.sortOrder }.filterNot { it.isPrivate }.forEach {
        append("- **").append(it.name).append("**：").append(it.value).append("\n")
    }
    val names = snapshot.tags.filter { t -> tagLinks.any { it.entityType == "record" && it.tagId == t.id } }.map { it.name }
    if (names.isNotEmpty()) append("\n标签：").append(names.joinToString("、")).append("\n")
    val attachments = snapshot.attachmentsFor("record", record.id)
    if (attachments.isNotEmpty()) {
        append("\n## 附件清单\n\n")
        attachments.forEach { append("- ").append(it.displayName).append("（").append(it.mime).append("）\n") }
        append("\n附件原件保存在应用内，本文本导出不包含原件。\n")
    }
}
