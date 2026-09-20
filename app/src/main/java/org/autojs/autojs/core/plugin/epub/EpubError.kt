package org.autojs.autojs.core.plugin.epub

/**
 * Host-side view of one EPUB plugin failure (plugin roadmap P5.4): a code from `EpubErrorCodes`
 * and a message bounded by the contract's ceiling.
 */
data class EpubError(
    val code: String,
    val message: String,
)

/** Thrown by the EPUB host client; the script layer (plugin roadmap P6) turns it into the `EpubError` JS object. */
class EpubPluginException(val error: EpubError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code
}
