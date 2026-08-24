package org.autojs.autojs.script

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.autojs.core.plugin.python.PythonExecutionTimeoutPolicy
import org.autojs.plugin.python.runtime.api.PythonEntryMode
import org.autojs.plugin.python.runtime.api.PythonExecutionMode
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractException
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/** Strict admission for Python projects transported as bounded workspace snapshots. */
object PythonProjectLaunchPolicy {

    @JvmStatic
    fun inspect(projectRoot: File, legacyMain: File?): PythonProjectLaunchAdmission? {
        val normalizedRoot = projectRoot.strictCanonicalFile(PythonProjectLaunchError.CONFIG_INVALID)
        val manifest = File(normalizedRoot, PROJECT_CONFIG_NAME)
        if (!manifest.isFile) return null

        val declaration = readDeclaration(manifest, legacyMain) ?: return null
        val projectType = declaration.projectType?.trim()
        val main = declaration.main?.trim()
        val pythonType = projectType.equals(PYTHON_PROJECT_TYPE, ignoreCase = true)
        val pythonMain = declaration.entryMode == PythonEntryMode.FILE && main?.let(::isPythonEntryName) == true

        if (!pythonType) {
            if (
                pythonMain ||
                declaration.entryMode == PythonEntryMode.MODULE ||
                legacyMain?.name?.let(::isPythonEntryName) == true
            ) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.TYPE_REQUIRED)
            }
            return null
        }
        if (main.isNullOrEmpty()) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }

        val entry = resolveEntry(normalizedRoot, main, declaration.entryMode)
        declaration.stdin?.let { validateStdinSource(normalizedRoot, it) }
        return PythonProjectLaunchAdmission(
            normalizedRoot,
            entry,
            declaration.timeoutMillis,
            declaration.entryMode,
            declaration.stdin,
            declaration.executionMode,
        )
    }

    /**
     * Resolves the nearest enclosing project whose declared entry is exactly [entryFile].
     * A nearer project marker is an ownership boundary: malformed or non-Python projects fail
     * closed instead of allowing the search to escape into an outer project.
     */
    internal fun inspectContainingEntry(entryFile: File): PythonProjectLaunchAdmission? {
        val entry = entryFile.strictCanonicalFile(PythonProjectLaunchError.ENTRY_INVALID)
        if (!entry.isFile || !entry.name.endsWith(".py", ignoreCase = true)) return null
        var directory = entry.parentFile
        var depth = 0
        while (directory != null) {
            if (depth++ >= MAX_PROJECT_ANCESTORS) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.CONTEXT_INVALID)
            }
            val manifest = File(directory, PROJECT_CONFIG_NAME)
            if (manifest.exists()) {
                if (!manifest.isFile) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
                }
                val admission = inspect(directory, entry)
                if (admission == null || admission.entryFile != entry) {
                    throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_NOT_DECLARED)
                }
                return admission
            }
            val parent = directory.parentFile ?: return null
            if (parent == directory) return null
            directory = parent
        }
        return null
    }

    /** Compatibility guard for callers which explicitly disable the stable runtime channel. */
    @JvmStatic
    fun rejectExecutionUntilWorkspaceSupport(admission: PythonProjectLaunchAdmission) {
        require(admission.projectRoot.isAbsolute) { "Python project admission is not normalized" }
        throw PythonProjectLaunchException(PythonProjectLaunchError.EXECUTION_NOT_ACTIVATED)
    }

    private fun readDeclaration(
        manifest: File,
        legacyMain: File?,
    ): Declaration? {
        if (manifest.length() !in 1..MAX_PROJECT_CONFIG_BYTES) {
            if (legacyMain?.name?.let(::isPythonEntryName) == true) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
            }
            return null
        }
        val text = try {
            val bytes = manifest.readBytes()
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            if (legacyMain?.name?.let(::isPythonEntryName) == true) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID, error)
            }
            return null
        }

        val json = try {
            val reader = JsonReader(StringReader(text)).apply { strictness = Strictness.STRICT }
            val element = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw IllegalArgumentException("project config has trailing JSON content")
            }
            if (!element.isJsonObject) throw IllegalArgumentException("project config must be an object")
            element.asJsonObject
        } catch (error: Exception) {
            if (containsPythonDeclarationHint(text) || legacyMain?.name?.let(::isPythonEntryName) == true) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID, error)
            }
            return null
        }
        return try {
            declarationFrom(json)
        } catch (error: PythonProjectLaunchException) {
            if (containsPythonDeclarationHint(text) || legacyMain?.name?.let(::isPythonEntryName) == true) {
                throw error
            }
            null
        }
    }

    private fun declarationFrom(json: JsonObject): Declaration {
        val type = stringValue(json, "type")
        val alias = stringValue(json, "projectType")
        if (type != null && alias != null && !type.equals(alias, ignoreCase = true)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }
        val executionMode = executionModeValue(json)
        val timeoutMillis = timeoutMillisValue(json)
        if (executionMode == PythonExecutionMode.LONG_RUNNING && timeoutMillis != null) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }
        return Declaration(
            projectType = type ?: alias,
            main = stringValue(json, "main"),
            timeoutMillis = timeoutMillis,
            entryMode = entryModeValue(json),
            stdin = stdinValue(json),
            executionMode = executionMode,
        )
    }

    private fun stdinValue(json: JsonObject): PythonProjectStdinSource? {
        val value = json.get("stdin") ?: return null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return PythonProjectStdinSource.InlineText(value.asString)
        }
        if (!value.isJsonObject) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID)
        }
        val stdinObject = value.asJsonObject
        if (stdinObject.size() != 1 || !stdinObject.has(STDIN_FILE_PROPERTY)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID)
        }
        val file = stdinObject.get(STDIN_FILE_PROPERTY)
        if (!file.isJsonPrimitive || !file.asJsonPrimitive.isString) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID)
        }
        return PythonProjectStdinSource.ProjectFile(file.asString)
    }

    private fun entryModeValue(json: JsonObject): PythonEntryMode =
        when (stringValue(json, "entryMode") ?: FILE_ENTRY_MODE) {
            FILE_ENTRY_MODE -> PythonEntryMode.FILE
            MODULE_ENTRY_MODE -> PythonEntryMode.MODULE
            else -> throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }

    private fun executionModeValue(json: JsonObject): PythonExecutionMode =
        when (stringValue(json, "executionMode") ?: BOUNDED_EXECUTION_MODE) {
            BOUNDED_EXECUTION_MODE -> PythonExecutionMode.BOUNDED
            LONG_RUNNING_EXECUTION_MODE -> PythonExecutionMode.LONG_RUNNING
            else -> throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }

    private fun timeoutMillisValue(json: JsonObject): Long? {
        val value = json.get("timeout") ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }
        val encoded = value.asJsonPrimitive.asString
        val timeoutMillis = if (JSON_INTEGER.matches(encoded)) encoded.toLongOrNull() else null
        if (
            timeoutMillis == null ||
            !PythonExecutionTimeoutPolicy.isValidConfiguredTimeout(timeoutMillis)
        ) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }
        return timeoutMillis
    }

    private fun stringValue(json: JsonObject, name: String): String? {
        val value = json.get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }
        return value.asString
    }

    private fun resolveEntry(projectRoot: File, main: String, entryMode: PythonEntryMode): File {
        val fileEntryPoint = when (entryMode) {
            PythonEntryMode.FILE -> main
            PythonEntryMode.MODULE -> try {
                PythonRuntimeValidation.fileEntryPointForModuleName(main)
            } catch (error: PythonRuntimeContractException) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_INVALID, error)
            }
        }
        if (
            fileEntryPoint.indexOf('\u0000') >= 0 ||
            fileEntryPoint.startsWith('/') ||
            fileEntryPoint.startsWith('\\') ||
            '\\' in fileEntryPoint ||
            WINDOWS_ABSOLUTE_PATH.matches(fileEntryPoint) ||
            URI_SCHEME.containsMatchIn(fileEntryPoint)
        ) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_INVALID)
        }
        val segments = fileEntryPoint.split('/')
        if (
            segments.isEmpty() ||
            segments.any { it.isEmpty() || it == "." || it == ".." } ||
            !segments.last().endsWith(".py", ignoreCase = true)
        ) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_INVALID)
        }

        val candidate = segments.fold(projectRoot) { parent, segment -> File(parent, segment) }
        val normalizedEntry = candidate.strictCanonicalFile(PythonProjectLaunchError.ENTRY_ESCAPES_ROOT)
        if (candidate.absoluteFile.path != normalizedEntry.path) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_ESCAPES_ROOT)
        }
        val rootPath = projectRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!normalizedEntry.path.startsWith(rootPath, ignoreCase = WINDOWS_ABSOLUTE_PATH.matches(projectRoot.path))) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_ESCAPES_ROOT)
        }
        if (!normalizedEntry.isFile) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_MISSING)
        }
        if (!normalizedEntry.name.endsWith(".py", ignoreCase = true)) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_INVALID)
        }
        return normalizedEntry
    }

    internal fun validateStdinSource(projectRoot: File, source: PythonProjectStdinSource) {
        when (source) {
            is PythonProjectStdinSource.InlineText -> validateInlineStdin(source.text)
            is PythonProjectStdinSource.ProjectFile -> resolveStdinFile(projectRoot, source.relativePath)
        }
    }

    internal fun snapshotStdin(projectRoot: File, source: PythonProjectStdinSource): Any =
        when (source) {
            is PythonProjectStdinSource.InlineText -> {
                validateInlineStdin(source.text)
                source.text
            }
            is PythonProjectStdinSource.ProjectFile -> readStdinFile(projectRoot, source.relativePath)
        }

    private fun validateInlineStdin(text: String) {
        if (strictUtf8Length(text) > MAX_STDIN_SNAPSHOT_BYTES) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_TOO_LARGE)
        }
    }

    private fun strictUtf8Length(text: String): Long =
        try {
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
                .remaining()
                .toLong()
        } catch (error: Exception) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID, error)
        }

    private fun resolveStdinFile(projectRoot: File, relativePath: String): File {
        if (
            relativePath.isBlank() ||
            strictUtf8Length(relativePath) > MAX_STDIN_PATH_BYTES ||
            Normalizer.normalize(relativePath, Normalizer.Form.NFC) != relativePath ||
            relativePath.any(Character::isISOControl) ||
            relativePath.startsWith('/') ||
            relativePath.startsWith('\\') ||
            '\\' in relativePath ||
            WINDOWS_ABSOLUTE_PATH.matches(relativePath) ||
            URI_SCHEME.containsMatchIn(relativePath)
        ) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID)
        }
        val segments = relativePath.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID)
        }

        val candidate = segments.fold(projectRoot) { parent, segment -> File(parent, segment) }
        val normalizedFile = candidate.strictCanonicalFile(PythonProjectLaunchError.STDIN_ESCAPES_ROOT)
        if (candidate.absoluteFile.path != normalizedFile.path) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_ESCAPES_ROOT)
        }
        val rootPath = projectRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!normalizedFile.path.startsWith(rootPath, ignoreCase = WINDOWS_ABSOLUTE_PATH.matches(projectRoot.path))) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_ESCAPES_ROOT)
        }
        if (!normalizedFile.isFile) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_MISSING)
        }
        if (normalizedFile.length() > MAX_STDIN_SNAPSHOT_BYTES) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_TOO_LARGE)
        }
        return normalizedFile
    }

    private fun readStdinFile(projectRoot: File, relativePath: String): ByteArray {
        val source = resolveStdinFile(projectRoot, relativePath)
        val output = ByteArrayOutputStream(minOf(source.length(), MAX_STDIN_SNAPSHOT_BYTES).toInt())
        val buffer = ByteArray(STDIN_COPY_BUFFER_BYTES)
        try {
            FileInputStream(source).use { input ->
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_STDIN_SNAPSHOT_BYTES) {
                        throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_TOO_LARGE)
                    }
                    output.write(buffer, 0, count)
                }
            }
            val revalidated = resolveStdinFile(projectRoot, relativePath)
            if (revalidated != source) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_ESCAPES_ROOT)
            }
            return output.toByteArray()
        } catch (error: PythonProjectLaunchException) {
            throw error
        } catch (error: Exception) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.STDIN_INVALID, error)
        } finally {
            buffer.fill(0)
        }
    }

    private fun containsPythonDeclarationHint(text: String): Boolean =
        PYTHON_TYPE_HINT.containsMatchIn(text) || PYTHON_MAIN_HINT.containsMatchIn(text)

    private fun isPythonEntryName(name: String): Boolean =
        name.endsWith(".py", ignoreCase = true) || name.endsWith(".pyc", ignoreCase = true)

    private fun File.strictCanonicalFile(error: PythonProjectLaunchError): File =
        try {
            canonicalFile
        } catch (failure: Exception) {
            throw PythonProjectLaunchException(error, failure)
        }

    private data class Declaration(
        val projectType: String?,
        val main: String?,
        val timeoutMillis: Long?,
        val entryMode: PythonEntryMode,
        val stdin: PythonProjectStdinSource?,
        val executionMode: PythonExecutionMode,
    )

    private const val PROJECT_CONFIG_NAME = "project.json"
    private const val PYTHON_PROJECT_TYPE = "python"
    private const val FILE_ENTRY_MODE = "file"
    private const val MODULE_ENTRY_MODE = "module"
    private const val BOUNDED_EXECUTION_MODE = "bounded"
    private const val LONG_RUNNING_EXECUTION_MODE = "long-running"
    private const val STDIN_FILE_PROPERTY = "file"
    private const val MAX_PROJECT_CONFIG_BYTES = 64L * 1024L
    private const val MAX_PROJECT_ANCESTORS = 64
    internal const val MAX_STDIN_SNAPSHOT_BYTES = 1L * 1024L * 1024L
    private const val MAX_STDIN_PATH_BYTES = 4 * 1024
    private const val STDIN_COPY_BUFFER_BYTES = 16 * 1024
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[\\\\/].*")
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val JSON_INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
    private val PYTHON_TYPE_HINT = Regex("\\\"(?:type|projectType)\\\"\\s*:\\s*\\\"python\\\"", RegexOption.IGNORE_CASE)
    private val PYTHON_MAIN_HINT = Regex("\\\"main\\\"\\s*:\\s*\\\"[^\\\"]+\\.pyc?\\\"", RegexOption.IGNORE_CASE)
}

data class PythonProjectLaunchAdmission(
    val projectRoot: File,
    val entryFile: File,
    val timeoutMillis: Long?,
    val entryMode: PythonEntryMode = PythonEntryMode.FILE,
    val stdin: PythonProjectStdinSource? = null,
    val executionMode: PythonExecutionMode = PythonExecutionMode.BOUNDED,
)

sealed class PythonProjectStdinSource {
    data class InlineText(val text: String) : PythonProjectStdinSource()

    data class ProjectFile(val relativePath: String) : PythonProjectStdinSource()
}

enum class PythonProjectLaunchError(
    val stableCode: String,
    val stableMessage: String,
) {
    TYPE_REQUIRED(
        "PYTHON_PROJECT_TYPE_REQUIRED",
        "A Python project entry requires project type python",
    ),
    CONFIG_INVALID(
        "PYTHON_PROJECT_CONFIG_INVALID",
        "Python project configuration is invalid",
    ),
    ENTRY_INVALID(
        "PYTHON_PROJECT_ENTRY_INVALID",
        "Python project entry is invalid",
    ),
    ENTRY_ESCAPES_ROOT(
        "PYTHON_PROJECT_ENTRY_ESCAPES_ROOT",
        "Python project entry escapes the project root",
    ),
    ENTRY_MISSING(
        "PYTHON_PROJECT_ENTRY_MISSING",
        "Python project entry does not exist",
    ),
    STDIN_INVALID(
        "PYTHON_PROJECT_STDIN_INVALID",
        "Python project stdin declaration is invalid",
    ),
    STDIN_ESCAPES_ROOT(
        "PYTHON_PROJECT_STDIN_ESCAPES_ROOT",
        "Python project stdin file escapes the project root",
    ),
    STDIN_MISSING(
        "PYTHON_PROJECT_STDIN_MISSING",
        "Python project stdin file does not exist",
    ),
    STDIN_TOO_LARGE(
        "PYTHON_PROJECT_STDIN_TOO_LARGE",
        "Python project stdin snapshot exceeds the 1 MiB limit",
    ),
    ENTRY_NOT_DECLARED(
        "PYTHON_PROJECT_ENTRY_NOT_DECLARED",
        "Only the declared Python project entry can launch with project context",
    ),
    PRE_EXECUTE_UNSUPPORTED(
        "PYTHON_PROJECT_PRE_EXECUTE_UNSUPPORTED",
        "Python project launch does not support a pre-execute script",
    ),
    CONTEXT_REQUIRED(
        "PYTHON_PROJECT_CONTEXT_REQUIRED",
        "Python project launch requires an explicit project context",
    ),
    CONTEXT_INVALID(
        "PYTHON_PROJECT_CONTEXT_INVALID",
        "Python project launch context is invalid or stale",
    ),
    EXECUTION_NOT_ACTIVATED(
        "PYTHON_PROJECT_EXECUTION_NOT_ACTIVATED",
        "Python project execution requires bounded workspace support",
    ),
}

class PythonProjectLaunchException(
    val error: PythonProjectLaunchError,
    cause: Throwable? = null,
) : IllegalStateException("${error.stableCode}: ${error.stableMessage}", cause)
