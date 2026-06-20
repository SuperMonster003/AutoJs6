package org.autojs.autojs.engine

import java.util.Locale

object NodeTypeScriptStripper {

    data class Result(
        val source: String,
        val stripped: Boolean,
        val diagnostics: Map<String, String> = emptyMap(),
    )

    class UnsupportedTypeScriptException(
        val errorCode: String,
        val sourceName: String,
        val syntaxKind: String,
        val line: Int,
        val column: Int,
        message: String,
    ) : IllegalArgumentException(message)

    fun stripIfTypeScript(sourceName: String, source: String): Result {
        val extension = sourceName.extension().lowercase(Locale.ROOT)
        if (extension == "tsx") {
            throw unsupportedExtension(sourceName)
        }
        if (extension !in TYPESCRIPT_EXTENSIONS) {
            return Result(source = source, stripped = false)
        }

        detectUnsupportedSyntax(sourceName, source)
        var output = source
        output = removeTypeOnlyStatements(output)
        output = removeInterfaceDeclarations(output)
        output = removeTypeAliasDeclarations(output)
        output = stripFunctionTypeParameters(output)
        output = stripTypeAnnotations(output)
        output = stripTypeAssertions(output)

        return Result(
            source = output,
            stripped = output != source,
            diagnostics = mapOf(
                "embedded_script.typescript.stripped" to (output != source).toString(),
                "embedded_script.typescript.source" to sourceName,
                "embedded_script.typescript.extension" to extension,
            ),
        )
    }

    fun isTypeScriptSourceName(sourceName: String): Boolean {
        return sourceName.extension().lowercase(Locale.ROOT) in TYPESCRIPT_EXTENSIONS
    }

    fun isUnsupportedTypeScriptSourceName(sourceName: String): Boolean {
        return sourceName.extension().equals("tsx", ignoreCase = true)
    }

    private fun detectUnsupportedSyntax(sourceName: String, source: String) {
        val masked = maskNonCode(source)
        unsupportedPatterns.forEach { pattern ->
            val match = pattern.regex.find(masked) ?: return@forEach
            throw unsupportedSyntax(sourceName, source, pattern.kind, pattern.description, match.range.first)
        }
    }

    private fun unsupportedExtension(sourceName: String): UnsupportedTypeScriptException {
        return UnsupportedTypeScriptException(
            errorCode = NodeRuntimeErrorCodes.TYPESCRIPT_UNSUPPORTED_EXTENSION,
            sourceName = sourceName,
            syntaxKind = "tsx",
            line = 1,
            column = 1,
            message = "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION: TypeScript TSX entry is not supported by AutoJs6 lightweight type stripping: $sourceName. Compile TSX to JavaScript before packaging or running it.",
        )
    }

    private fun unsupportedSyntax(
        sourceName: String,
        source: String,
        kind: String,
        description: String,
        index: Int,
    ): UnsupportedTypeScriptException {
        val position = source.lineColumnAt(index)
        return UnsupportedTypeScriptException(
            errorCode = NodeRuntimeErrorCodes.UNSUPPORTED_TYPESCRIPT_SYNTAX,
            sourceName = sourceName,
            syntaxKind = kind,
            line = position.first,
            column = position.second,
            message = "ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX: $description in $sourceName:${position.first}:${position.second}. AutoJs6 lightweight TypeScript support only strips erasable type syntax; compile this project before running it.",
        )
    }

    private fun removeTypeOnlyStatements(source: String): String {
        var output = source
        output = IMPORT_TYPE_PATTERN.replace(output) { it.value.newlinePadding() }
        output = EXPORT_TYPE_PATTERN.replace(output) { it.value.newlinePadding() }
        output = EXPORT_INTERFACE_INLINE_PATTERN.replace(output) { it.value.newlinePadding() }
        return output
    }

    private fun removeInterfaceDeclarations(source: String): String {
        return removeBraceDeclarations(source, INTERFACE_DECLARATION_PATTERN)
    }

    private fun removeTypeAliasDeclarations(source: String): String {
        return TYPE_ALIAS_PATTERN.replace(source) { it.value.newlinePadding() }
    }

    private fun removeBraceDeclarations(source: String, pattern: Regex): String {
        val result = StringBuilder(source.length)
        var cursor = 0
        while (cursor < source.length) {
            val match = pattern.find(source, cursor) ?: break
            val braceStart = source.indexOf('{', match.range.last + 1)
            if (braceStart < 0) {
                break
            }
            val end = matchingBraceEnd(source, braceStart) ?: break
            result.append(source, cursor, match.range.first)
            result.append(source.substring(match.range.first, end + 1).newlinePadding())
            cursor = end + 1
        }
        result.append(source, cursor, source.length)
        return result.toString()
    }

    private fun stripFunctionTypeParameters(source: String): String {
        return FUNCTION_TYPE_PARAMETERS_PATTERN.replace(source) {
            "function ${it.groupValues[1]}("
        }
    }

    private fun stripTypeAnnotations(source: String): String {
        var output = source
        output = VARIABLE_TYPE_PATTERN.replace(output) {
            "${it.groupValues[1]} ${it.groupValues[2]}"
        }
        output = PARAMETER_TYPE_PATTERN.replace(output) {
            it.groupValues[1]
        }
        output = RETURN_TYPE_PATTERN.replace(output) {
            it.groupValues[1]
        }
        output = PROPERTY_TYPE_PATTERN.replace(output) {
            it.groupValues[1] + it.groupValues[2]
        }
        return output
    }

    private fun stripTypeAssertions(source: String): String {
        var output = AS_ASSERTION_PATTERN.replace(source) {
            it.groupValues[1]
        }
        output = SATISFIES_ASSERTION_PATTERN.replace(output) {
            it.groupValues[1]
        }
        return output
    }

    private fun matchingBraceEnd(source: String, braceStart: Int): Int? {
        var depth = 0
        var index = braceStart
        var state = ScanState.CODE
        var escaped = false
        while (index < source.length) {
            val ch = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                ScanState.LINE_COMMENT -> {
                    if (ch == '\n' || ch == '\r') {
                        state = ScanState.CODE
                    }
                }
                ScanState.BLOCK_COMMENT -> {
                    if (ch == '*' && next == '/') {
                        index += 1
                        state = ScanState.CODE
                    }
                }
                ScanState.SINGLE_QUOTE, ScanState.DOUBLE_QUOTE, ScanState.TEMPLATE -> {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (
                        (state == ScanState.SINGLE_QUOTE && ch == '\'') ||
                        (state == ScanState.DOUBLE_QUOTE && ch == '"') ||
                        (state == ScanState.TEMPLATE && ch == '`')
                    ) {
                        state = ScanState.CODE
                    }
                }
                ScanState.CODE -> {
                    if (ch == '/' && next == '/') {
                        index += 1
                        state = ScanState.LINE_COMMENT
                    } else if (ch == '/' && next == '*') {
                        index += 1
                        state = ScanState.BLOCK_COMMENT
                    } else if (ch == '\'') {
                        state = ScanState.SINGLE_QUOTE
                    } else if (ch == '"') {
                        state = ScanState.DOUBLE_QUOTE
                    } else if (ch == '`') {
                        state = ScanState.TEMPLATE
                    } else if (ch == '{') {
                        depth += 1
                    } else if (ch == '}') {
                        depth -= 1
                        if (depth == 0) {
                            return index
                        }
                    }
                }
            }
            index += 1
        }
        return null
    }

    private fun maskNonCode(source: String): String {
        val output = StringBuilder(source.length)
        var index = 0
        var state = ScanState.CODE
        var escaped = false
        while (index < source.length) {
            val ch = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                ScanState.LINE_COMMENT -> {
                    if (ch == '\n' || ch == '\r') {
                        output.append(ch)
                        state = ScanState.CODE
                    } else {
                        output.append(' ')
                    }
                }
                ScanState.BLOCK_COMMENT -> {
                    if (ch == '*' && next == '/') {
                        output.append("  ")
                        index += 1
                        state = ScanState.CODE
                    } else {
                        output.append(if (ch == '\n' || ch == '\r') ch else ' ')
                    }
                }
                ScanState.SINGLE_QUOTE, ScanState.DOUBLE_QUOTE, ScanState.TEMPLATE -> {
                    output.append(if (ch == '\n' || ch == '\r') ch else ' ')
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (
                        (state == ScanState.SINGLE_QUOTE && ch == '\'') ||
                        (state == ScanState.DOUBLE_QUOTE && ch == '"') ||
                        (state == ScanState.TEMPLATE && ch == '`')
                    ) {
                        state = ScanState.CODE
                    }
                }
                ScanState.CODE -> {
                    if (ch == '/' && next == '/') {
                        output.append("  ")
                        index += 1
                        state = ScanState.LINE_COMMENT
                    } else if (ch == '/' && next == '*') {
                        output.append("  ")
                        index += 1
                        state = ScanState.BLOCK_COMMENT
                    } else if (ch == '\'') {
                        output.append(' ')
                        state = ScanState.SINGLE_QUOTE
                    } else if (ch == '"') {
                        output.append(' ')
                        state = ScanState.DOUBLE_QUOTE
                    } else if (ch == '`') {
                        output.append(' ')
                        state = ScanState.TEMPLATE
                    } else {
                        output.append(ch)
                    }
                }
            }
            index += 1
        }
        return output.toString()
    }

    private fun String.extension(): String {
        val slash = maxOf(lastIndexOf('/'), lastIndexOf('\\'))
        val dot = lastIndexOf('.')
        return if (dot > slash && dot < length - 1) substring(dot + 1) else ""
    }

    private fun String.lineColumnAt(index: Int): Pair<Int, Int> {
        var line = 1
        var column = 1
        val end = index.coerceIn(0, length)
        for (cursor in 0 until end) {
            if (this[cursor] == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
        return line to column
    }

    private fun String.newlinePadding(): String {
        val newlines = count { it == '\n' }
        return if (newlines == 0) "" else "\n".repeat(newlines)
    }

    private enum class ScanState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        TEMPLATE,
    }

    private data class UnsupportedPattern(
        val kind: String,
        val description: String,
        val regex: Regex,
    )

    private val TYPESCRIPT_EXTENSIONS = setOf("ts", "mts", "cts")
    private val unsupportedPatterns = listOf(
        UnsupportedPattern(
            kind = "enum",
            description = "TypeScript enum declarations require code generation and are not supported",
            regex = Regex("""(?m)(^|[;{}\r\n])\s*(?:export\s+)?(?:const\s+)?enum\s+[A-Za-z_$][0-9A-Za-z_$]*\b"""),
        ),
        UnsupportedPattern(
            kind = "namespace",
            description = "TypeScript namespace/module declarations require code generation and are not supported",
            regex = Regex("""(?m)(^|[;{}\r\n])\s*(?:export\s+)?(?:declare\s+)?(?:namespace|module)\s+[A-Za-z_$][0-9A-Za-z_$.]*\s*\{"""),
        ),
        UnsupportedPattern(
            kind = "decorator",
            description = "TypeScript decorators are not supported by AutoJs6 lightweight type stripping",
            regex = Regex("""(?m)^\s*@[A-Za-z_][0-9A-Za-z_$.]*"""),
        ),
        UnsupportedPattern(
            kind = "parameter_property",
            description = "TypeScript constructor parameter properties require code generation and are not supported",
            regex = Regex("""\bconstructor\s*\([^)]*\b(?:public|private|protected|readonly)\s+[A-Za-z_$][0-9A-Za-z_$]*\s*:"""),
        ),
        UnsupportedPattern(
            kind = "import_alias",
            description = "TypeScript import aliases require code generation and are not supported",
            regex = Regex("""(?m)(^|[;{}\r\n])\s*import\s+[A-Za-z_$][0-9A-Za-z_$]*\s*="""),
        ),
    )

    private val IMPORT_TYPE_PATTERN = Regex("""(?m)^\s*import\s+type\b[^\r\n;]*(?:;)?\s*""")
    private val EXPORT_TYPE_PATTERN = Regex("""(?m)^\s*export\s+type\b[^\r\n;]*(?:;)?\s*""")
    private val EXPORT_INTERFACE_INLINE_PATTERN = Regex("""(?m)^\s*export\s+interface\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\s+extends\s+[^{]+)?\s*\{\s*\}\s*""")
    private val INTERFACE_DECLARATION_PATTERN = Regex("""(?m)(^|[\r\n])\s*(?:export\s+)?interface\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\s+extends\s+[^{]+)?\s*""")
    private val TYPE_ALIAS_PATTERN = Regex("""(?ms)(^|[\r\n])\s*(?:export\s+)?type\s+[A-Za-z_$][0-9A-Za-z_$]*(?:\s*<[^;\r\n=]+>)?\s*=\s*.*?;\s*""")
    private val FUNCTION_TYPE_PARAMETERS_PATTERN = Regex("""\bfunction\s+([A-Za-z_$][0-9A-Za-z_$]*)\s*<[^>\r\n]+>\s*\(""")
    private val VARIABLE_TYPE_PATTERN = Regex("""\b(const|let|var)\s+([A-Za-z_$][0-9A-Za-z_$]*)\s*:\s*[^=;\r\n]+(?=\s*=)""")
    private val PARAMETER_TYPE_PATTERN = Regex("""([,(]\s*[A-Za-z_$][0-9A-Za-z_$]*)\??\s*:\s*[^,)=\r\n]+(?=\s*[,)=])""")
    private val RETURN_TYPE_PATTERN = Regex("""(\))\s*:\s*[^={;\r\n]+(?=\s*(?:=>|\{|;))""")
    private val PROPERTY_TYPE_PATTERN = Regex("""(?m)^(\s*(?:(?:public|private|protected|readonly|static)\s+)*[A-Za-z_$][0-9A-Za-z_$]*)(\??)\s*:\s*[^=;\r\n]+(?=\s*[=;])""")
    private val AS_ASSERTION_PATTERN = Regex("""(\S)\s+as\s+[A-Za-z_$][0-9A-Za-z_$<>,.? \t\[\]|&{}:'"-]*(?=\s*[,);+\-*/\]\r\n])""")
    private val SATISFIES_ASSERTION_PATTERN = Regex("""(\S)\s+satisfies\s+[A-Za-z_$][0-9A-Za-z_$<>,.? \t\[\]|&{}:'"-]*(?=\s*[,);+\-*/\]\r\n])""")
}
