package org.autojs.autojs.script

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NodeProjectLaunchDescriptor {

    const val DESCRIPTOR_FILE_NAME = ".autojs6-node-launch.json"
    const val INVALID_JSON_SANITIZED_MARKER =
        "autojs6.node_project_launch_descriptor.invalid_json_sanitized.v1"

    @JvmStatic
    fun descriptorFile(projectRoot: File): File = File(projectRoot, DESCRIPTOR_FILE_NAME)

    @JvmStatic
    fun readOptionsOrDefault(projectRoot: File): NodeProjectLaunchOptions {
        val descriptor = descriptorFile(projectRoot)
        return if (descriptor.isFile) {
            readOptions(projectRoot)
        } else {
            NodeProjectLaunchOptions()
        }
    }

    @JvmStatic
    fun readOptions(projectRoot: File): NodeProjectLaunchOptions {
        val descriptor = descriptorFile(projectRoot)
        require(descriptor.isFile) {
            "Missing internal Node project launch descriptor: ${descriptor.absolutePath}"
        }
        val json = runCatching {
            JSONObject(descriptor.readText(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException(
                "Invalid internal Node project launch descriptor: ${descriptor.absolutePath} " +
                        INVALID_JSON_SANITIZED_MARKER,
            )
        }
        val descriptorLaunchKind = json.optNodeProjectLaunchKindOrNull("launchKind")
        val launchOptions = NodeProjectLaunchOptions(
            packageDir = json.optRelativeDirectory(projectRoot, "packageDir"),
            workingDirectory = json.optRelativeDirectory(projectRoot, "workingDirectory"),
            scriptName = json.optStringOrNull("scriptName"),
            binName = json.optStringOrNull("binName"),
            argv = json.optStringArray("argv"),
            env = json.optStringMap("env"),
            timeoutMs = json.optPositiveLongOrNull("timeoutMs"),
        )
        return launchOptions.validateDescriptorLaunchKind(descriptorLaunchKind)
    }

    private fun NodeProjectLaunchOptions.validateDescriptorLaunchKind(
        descriptorLaunchKind: NodeProjectLaunchKind?,
    ): NodeProjectLaunchOptions {
        descriptorLaunchKind ?: return this
        val inferredLaunchKind = launchKind
        require(inferredLaunchKind == descriptorLaunchKind) {
            "Internal Node project launch descriptor launchKind mismatch: " +
                    "launchKind=${descriptorLaunchKind.descriptorValue} inferred=${inferredLaunchKind.descriptorValue}"
        }
        return this
    }

    private fun JSONObject.optRelativeDirectory(projectRoot: File, key: String): File? {
        val value = optStringOrNull(key) ?: return null
        require(!value.contains('\u0000')) {
            "Internal Node project launch descriptor path contains NUL: $key"
        }
        val normalized = value.replace('\\', '/').trim()
        if (normalized.isEmpty()) {
            return null
        }
        require(
            !normalized.startsWith("/") &&
                    !normalized.startsWith("\\") &&
                    !PROTOCOL_OR_WINDOWS_DRIVE_PATTERN.containsMatchIn(normalized),
        ) {
            "Internal Node project launch descriptor path must be relative: $key"
        }
        val parts = normalized.split('/')
            .filter { part -> part.isNotEmpty() && part != "." }
        require(parts.none { part -> part == ".." }) {
            "Internal Node project launch descriptor path escapes project root: $key"
        }
        val root = projectRoot.normalizedCanonicalFile()
        val target = parts.fold(root) { current, part -> File(current, part) }.normalizedCanonicalFile()
        require(target.isInsideOrSame(root)) {
            "Internal Node project launch descriptor path escapes project root: $key"
        }
        require(target.isDirectory) {
            "Internal Node project launch descriptor path is not a directory: $key"
        }
        return target
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        require(value is String) {
            "Internal Node project launch descriptor field must be a string: $key"
        }
        return value.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNodeProjectLaunchKindOrNull(key: String): NodeProjectLaunchKind? {
        val value = optStringOrNull(key) ?: return null
        return NodeProjectLaunchKind.fromDescriptorValue(value)
    }

    private fun JSONObject.optStringArray(key: String): List<String> {
        if (!has(key) || isNull(key)) {
            return emptyList()
        }
        val array = opt(key)
        require(array is JSONArray) {
            "Internal Node project launch descriptor field must be a string array: $key"
        }
        return List(array.length()) { index ->
            val value = array.opt(index)
            require(value is String) {
                "Internal Node project launch descriptor array value must be a string: $key[$index]"
            }
            value
        }
    }

    private fun JSONObject.optStringMap(key: String): Map<String, String> {
        if (!has(key) || isNull(key)) {
            return emptyMap()
        }
        val obj = opt(key)
        require(obj is JSONObject) {
            "Internal Node project launch descriptor field must be a string map: $key"
        }
        val result = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val envKey = keys.next()
            val value = obj.opt(envKey)
            require(value is String) {
                "Internal Node project launch descriptor map value must be a string: $key.$envKey"
            }
            result[envKey] = value
        }
        return result
    }

    private fun JSONObject.optPositiveLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        val timeoutMs = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
        require(timeoutMs != null && timeoutMs > 0L) {
            "Internal Node project launch descriptor timeoutMs must be positive"
        }
        return timeoutMs
    }

    private fun File.normalizedCanonicalFile(): File {
        return runCatching { canonicalFile }.getOrElse { absoluteFile }
    }

    private fun File.isInsideOrSame(root: File): Boolean {
        val rootPath = root.normalizedCanonicalFile().path.trimEnd(File.separatorChar)
        val path = normalizedCanonicalFile().path
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    private val PROTOCOL_OR_WINDOWS_DRIVE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}
