package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalStore
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.EXPLORER
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > File manager (docs/settings/ROADMAP.md P2.2, appendix A.1 `explorer`): the working directory (an import
 * whose directory does not exist offers the "create working directory" deployment item, D20 / appendix C Q1), hidden
 * files, file extensions, the playback history switch, the default prefix of new files and the retention of batch
 * rename history (the readers of the former `BatchRenameHistoryPrefs`, T17).
 *
 * zh-CN: 设置 > 文件管理器 (ROADMAP P2.2, 附录 A.1 `explorer`): 工作目录 (导入时目录不存在则提出部署项 "创建工作目录", D20 / 附录 C
 * Q1), 隐藏文件, 文件扩展名, 回放历史开关, 新文件默认前缀, 批量重命名历史保留批数 (原 `BatchRenameHistoryPrefs` 的读取函数, T17).
 */
object ExplorerKeys : SettingsDomain {

    private val HIDDEN_FILES_VALUES = intArrayOf(
        R.string.key_hidden_files_not_show,
        R.string.key_hidden_files_show,
    )

    private val FILE_EXTENSIONS_VALUES = intArrayOf(
        R.string.key_file_extensions_not_show,
        R.string.key_file_extensions_show_all,
        R.string.key_file_extensions_show_all_but_executable,
    )

    /**
     * The stored working directory: a path relative to the external storage or an encoded absolute path
     * (`WorkingDirectoryPathPolicy`); the default is the directory name of the current language. On first launch
     * `WorkingDirectoryUtils.determineIfNeeded` picks the newest non-empty default directory.
     * zh-CN: 存储的工作目录: 相对外部存储的路径或编码后的绝对路径; 默认值为当前语言的目录名. 首次启动时由
     * `WorkingDirectoryUtils.determineIfNeeded` 选择最近修改的非空默认目录.
     */
    @JvmField
    val WORKING_DIRECTORY = SettingKey.Str(
        R.string.key_working_directory, EXPLORER,
        stringResource(R.string.default_value_working_directory, "default_value_working_directory"),
        deployment = DeploymentKind.CREATE_DIRECTORY,
    )

    @JvmField
    val HIDDEN_FILES = SettingKey.Enum(
        R.string.key_hidden_files, EXPLORER, keyChoices(*HIDDEN_FILES_VALUES),
        keyResource(R.string.default_key_hidden_files, "default_key_hidden_files"),
    )

    @JvmField
    val FILE_EXTENSIONS = SettingKey.Enum(
        R.string.key_file_extensions, EXPLORER, keyChoices(*FILE_EXTENSIONS_VALUES),
        keyResource(R.string.default_key_file_extensions, "default_key_file_extensions"),
    )

    @JvmField
    val EXPLORER_PLAYBACK_HISTORY = SettingKey.Bool(
        R.string.key_explorer_playback_history, EXPLORER,
        boolResource(R.bool.pref_explorer_playback_history, "pref_explorer_playback_history"),
    )

    /** Index of the default prefix in the new file dialog (`ScriptOperations`). */
    @JvmField
    val EXPLORER_FILE_DEFAULT_PREFIX = SettingKey.Int(R.string.key_explorer_file_default_prefix, EXPLORER, SettingDefault.of(0))

    /** Batches of rename history kept for scripts and for each plugin, one of `BatchRenameJournalStore.RETENTION_CHOICES`. */
    @JvmField
    val BATCH_RENAME_HISTORY_RETENTION = SettingKey.Int(R.string.key_batch_rename_history_retention, EXPLORER, SettingDefault.of(BatchRenameJournalStore.MAX_BATCHES))

    // The sort order of the file manager, named by the class `ExplorerItemManager.SortConfig` (P4.2, appendix C Q18);
    // sort types are the `ExplorerItemManager.SORT_TYPE_*` values (0x10 = by name).
    // zh-CN: 文件管理器的排序方式, 以类 `ExplorerItemManager.SortConfig` 命名 (P4.2, 附录 C Q18); 排序类型为 `ExplorerItemManager.SORT_TYPE_*` 的值 (0x10 = 按名称).

    @JvmField
    val EXPLORER_FILE_SORT_TYPE = SettingKey.Int(name = "org.autojs.autojs.ui.viewmodel.ExplorerItemManager\$SortConfig.file_sort_type", category = EXPLORER, default = SettingDefault.of(0x10))

    @JvmField
    val EXPLORER_DIR_SORT_TYPE = SettingKey.Int(name = "org.autojs.autojs.ui.viewmodel.ExplorerItemManager\$SortConfig.dir_sort_type", category = EXPLORER, default = SettingDefault.of(0x10))

    @JvmField
    val EXPLORER_FILE_ASCENDING = SettingKey.Bool(name = "org.autojs.autojs.ui.viewmodel.ExplorerItemManager\$SortConfig.file_ascending", category = EXPLORER, default = SettingDefault.of(true))

    @JvmField
    val EXPLORER_DIR_ASCENDING = SettingKey.Bool(name = "org.autojs.autojs.ui.viewmodel.ExplorerItemManager\$SortConfig.dir_ascending", category = EXPLORER, default = SettingDefault.of(true))

    override val keys: List<PrefKey<*>> = listOf(
        WORKING_DIRECTORY, HIDDEN_FILES, FILE_EXTENSIONS, EXPLORER_PLAYBACK_HISTORY, EXPLORER_FILE_DEFAULT_PREFIX, BATCH_RENAME_HISTORY_RETENTION,
        EXPLORER_FILE_SORT_TYPE, EXPLORER_DIR_SORT_TYPE, EXPLORER_FILE_ASCENDING, EXPLORER_DIR_ASCENDING,
    )

    override val names: List<RegisteredName> = valueConstants(*HIDDEN_FILES_VALUES, *FILE_EXTENSIONS_VALUES) +
            // Entries of the page that hold no value.
            excludedNames(R.string.key_change_working_dir, R.string.key_clear_explorer_playback_history)

    /** The former `Pref.isHiddenFilesShown`: only the "show" key shows hidden files (R2). */
    @JvmStatic
    val isHiddenFilesShown: Boolean
        get() = HIDDEN_FILES.storedKeyOrDefault() == keyOf(R.string.key_hidden_files_show)

    @JvmStatic
    val isFileExtensionsShownForAll: Boolean
        get() = FILE_EXTENSIONS.storedKeyOrDefault() == keyOf(R.string.key_file_extensions_show_all)

    @JvmStatic
    val isFileExtensionsShownForAllButExecutable: Boolean
        get() = FILE_EXTENSIONS.storedKeyOrDefault() == keyOf(R.string.key_file_extensions_show_all_but_executable)

    @JvmStatic
    val isFileExtensionsHidden: Boolean
        get() = FILE_EXTENSIONS.storedKeyOrDefault() == keyOf(R.string.key_file_extensions_not_show)

    /**
     * The batch rename retention, falling back to the default for a value that is no choice (as the former
     * `BatchRenameHistoryPrefs` did).
     * zh-CN: 批量重命名保留批数, 不在可选值中时回退为默认值.
     */
    @JvmStatic
    fun batchRenameHistoryRetention(): Int = BATCH_RENAME_HISTORY_RETENTION.get()
        .takeIf { it in BatchRenameJournalStore.RETENTION_CHOICES } ?: BatchRenameJournalStore.MAX_BATCHES

    @JvmStatic
    fun setBatchRenameHistoryRetention(value: Int) {
        require(value in BatchRenameJournalStore.RETENTION_CHOICES) { "Unsupported retention $value" }
        BATCH_RENAME_HISTORY_RETENTION.set(value)
    }
}
