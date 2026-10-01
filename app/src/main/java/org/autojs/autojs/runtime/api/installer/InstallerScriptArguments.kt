package org.autojs.autojs.runtime.api.installer

import org.autojs.autojs.runtime.api.installer.InstallerScriptOptions.Companion.invalid
import org.autojs.plugin.installer.api.InstallerContract
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Source snapshots made on the script thread. This layer has no Rhino or Android dependency,
 * does not inspect the filesystem, and never opens or deletes a source.
 */
object InstallerScriptArguments {

    sealed interface Source {
        data class FilePath(val file: File) : Source
        data class ContentUri(val uri: String) : Source
    }

    /** Each item is one application, possibly with splits. Even a one-item array is a batch. */
    data class SourcePlan(val items: List<List<Source>>, val isBatch: Boolean)

    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun sources(value: Any?, cwd: String?): SourcePlan {
        val batch = array(value)
        if (batch != null) {
            if (batch.isEmpty()) throw invalid("'sources' must not be empty")
            if (batch.size > InstallerContract.MAX_BATCH_SOURCES) {
                throw invalid("'sources' accepts at most ${InstallerContract.MAX_BATCH_SOURCES} packages")
            }
            return SourcePlan(batch.mapIndexed { index, item -> item(item, cwd, "sources[$index]") }, isBatch = true)
        }
        return SourcePlan(listOf(item(value, cwd, "source")), isBatch = false)
    }

    /** V1 inspect has one descriptor; it cannot inspect a batch or explicit split set. */
    fun inspect(value: Any?, cwd: String?): Source {
        if (value is Map<*, *> || array(value) != null) {
            throw invalid("'source' for inspect must be one file or content URI; arrays and split sets are not supported")
        }
        return source(value, cwd, "source")
    }

    fun packageName(value: Any?): String = InstallerScriptOptions.packageName(
        value as? String ?: throw invalid("'packageName' must be a string"),
        "packageName",
    )

    private fun item(value: Any?, cwd: String?, label: String): List<Source> {
        if (value is Map<*, *>) {
            value.keys.forEach { if (it != "splits") throw invalid("unknown source option '$label.$it'") }
            if (!value.containsKey("splits")) throw invalid("'$label.splits' is required")
            val splits = array(value["splits"]) ?: throw invalid("'$label.splits' must be an array")
            if (splits.isEmpty()) throw invalid("'$label.splits' must not be empty")
            if (splits.size > InstallerContract.MAX_SPLITS_PER_PACKAGE) {
                throw invalid("'$label.splits' accepts at most ${InstallerContract.MAX_SPLITS_PER_PACKAGE} files")
            }
            return splits.mapIndexed { index, split -> source(split, cwd, "$label.splits[$index]") }
        }
        return listOf(source(value, cwd, label))
    }

    private fun source(value: Any?, cwd: String?, label: String): Source = when (value) {
        is File -> file(value, cwd, label)
        is String -> {
            checkPath(value, label)
            if (!File(value).isAbsolute && SCHEME.containsMatchIn(value)) uri(value, cwd, label) else file(File(value), cwd, label)
        }
        else -> throw invalid("'$label' must be a path string, File or content URI; use { splits: [...] } for a split set")
    }

    private fun uri(value: String, cwd: String?, label: String): Source {
        val uri = try {
            URI(value)
        } catch (_: URISyntaxException) {
            throw invalid("'$label' must be a valid file or content URI")
        }
        if (uri.isOpaque || uri.path?.contains('\u0000') == true) throw invalid("'$label' must be a valid file or content URI")
        return when (uri.scheme.lowercase(Locale.ROOT)) {
            "content" -> {
                if (uri.rawAuthority.isNullOrBlank()) throw invalid("'$label' content URI must have an authority")
                Source.ContentUri(value)
            }
            "file" -> {
                if (!uri.rawAuthority.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null) {
                    throw invalid("'$label' file URI must be local and have no query or fragment")
                }
                val path = uri.path ?: throw invalid("'$label' file URI must have a path")
                if (!path.startsWith('/')) throw invalid("'$label' file URI must have an absolute path")
                file(File(uri), cwd, label)
            }
            else -> throw invalid("'$label' supports only file and content URI schemes")
        }
    }

    private fun file(value: File, cwd: String?, label: String): Source.FilePath {
        checkPath(value.path, label)
        val resolved = if (value.isAbsolute || cwd == null) value else {
            checkPath(cwd, "cwd")
            File(cwd, value.path)
        }
        return Source.FilePath(resolved.absoluteFile.normalize())
    }

    private fun checkPath(value: String, label: String) {
        if (value.isBlank()) throw invalid("'$label' must not be blank")
        if ('\u0000' in value) throw invalid("'$label' must not contain NUL")
    }

    private fun array(value: Any?): List<*>? = when (value) {
        is List<*> -> value
        is Array<*> -> value.asList()
        else -> null
    }
}
