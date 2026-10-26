package com.example.dailylife.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.tables.*
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import com.example.dailylife.core.RecordRules

private val markdownParser = Parser.builder().extensions(listOf(TablesExtension.create(), TaskListItemsExtension.create())).build()
private fun children(node: Node): List<Node> = buildList {
    var current = node.firstChild
    while (current != null) { add(current); current = current.next }
}
private fun inline(node: Node, accent: Color): AnnotatedString = buildAnnotatedString {
    fun visit(n: Node) {
        when (n) {
            is Text -> append(n.literal)
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = accent.copy(alpha = .1f))) { append(n.literal) }
            is SoftLineBreak, is HardLineBreak -> append("\n")
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children(n).forEach { visit(it) } }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children(n).forEach { visit(it) } }
            is Link -> {
                // Only launch explicit web links. File, javascript and custom schemes never execute.
                val webUrl = if (n.destination.startsWith("https://", true) || n.destination.startsWith("http://", true))
                    runCatching { RecordRules.url(n.destination) }.getOrNull() else null
                if (webUrl != null)
                    withLink(LinkAnnotation.Url(webUrl, TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) { children(n).forEach { visit(it) } }
                else { children(n).forEach { visit(it) }; append("（链接未启用）") }
            }
            is Image -> { append("［图片："); children(n).forEach { visit(it) }; append("］") }
            is HtmlInline -> append(n.literal)
            else -> children(n).forEach { visit(it) }
        }
    }
    visit(node)
}

@Composable fun MarkdownBody(source: String, modifier: Modifier = Modifier) {
    val document = remember(source) { markdownParser.parse(source) }
    SelectionContainer(modifier) { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        children(document).forEach { MarkdownBlock(it) }
    } }
}

@Composable private fun MarkdownBlock(node: Node) {
    val p = LocalGlass.current
    when (node) {
        is Heading -> Text(inline(node, p.accent), style = when (node.level) {
            1 -> MaterialTheme.typography.headlineMedium
            2 -> MaterialTheme.typography.headlineSmall
            else -> MaterialTheme.typography.titleLarge
        }, fontWeight = FontWeight.SemiBold)
        is org.commonmark.node.Paragraph -> Text(inline(node, p.accent), style = MaterialTheme.typography.bodyLarge)
        is BlockQuote -> Row(Modifier.fillMaxWidth().background(p.accent.copy(alpha = .07f)).padding(12.dp)) {
            Box(Modifier.width(3.dp).height(24.dp).background(p.accent)); Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { children(node).forEach { MarkdownBlock(it) } }
        }
        is FencedCodeBlock -> CodeBlock(node.literal, node.info)
        is IndentedCodeBlock -> CodeBlock(node.literal, "")
        is BulletList, is OrderedList -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            children(node).forEachIndexed { i, item -> Row {
                Text(if (node is OrderedList) (node.markerStartNumber + i).toString() + ". " else "• ", color = p.accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    children(item).forEach { MarkdownBlock(it) }
                }
            } }
        }
        is ThematicBreak -> HorizontalDivider(color = p.muted.copy(alpha = .25f))
        is TableBlock -> Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            children(node).flatMap { if (it is TableHead || it is TableBody) children(it) else listOf(it) }.forEach { row ->
                Row(Modifier.border(0.5.dp, p.muted.copy(alpha = .2f))) {
                    children(row).forEach { cell ->
                        Text(inline(cell, p.accent), Modifier.width(150.dp).padding(10.dp),
                            fontWeight = if ((cell as? TableCell)?.isHeader == true) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        is HtmlBlock -> Text(node.literal, fontFamily = FontFamily.Monospace, color = p.muted)
        else -> {
            // Task marker is text-only in reading mode; changing it requires editing the source.
            if (node.javaClass.simpleName == "TaskListItemMarker") {
                val checked = runCatching { node.javaClass.getMethod("isChecked").invoke(node) as Boolean }.getOrDefault(false)
                Text(if (checked) "☑ 已完成" else "☐ 待完成", color = p.muted)
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { children(node).forEach { MarkdownBlock(it) } }
        }
    }
}
@Composable private fun CodeBlock(text: String, language: String) {
    GlassPanel(radius = 16) {
        if (language.isNotBlank()) Text(language, Modifier.padding(start = 12.dp, top = 8.dp), color = LocalGlass.current.muted, fontSize = 12.sp)
        Text(text.trimEnd(), Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
            fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp)
    }
}
