package org.autojs.autojs.ui.edit.editor.ace

import org.json.JSONObject

data class AceFontDescriptor(
    val source: Source,
    val id: String,
    val family: String,
    val url: String? = null,
    val sha256: String? = null,
    val format: String = FORMAT_WOFF2,
) {

    fun toJson(): String = JSONObject()
        .put("source", source.value)
        .put("id", id)
        .put("family", family)
        .put("format", format)
        .apply {
            url?.let { put("url", it) }
            sha256?.let { put("sha256", it) }
        }
        .toString()

    enum class Source(val value: String) {
        SYSTEM("system"),
        BUNDLED("bundled"),
        INSTALLED("installed"),
    }

    companion object {
        const val FORMAT_WOFF2 = "woff2"
        const val SYSTEM_FONT_ID = "system_monospace"
        const val BUNDLED_FONT_ID = "iosevka"
        const val BUNDLED_FONT_FAMILY = "Iosevka"
        const val VIRTUAL_FONT_URL_PREFIX =
            "file:///android_asset/editor/ace-builds-1.4.12/autojs6-fonts/"

        fun system(): AceFontDescriptor = AceFontDescriptor(
            source = Source.SYSTEM,
            id = SYSTEM_FONT_ID,
            family = "monospace",
        )

        fun bundledIosevka(): AceFontDescriptor = AceFontDescriptor(
            source = Source.BUNDLED,
            id = BUNDLED_FONT_ID,
            family = BUNDLED_FONT_FAMILY,
        )

        fun installed(id: String, family: String, sha256: String): AceFontDescriptor {
            require(VALID_FONT_ID.matches(id)) { "Invalid installed font id: $id" }
            require(VALID_SHA256.matches(sha256)) { "Invalid installed font SHA-256: $sha256" }
            return AceFontDescriptor(
                source = Source.INSTALLED,
                id = id,
                family = family,
                url = "$VIRTUAL_FONT_URL_PREFIX$id/$sha256.woff2",
                sha256 = sha256,
            )
        }

        private val VALID_FONT_ID = Regex("^[a-z0-9][a-z0-9_]{0,63}$")
        private val VALID_SHA256 = Regex("^[a-f0-9]{64}$")
    }
}
