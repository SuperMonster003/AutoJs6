package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.editor.AceEditorPluginHost
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.archive.AndroidSettingsAutoBackup
import org.autojs.autojs.core.pref.archive.AndroidSettingsEffects
import org.autojs.autojs.core.pref.archive.AndroidSettingsKeys
import org.autojs.autojs.core.pref.archive.ImportEnvironment
import org.autojs.autojs.core.pref.archive.ImportPlan
import org.autojs.autojs.core.pref.archive.SettingsArchive
import org.autojs.autojs.core.pref.archive.SettingsArchiveException
import org.autojs.autojs.core.pref.archive.SettingsArchiveManifest
import org.autojs.autojs.core.pref.archive.SettingsArchiveReader
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup
import org.autojs.autojs.core.pref.archive.SettingsBackupVault
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsEffects
import org.autojs.autojs.core.pref.archive.SettingsImportApplier
import org.autojs.autojs.core.pref.archive.SettingsImportPlanner
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFont
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontManager
import org.autojs.autojs.util.EnvironmentUtils
import org.autojs.autojs.util.WorkingDirectoryPathPolicy
import java.io.File
import java.io.IOException

/**
 * The work behind the import wizard and "Undo last import" (docs/settings/ROADMAP.md P7.5, T8 / T9 / T12): the chosen
 * document is copied to [CACHE_DIRECTORY] first (at most [SettingsArchive.MAX_BYTES], so that every later read is a
 * plain file and survives the recreation of the page), its manifest is inspected for step 1 ([inspect]: the warnings
 * of D9 / appendix C Q26, the categories sealed for another device, D23, and those unknown to this version), and the
 * import itself ([importArchive]) plans the selected categories (plus the unknown ones, which only count as
 * `NEWER_FORMAT`), backs up every category to the internal vault, writes the plan, records it and runs the effect
 * table. [undo] imports the touched categories again from that backup. Values are never logged (R6).
 *
 * zh-CN: 导入向导与 "撤销上次导入" 的实际工作 (ROADMAP P7.5, T8 / T9 / T12): 所选文档先复制到 [CACHE_DIRECTORY] (不超过
 * [SettingsArchive.MAX_BYTES], 之后的每次读取都是普通文件, 页面重建后仍可用), 第 1 步检查其 manifest ([inspect]: D9 / 附录 C Q26
 * 的警告, 以其他设备密钥加密的分类 (D23), 本版本不认识的分类); 导入本身 ([importArchive]) 为勾选的分类 (另加不认识的分类, 只计为
 * `NEWER_FORMAT`) 生成计划, 把全部分类备份到内部备份库, 写入计划, 记录本次操作并执行生效表. [undo] 以该备份再次导入触及的分类.
 * 不记录任何值 (R6).
 */
object SettingsImporter {

    /** The directory under `cacheDir` that holds the copy of the chosen file. zh-CN: `cacheDir` 下存放所选文件副本的目录. */
    const val CACHE_DIRECTORY = "settings-import"

    private const val CACHE_FILE = "import.zip"

    /**
     * What step 1 shows of a settings file: its manifest and [warnings], the sealed categories this device cannot open
     * ([locked], D23) and the categories unknown to this version ([unknown]). Pure data (R3).
     * zh-CN: 第 1 步显示的设置文件信息: manifest 与 [warnings], 本机无法打开的加密分类 ([locked], D23), 本版本不认识的分类 ([unknown]).
     */
    data class Inspection(
        val fileName: String,
        val manifest: SettingsArchiveManifest,
        val warnings: Set<SettingsArchiveManifest.Warning>,
        val locked: List<String>,
        val unknown: List<String>,
    ) {
        val choices: List<SettingsCategoryChoice>
            get() = SettingsCategoryChoices.import(manifest, locked)
    }

    /**
     * Inspects the archive of [reader] (P7.5 step 1); throws [SettingsArchiveException] for a file that is no settings
     * archive. Pure JVM (R3).
     * zh-CN: 检查 [reader] 的归档 (P7.5 第 1 步); 不是设置归档时抛 [SettingsArchiveException]. 纯 JVM (R3).
     */
    @JvmStatic
    fun inspect(reader: SettingsArchiveReader, fileName: String, hostPackageName: String): Inspection {
        val manifest = reader.manifest()
        val locked = manifest.categories.filter { it.sensitive && reader.isLocked(it.id) }.map { it.id }
        val unknown = manifest.categories.map { it.id }.filter { SettingCategory.ofIdOrNull(it) == null }
        return Inspection(fileName, manifest, manifest.warnings(hostPackageName), locked, unknown)
    }

    /**
     * The plan of importing [selected] from the archive of [reader] (T8): the selected categories that can be ticked
     * and the categories unknown to this version (counted as `NEWER_FORMAT`), with the plugin directories of the
     * manifest (P13.1). Pure JVM (R3).
     * zh-CN: 从 [reader] 的归档导入 [selected] 的计划 (T8): 可勾选的已选分类加上本版本不认识的分类 (计为 `NEWER_FORMAT`), 以及
     * manifest 中的插件目录 (P13.1). 纯 JVM (R3).
     */
    @JvmStatic
    fun plan(reader: SettingsArchiveReader, inspection: Inspection, selected: List<String>, environment: ImportEnvironment): ImportPlan {
        val ids = SettingsCategorySelection.normalize(selected, SettingsCategoryChoices.selectable(inspection.choices)) + inspection.unknown
        return SettingsImportPlanner.plan(reader.categories(ids, inspection.manifest), ids, environment, plugins = inspection.manifest.plugins)
    }

    /**
     * Copies the document [uri] to the cache (replacing an earlier copy) and returns the copy with the display name
     * of the document; [SettingsArchiveException.Reason.TOO_LARGE] beyond [SettingsArchive.MAX_BYTES]. Runs on a
     * background thread.
     * zh-CN: 把文档 [uri] 复制到缓存 (替换之前的副本), 返回副本与文档的显示名称; 超过 [SettingsArchive.MAX_BYTES] 时为 `TOO_LARGE`.
     * 在后台线程调用.
     */
    @JvmStatic
    fun copyToCache(context: Context, uri: Uri): Pair<File, String> {
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        directory.deleteRecursively()
        if (!directory.mkdirs()) throw IOException("Cannot create $directory")
        val file = File(directory, CACHE_FILE)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream for $uri")
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > SettingsArchive.MAX_BYTES) {
                            throw SettingsArchiveException(SettingsArchiveException.Reason.TOO_LARGE, "The settings file exceeds ${SettingsArchive.MAX_BYTES} bytes")
                        }
                        target.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
        return file to (SettingsExporter.displayName(context, uri) ?: uri.lastPathSegment.orEmpty())
    }

    /** Deletes the copy of the chosen file. zh-CN: 删除所选文件的副本. */
    @JvmStatic
    fun clearCache(context: Context) {
        File(context.cacheDir, CACHE_DIRECTORY).deleteRecursively()
    }

    /** [inspect] of the copy [state] holds; runs on a background thread. zh-CN: 检查 [state] 中的副本; 在后台线程调用. */
    @JvmStatic
    fun inspect(context: Context, state: SettingsWizardState): Inspection =
        inspect(reader(context, archiveOf(state)), state.fileName.orEmpty(), context.packageName)

    /**
     * Imports [SettingsWizardState.selected] from the copy [state] holds (P7.5 step 4): plans on a background thread,
     * backs up every category to the internal vault (T9), writes the plan (T8, one commit per store), records the
     * operation, then runs the effect table on the calling thread, the main thread (T12). The deployment items of the
     * plan are given up until P7.6 asks for them.
     * zh-CN: 从 [state] 中的副本导入所选分类 (P7.5 第 4 步): 在后台线程生成计划, 把全部分类备份到内部备份库 (T9), 写入计划 (T8, 每个
     * 存储提交一次), 记录本次操作, 然后在调用线程 (主线程) 执行生效表 (T12). 在 P7.6 询问之前, 计划中的部署项一律放弃.
     */
    suspend fun importArchive(context: Context, state: SettingsWizardState): SettingsTransferOutcome {
        val app = context.applicationContext
        val (plan, changes, locked) = withContext(Dispatchers.IO) {
            val reader = reader(app, archiveOf(state))
            val inspection = inspect(reader, state.fileName.orEmpty(), app.packageName)
            val planned = plan(reader, inspection, state.selected, environment(app))
            val plan = planned.decline(planned.deployments)
            val vault = vault(app)
            val backup = vault.backup(SettingsBackupVault.Operation.IMPORT, SettingsSnapshot.capture(SettingCategory.ALL), SettingsExporter.host(app), SettingsExporter.device())
            val changes = SettingsImportApplier.write(plan)
            val categories = plan.categories.filter { SettingCategory.ofIdOrNull(it) != null }
            vault.record(SettingsBackupVault.LastOperation(SettingsBackupVault.Operation.IMPORT, System.currentTimeMillis(), backup.name, categories))
            Triple(plan, changes, inspection.locked.isNotEmpty())
        }
        val report = SettingsEffects.run(changes, actions(app))
        val backupFailure = afterOperation(app)
        return SettingsTransferOutcome.of(plan, changes, report, credentialsLocked = locked, backupFailure = backupFailure, launcherIconPending = launcherIconPending(report))
    }

    /**
     * Undoes the last import (T9): imports its touched categories again from the backup taken before it, forgets it,
     * then runs the effect table on the calling thread, the main thread.
     * zh-CN: 撤销上次导入 (T9): 以导入前的备份再次导入触及的分类并忘记该操作, 然后在调用线程 (主线程) 执行生效表.
     */
    suspend fun undo(context: Context): SettingsTransferOutcome {
        val app = context.applicationContext
        val (undo, changes) = withContext(Dispatchers.IO) {
            val vault = vault(app)
            val undo = vault.undoPlan(environment(app)) ?: throw IOException("There is no import to undo")
            val changes = SettingsImportApplier.write(undo.plan)
            vault.clearLastOperation()
            undo to changes
        }
        val report = SettingsEffects.run(changes, actions(app))
        val backupFailure = afterOperation(app)
        return SettingsTransferOutcome.of(undo.plan, changes, report, dataNotUndone = undo.dataNotUndone, backupFailure = backupFailure, launcherIconPending = launcherIconPending(report))
    }

    /**
     * The operation "Undo last import" undoes, or null when there is none (its backup is gone); runs on a background
     * thread.
     * zh-CN: "撤销上次导入" 将撤销的操作, 没有 (或其备份已不在) 时为 null; 在后台线程调用.
     */
    @JvmStatic
    fun lastOperation(context: Context): SettingsBackupVault.LastOperation? =
        vault(context.applicationContext).takeIf { it.canUndo() }?.lastOperation()

    /**
     * The entries of the effect table (T12), except the launcher icon: switching the launcher component disables the
     * alias the task of the page was started from, and the system then closes the task, result page included. The
     * result page switches it once it is closed ([SettingsTransferOutcome.launcherIconPending]).
     * zh-CN: 生效表的入口 (T12), 启动图标除外: 切换启动组件会禁用本页任务所用的别名, 系统随即关闭该任务 (结果页也随之消失). 由结果页在
     * 关闭后切换 ([SettingsTransferOutcome.launcherIconPending]).
     */
    private fun actions(context: Context): (SettingsEffects.Entry) -> SettingsEffects.Action {
        val effects = AndroidSettingsEffects(context)
        return { entry -> if (entry == SettingsEffects.Entry.LAUNCHER_ICON) SettingsEffects.Action {} else effects.actionOf(entry) }
    }

    private fun launcherIconPending(report: SettingsEffects.Report) = SettingsEffects.Entry.LAUNCHER_ICON in report.applied

    /**
     * The operation backup (T20, P9.1) after an import or an undo, before the result page shows; returns why it failed,
     * or null. A failure does not block: the result page tells it.
     * zh-CN: 导入或撤销之后, 结果页显示之前的操作后备份 (T20, P9.1); 返回失败原因, 成功或关闭时为 null. 失败不阻断, 由结果页提示.
     */
    private suspend fun afterOperation(context: Context): String? = withContext(Dispatchers.IO) {
        when (val outcome = AndroidSettingsAutoBackup.afterOperation(context)) {
            is SettingsAutoBackup.Outcome.Failed -> outcome.error.message ?: outcome.error.javaClass.simpleName
            else -> null
        }
    }

    /**
     * What an import plan needs to know about this device (T8): the installed packages, whether the Ace editor plugin
     * is installed, whether a working directory exists and whether an editor font is installed (bundled or
     * downloaded). Runs on a background thread.
     * zh-CN: 导入计划需要的本机情况 (T8): 已安装的包, Ace 编辑器插件是否已安装, 工作目录是否存在, 编辑器字体是否已安装 (内置或已下载).
     * 在后台线程调用.
     */
    @JvmStatic
    fun environment(context: Context): ImportEnvironment {
        val fonts by lazy { AceEditorFontManager.get(context).store }
        return ImportEnvironment(installedPackages(context), AceEditorPluginHost.isAvailable(context)) { kind, value ->
            val text = value as? String
            when (kind) {
                DeploymentKind.CREATE_DIRECTORY -> text != null &&
                        File(WorkingDirectoryPathPolicy.resolveStored(text, EnvironmentUtils.externalStoragePath)).isDirectory
                DeploymentKind.DOWNLOAD_EDITOR_FONT -> text != null &&
                        (AceEditorFont.entries.any { it.preferenceValue == text } || fonts.installedFont(text) != null)
            }
        }
    }

    private fun installedPackages(context: Context): Set<String> {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledPackages(0)
        }
        return packages.mapTo(HashSet()) { it.packageName }
    }

    private fun deviceKey(context: Context): SettingsDeviceKey? = try {
        AndroidSettingsKeys.device(context)
    } catch (_: IOException) {
        null
    }

    private fun reader(context: Context, file: File) = SettingsArchiveReader({ file.inputStream() }, key = deviceKey(context))

    private fun vault(context: Context) = SettingsBackupVault(File(context.filesDir, SettingsBackupVault.DIRECTORY), key = deviceKey(context))

    private fun archiveOf(state: SettingsWizardState): File =
        state.archive?.let(::File)?.takeIf { it.isFile } ?: throw IOException("The copy of the settings file is gone")
}
