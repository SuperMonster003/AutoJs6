package org.autojs.autojs.core.plugin.explorer

import org.autojs.autojs.util.FileUtils
import java.util.Locale

internal data class ExplorerActionTargetType(
    val mimeType: String,
    val extension: String,
)

/**
 * Converts Explorer's content-aware file type into plugin matching metadata.
 *
 * Android's extension-only MIME lookup cannot distinguish TypeScript from MPEG transport streams.
 * For the ambiguous suffixes, omit extension fallback after Explorer has resolved a source file;
 * otherwise an extension-only video action would still override the content-aware decision.
 */
internal object ExplorerActionTargetTypePolicy {

    fun resolve(
        type: FileUtils.TYPE,
        fallbackMimeType: String,
        rawExtension: String,
    ): ExplorerActionTargetType {
        val extension = rawExtension.lowercase(Locale.ROOT)
        if (type.isVideoFile()) {
            return ExplorerActionTargetType(
                mimeType = VIDEO_MIME_TYPE,
                extension = extension,
            )
        }
        return when (type) {
            FileUtils.TYPE.TYPESCRIPT,
            FileUtils.TYPE.MODULE_TYPESCRIPT,
            FileUtils.TYPE.COMMONJS_TYPESCRIPT,
            FileUtils.TYPE.TYPESCRIPT_DECLARATION,
            FileUtils.TYPE.TYPESCRIPT_MODULE_DECLARATION,
            FileUtils.TYPE.TYPESCRIPT_COMMONJS_DECLARATION,
            FileUtils.TYPE.REACT_TYPESCRIPT,
            -> ExplorerActionTargetType(
                mimeType = TYPESCRIPT_MIME_TYPE,
                extension = if (extension == "ts" || extension == "mts") "" else extension,
            )

            else -> ExplorerActionTargetType(
                mimeType = fallbackMimeType,
                extension = extension,
            )
        }
    }

    private const val TYPESCRIPT_MIME_TYPE = "text/typescript"
    private const val VIDEO_MIME_TYPE = "video/*"
}
