package com.playertwo.controlegithub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak

internal val LocalGitHubMarkdownNavigator =
    staticCompositionLocalOf<((GitHubMarkdownDestination) -> Unit)?> { null }

private sealed interface MarkdownParseState {
    data object Loading : MarkdownParseState
    data class Parsed(val document: org.commonmark.node.Document) : MarkdownParseState
    data object Fallback : MarkdownParseState
}

@Composable
internal fun GitHubMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val state by produceState<MarkdownParseState>(MarkdownParseState.Loading, markdown) {
        value = withContext(Dispatchers.Default) {
            parseGitHubMarkdown(markdown)?.let(MarkdownParseState::Parsed) ?: MarkdownParseState.Fallback
        }
    }
    val navigator = LocalGitHubMarkdownNavigator.current
    val context = LocalContext.current
    var pendingExternalLink by remember(markdown) { mutableStateOf<GitHubMarkdownLinkTarget.External?>(null) }
    var browserUnavailable by remember(markdown) { mutableStateOf(false) }

    fun openMarkdownTarget(target: GitHubMarkdownLinkTarget) {
        when (target) {
            is GitHubMarkdownLinkTarget.InApp -> navigator?.invoke(target.destination)
            is GitHubMarkdownLinkTarget.External -> pendingExternalLink = target
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        SelectionContainer {
            when (val parsed = state) {
                MarkdownParseState.Loading -> Text(markdown, modifier = Modifier.padding(contentPadding), lineHeight = 22.sp)
                MarkdownParseState.Fallback -> Text(markdown, modifier = Modifier.padding(contentPadding), lineHeight = 22.sp)
                is MarkdownParseState.Parsed -> Column(
                    modifier = Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MarkdownBlocks(parsed.document, ::openMarkdownTarget)
                }
            }
        }
    }

    pendingExternalLink?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingExternalLink = null },
            title = { Text("Abrir link externo?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("O endereço será aberto fora do app. Sua sessão do GitHub não será enviada.")
                    Text(target.host, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(target.url, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingExternalLink = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                    } catch (_: ActivityNotFoundException) {
                        browserUnavailable = true
                    } catch (_: SecurityException) {
                        browserUnavailable = true
                    }
                }) { Text("Abrir no navegador") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalLink = null }) { Text("Cancelar") }
            }
        )
    }
    if (browserUnavailable) {
        AlertDialog(
            onDismissRequest = { browserUnavailable = false },
            title = { Text("Não foi possível abrir o endereço") },
            confirmButton = { TextButton(onClick = { browserUnavailable = false }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun MarkdownBlocks(node: Node, onOpenLink: (GitHubMarkdownLinkTarget) -> Unit) {
    var child = node.firstChild
    while (child != null) {
        when (val block = child) {
            is Paragraph -> MarkdownInlineText(block, onOpenLink)
            is Heading -> MarkdownInlineText(
                block,
                onOpenLink,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineSmall
                    2 -> MaterialTheme.typography.titleLarge
                    3 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }.copy(fontWeight = FontWeight.SemiBold)
            )
            is BlockQuote -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                MarkdownBlocks(block, onOpenLink)
            }
            is BulletList -> MarkdownList(block, ordered = false, onOpenLink = onOpenLink)
            is OrderedList -> MarkdownList(block, ordered = true, start = block.markerStartNumber ?: 1, onOpenLink = onOpenLink)
            is FencedCodeBlock -> MarkdownCode(block.literal)
            is IndentedCodeBlock -> MarkdownCode(block.literal)
            is HtmlBlock -> Text(block.literal, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            is TableBlock -> MarkdownTable(block, onOpenLink)
            else -> if (block.firstChild != null) MarkdownBlocks(block, onOpenLink)
                else MarkdownInlineText(block, onOpenLink)
        }
        child = child.next
    }
}

@Composable
private fun MarkdownList(
    list: Node,
    ordered: Boolean,
    start: Int = 1,
    onOpenLink: (GitHubMarkdownLinkTarget) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        var itemNumber = start
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (ordered) "${itemNumber++}." else "•",
                        modifier = Modifier.width(28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        MarkdownBlocks(item, onOpenLink)
                    }
                }
            }
            item = item.next
        }
    }
}

@Composable
private fun MarkdownCode(literal: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = literal,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MarkdownTable(table: TableBlock, onOpenLink: (GitHubMarkdownLinkTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var section = table.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> MarkdownTableRows(section, isHeader = true, onOpenLink)
                is TableBody -> MarkdownTableRows(section, isHeader = false, onOpenLink)
            }
            section = section.next
        }
    }
}

@Composable
private fun MarkdownTableRows(
    section: Node,
    isHeader: Boolean,
    onOpenLink: (GitHubMarkdownLinkTarget) -> Unit
) {
    var row = section.firstChild
    while (row != null) {
        if (row is TableRow) Row(Modifier.fillMaxWidth()) {
            var cell = row.firstChild
            while (cell != null) {
                if (cell is TableCell) Surface(
                    modifier = Modifier.weight(1f),
                    color = if (isHeader) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                ) {
                    MarkdownInlineText(
                        cell,
                        onOpenLink,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
                cell = cell.next
            }
        }
        row = row.next
    }
}

@Composable
private fun MarkdownInlineText(
    node: Node,
    onOpenLink: (GitHubMarkdownLinkTarget) -> Unit,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    val annotated = remember(node, linkColor, codeBackground, codeColor) {
        buildAnnotatedString {
            var child = node.firstChild
            while (child != null) {
                appendInline(child, linkColor, codeBackground, codeColor) { currentOnOpenLink(it) }
                child = child.next
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, lineHeight = 22.sp)
}

private fun AnnotatedString.Builder.appendInline(
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    onOpenLink: (GitHubMarkdownLinkTarget) -> Unit
) {
    when (node) {
        is MarkdownTextNode -> append(node.literal)
        is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)) {
            append(node.literal)
        }
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlineChildren(node, linkColor, codeBackground, codeColor, onOpenLink) }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlineChildren(node, linkColor, codeBackground, codeColor, onOpenLink) }
        is Link -> {
            val target = classifyGitHubMarkdownLink(node.destination)
            if (target == null) appendInlineChildren(node, linkColor, codeBackground, codeColor, onOpenLink)
            else withLink(
                LinkAnnotation.Url(
                    node.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                ) { onOpenLink(target) }
            ) { appendInlineChildren(node, linkColor, codeBackground, codeColor, onOpenLink) }
        }
        is org.commonmark.node.Image -> {
            val label = inlinePlainText(node).ifBlank { "Imagem" }.let { "Imagem: $it" }
            val target = classifyGitHubMarkdownLink(node.destination)
            if (target == null) append(label)
            else withLink(
                LinkAnnotation.Url(
                    node.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                ) { onOpenLink(target) }
            ) { append(label) }
        }
        is HtmlInline -> append(node.literal)
        is SoftLineBreak, is HardLineBreak -> append('\n')
        else -> appendInlineChildren(node, linkColor, codeBackground, codeColor, onOpenLink)
    }
}

private fun AnnotatedString.Builder.appendInlineChildren(
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    onOpenLink: (GitHubMarkdownLinkTarget) -> Unit
) {
    var child = node.firstChild
    while (child != null) {
        appendInline(child, linkColor, codeBackground, codeColor, onOpenLink)
        child = child.next
    }
}

private fun inlinePlainText(node: Node): String = buildString {
    fun visit(current: Node) {
        when (current) {
            is MarkdownTextNode -> append(current.literal)
            is Code -> append(current.literal)
            is HtmlInline -> append(current.literal)
            is SoftLineBreak, is HardLineBreak -> append('\n')
            else -> {
                var child = current.firstChild
                while (child != null) {
                    visit(child)
                    child = child.next
                }
            }
        }
    }
    var child = node.firstChild
    while (child != null) {
        visit(child)
        child = child.next
    }
}
