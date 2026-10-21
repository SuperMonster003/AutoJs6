package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The automatic backups of the settings (docs/settings/ROADMAP.md D13 / D22 / T20, P9) in [directory]
 * (`<working directory>/backups/settings/` in the app, [directoryIn]): settings archives named
 * `autojs6-settings-auto-<mechanism>-<yyyyMMdd-HHmmss>.zip` for the three [Mechanism]s, of which the newest [keep] of
 * each mechanism are kept. A backup holds the categories selected by default ([CATEGORIES]: data categories included,
 * never `credentials` (R6) nor `other`), so [write] leaves out every other category of the snapshot and every data file
 * of another category; nothing in it is sealed. Files that are no automatic backups (exports, notes, the internal
 * backups of T9) are never pruned. Pure JVM (R3): the snapshot, the data files, the host and the clock come from the
 * caller; the app calls it through `AndroidSettingsAutoBackup`.
 *
 * zh-CN: 设置的自动备份 (ROADMAP D13 / D22 / T20, P9), 位于 [directory] (应用中为 `<工作目录>/backups/settings/`, [directoryIn]):
 * 三种 [Mechanism] 各自写为 `autojs6-settings-auto-<机制>-<yyyyMMdd-HHmmss>.zip` 设置归档, 每种机制保留最新的 [keep] 份. 备份含默认勾选的
 * 分类 ([CATEGORIES]: 含数据分类, 不含 `credentials` (R6) 与 `other`), 因此 [write] 略去快照中的其他分类与其他分类的数据文件, 备份中没有
 * 加密内容. 不是自动备份的文件 (导出文件, 笔记, T9 的内部备份) 不会被清理. 纯 JVM (R3): 快照, 数据文件, 宿主与时钟由调用方提供; 应用经
 * `AndroidSettingsAutoBackup` 调用.
 */
class SettingsAutoBackup @JvmOverloads constructor(
    val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val keep: Int = KEEP,
) {

    init {
        require(keep >= 1) { "Automatic backups keep at least one of each mechanism" }
    }

    /** What takes an automatic backup (D22). zh-CN: 触发自动备份的机制 (D22). */
    enum class Mechanism(val label: String) {
        /** Daily, weekly or monthly (P9.2). zh-CN: 每日, 每周或每月 (P9.2). */
        PERIODIC("periodic"),

        /** Five minutes after the last change of the settings (P9.3). zh-CN: 设置最后一次变化 5 分钟后 (P9.3). */
        CHANGE("change"),

        /** Right after an import, a reset to defaults or an undo (P9.1). zh-CN: 导入, 恢复默认或撤销之后立即 (P9.1). */
        OPERATION("operation"),
        ;

        val prefix: String
            get() = "$FILE_PREFIX$label-"

        companion object {
            @JvmStatic
            fun ofLabel(label: String): Mechanism? = values().firstOrNull { it.label == label }
        }
    }

    /** One backup file: its mechanism, its time as named (`yyyyMMdd-HHmmss`) and a counter of the same second. */
    data class Backup(val file: File, val mechanism: Mechanism, val time: String, val sequence: Int) {
        val name: String
            get() = file.name
    }

    /**
     * What an automatic backup did: the operation backup reports a failure on the result page without blocking it (T20).
     * zh-CN: 一次自动备份的结果: 操作后备份失败时只在结果页提示, 不阻断 (T20).
     */
    sealed interface Outcome {

        /** [backup] was written. zh-CN: 已写入 [backup]. */
        data class Written(val backup: Backup) : Outcome

        /** The mechanism is switched off. zh-CN: 该机制已关闭. */
        data object Off : Outcome

        /** Writing failed, for example in a working directory that is not writable. zh-CN: 写入失败, 例如工作目录不可写. */
        data class Failed(val error: IOException) : Outcome
    }

    /** The backups of [mechanism] (of all of them when null), newest first. zh-CN: [mechanism] 的备份 (为 null 时为全部), 最新的在前. */
    @JvmOverloads
    fun backups(mechanism: Mechanism? = null): List<Backup> = (directory.listFiles() ?: emptyArray()).mapNotNull(::backupOf)
        .filter { mechanism == null || it.mechanism == mechanism }
        .sortedWith(compareByDescending<Backup> { it.time }.thenByDescending { it.sequence })

    /**
     * Writes the [CATEGORIES] of [snapshot] (`SettingsSnapshot.capture(SettingsAutoBackup.CATEGORIES)` in the app) and
     * the [data] files of them as a backup of [mechanism], then keeps the newest [keep] backups of the mechanism
     * ([prune]); returns the backup. Throws [IOException] when the directory cannot be created or written, and leaves no
     * partial file behind.
     * zh-CN: 把 [snapshot] 中属于 [CATEGORIES] 的分类及其数据文件 [data] 写为 [mechanism] 的备份, 然后该机制只保留最新的 [keep] 份
     * ([prune]); 返回该备份. 目录无法创建或写入时抛 [IOException], 不留下不完整的文件.
     */
    @JvmOverloads
    fun write(
        mechanism: Mechanism,
        snapshot: SettingsSnapshot,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
        data: List<SettingsArchiveWriter.DataFile> = emptyList(),
    ): Backup {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
        val now = clock()
        val time = FILE_NAME_TIME.format(Instant.ofEpochMilli(now).atZone(zone))
        val taken = backups().filter { it.time == time }.maxOfOrNull { it.sequence }
        val sequence = if (taken == null) 0 else taken + 1
        val file = File(directory, mechanism.prefix + time + (if (sequence == 0) "" else "-$sequence") + ".zip")
        val included = SettingsSnapshot(snapshot.categories.filter { it.id in CATEGORY_IDS })
        val files = data.filter { it.id in CATEGORY_IDS }
        writeAtomically(file) { out -> SettingsArchiveWriter.write(out, included, SettingsArchive.exportTime(now, zone), host, device, files) }
        prune(mechanism)
        return backupOf(file)!!
    }

    /**
     * Like [write], with the result as an [Outcome]: [Outcome.Failed] instead of an [IOException].
     * zh-CN: 与 [write] 相同, 结果为 [Outcome]: 以 [Outcome.Failed] 代替 [IOException].
     */
    @JvmOverloads
    fun tryWrite(
        mechanism: Mechanism,
        snapshot: SettingsSnapshot,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
        data: List<SettingsArchiveWriter.DataFile> = emptyList(),
    ): Outcome = try {
        Outcome.Written(write(mechanism, snapshot, host, device, data))
    } catch (e: IOException) {
        Outcome.Failed(e)
    }

    /**
     * Deletes the backups of [mechanism] beyond the newest [keep] and returns them; other mechanisms and files that are
     * no backups stay.
     * zh-CN: 删除 [mechanism] 最新 [keep] 份之外的备份并返回它们; 其他机制的备份与不是备份的文件保留.
     */
    fun prune(mechanism: Mechanism): List<File> = backups(mechanism).drop(keep).map { it.file }.filter { it.delete() }

    private fun backupOf(file: File): Backup? {
        if (!file.isFile) return null
        val match = BACKUP_NAME.matchEntire(file.name) ?: return null
        val (label, time, sequence) = match.destructured
        return Backup(file, Mechanism.ofLabel(label)!!, time, sequence.ifEmpty { "0" }.toInt())
    }

    private fun writeAtomically(file: File, write: (OutputStream) -> Unit) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.outputStream().use(write)
            if (file.exists() && !file.delete()) throw IOException("Cannot replace $file")
            if (!temp.renameTo(file)) throw IOException("Cannot rename $temp to $file")
        } finally {
            temp.delete()
        }
    }

    companion object {

        /** The directory under the working directory (T20). zh-CN: 工作目录下的目录 (T20). */
        const val DIRECTORY = "backups/settings"

        /** Backups kept per mechanism (T20). zh-CN: 每种机制保留的备份数 (T20). */
        const val KEEP = 7

        /** The common start of the file names. zh-CN: 文件名的共同开头. */
        const val FILE_PREFIX = "autojs6-settings-auto-"

        /**
         * The categories of an automatic backup (T20): those selected by default, data categories included; never
         * `credentials` (R6), `other` or `developer`.
         * zh-CN: 自动备份的分类 (T20): 默认勾选的分类, 含数据分类; 不含 `credentials` (R6), `other` 与 `developer`.
         */
        @JvmField
        val CATEGORIES: List<SettingCategory> = SettingCategory.ALL.filter { it.defaultSelected && !it.sensitive && it != SettingCategory.OTHER }

        private val CATEGORY_IDS: Set<String> = CATEGORIES.map { it.id }.toSet()

        private val BACKUP_NAME = Regex("""autojs6-settings-auto-(periodic|change|operation)-(\d{8}-\d{6})(?:-([1-9]\d{0,8}))?\.zip""")

        private val FILE_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

        /** The backup directory of [workingDirectory]. zh-CN: 工作目录 [workingDirectory] 下的备份目录. */
        @JvmStatic
        fun directoryIn(workingDirectory: File): File = File(workingDirectory, DIRECTORY)
    }
}
