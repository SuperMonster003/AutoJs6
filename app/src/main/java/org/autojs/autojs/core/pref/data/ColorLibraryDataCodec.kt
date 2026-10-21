package org.autojs.autojs.core.pref.data

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.theme.library.ColorLibraryJson
import org.autojs.autojs.theme.library.ColorLibraryJson.ColorLibraryJsonException
import java.time.ZoneId

/**
 * The data category `data_color_library` of a settings archive (docs/settings/ROADMAP.md D21 / T19):
 * `data/color-library.json` is the [ColorLibraryJson] document the page "Color libraries" exports, encoded and read the
 * way `ColorLibraryExchange` does (UTF-8, at most [ColorLibraryJson.MAX_FILE_BYTES], a byte order mark ignored), so that
 * for the same libraries and time both are equal byte for byte. The manifest's `data` lists the number of libraries.
 * Which libraries are exported (user and intelligent ones, not the presets) and the import with its group id remapping
 * (`ColorLibraryExchange.importLibraries`) are left to the detail pages of the wizards (P10.2). Pure JVM (R3).
 *
 * zh-CN: 设置归档的数据分类 `data_color_library` (ROADMAP D21 / T19): `data/color-library.json` 即 "颜色库" 页面导出的
 * [ColorLibraryJson] 文档, 编码与读取方式与 `ColorLibraryExchange` 相同 (UTF-8, 不超过 [ColorLibraryJson.MAX_FILE_BYTES], 忽略字节
 * 顺序标记), 因此同样的库与时间下两者逐字节相同. manifest 的 `data` 记录库的个数. 导出哪些库 (用户库与智能库, 不含预设库) 以及导入时的
 * 分组 id 重映射 (`ColorLibraryExchange.importLibraries`) 由向导的细化页处理 (P10.2). 纯 JVM (R3).
 */
object ColorLibraryDataCodec {

    @JvmField
    val CATEGORY: SettingCategory = SettingCategory.DATA_COLOR_LIBRARY

    /** The file of the category under `data/`. zh-CN: 该分类在 `data/` 下的文件名. */
    const val FILE_NAME = "color-library.json"

    private val BYTE_ORDER_MARK = Char(0xFEFF).toString()

    /**
     * The data file of [libraries] for [SettingsArchiveWriter.write]; [exportedAt] is the export time of the archive.
     * Throws [ColorLibraryJsonException] when a limit of [ColorLibraryJson] is exceeded.
     * zh-CN: [libraries] 对应的数据文件, 交给 [SettingsArchiveWriter.write]; [exportedAt] 为归档的导出时间. 超出 [ColorLibraryJson] 的
     * 限额时抛 [ColorLibraryJsonException].
     */
    @JvmStatic
    @JvmOverloads
    fun encode(
        libraries: List<ColorLibraryJson.Library>,
        exportedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        app: ColorLibraryJson.AppInfo? = null,
    ): SettingsArchiveWriter.DataFile {
        val text = ColorLibraryJson.encode(libraries, exportedAt, zone, app)
        return SettingsArchiveWriter.DataFile(CATEGORY.id, libraries.size, FILE_NAME, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * The document of `data/color-library.json` (or of a file the page exported); blank library names become
     * [fallbackName]. Throws [ColorLibraryJsonException] like the import of the page, whose messages its reasons have.
     * zh-CN: `data/color-library.json` (或页面导出的文件) 中的文档; 空白的库名替换为 [fallbackName]. 与页面的导入一样抛
     * [ColorLibraryJsonException], 其原因各有页面已有的提示.
     */
    @JvmStatic
    @JvmOverloads
    fun decode(bytes: ByteArray, fallbackName: String = ""): ColorLibraryJson.Document {
        if (bytes.size > ColorLibraryJson.MAX_FILE_BYTES) throw ColorLibraryJsonException(ColorLibraryJson.Reason.TOO_LARGE)
        return ColorLibraryJson.decode(String(bytes, Charsets.UTF_8).removePrefix(BYTE_ORDER_MARK), fallbackName)
    }
}
