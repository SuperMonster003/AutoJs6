package org.autojs.autojs.engine

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

internal fun NodePluginScriptRequest.withHostModuleSourceProvider(): NodePluginScriptRequest {
    if (moduleSourceProvider != null) {
        return this
    }
    val rootPath = sandboxRoot?.takeIf(String::isNotBlank)
        ?: workingDirectory?.takeIf(String::isNotBlank)
        ?: return this
    val execution = executionId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
    val root = runCatching { File(rootPath).canonicalFile }
        .getOrNull()
        ?.takeIf(File::isDirectory)
        ?: return copy(executionId = execution)
    val initialSourceBytes = moduleSources.values.sumOf { source ->
        source.toByteArray(StandardCharsets.UTF_8).size.toLong()
    }
    val provider = runCatching {
        NodeJsEncryptedModuleSourceProvider(
            rootDirectory = root,
            executionId = execution,
            timeoutMs = timeoutMs,
            initialSourceCount = moduleSources.size,
            initialSourceBytes = initialSourceBytes,
        )
    }.getOrNull() ?: return copy(executionId = execution)
    return copy(
        executionId = execution,
        moduleSourceProvider = provider,
    )
}
