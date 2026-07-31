package org.autojs.autojs.ui.preview

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.autojs.autojs6.R
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

enum class DocumentPreviewTheme(
    val id: String,
    @param:StringRes val labelRes: Int,
) {
    GITHUB_AUTO("github_auto", R.string.text_preview_theme_github_auto),
    GITHUB_LIGHT("github_light", R.string.text_preview_theme_github_light),
    GITHUB_DARK("github_dark", R.string.text_preview_theme_github_dark),
    PAPER("paper", R.string.text_preview_theme_paper),
    SEPIA("sepia", R.string.text_preview_theme_sepia),
    CUSTOM("custom", R.string.text_preview_theme_custom),
    ;

    fun stylesheetName(nightMode: Boolean): String = when (this) {
        GITHUB_AUTO, CUSTOM -> if (nightMode) "github-dark.css" else "github-light.css"
        GITHUB_LIGHT -> "github-light.css"
        GITHUB_DARK -> "github-dark.css"
        PAPER -> "paper.css"
        SEPIA -> "sepia.css"
    }

    companion object {
        val BUILT_IN = entries.filterNot { it == CUSTOM }

        fun fromId(id: String?): DocumentPreviewTheme =
            entries.firstOrNull { it.id == id } ?: GITHUB_AUTO
    }
}

class CustomCssTooLargeException(
    val limitBytes: Int,
) : IOException("Custom CSS exceeds the $limitBytes-byte import limit")

class DocumentPreviewPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val customCssFile = AtomicFile(appContext.filesDir.resolve(CUSTOM_CSS_RELATIVE_PATH))

    var theme: DocumentPreviewTheme
        get() {
            val stored = DocumentPreviewTheme.fromId(preferences.getString(KEY_THEME, null))
            return stored.takeUnless { it == DocumentPreviewTheme.CUSTOM && !hasCustomCss }
                ?: DocumentPreviewTheme.GITHUB_AUTO
        }
        set(value) {
            val normalized = value.takeUnless { it == DocumentPreviewTheme.CUSTOM && !hasCustomCss }
                ?: DocumentPreviewTheme.GITHUB_AUTO
            preferences.edit { putString(KEY_THEME, normalized.id) }
        }

    var startInFullscreenMode: Boolean
        get() = preferences.getBoolean(KEY_START_IN_FULLSCREEN_MODE, false)
        set(value) = preferences.edit { putBoolean(KEY_START_IN_FULLSCREEN_MODE, value) }

    val hasCustomCss: Boolean
        get() = customCssFile.baseFile.isFile

    fun readCustomCss(): String? {
        if (!hasCustomCss) return null
        val file = customCssFile.baseFile
        if (file.length() > MAX_CUSTOM_CSS_BYTES) {
            throw CustomCssTooLargeException(MAX_CUSTOM_CSS_BYTES)
        }
        return file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    fun importCustomCss(input: InputStream) {
        val bytes = readBounded(input, MAX_CUSTOM_CSS_BYTES)
        val parent = customCssFile.baseFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Cannot create the custom CSS directory")
        }
        val output = customCssFile.startWrite()
        try {
            output.write(bytes)
            customCssFile.finishWrite(output)
        } catch (error: Throwable) {
            customCssFile.failWrite(output)
            throw error
        }
    }

    fun clearCustomCss() {
        val wasCustom = theme == DocumentPreviewTheme.CUSTOM
        customCssFile.delete()
        if (wasCustom) {
            theme = DocumentPreviewTheme.GITHUB_AUTO
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val singleByte = input.read()
                if (singleByte < 0) break
                total++
                if (total > maxBytes) {
                    throw CustomCssTooLargeException(maxBytes)
                }
                output.write(singleByte)
                continue
            }
            total += read
            if (total > maxBytes) {
                throw CustomCssTooLargeException(maxBytes)
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MAX_CUSTOM_CSS_BYTES = 256 * 1024

        private const val PREFERENCES_NAME = "document_preview"
        private const val KEY_START_IN_FULLSCREEN_MODE = "start_in_fullscreen_mode"
        private const val KEY_THEME = "theme"
        private const val CUSTOM_CSS_RELATIVE_PATH = "document-preview/custom.css"
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
