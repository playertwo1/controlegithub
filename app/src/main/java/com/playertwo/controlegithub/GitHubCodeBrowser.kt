package com.playertwo.controlegithub

import java.io.IOException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_CODE_FILE_BYTES = 1_048_576

internal enum class CodeLanguage { KOTLIN, JAVA, JAVASCRIPT, PYTHON, JSON, XML, YAML, MARKDOWN, PLAIN }

internal enum class CodeEntryKind { DIR, FILE, OTHER }

internal enum class CodeTokenKind { KEYWORD, STRING, COMMENT }

internal data class CodeEntry(val name: String, val path: String, val kind: CodeEntryKind)

internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

internal sealed interface CodeFileBody {
    data class Text(val text: String, val language: CodeLanguage) : CodeFileBody
    data class Unavailable(val htmlUrl: String?) : CodeFileBody
}

internal sealed interface CodeListing {
    data class Directory(val entries: List<CodeEntry>) : CodeListing
    data class File(val body: CodeFileBody) : CodeListing
}

internal sealed interface CodeLoad {
    data class Found(val listing: CodeListing) : CodeLoad
    data object Unauthorized : CodeLoad
    data class Failed(val message: String) : CodeLoad
}

internal sealed interface RefLoad {
    data class Found(val names: List<String>) : RefLoad
    data object Unauthorized : RefLoad
    data object Failed : RefLoad
}

internal fun codeLanguage(name: String): CodeLanguage = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "kt", "kts" -> CodeLanguage.KOTLIN
    "java" -> CodeLanguage.JAVA
    "js", "jsx", "mjs", "cjs", "ts", "tsx" -> CodeLanguage.JAVASCRIPT
    "py" -> CodeLanguage.PYTHON
    "json" -> CodeLanguage.JSON
    "xml" -> CodeLanguage.XML
    "yml", "yaml" -> CodeLanguage.YAML
    "md", "markdown" -> CodeLanguage.MARKDOWN
    else -> CodeLanguage.PLAIN
}

internal fun githubContentsPath(owner: String, name: String, path: String, ref: String): String {
    val root = "/repos/${encodeSegment(owner)}/${encodeSegment(name)}/contents"
    val suffix = path.split('/').filter(String::isNotEmpty).joinToString("/") { encodeSegment(it) }
    val target = if (suffix.isEmpty()) root else "$root/$suffix"
    return "$target?ref=${encodeSegment(ref)}"
}

internal fun githubRefListPath(owner: String, name: String, kind: String): String {
    require(kind == "branches" || kind == "tags")
    return "/repos/${encodeSegment(owner)}/${encodeSegment(name)}/$kind?per_page=100"
}

internal fun parseRefNames(json: String): List<String> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            val name = array.optJSONObject(index)?.optString("name")?.trim().orEmpty()
            if (name.isNotEmpty() && none { it.equals(name, ignoreCase = true) }) add(name)
        }
    }
}

internal fun parseCodeListing(json: String): CodeListing {
    val trimmed = json.trim()
    return if (trimmed.startsWith("[")) parseDirectory(trimmed) else parseFile(JSONObject(trimmed))
}

internal fun loadCodeListing(
    client: GitHubHttpClient,
    accessToken: String,
    owner: String,
    name: String,
    path: String,
    ref: String
): CodeLoad = when (val result = client.get(githubContentsPath(owner, name, path, ref), accessToken).execute()) {
    is GitHubHttpResult.Success -> if (result.statusCode == 200) {
        runCatching { CodeLoad.Found(parseCodeListing(result.body)) }.getOrElse { CodeLoad.Failed("Resposta de código inválida.") }
    } else {
        CodeLoad.Failed("Não foi possível ler este caminho.")
    }
    is GitHubHttpResult.Failure -> if (result.error == GitHubHttpError.UNAUTHORIZED) {
        CodeLoad.Unauthorized
    } else {
        CodeLoad.Failed(result.error.userMessage)
    }
}

internal fun loadRefNames(
    client: GitHubHttpClient,
    accessToken: String,
    owner: String,
    name: String,
    kind: String
): RefLoad = when (val result = client.get(githubRefListPath(owner, name, kind), accessToken).execute()) {
    is GitHubHttpResult.Success -> if (result.statusCode == 200) {
        runCatching { RefLoad.Found(parseRefNames(result.body)) }.getOrElse { RefLoad.Failed }
    } else {
        RefLoad.Failed
    }
    is GitHubHttpResult.Failure -> if (result.error == GitHubHttpError.UNAUTHORIZED) RefLoad.Unauthorized else RefLoad.Failed
}

internal fun highlightCode(line: String, language: CodeLanguage): List<CodeToken> {
    if (language == CodeLanguage.PLAIN || language == CodeLanguage.MARKDOWN) return emptyList()
    val keywords = keywords(language)
    val comment = if (language == CodeLanguage.PYTHON || language == CodeLanguage.YAML) "#" else "//"
    val tokens = mutableListOf<CodeToken>()
    var index = 0
    while (index < line.length) {
        if (language == CodeLanguage.XML && line.startsWith("<!--", index)) {
            val end = line.indexOf("-->", index + 4).let { if (it < 0) line.length else it + 3 }
            tokens += CodeToken(index, end, CodeTokenKind.COMMENT)
            index = end
            continue
        }
        if (language != CodeLanguage.XML && line.startsWith(comment, index)) {
            tokens += CodeToken(index, line.length, CodeTokenKind.COMMENT)
            break
        }
        if (language == CodeLanguage.XML && line[index] == '<') {
            val end = line.indexOf('>', index + 1).let { if (it < 0) line.length else it + 1 }
            tokens += CodeToken(index, end, CodeTokenKind.KEYWORD)
            index = end
            continue
        }
        val quote = line[index]
        if (quote == '"' || quote == '\'' || (quote == '`' && language == CodeLanguage.JAVASCRIPT)) {
            val end = endOfString(line, index)
            tokens += CodeToken(index, end, CodeTokenKind.STRING)
            index = end
            continue
        }
        if (line[index].isLetter() || line[index] == '_') {
            val start = index
            while (index < line.length && (line[index].isLetterOrDigit() || line[index] == '_')) index++
            if (line.substring(start, index) in keywords) tokens += CodeToken(start, index, CodeTokenKind.KEYWORD)
            continue
        }
        index++
    }
    return tokens
}

internal fun parentCodePath(path: String): String =
    path.split('/').filter(String::isNotEmpty).dropLast(1).joinToString("/")

private fun parseDirectory(json: String): CodeListing.Directory {
    val array = JSONArray(json)
    val entries = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: throw IOException("Diretório inválido")
            val name = item.optString("name").trim()
            if (name.isEmpty()) throw IOException("Diretório inválido")
            val path = item.optString("path").trim().ifEmpty { name }
            val kind = when (item.optString("type")) {
                "dir" -> CodeEntryKind.DIR
                "file" -> CodeEntryKind.FILE
                else -> CodeEntryKind.OTHER
            }
            add(CodeEntry(name, path, kind))
        }
    }.sortedWith(compareBy<CodeEntry> { it.kind.ordinal }.thenBy { it.name.lowercase(Locale.ROOT) })
    return CodeListing.Directory(entries)
}

private fun parseFile(item: JSONObject): CodeListing.File {
    val name = item.optString("name")
    val htmlUrl = item.optString("html_url").trim().takeIf { it.startsWith("https://") && it.none(Char::isWhitespace) }
    val size = item.optLong("size", 0L)
    val type = item.optString("type")
    if (type == "submodule" || size > MAX_CODE_FILE_BYTES) {
        return CodeListing.File(CodeFileBody.Unavailable(htmlUrl))
    }
    if (item.optString("encoding") != "base64") {
        return CodeListing.File(CodeFileBody.Unavailable(htmlUrl))
    }
    val bytes = try {
        Base64.getMimeDecoder().decode(item.optString("content"))
    } catch (_: IllegalArgumentException) {
        return CodeListing.File(CodeFileBody.Unavailable(htmlUrl))
    }
    if (bytes.any { it == 0.toByte() }) return CodeListing.File(CodeFileBody.Unavailable(htmlUrl))
    val text = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        return CodeListing.File(CodeFileBody.Unavailable(htmlUrl))
    }
    return CodeListing.File(CodeFileBody.Text(text, codeLanguage(name)))
}

private fun keywords(language: CodeLanguage): Set<String> = when (language) {
    CodeLanguage.KOTLIN -> setOf("fun", "val", "var", "class", "object", "if", "else", "when", "return", "package", "import", "true", "false", "null")
    CodeLanguage.JAVA -> setOf("class", "public", "private", "static", "void", "if", "else", "return", "import", "package", "new", "true", "false", "null")
    CodeLanguage.JAVASCRIPT -> setOf("function", "const", "let", "var", "class", "if", "else", "return", "import", "export", "true", "false", "null")
    CodeLanguage.PYTHON -> setOf("def", "class", "if", "else", "elif", "return", "import", "from", "for", "while", "True", "False", "None")
    CodeLanguage.JSON, CodeLanguage.YAML -> setOf("true", "false", "null")
    else -> emptySet()
}

private fun endOfString(line: String, start: Int): Int {
    val quote = line[start]
    var index = start + 1
    while (index < line.length) {
        if (line[index] == '\\') {
            index += 2
            continue
        }
        if (line[index] == quote) return index + 1
        index++
    }
    return line.length
}

private fun encodeSegment(value: String) =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
