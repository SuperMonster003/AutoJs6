package org.autojs.autojs.core.plugin.gallery

/**
 * Host-side view of one gallery plugin failure (3-Maple Image roadmap M6): a code from
 * `GalleryErrorCodes` and a message bounded by the contract's ceiling.
 */
data class GalleryError(
    val code: String,
    val message: String,
)

/** Thrown by the gallery host client; the script layer turns it into the `GalleryError` JS object. */
class GalleryPluginException(val error: GalleryError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code
}
