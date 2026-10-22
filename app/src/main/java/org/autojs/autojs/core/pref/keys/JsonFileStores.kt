package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The JSON files that the host keeps under `filesDir` and `noBackupFilesDir` (docs/settings/ROADMAP.md P5.1, D18 /
 * T17). None of them holds a user setting: each one is a cache or a record of this device (appendix A.2), so each is
 * registered as an excluded [StoreId.Backend.JSON_FILE] store, only for guard (b) of T5 to know it. It holds no
 * declared keys and is never exported, imported or reset; its owner keeps reading and writing it as before, in its own
 * format and place (R1), with the JSON codec of [org.autojs.autojs.core.pref.SettingsJson].
 *
 * A name is the path relative to `filesDir`, `../no_backup/` for `noBackupFilesDir`, with `*` for the parts built at
 * run time. `tools/settings/classification.json` (`jsonFileStores`) ties every source file that names such a file to
 * its stores, and the guard checks both against the sources.
 *
 * zh-CN: 宿主在 `filesDir` 与 `noBackupFilesDir` 下保存的 JSON 文件 (ROADMAP P5.1, D18 / T17). 其中没有用户设置: 每个都是缓存
 * 或本机记录 (附录 A.2), 因此都登记为排除的 [StoreId.Backend.JSON_FILE] 存储, 只为让 T5 守卫 (b) 认识它们. 它们不含键声明,
 * 不导出, 不导入, 不恢复默认; 读写方照旧以原格式与原位置读写 (R1), JSON 编解码经 `SettingsJson`. 名称为相对 `filesDir` 的路径,
 * `noBackupFilesDir` 下的以 `../no_backup/` 开头, `*` 代表运行时拼出的部分. `classification.json` 的 `jsonFileStores` 把每个
 * 提到这些文件的源文件对应到它的存储, 守卫以源码核对两者.
 */
object JsonFileStores {

    private fun excluded(path: String) = SettingsStores.register(StoreId(path, StoreId.Backend.JSON_FILE, excluded = true))

    // Caches: downloaded again or rebuilt when missing (fonts are downloaded again by the deployment of P7.6).
    // zh-CN: 缓存: 缺失时重新下载或重建 (字体由 P7.6 的部署项重新下载).

    /** The font catalog of the font dialog (`FontCatalogRepository`). */
    @JvmField
    val FONT_CATALOG = excluded("../no_backup/ace_fonts/catalog-v1.json")

    /** The signature of [FONT_CATALOG] (`FontCatalogRepository`). */
    @JvmField
    val FONT_CATALOG_SIGNATURE = excluded("../no_backup/ace_fonts/catalog-v1.sig.json")

    /** The metadata of one downloaded font, next to its font files (`FontStore`). */
    @JvmField
    val INSTALLED_FONTS = excluded("../no_backup/ace_fonts/fonts/*/installed.json")

    /**
     * The registry of downloaded layout code generation models and its backup `registry.json.bak` (`ModelStore`);
     * `*` is empty in release builds and `-debug-<profile>` in debug builds (`ModelCatalogConfiguration.modelDirectory`).
     */
    @JvmField
    val CODEGEN_MODEL_REGISTRY = excluded("../no_backup/codegen_models*/registry.json")

    /** The metadata of installed plugins (`InstalledPluginMetadataStore`). */
    @JvmField
    val INSTALLED_PLUGIN_METADATA = excluded("plugin_center/autojs6_installed_plugin_metadata_v1.json")

    /** The plugin index of the plugin center (`PluginIndexRepository`; older versions kept it in `cacheDir`). */
    @JvmField
    val PLUGIN_INDEX = excluded("plugin_center/autojs6_plugin_index_v11.json")

    /** The state of one download of a web runtime page (`RuntimeWebLocalDownloads`). */
    @JvmField
    val RUNTIME_WEB_DOWNLOADS = excluded("runtime-web-downloads/*.json")

    // Records of this device: run-time state that ends with its processes, or that points to files of this device.
    // zh-CN: 本机记录: 随进程结束而失效的运行时状态, 或指向本机文件的记录.

    /** The checkpoint of one Node.js project (`NodeLifecycleCheckpointStore`). */
    @JvmField
    val NODE_LIFECYCLE_CHECKPOINTS = excluded("nodejs/checkpoints/*.json")

    /** One task scheduled with WorkManager by a Node.js script (`NodeWorkManagerBridgeScheduler`). */
    @JvmField
    val NODE_WORK_MANAGER_TASKS = excluded("nodejs-work-manager/tasks/*.json")

    /** The host capabilities handed to one Python execution, a private temporary file (`PythonPrivateExecutionSnapshot`). */
    @JvmField
    val PYTHON_EXECUTION_SNAPSHOTS = excluded("../no_backup/python-runtime-preview/host-capabilities-*.json")

    /** One privileged edit session (`PrivilegedEditSessions`). */
    @JvmField
    val PRIVILEGED_EDIT_SESSIONS = excluded("privileged-edit-sessions/*/session.json")

    /** The journal entry of one privileged file operation in progress (`PrivilegedFileOperationJournal`). */
    @JvmField
    val PRIVILEGED_FILE_OPERATIONS = excluded("privileged-file-operations/*.json")

    /** The record of one backup made by a privileged save (`PrivilegedBackupManager`). */
    @JvmField
    val PRIVILEGED_EDIT_BACKUP_RECORDS = excluded("privileged-edit-backups/*.json")

    /** The last report of the Node.js doctor, written to be shared (`NodeDoctorReportGenerator`); not read back. */
    @JvmField
    val NODE_DOCTOR_REPORT = excluded("node-doctor/node-doctor-report.json")

    /** Every declared JSON file store, in declaration order. */
    @JvmField
    val ALL: List<StoreId> = listOf(
        FONT_CATALOG, FONT_CATALOG_SIGNATURE, INSTALLED_FONTS, CODEGEN_MODEL_REGISTRY, INSTALLED_PLUGIN_METADATA, PLUGIN_INDEX,
        RUNTIME_WEB_DOWNLOADS,
        NODE_LIFECYCLE_CHECKPOINTS, NODE_WORK_MANAGER_TASKS, PYTHON_EXECUTION_SNAPSHOTS, PRIVILEGED_EDIT_SESSIONS,
        PRIVILEGED_FILE_OPERATIONS, PRIVILEGED_EDIT_BACKUP_RECORDS, NODE_DOCTOR_REPORT,
    )
}
