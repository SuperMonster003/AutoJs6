package org.autojs.autojs.script

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Classifies Python-looking launch locations before a legacy engine source is created.
 *
 * The stable Python engine now owns admitted `.py` files in the normal Host flavor. This
 * fail-closed boundary remains for unsupported locations and Host flavors: a Python launch must
 * never fall through to Rhino, Node.js, or RootAutomator merely because it entered through a
 * different Host API.
 */
internal object PythonLaunchBoundaryPolicy {

    private val uriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    private val windowsAbsolutePath = Regex("^[A-Za-z]:[\\\\/].*")
    private val pythonMimeTypes = setOf(
        "application/python",
        "application/x-python",
        "application/x-python-code",
        "text/python",
        "text/x-python",
    )

    fun classify(location: String): PythonLaunchClassification {
        if (location.isBlank()) {
            return PythonLaunchClassification.NOT_PYTHON
        }

        if (isUri(location)) {
            val rawUriPath = location.substringBefore('#').substringBefore('?')
            val uriPath = runCatching {
                URLDecoder.decode(rawUriPath, StandardCharsets.UTF_8.name())
            }.getOrDefault(rawUriPath)
            return when (extensionOf(uriPath)) {
                "py", "pyc" -> PythonLaunchClassification.PYTHON_URI_UNSUPPORTED
                else -> PythonLaunchClassification.NOT_PYTHON
            }
        }

        return when (extensionOf(location)) {
            "py" -> PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME
            "pyc" -> PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED
            else -> PythonLaunchClassification.NOT_PYTHON
        }
    }

    fun enforceDefaultOff(location: String) {
        val error = when (classify(location)) {
            PythonLaunchClassification.NOT_PYTHON -> return
            PythonLaunchClassification.PYTHON_SOURCE_REQUIRES_RUNTIME ->
                PythonLaunchBoundaryError.RUNTIME_NOT_ACTIVATED
            PythonLaunchClassification.PYTHON_COMPILED_SOURCE_UNSUPPORTED ->
                PythonLaunchBoundaryError.COMPILED_SOURCE_UNSUPPORTED
            PythonLaunchClassification.PYTHON_URI_UNSUPPORTED ->
                PythonLaunchBoundaryError.URI_UNSUPPORTED
        }
        throw PythonLaunchBoundaryException(error)
    }

    fun enforceDefaultOffMimeType(mimeType: String?) {
        if (isPythonMimeType(mimeType)) {
            throw PythonLaunchBoundaryException(PythonLaunchBoundaryError.URI_UNSUPPORTED)
        }
    }

    fun isPythonMimeType(mimeType: String?): Boolean {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return false
        return normalized in pythonMimeTypes
    }

    private fun isUri(location: String): Boolean {
        return !windowsAbsolutePath.matches(location) && uriScheme.containsMatchIn(location)
    }

    private fun extensionOf(location: String): String? {
        val fileName = location.substringAfterLast('/').substringAfterLast('\\')
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.lastIndex) {
            return null
        }
        return fileName.substring(dot + 1).lowercase(Locale.ROOT)
    }
}

internal enum class PythonLaunchClassification {
    NOT_PYTHON,
    PYTHON_SOURCE_REQUIRES_RUNTIME,
    PYTHON_COMPILED_SOURCE_UNSUPPORTED,
    PYTHON_URI_UNSUPPORTED,
}

internal enum class PythonLaunchBoundaryError(
    val stableCode: String,
    val stableMessage: String,
) {
    RUNTIME_NOT_ACTIVATED(
        stableCode = "PYTHON_RUNTIME_NOT_ACTIVATED",
        stableMessage = "Python execution is not activated in this host build",
    ),
    COMPILED_SOURCE_UNSUPPORTED(
        stableCode = "PYTHON_COMPILED_SOURCE_UNSUPPORTED",
        stableMessage = "Compiled Python source is not supported",
    ),
    URI_UNSUPPORTED(
        stableCode = "PYTHON_URI_UNSUPPORTED",
        stableMessage = "Python URI execution is not supported",
    ),
    PRE_EXECUTE_UNSUPPORTED(
        stableCode = "PYTHON_PRE_EXECUTE_UNSUPPORTED",
        stableMessage = "Python launch does not support a pre-execute script",
    ),
    PROJECT_IMPORT_UNSUPPORTED(
        stableCode = "PYTHON_PROJECT_IMPORT_UNSUPPORTED",
        stableMessage = "Python project descriptors cannot be imported as scripts",
    ),
}

internal class PythonLaunchBoundaryException(
    val error: PythonLaunchBoundaryError,
) : IllegalStateException("${error.stableCode}: ${error.stableMessage}")
