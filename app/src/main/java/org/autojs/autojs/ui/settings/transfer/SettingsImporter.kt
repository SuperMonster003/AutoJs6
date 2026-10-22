package org.autojs.autojs.ui.settings.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.autojs.autojs.core.pref.archive.SettingsArchiveWriter
import org.autojs.autojs.core.pref.archive.SettingsAutoBackup
import org.autojs.autojs.core.pref.archive.SettingsBackupVault
import org.autojs.autojs.core.pref.archive.SettingsDeviceKey
import org.autojs.autojs.core.pref.archive.SettingsEffects
import org.autojs.autojs.core.pref.archive.SettingsImportApplier
import org.autojs.autojs.core.pref.archive.SettingsImportPlanner
import org.autojs.autojs.core.pref.archive.SettingsPassword
import org.autojs.autojs.core.pref.archive.SettingsResetPlanner
import org.autojs.autojs.core.pref.archive.SettingsSealedFile
import org.autojs.autojs.core.pref.archive.SettingsSnapshot
import org.autojs.autojs.core.pref.data.SettingsDataCapture
import org.autojs.autojs.core.pref.data.SettingsDataEntries
import org.autojs.autojs.core.pref.data.SettingsDataImport
import org.autojs.autojs.core.pref.deploy.AndroidDeployments
import org.autojs.autojs.core.pref.deploy.DeploymentException
import org.autojs.autojs.core.pref.deploy.DeploymentItem
import org.autojs.autojs.core.pref.deploy.DeploymentItems
import org.autojs.autojs.core.pref.deploy.DeploymentPlanner
import org.autojs.autojs.core.pref.deploy.DeploymentResult
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import java.io.File
import java.io.IOException

/**
 * The work behind the import wizard and "Undo last import" (docs/settings/ROADMAP.md P7.5, T8 / T9 / T12): the chosen
 * document is copied to [CACHE_DIRECTORY] first (at most [SettingsArchive.MAX_BYTES], so that every later read is a
 * plain file and survives the recreation of the page), its manifest is inspected for step 1 ([inspect]: the warnings
 * of D9 / appendix C Q26, the categories sealed for another device, D23, and those unknown to this version), and the
 * import itself ([importArchive]) plans the selected categories (plus the unknown ones, which only count as
 * `NEWER_FORMAT`), backs up every category to the internal vault, writes the plan, imports the chosen entries of the
 * selected data categories (P10.2, [SettingsDataImport]), records it and runs the effect table. [undo] imports the
 * touched categories again from that backup. Values are never logged (R6).
 *
 * zh-CN: 导入向导与 "撤销上次导入" 的实际工作 (ROADMAP P7.5, T8 / T9 / T12): 所选文档先复制到 [CACHE_DIRECTORY] (不超过
 * [SettingsArchive.MAX_BYTES], 之后的每次读取都是普通文件, 页面重建后仍可用), 第 1 步检查其 manifest ([inspect]: D9 / 附录 C Q26
 * 的警告, 以其他设备密钥加密的分类 (D23), 本版本不认识的分类); 导入本身 ([importArchive]) 为勾选的分类 (另加不认识的分类, 只计为
 * `NEWER_FORMAT`) 生成计划, 把全部分类备份到内部备份库, 写入计划, 导入所选数据分类中选中的条目 (P10.2, [SettingsDataImport]), 记录本次
 * 操作并执行生效表. [undo] 以该备份再次导入触及的分类.
 * 不记录任何值 (R6).
 */
object SettingsImporter {

    /** The directory under `cacheDir` that holds the copy of the chosen file. zh-CN: `cacheDir` 下存放所选文件副本的目录. */
    const val CACHE_DIRECTORY = "settings-import"

    private const val CACHE_FILE = "import.zip"

    /** A deployment item of step 3 and what it shows. zh-CN: 第 3 步的一个部署项及其显示内容. */
    data class PendingDeployment(val pending: DeploymentPlanner.Pending, val description: DeploymentItem.Description)

    /**
     * What step 1 shows of a settings file: its manifest and [warnings], the sealed categories this device cannot open
     * now ([locked], D23), those of them a password opens ([password], P7.7) and the categories unknown to this version
     * ([unknown]). Pure data (R3).
     * zh-CN: 第 1 步显示的设置文件信息: manifest 与 [warnings], 本机当前无法打开的加密分类 ([locked], D23), 其中可用口令打开的分类
     * ([password], P7.7), 本版本不认识的分类 ([unknown]).
     */
    data class Inspection @JvmOverloads constructor(
        val fileName: String,
        val manifest: SettingsArchiveManifest,
        val warnings: Set<SettingsArchiveManifest.Warning>,
        val locked: List<String>,
        val unknown: List<String>,
        val password: List<String> = emptyList(),
    ) {
        val choices: List<SettingsCategoryChoice>
            get() = SettingsCategoryChoices.import(manifest, locked, password = password)
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
        val password = locked.filter { reader.access(it) == SettingsSealedFile.Access.PASSWORD }
        val unknown = manifest.categories.map { it.id }.filter { SettingCategory.ofIdOrNull(it) == null }
        return Inspection(fileName, manifest, manifest.warnings(hostPackageName), locked, unknown, password)
    }

    /**
     * The plan of importing [selected] from the archive of [reader] (T8): the selected settings categories that can be
     * ticked and the categories unknown to this version (counted as `NEWER_FORMAT`), with the plugin directories of the
     * manifest (P13.1); the data categories are imported apart ([dataFiles]). Pure JVM (R3).
     * zh-CN: 从 [reader] 的归档导入 [selected] 的计划 (T8): 可勾选的已选设置分类加上本版本不认识的分类 (计为 `NEWER_FORMAT`), 以及
     * manifest 中的插件目录 (P13.1); 数据分类另行导入 ([dataFiles]). 纯 JVM (R3).
     */
    @JvmStatic
    fun plan(reader: SettingsArchiveReader, inspection: Inspection, selected: List<String>, environment: ImportEnvironment): ImportPlan {
        val ids = SettingsCategorySelection.normalize(selected, SettingsCategoryChoices.selectable(inspection.choices).filterNot { it.isData }) + inspection.unknown
        return SettingsImportPlanner.plan(reader.categories(ids, inspection.manifest), ids, environment, plugins = inspection.manifest.plugins)
    }

    /**
     * The data files of the selected data categories of the archive of [reader], each with only the entries [refine]
     * chose for it (all of them without a choice, P10.2); a category left without entries is not imported. [now] is
     * the time the files are written again with. Pure JVM (R3).
     * zh-CN: [reader] 归档中所选数据分类的数据文件, 每个只含 [refine] 为其选中的条目 (没有选择时为全部, P10.2); 没有剩下条目的分类不导入.
     * [now] 为重新写出文件所用的时间. 纯 JVM (R3).
     */
    @JvmStatic
    @JvmOverloads
    fun dataFiles(
        reader: SettingsArchiveReader,
        inspection: Inspection,
        selected: List<String>,
        refine: Map<String, List<String>> = emptyMap(),
        now: Long = System.currentTimeMillis(),
    ): List<SettingsArchiveWriter.DataFile> {
        val ids = SettingsCategorySelection.normalize(selected, SettingsCategoryChoices.selectable(inspection.choices).filter { it.isData })
        return inspection.manifest.data.filter { it.id in ids }.mapNotNull { entry ->
            val file = SettingsArchiveWriter.DataFile(entry.id, entry.entries, entry.file.substringAfterLast('/'), reader.data(entry.file))
            SettingsDataEntries.select(file, refine[entry.id], now)
        }
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
        inspect(reader(context, state), state.fileName.orEmpty(), context.packageName)

    /**
     * The entries of the data category [id] of the copy [state] holds, for its detail page (P10.2); empty when the
     * file has none. Runs on a background thread.
     * zh-CN: [state] 中副本的数据分类 [id] 的条目, 供其细化页使用 (P10.2); 文件中没有该分类时为空. 在后台线程调用.
     */
    @JvmStatic
    fun dataEntries(context: Context, state: SettingsWizardState, id: String): List<SettingsDataEntries.Entry> {
        val reader = reader(context, state)
        val entry = reader.manifest().data.firstOrNull { it.id == id } ?: return emptyList()
        return SettingsDataEntries.entries(id, reader.data(entry.file))
    }

    /**
     * True when [password] opens the sealed category [id] of the copy [state] holds (P7.7,
     * [SettingsArchiveReader.checkPassword]); derives the slot key, so it runs on a background thread.
     * zh-CN: [password] 能打开 [state] 中副本的加密分类 [id] 时为 true (P7.7); 需要派生槽密钥, 因此在后台线程调用.
     */
    @JvmStatic
    fun checkPassword(context: Context, state: SettingsWizardState, id: String, password: SettingsPassword): Boolean =
        reader(context, archiveOf(state)).checkPassword(id, password)

    /**
     * Imports [SettingsWizardState.selected] from the copy [state] holds (P7.5 step 4): plans on a background thread,
     * backs up every category to the internal vault (T9; the data categories to import as well, P10.3), writes the plan
     * (T8, one commit per store), imports the chosen data entries (P10.2; a data category that fails does not stop the
     * others), records the operation with the entries it added (P10.3), then runs the
     * effect table on the calling thread, the main thread (T12), and the deployment items the user confirmed
     * ([SettingsWizardState.deploy], T18, P7.6); the others are given up.
     * zh-CN: 从 [state] 中的副本导入所选分类 (P7.5 第 4 步): 在后台线程生成计划, 把全部分类备份到内部备份库 (T9; 要导入的数据分类也备份,
     * P10.3), 写入计划 (T8, 每个存储提交一次), 导入选中的数据条目 (P10.2; 某个数据分类失败不影响其他分类), 连同新增的条目记录本次操作
     * (P10.3), 然后在调用线程 (主线程) 执行生效表 (T12),
     * 再运行用户确认的部署项 ([SettingsWizardState.deploy], T18, P7.6); 其余部署项放弃.
     */
    suspend fun importArchive(context: Context, state: SettingsWizardState): SettingsTransferOutcome {
        val app = context.applicationContext
        val device = AndroidDeployments(app)
        val imported = withContext(Dispatchers.IO) {
            val reader = reader(app, state)
            val inspection = inspect(reader, state.fileName.orEmpty(), app.packageName)
            val environment = device.environment()
            val plan = DeploymentPlanner.decide(plan(reader, inspection, state.selected, environment), state.deploy.orEmpty(), environment)
            val files = dataFiles(reader, inspection, state.selected, state.refine)
            val vault = vault(app)
            val snapshot = SettingsDataCapture.capture(app, files.mapNotNull { SettingCategory.ofIdOrNull(it.id) }, System.currentTimeMillis())
            val backup = vault.backup(SettingsBackupVault.Operation.IMPORT, SettingsSnapshot.capture(SettingCategory.ALL), SettingsExporter.host(app), SettingsExporter.device(), snapshot)
            val changes = SettingsImportApplier.write(plan)
            val data = files.map { SettingsDataImport.import(app, it, state.fileName) }
            val categories = plan.categories.filter { SettingCategory.ofIdOrNull(it) != null }
            val touched = data.filter { it.failure == null || it.added.isNotEmpty() }
            val added = touched.associate { it.id to it.added }
            vault.record(SettingsBackupVault.LastOperation(SettingsBackupVault.Operation.IMPORT, System.currentTimeMillis(), backup.name, categories, touched.map { it.id }, added))
            Imported(plan, changes, inspection.locked.isNotEmpty(), data)
        }
        val (plan, changes, locked) = imported
        val report = SettingsEffects.run(changes, actions(app))
        val deployments = deploy(app, plan, device, state.operation)
        val backupFailure = afterOperation(app)
        return SettingsTransferOutcome.of(
            plan, changes, report,
            credentialsLocked = locked, backupFailure = backupFailure, launcherIconPending = launcherIconPending(report), deployments = deployments,
            data = imported.data.map { SettingsTransferOutcome.DataOutcome(it.id, it.entries, it.added.size, it.restorable, it.iconsMissing, it.failure) },
        )
    }

    /** What the background part of [importArchive] did. */
    private data class Imported(
        val plan: ImportPlan,
        val changes: List<SettingsEffects.Change>,
        val locked: Boolean,
        val data: List<SettingsDataImport.Result>,
    )

    /**
     * Runs the deployment items of [plan] one after another (T18) and writes the key of each one that succeeds; a
     * failure does not stop the others, and the items not started yet when the user cancels the rest are given up.
     * The progress goes to [SettingsOperations.deploymentsOf] of [operation].
     * zh-CN: 依次运行 [plan] 中的部署项 (T18), 每项成功后写入其键; 某项失败不影响其他项, 用户取消其余项时尚未开始的项被放弃. 进度写入
     * [operation] 的 [SettingsOperations.deploymentsOf].
     */
    private suspend fun deploy(context: Context, plan: ImportPlan, device: AndroidDeployments, operation: String?): List<DeploymentResult> {
        if (plan.deployments.isEmpty()) return emptyList()
        val items = plan.deployments.map { DeploymentItems.of(it, device) }
        val descriptions = withContext(Dispatchers.IO) { items.map { it.describe() } }
        val run = DeploymentRun(descriptions.map { DeploymentRun.Row(it.kind, it.target) })
        operation?.let { SettingsOperations.deploymentsOf(it).value = run }
        return items.mapIndexed { index, item ->
            val deployment = item.deployment
            fun result(failure: DeploymentException.Reason? = null, detail: String? = null) =
                DeploymentResult(deployment.kind, deployment.store.name, deployment.name, descriptions[index].target, failure, detail)
            if (run.cancelRequested) {
                run.update(index, DeploymentRun.Status.CANCELLED)
                return@mapIndexed result(DeploymentException.Reason.CANCELLED)
            }
            run.update(index, DeploymentRun.Status.RUNNING)
            try {
                item.run { fraction -> run.update(index, DeploymentRun.Status.RUNNING, fraction) }
                val changes = withContext(Dispatchers.IO) { SettingsImportApplier.write(DeploymentPlanner.completed(plan, listOf(deployment))) }
                SettingsEffects.run(changes, actions(context))
                run.update(index, DeploymentRun.Status.DONE)
                result()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeploymentException) {
                run.update(index, if (e.reason == DeploymentException.Reason.CANCELLED) DeploymentRun.Status.CANCELLED else DeploymentRun.Status.FAILED)
                result(e.reason, e.message)
            } catch (e: Exception) {
                run.update(index, DeploymentRun.Status.FAILED)
                result(DeploymentException.Reason.IO, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Undoes the last import (T9): imports its touched categories again from the backup taken before it, deletes the
     * entries it added to the data categories (P10.3, Q27), forgets it, then falls back from a deleted color library that
     * held the current color and runs the effect table on the calling thread, the main thread.
     * zh-CN: 撤销上次导入 (T9): 以导入前的备份再次导入触及的分类, 删除其在数据分类中新增的条目 (P10.3, Q27) 并忘记该操作, 然后在调用线程
     * (主线程) 从持有当前颜色的已删除颜色库回退, 并执行生效表.
     */
    suspend fun undo(context: Context): SettingsTransferOutcome {
        val app = context.applicationContext
        val (undo, changes, removals) = withContext(Dispatchers.IO) {
            val vault = vault(app)
            val undo = vault.undoPlan(environment(app)) ?: throw IOException("There is no import to undo")
            val changes = SettingsImportApplier.write(undo.plan)
            val removals = undo.operation.added.orEmpty().map { (id, ids) -> SettingsDataImport.remove(app, id, ids) }
            vault.clearLastOperation()
            Triple(undo, changes, removals)
        }
        // As the color library pages do after deleting a library.
        if (removals.any { it.intelligentRemoved }) IntelligentColorEngine.deactivate()
        if (removals.any { it.selectionRemoved || it.intelligentRemoved }) ColorSelectBaseActivity.fallBackToPaletteSelection(app)
        val report = SettingsEffects.run(changes, actions(app))
        val backupFailure = afterOperation(app)
        return SettingsTransferOutcome.of(
            undo.plan, changes, report,
            dataNotUndone = undo.dataNotUndone, backupFailure = backupFailure, launcherIconPending = launcherIconPending(report),
            data = removals.map { SettingsTransferOutcome.DataOutcome(it.id, it.ids.size, 0, failure = it.failure, removed = it.removed, kept = it.kept) },
        )
    }

    /**
     * Restores the categories of [SettingsWizardState.selected] to their defaults (D11, P8.1): backs up every category
     * to the internal vault ("auto-reset", T9), removes every stored name of the selected categories
     * ([SettingsResetPlanner]), records the operation for "Undo last import", runs the effect table on the calling
     * thread, the main thread, and writes the operation backup (T20).
     * zh-CN: 把所选分类恢复为默认值 (D11, P8.1): 先把全部分类备份到内部备份库 ("auto-reset", T9), 删除所选分类的全部已存储名称
     * ([SettingsResetPlanner]), 记录本次操作以便 "撤销上次导入", 在调用线程 (主线程) 执行生效表, 最后写操作后备份 (T20).
     */
    suspend fun reset(context: Context, state: SettingsWizardState): SettingsTransferOutcome {
        val app = context.applicationContext
        val (plan, changes) = withContext(Dispatchers.IO) {
            val plan = SettingsResetPlanner.plan(state.selected)
            val vault = vault(app)
            val backup = vault.backup(SettingsBackupVault.Operation.RESET, SettingsSnapshot.capture(SettingCategory.ALL), SettingsExporter.host(app), SettingsExporter.device())
            val changes = SettingsImportApplier.write(plan)
            vault.record(SettingsBackupVault.LastOperation(SettingsBackupVault.Operation.RESET, System.currentTimeMillis(), backup.name, plan.categories))
            plan to changes
        }
        val report = SettingsEffects.run(changes, actions(app))
        val backupFailure = afterOperation(app)
        return SettingsTransferOutcome.of(plan, changes, report, backupFailure = backupFailure, launcherIconPending = launcherIconPending(report))
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
     * What an import plan needs to know about this device (T8, [AndroidDeployments.environment]); runs on a background
     * thread.
     * zh-CN: 导入计划需要的本机情况 (T8, [AndroidDeployments.environment]); 在后台线程调用.
     */
    @JvmStatic
    fun environment(context: Context): ImportEnvironment = AndroidDeployments(context).environment()

    /**
     * The deployment items the selection of [state] needs on this device (P7.6, step 3), each with what it shows;
     * runs on a background thread.
     * zh-CN: [state] 所选分类在本机需要的部署项 (P7.6, 第 3 步) 及其显示内容; 在后台线程调用.
     */
    @JvmStatic
    fun pendingDeployments(context: Context, state: SettingsWizardState): List<PendingDeployment> {
        val reader = reader(context, state)
        val device = AndroidDeployments(context)
        val environment = device.environment()
        val plan = plan(reader, inspect(reader, state.fileName.orEmpty(), context.packageName), state.selected, environment)
        return DeploymentPlanner.pending(plan, environment).map { PendingDeployment(it, DeploymentItems.of(it.deployment, device).describe()) }
    }

    private fun deviceKey(context: Context): SettingsDeviceKey? = try {
        AndroidSettingsKeys.device(context)
    } catch (_: IOException) {
        null
    }

    private fun reader(context: Context, file: File, password: SettingsPassword? = null) =
        SettingsArchiveReader({ file.inputStream() }, key = deviceKey(context), password = password)

    /** The reader of the copy [state] holds, with the password the user entered (P7.7). zh-CN: [state] 中副本的读取方, 带用户输入的口令. */
    private fun reader(context: Context, state: SettingsWizardState) = reader(context, archiveOf(state), SettingsPasswords.find(state.password))

    private fun vault(context: Context) = SettingsBackupVault(File(context.filesDir, SettingsBackupVault.DIRECTORY), key = deviceKey(context))

    private fun archiveOf(state: SettingsWizardState): File =
        state.archive?.let(::File)?.takeIf { it.isFile } ?: throw IOException("The copy of the settings file is gone")
}
