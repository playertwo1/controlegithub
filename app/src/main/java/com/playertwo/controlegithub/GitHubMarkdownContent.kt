package com.playertwo.controlegithub

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

internal val LocalGitHubMarkdownImageLoader =
    staticCompositionLocalOf { GitHubMarkdownImageLoader() }

private val LocalReadmeImages = staticCompositionLocalOf { emptyMap<Node, List<Pair<String, String>>>() }

private val LocalEnlargeReadmeImage =
    staticCompositionLocalOf<((String, String) -> Unit)?> { null }

private fun Node.markdownDescendants(): Sequence<Node> = sequence {
    var child = firstChild
    while (child != null) {
        val node = child
        yield(node)
        yieldAll(node.markdownDescendants())
        child = node.next
    }
}

private fun Node.imageSources(): List<Pair<String, String>> = when (this) {
    is org.commonmark.node.Image -> listOf(destination to inlinePlainText(this).ifBlank { "Imagem" })
    is HtmlBlock -> githubHtmlImageSources(literal)
    is HtmlInline -> githubHtmlImageSources(literal)
    else -> emptyList()
}

internal fun collectReadmeImages(
    document: Node,
    repository: GitHubMarkdownRepositoryImages?
): Map<Node, List<Pair<String, String>>> {
    if (repository == null) return emptyMap()
    val images = mutableMapOf<Node, MutableList<Pair<String, String>>>()
    for (node in document.markdownDescendants()) {
        for ((source, label) in node.imageSources()) {
            val url = automaticReadmeImageUrl(source, repository) ?: continue
            images.getOrPut(node) { mutableListOf() }.add(url to label)
        }
    }
    return images
}

private sealed interface MarkdownParseState {
    data object Loading : MarkdownParseState
    data class Parsed(val document: org.commonmark.node.Document) : MarkdownParseState
    data object Fallback : MarkdownParseState
}

private sealed interface MarkdownImageUiState {
    data object Loading : MarkdownImageUiState
    data class Ready(val bitmap: ImageBitmap) : MarkdownImageUiState
    data class Error(val message: String) : MarkdownImageUiState
}

@Composable
internal fun GitHubMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    repositoryImages: GitHubMarkdownRepositoryImages? = null
) {
    val state by produceState<MarkdownParseState>(MarkdownParseState.Loading, markdown) {
        value = withContext(Dispatchers.Default) {
            parseGitHubMarkdown(markdown)?.let(MarkdownParseState::Parsed) ?: MarkdownParseState.Fallback
        }
    }
    val navigator = LocalGitHubMarkdownNavigator.current
    val imageLoader = LocalGitHubMarkdownImageLoader.current
    val context = LocalContext.current
    var pendingExternalLink by remember(markdown) { mutableStateOf<GitHubMarkdownLinkTarget.External?>(null) }
    var activeImagePreview by remember(markdown) {
        mutableStateOf<Pair<GitHubMarkdownLinkTarget.External, String>?>(null)
    }
    var browserUnavailable by remember(markdown) { mutableStateOf(false) }

    fun openMarkdownTarget(target: GitHubMarkdownLinkTarget, imageLabel: String? = null) {
        when (target) {
            is GitHubMarkdownLinkTarget.InApp -> navigator?.invoke(target.destination)
            is GitHubMarkdownLinkTarget.External -> {
                if (imageLabel != null) {
                    activeImagePreview = target to imageLabel
                } else {
                    pendingExternalLink = target
                }
            }
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
                    val readmeImages = remember(parsed.document, repositoryImages) {
                        collectReadmeImages(parsed.document, repositoryImages)
                    }
                    CompositionLocalProvider(
                        LocalReadmeImages provides readmeImages,
                        LocalEnlargeReadmeImage provides { url, label ->
                            val target = classifyGitHubMarkdownLink(url)
                            if (target is GitHubMarkdownLinkTarget.External) {
                                activeImagePreview = target to label
                            }
                        }
                    ) {
                        MarkdownBlocks(parsed.document, ::openMarkdownTarget) { source ->
                            resolveGitHubMarkdownImage(source, repositoryImages)
                        }
                    }
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

    activeImagePreview?.let { (target, imageLabel) ->
        MarkdownImagePreviewDialog(
            url = target.url,
            host = target.host,
            imageLabel = imageLabel,
            imageLoader = imageLoader,
            onDismiss = { activeImagePreview = null }
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
private fun MarkdownImagePreviewDialog(
    url: String,
    host: String,
    imageLabel: String,
    imageLoader: GitHubMarkdownImageLoader,
    onDismiss: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("markdown_image_preview_dialog"),
        title = { Text("Imagem: $imageLabel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(host, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                MarkdownImage(url, imageLabel, imageLoader, showStatusLabel = false)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun MarkdownImage(
    url: String,
    imageLabel: String,
    imageLoader: GitHubMarkdownImageLoader,
    maxDimension: Int = 2048,
    showStatusLabel: Boolean = true,
    onEnlarge: (() -> Unit)? = null
) {
    var attempt by remember(url) { mutableIntStateOf(0) }
    val uiState by produceState<MarkdownImageUiState>(MarkdownImageUiState.Loading, url, imageLoader, attempt) {
        value = MarkdownImageUiState.Loading
        value = withContext(Dispatchers.IO) {
            when (val result = imageLoader.load(url)) {
                is MarkdownImageLoadResult.Failed -> MarkdownImageUiState.Error(result.message)
                is MarkdownImageLoadResult.Loaded -> {
                    val unsupported = unsupportedMarkdownImageMessage(result.bytes, result.contentType)
                    when {
                        unsupported != null -> MarkdownImageUiState.Error(unsupported)
                        else -> {
                            val decoded = decodeMarkdownImageBitmap(result.bytes, maxDimension)
                            if (decoded != null) MarkdownImageUiState.Ready(decoded.asImageBitmap())
                            else MarkdownImageUiState.Error("Não foi possível decodificar a imagem recebida.")
                        }
                    }
                }
            }
        }
    }

    when (val current = uiState) {
        MarkdownImageUiState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showStatusLabel) Text(imageLabel)
            Text("Carregando imagem...")
        }
        is MarkdownImageUiState.Ready -> {
            val ratio = current.bitmap.width.coerceAtLeast(1).toFloat() / current.bitmap.height.coerceAtLeast(1)
            Image(
                bitmap = current.bitmap,
                contentDescription = imageLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .testTag("markdown_loaded_image")
                    .then(
                        if (onEnlarge != null) {
                            Modifier.clickable(onClickLabel = "Ampliar imagem") { onEnlarge() }
                        } else {
                            Modifier
                        }
                    )
            )
        }
        is MarkdownImageUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showStatusLabel) Text(imageLabel)
            Text(current.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { attempt++ }) { Text("Tentar novamente") }
        }
    }
}

@Composable
private fun ReadmeImages(node: Node) {
    val images = LocalReadmeImages.current
    if (images.isEmpty()) return
    for (element in sequenceOf(node) + node.markdownDescendants()) {
        for ((url, label) in images[element].orEmpty()) {
            ReadmeImageSlot(url, label)
        }
    }
}

@Composable
private fun ReadmeImageSlot(url: String, label: String) {
    var visible by remember(url) { mutableStateOf(false) }
    val enlarge = LocalEnlargeReadmeImage.current
    val loader = LocalGitHubMarkdownImageLoader.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (visible || !coordinates.isAttached) return@onGloballyPositioned
                if (coordinates.size.width == 0 || coordinates.size.height == 0) return@onGloballyPositioned
                val bounds = coordinates.boundsInRoot()
                val root = coordinates.findRootCoordinates().size
                val onScreen = bounds.bottom > 0f && bounds.top < root.height &&
                    bounds.right > 0f && bounds.left < root.width
                if (onScreen) visible = true
            }
            .then(if (visible) Modifier else Modifier.semantics { contentDescription = label })
    ) {
        if (!visible) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MarkdownImage(
                url = url,
                imageLabel = label,
                imageLoader = loader,
                maxDimension = 1024,
                onEnlarge = enlarge?.let { callback -> { callback(url, label) } }
            )
        }
    }
}

@Composable
private fun MarkdownBlocks(
    node: Node,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    resolveImage: (String) -> String? = { null }
) {
    var child = node.firstChild
    while (child != null) {
        when (val block = child) {
            is Paragraph -> MarkdownInlineText(block, onOpenLink, resolveImage = resolveImage)
            is Heading -> MarkdownInlineText(
                block,
                onOpenLink,
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineSmall
                    2 -> MaterialTheme.typography.titleLarge
                    3 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }.copy(fontWeight = FontWeight.SemiBold),
                resolveImage = resolveImage
            )
            is BlockQuote -> Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                MarkdownBlocks(block, onOpenLink, resolveImage)
            }
            is BulletList -> MarkdownList(block, ordered = false, onOpenLink = onOpenLink, resolveImage = resolveImage)
            is OrderedList -> MarkdownList(block, ordered = true, start = block.markerStartNumber ?: 1, onOpenLink = onOpenLink, resolveImage = resolveImage)
            is FencedCodeBlock -> MarkdownCode(block.literal)
            is IndentedCodeBlock -> MarkdownCode(block.literal)
            is HtmlBlock -> MarkdownHtmlBlock(block, onOpenLink, resolveImage)
            is ThematicBreak -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            is TableBlock -> MarkdownTable(block, onOpenLink, resolveImage)
            else -> if (block.firstChild != null) MarkdownBlocks(block, onOpenLink, resolveImage)
                else MarkdownInlineText(block, onOpenLink, resolveImage = resolveImage)
        }
        child = child.next
    }
}

@Composable
private fun MarkdownHtmlBlock(
    block: HtmlBlock,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    resolveImage: (String) -> String?
) {
    val html = block.literal
    val text = remember(html) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('\uFFFC', ' ')
            .trim()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (text.isNotBlank()) Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReadmeImages(block)
        val automaticImages = LocalReadmeImages.current[block].orEmpty()
        githubHtmlImageSources(html).forEach { (source, label) ->
            if ((resolveImage(source) to label) in automaticImages) return@forEach
            val target = resolveImage(source)?.let(::classifyGitHubMarkdownLink)
                ?: classifyGitHubMarkdownLink(source)
            if (target != null) TextButton(onClick = { onOpenLink(target, label) }) {
                Text("Carregar imagem: $label")
            }
        }
    }
}

@Composable
private fun MarkdownList(
    list: Node,
    ordered: Boolean,
    start: Int = 1,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    resolveImage: (String) -> String? = { null }
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
                        MarkdownBlocks(item, onOpenLink, resolveImage)
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
private fun MarkdownTable(
    table: TableBlock,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    resolveImage: (String) -> String? = { null }
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var section = table.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> MarkdownTableRows(section, isHeader = true, onOpenLink, resolveImage)
                is TableBody -> MarkdownTableRows(section, isHeader = false, onOpenLink, resolveImage)
            }
            section = section.next
        }
    }
}

@Composable
private fun MarkdownTableRows(
    section: Node,
    isHeader: Boolean,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    resolveImage: (String) -> String? = { null }
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        resolveImage = resolveImage
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
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    resolveImage: (String) -> String? = { null }
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    val annotated = remember(node, linkColor, codeBackground, codeColor, resolveImage) {
        buildAnnotatedString {
            var child = node.firstChild
            while (child != null) {
                appendInline(child, linkColor, codeBackground, codeColor, resolveImage) { target, imageLabel ->
                    currentOnOpenLink(target, imageLabel)
                }
                child = child.next
            }
        }
    }
    val automaticImages = LocalReadmeImages.current
    if (automaticImages.isEmpty() || node.markdownDescendants().none(automaticImages::containsKey)) {
        Text(text = annotated, modifier = modifier, style = style, lineHeight = 22.sp)
    } else {
        Column(modifier = modifier) {
            if (annotated.isNotBlank()) Text(text = annotated, style = style, lineHeight = 22.sp)
            ReadmeImages(node)
        }
    }
}

private fun AnnotatedString.Builder.appendInline(
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    resolveImage: (String) -> String?,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit
) {
    when (node) {
        is MarkdownTextNode -> append(node.literal)
        is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)) {
            append(node.literal)
        }
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlineChildren(node, linkColor, codeBackground, codeColor, resolveImage, onOpenLink) }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlineChildren(node, linkColor, codeBackground, codeColor, resolveImage, onOpenLink) }
        is Link -> {
            val target = classifyGitHubMarkdownLink(node.destination)
            if (target == null) appendInlineChildren(node, linkColor, codeBackground, codeColor, resolveImage, onOpenLink)
            else withLink(
                LinkAnnotation.Url(
                    node.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                ) { onOpenLink(target, null) }
            ) { appendInlineChildren(node, linkColor, codeBackground, codeColor, resolveImage, onOpenLink) }
        }
        is org.commonmark.node.Image -> {
            val altText = inlinePlainText(node).ifBlank { "Imagem" }
            val label = altText
            val target = classifyGitHubMarkdownLink(resolveImage(node.destination) ?: node.destination)
            if (target == null) append(label)
            else withLink(
                LinkAnnotation.Url(
                    node.destination,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                ) { onOpenLink(target, altText) }
            ) { append(label) }
        }
        is HtmlInline -> append(Html.fromHtml(node.literal, Html.FROM_HTML_MODE_COMPACT).toString().replace('\uFFFC', ' '))
        is SoftLineBreak, is HardLineBreak -> append('\n')
        else -> appendInlineChildren(node, linkColor, codeBackground, codeColor, resolveImage, onOpenLink)
    }
}

private fun AnnotatedString.Builder.appendInlineChildren(
    node: Node,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    resolveImage: (String) -> String?,
    onOpenLink: (GitHubMarkdownLinkTarget, String?) -> Unit
) {
    var child = node.firstChild
    while (child != null) {
        appendInline(child, linkColor, codeBackground, codeColor, resolveImage, onOpenLink)
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
