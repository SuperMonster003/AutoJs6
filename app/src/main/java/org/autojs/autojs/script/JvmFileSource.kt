package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import java.io.File

open class JvmFileSource(requestedFile: File) : ScriptSource(PFiles.getNameWithoutExtension(requestedFile.name)) {

    val sourceKind: JvmSourceKind = JvmSourceKind.fromFileName(requestedFile.name)
        ?: throw UnsupportedJvmSourceException(requestedFile.path)

    /**
     * The canonical form of the requested file. Symlinked prefixes such as `/sdcard` or
     * `/data/user/0` are resolved instead of rejected; only a canonicalization failure or a
     * routing-class change (extension differs after resolution) fails the admission.
     * zh-CN: 请求文件的规范形式. `/sdcard`、`/data/user/0` 等符号链接前缀做解析而非拒绝;
     * 仅当无法规范化或解析后路由类型 (扩展名) 发生变化时才拒绝.
     */
    val file: File = run {
        val canonical = runCatching { requestedFile.canonicalFile }
            .getOrElse { throw UnsafeJvmSourcePathException(requestedFile.path, "the canonical path cannot be resolved") }
        if (!canonical.isFile) {
            throw UnsafeJvmSourcePathException(requestedFile.path, "the source must be an existing regular file")
        }
        if (JvmSourceKind.hasCanonicalJvmRoutingMismatch(requestedFile.name, canonical.name)) {
            throw UnsafeJvmSourcePathException(
                requestedFile.path,
                "requested file type differs from canonical JVM source '${canonical.name}'",
            )
        }
        canonical
    }

    override val extension: String = requestedFile.extension

    override val engineName: String = ENGINE

    override fun toString(): String {
        return originPath ?: originUri ?: file.toString()
    }

    companion object {
        const val ENGINE = "jvm-source"
    }
}
