package org.autojs.autojs.script

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.autojs.core.plugin.python.PythonExecutionTimeoutPolicy
import java.io.File
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Strict admission for R4 Python projects. Execution remains closed until workspace transport lands. */
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
        val pythonMain = main?.let(::isPythonEntryName) == true

        if (!pythonType) {
            if (pythonMain || legacyMain?.name?.let(::isPythonEntryName) == true) {
                throw PythonProjectLaunchException(PythonProjectLaunchError.TYPE_REQUIRED)
            }
            return null
        }
        if (main.isNullOrEmpty()) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.CONFIG_INVALID)
        }

        val entry = resolveEntry(normalizedRoot, main)
        return PythonProjectLaunchAdmission(normalizedRoot, entry, declaration.timeoutMillis)
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

    /** R4-P1 recognizes valid projects but deliberately keeps execution fail-closed. */
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
        return Declaration(
            projectType = type ?: alias,
            main = stringValue(json, "main"),
            timeoutMillis = timeoutMillisValue(json),
        )
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

    private fun resolveEntry(projectRoot: File, main: String): File {
        if (
            main.indexOf('\u0000') >= 0 ||
            main.startsWith('/') ||
            main.startsWith('\\') ||
            '\\' in main ||
            WINDOWS_ABSOLUTE_PATH.matches(main) ||
            URI_SCHEME.containsMatchIn(main)
        ) {
            throw PythonProjectLaunchException(PythonProjectLaunchError.ENTRY_INVALID)
        }
        val segments = main.split('/')
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
    )

    private const val PROJECT_CONFIG_NAME = "project.json"
    private const val PYTHON_PROJECT_TYPE = "python"
    private const val MAX_PROJECT_CONFIG_BYTES = 64L * 1024L
    private const val MAX_PROJECT_ANCESTORS = 64
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
)

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
