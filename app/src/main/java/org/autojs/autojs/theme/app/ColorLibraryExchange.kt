package org.autojs.autojs.theme.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.theme.library.ColorLibraryJson.ColorLibraryJsonException
import org.autojs.autojs.theme.library.ColorLibraryJson.Reason
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exports color libraries to and imports them from AutoJs6 JSON files through the Storage Access Framework
 * (roadmap P2.2, P2.3). Create it while the activity is being constructed, as it registers activity result launchers.
 * zh-CN: 通过存储访问框架将颜色库导出为 AutoJs6 JSON 文件, 或从中导入 (Roadmap P2.2, P2.3).
 * 须在 Activity 构造期间创建, 因为它会注册 Activity 结果启动器.
 *
 * Libraries to export are identified by [Source] keys, which are kept in the saved state while the document picker is
 * open, so a recreated activity still finishes the export.
 * zh-CN: 待导出的库以 [Source] 键标识, 文档选择器打开期间保存在已保存状态中, 因此 Activity 重建后仍能完成导出.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
class ColorLibraryExchange(private val activity: AppCompatActivity, private val onImported: () -> Unit) {

    /** A library that can be exported. zh-CN: 可导出的库. */
    sealed interface Source {
        val key: String

        data class PresetPlain(val id: Int) : Source {
            override val key get() = "$PREFIX_PRESET_PLAIN$id"
        }

        data class PresetIntelligent(val id: Int) : Source {
            override val key get() = "$PREFIX_PRESET_INTELLIGENT$id"
        }

        data class User(val rowId: Long) : Source {
            override val key get() = "$PREFIX_USER$rowId"
        }

        companion object {
            fun of(key: String): Source? = when {
                key.startsWith(PREFIX_PRESET_PLAIN) -> key.removePrefix(PREFIX_PRESET_PLAIN).toIntOrNull()?.let(::PresetPlain)
                key.startsWith(PREFIX_PRESET_INTELLIGENT) -> key.removePrefix(PREFIX_PRESET_INTELLIGENT).toIntOrNull()?.let(::PresetIntelligent)
                key.startsWith(PREFIX_USER) -> key.removePrefix(PREFIX_USER).toLongOrNull()?.let(::User)
                else -> null
            }
        }
    }

    /** A library offered in the export chooser. zh-CN: 导出选择列表中的库. */
    data class Choice(val source: Source, val label: String)

    private val repository by lazy { ColorLibraryRepository.get(activity) }

    private var pendingExport: List<Source>? = null

    private val createDocument = activity.registerForActivityResult(ActivityResultContracts.CreateDocument(ColorLibraryJson.MIME_TYPE)) { uri ->
        val sources = pendingExport ?: restoredExport()
        pendingExport = null
        if (uri != null && !sources.isNullOrEmpty()) write(uri, sources)
    }

    private val openDocument = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::read)
    }

    init {
        activity.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
            bundleOf(SAVED_STATE_SOURCES to ArrayList(pendingExport.orEmpty().map { it.key }))
        }
    }

    /** Asks for a file and exports [sources] into it. zh-CN: 选择文件并将 [sources] 导出到其中. */
    fun export(sources: List<Source>) {
        if (sources.isEmpty()) return
        pendingExport = sources
        createDocument.launch(defaultFileName())
    }

    /**
     * Lets the user check the libraries to export among [choices], then exports them.
     * zh-CN: 让用户在 [choices] 中勾选要导出的库, 然后导出.
     */
    fun chooseAndExport(choices: List<Choice>) {
        ColorLibraryDialogs.showMultiChoice(activity, R.string.text_choose_color_libraries_to_export, choices.map { it.label }) { indices ->
            export(indices.map { choices[it].source })
        }
    }

    /** Asks for a file and offers its libraries for import. zh-CN: 选择文件, 并提供其中的库以供导入. */
    fun import() {
        openDocument.launch(IMPORT_MIME_TYPES)
    }

    /**
     * All libraries that can be exported: presets first, then user libraries in list order.
     * zh-CN: 所有可导出的库: 先预设库, 再按列表顺序列出用户库.
     */
    suspend fun allChoices(): List<Choice> {
        val identifier = activity.getString(R.string.color_library_identifier_intelligent_colors)
        val presets = ColorLibrariesActivity.presetColorLibraries.filterNot { it.isIntelligent }.map {
            Choice(Source.PresetPlain(it.id), activity.getString(it.nameRes))
        }
        val intelligentPresets = IntelligentPresets.all(activity).map {
            Choice(Source.PresetIntelligent(it.libraryId), "${it.name} [$identifier]")
        }
        val users = withContext(Dispatchers.IO) { repository.listLibraries() }.map { library ->
            Choice(Source.User(library.id), if (library.isIntelligent) "${library.name} [$identifier]" else library.name)
        }
        return presets + intelligentPresets + users
    }

    private fun restoredExport(): List<Source>? {
        return activity.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
            ?.getStringArrayList(SAVED_STATE_SOURCES)
            ?.mapNotNull(Source::of)
    }

    private fun write(uri: Uri, sources: List<Source>) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val libraries = sources.mapNotNull { load(it) }
                    val app = ColorLibraryJson.AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                    val text = ColorLibraryJson.encode(libraries, System.currentTimeMillis(), ZoneId.systemDefault(), app)
                    activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Cannot open $uri")
                    libraries.size
                }
            }
            result.onSuccess { count ->
                ViewUtils.showToast(activity, activity.resources.getQuantityString(R.plurals.text_n_color_libraries_exported, count, count))
            }.onFailure { e ->
                val message = (e as? ColorLibraryJsonException)?.let(::messageOf) ?: activity.getString(R.string.text_failed_to_export_color_libraries)
                ViewUtils.showToast(activity, message, true)
            }
        }
    }

    private suspend fun load(source: Source): ColorLibraryJson.Library? = when (source) {
        is Source.PresetPlain -> ColorLibrariesActivity.presetColorLibraries.find { it.id == source.id }?.let { library ->
            ColorLibraryJson.PlainLibrary(
                activity.getString(library.nameRes),
                library.colors.map { ColorLibraryJson.Color(activity.getColor(it.colorRes), activity.getString(it.nameRes)) },
            )
        }
        is Source.PresetIntelligent -> IntelligentPresets.find(activity, source.id)?.let {
            ColorLibraryJson.IntelligentLibrary(it.name, it.config, it.groups)
        }
        is Source.User -> repository.findLibrary(source.rowId)?.let { library ->
            when {
                library.isIntelligent -> repository.loadIntelligent(library.id)?.let {
                    ColorLibraryJson.IntelligentLibrary(it.name, it.config, it.groups)
                }
                else -> ColorLibraryJson.PlainLibrary(
                    library.name,
                    repository.listColors(library.id).map { ColorLibraryJson.Color(it.color, it.name, it.createdTime, it.modifiedTime) },
                )
            }
        }
    }

    private fun read(uri: Uri) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = input.readNBytesCompat(ColorLibraryJson.MAX_FILE_BYTES + 1)
                        if (buffer.size > ColorLibraryJson.MAX_FILE_BYTES) throw ColorLibraryJsonException(Reason.TOO_LARGE)
                        buffer
                    } ?: throw IOException("Cannot open $uri")
                    val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
                    ColorLibraryJson.decode(text, activity.getString(R.string.text_unnamed_color_library)) to displayNameOf(uri)
                }
            }
            result.onSuccess { (document, fileName) -> showImportPreview(document, fileName) }.onFailure { e ->
                val message = (e as? ColorLibraryJsonException)?.let(::messageOf) ?: activity.getString(R.string.text_failed_to_import)
                ViewUtils.showToast(activity, message, true)
            }
        }
    }

    private fun showImportPreview(document: ColorLibraryJson.Document, fileName: String?) {
        val identifier = activity.getString(R.string.color_library_identifier_intelligent_colors)
        val labels = document.libraries.map { decoded ->
            when (val library = decoded.library) {
                is ColorLibraryJson.PlainLibrary -> {
                    val count = activity.resources.getQuantityString(R.plurals.text_n_colors, library.colors.size, library.colors.size)
                    "${library.name} [$count]"
                }
                is ColorLibraryJson.IntelligentLibrary -> {
                    val count = activity.resources.getQuantityString(R.plurals.text_n_groups, library.groups.size, library.groups.size)
                    "${library.name} [$identifier | $count]"
                }
            }
        }
        val dropped = document.libraries.sumOf { it.droppedValues }
        val notes = buildList {
            if (dropped > 0) add(activity.resources.getQuantityString(R.plurals.text_n_invalid_values_skipped, dropped, dropped))
            if (document.skippedLibraries > 0) {
                add(activity.resources.getQuantityString(R.plurals.text_n_unknown_libraries_skipped, document.skippedLibraries, document.skippedLibraries))
            }
        }
        MaterialDialog.Builder(activity)
            .title(R.string.text_choose_color_libraries_to_import)
            .apply { if (notes.isNotEmpty()) content(notes.joinToString("\n")) }
            .items(labels)
            .itemsCallbackMultiChoice(labels.indices.toList().toTypedArray()) { _, indices, _ ->
                indices.filterNotNull().sorted().takeIf { it.isNotEmpty() }?.let { chosen ->
                    importLibraries(chosen.map { document.libraries[it].library }, fileName)
                }
                true
            }
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .show()
    }

    private fun importLibraries(libraries: List<ColorLibraryJson.Library>, fileName: String?) {
        val presetNames = reservedNames(activity)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    libraries.forEach { importLibrary(repository, it, fileName, presetNames) }
                    libraries.size
                }
            }
            result.onSuccess { count ->
                ViewUtils.showToast(activity, activity.resources.getQuantityString(R.plurals.text_n_color_libraries_imported, count, count))
                onImported()
            }.onFailure {
                ViewUtils.showToast(activity, R.string.text_failed_to_import, true)
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun messageOf(e: ColorLibraryJsonException): String = when (e.reason) {
        Reason.TOO_LARGE -> activity.getString(R.string.error_color_library_file_too_large, "${ColorLibraryJson.MAX_FILE_BYTES / 1024 / 1024} MiB")
        Reason.MALFORMED -> activity.getString(R.string.error_color_library_file_malformed)
        Reason.NOT_A_COLOR_LIBRARY -> activity.getString(R.string.error_not_a_color_library_file)
        Reason.NEWER_SCHEMA -> activity.getString(R.string.error_color_library_file_newer_schema, e.detail.orEmpty())
        Reason.NO_LIBRARIES -> activity.getString(R.string.error_color_library_file_no_libraries)
        Reason.TOO_MANY_LIBRARIES -> activity.getString(R.string.error_color_library_file_too_many_libraries, ColorLibraryJson.MAX_LIBRARIES)
        Reason.TOO_MANY_ITEMS -> activity.getString(R.string.error_color_library_too_many_items, e.detail.orEmpty(), ColorLibraryJson.MAX_ITEMS_PER_LIBRARY)
    }

    private fun defaultFileName() = "autojs6-colors-${LocalDateTime.now().format(FILE_NAME_TIME)}.json"

    companion object {

        private const val PREFIX_PRESET_PLAIN = "preset:"
        private const val PREFIX_PRESET_INTELLIGENT = "intelligent-preset:"
        private const val PREFIX_USER = "user:"

        private const val SAVED_STATE_KEY = "org.autojs.autojs.theme.app.ColorLibraryExchange"
        private const val SAVED_STATE_SOURCES = "sources"

        private val FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

        /** Some providers report JSON files as text or as generic binary. zh-CN: 部分文件提供方将 JSON 文件报告为文本或通用二进制类型. */
        private val IMPORT_MIME_TYPES = arrayOf(ColorLibraryJson.MIME_TYPE, "text/plain", "application/octet-stream")

        /** Reads at most [limit] bytes (`InputStream.readNBytes` needs API 33). zh-CN: 至多读取 [limit] 字节 (`InputStream.readNBytes` 需要 API 33). */
        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (output.size() < limit) {
                val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (read < 0) break
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        /**
         * The names imported libraries never take: those of the presets, such as an exported preset snapshot would
         * have ([importLibrary]).
         * zh-CN: 导入的库不使用的名称: 预设库的名称 (例如导出的预设快照会带有) ([importLibrary]).
         */
        @JvmStatic
        fun reservedNames(context: Context): List<String> =
            ColorLibrariesActivity.presetColorLibraries.map { context.getString(it.nameRes) } + IntelligentPresets.all(context).map { it.name }

        /**
         * Stores [library] as a new imported user library with the origin [fileName], its name made unique among the
         * libraries and [reservedNames], the groups of an intelligent library with new ids; returns its row id. The
         * import of this page and the settings import wizard (docs/settings/ROADMAP.md P10.2) use it.
         * zh-CN: 把 [library] 保存为新的导入用户库, 来源为 [fileName], 名称在现有库名与 [reservedNames] 中去重, 智能库的组使用新的 id;
         * 返回其行 id. 本页面的导入与设置导入向导 (ROADMAP P10.2) 使用它.
         */
        suspend fun importLibrary(repository: ColorLibraryRepository, library: ColorLibraryJson.Library, fileName: String?, reservedNames: Collection<String>): Long =
            when (library) {
                is ColorLibraryJson.PlainLibrary -> repository.createPlainLibrary(
                    library.name,
                    ColorLibrary.Kind.IMPORTED,
                    origin = fileName,
                    colors = library.colors.map { ColorLibraryRepository.NewColor(it.color, it.name) },
                    reservedNames = reservedNames,
                ).id
                is ColorLibraryJson.IntelligentLibrary -> {
                    // Decoded groups use their file index as id; new groups need temporary negative ids.
                    // zh-CN: 解码后的组以文件下标为 id; 新组需要临时的负数 id.
                    val groups = library.groups.mapIndexed { index, group -> group.copy(id = -(index + 1L)) }
                    val config = library.config.remapGroupIds(library.groups.zip(groups).associate { (old, new) -> old.id to new.id })
                    val saved = repository.saveIntelligent(null, library.name, config, groups, ColorLibrary.Kind.IMPORTED, origin = fileName, reservedNames = reservedNames)
                    requireNotNull(ColorLibraryIds.userRowIdOf(saved.libraryId)) { "Not a user library: ${saved.libraryId}" }
                }
            }

    }

}
