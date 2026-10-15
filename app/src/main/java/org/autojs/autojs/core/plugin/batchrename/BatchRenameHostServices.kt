package org.autojs.autojs.core.plugin.batchrename

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.storage.history.HistoryRepository
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.util.DialogUtils
import java.io.File

/**
 * The process-wide rename executor and journal (plugin roadmap P3.1), shared by `files.batchRename` and Explorer
 * Action sessions so that both see the same batches. The journal lives in `noBackupFilesDir/batch-rename/journals`.
 * zh-CN: 进程级的改名执行器与日志 (插件路线图 P3.1), 由 `files.batchRename` 与 Explorer Action 会话共用, 两者看到相同的批次.
 * 日志位于 `noBackupFilesDir/batch-rename/journals`.
 */
object BatchRenameHostServices {

    @Volatile
    private var executor: BatchRenameExecutor? = null

    @Volatile
    private var journal: BatchRenameJournalStore? = null

    fun journal(context: Context): BatchRenameJournalStore = journal ?: synchronized(this) {
        journal ?: BatchRenameJournalStore(File(context.applicationContext.noBackupFilesDir, "batch-rename/journals")).also { journal = it }
    }

    fun executor(context: Context): BatchRenameExecutor = executor ?: synchronized(this) {
        executor ?: BatchRenameExecutor(journal(context), AndroidBatchRenameEnvironment(context.applicationContext)).also { executor = it }
    }
}

/**
 * [BatchRenameEnvironment] on the host: the host Trash, version history and the workspace file manager. Calls must
 * not run on the main thread (Room).
 * zh-CN: 宿主上的 [BatchRenameEnvironment]: 宿主回收站, 版本历史与工作区文件管理器. 不得在主线程调用 (Room).
 */
internal class AndroidBatchRenameEnvironment(private val context: Context) : BatchRenameEnvironment {

    override fun moveToTrash(file: File): String? = try {
        val repository = TrashRepository(context)
        repository.moveToTrashWithProgress(file.path, null, null, DialogUtils.OperationController(), null, repository.newTrashId()).trashId
    } catch (error: Exception) {
        // A move that needs recovery leaves its Trash item behind; the entry is reported as not renamed.
        // zh-CN: 需要恢复处理的移动会留下回收站条目; 该条目报告为未改名.
        Log.w(TAG, "Could not move ${file.path} to the Trash", error)
        null
    }

    override fun restoreFromTrash(id: String, target: File): Boolean = try {
        val repository = TrashRepository(context)
        val item = repository.findTrashItem(id)
        if (item == null) {
            false
        } else {
            repository.restoreTrashItemToPathWithoutOverwrite(item, target, DialogUtils.OperationController())
            true
        }
    } catch (error: Exception) {
        Log.w(TAG, "Could not restore the Trash item $id to ${target.path}", error)
        false
    }

    override fun relocateHistory(from: File, to: File) {
        HistoryRepository(context).relocate(from.path, to.path)
    }

    override fun notifyChanged(directories: Collection<File>) {
        val refresh = Runnable { Explorers.workspace().refreshAll() }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run() else Handler(Looper.getMainLooper()).post(refresh)
    }

    private companion object {
        const val TAG = "BatchRenameEnvironment"
    }
}

/**
 * File-system traits of a directory that change how names compare and which characters they may hold (plugin
 * roadmap D20). Case folding is probed on an existing entry; restricted characters follow the volume: shared and
 * removable storage go through FAT rules (FUSE / sdcardfs, FAT32, exFAT), app-private storage does not.
 * zh-CN: 目录所在文件系统的特征, 影响名称的比较方式与允许的字符 (插件路线图 D20). 大小写折叠通过已有条目探测; 受限字符按卷判断:
 * 共享存储与可移动存储遵循 FAT 规则 (FUSE / sdcardfs, FAT32, exFAT), 应用私有存储不受限.
 */
data class BatchRenameVolumeTraits(val caseInsensitive: Boolean, val restrictedCharacters: Boolean) {

    companion object {
        private val SHARED_STORAGE_PREFIXES = listOf("/storage/", "/sdcard", "/mnt/media_rw/", "/mnt/sdcard")

        fun of(directory: File, names: Collection<String>? = null): BatchRenameVolumeTraits {
            val path = directory.absolutePath
            val restricted = SHARED_STORAGE_PREFIXES.any { path.startsWith(it) }
            return BatchRenameVolumeTraits(probeCaseInsensitive(directory, names ?: directory.list()?.toList().orEmpty()), restricted)
        }

        /**
         * True when a name of [directory] with its case swapped is reported as existing although no such entry is
         * listed. Without a usable entry the directory's own name is probed in its parent.
         */
        internal fun probeCaseInsensitive(directory: File, names: Collection<String>): Boolean {
            val listed = names.toHashSet()
            val candidate = names.firstOrNull { name -> swapCase(name).let { it != name && it !in listed } }
            if (candidate != null) return File(directory, swapCase(candidate)).exists()
            val parent = directory.absoluteFile.parentFile ?: return false
            val swapped = swapCase(directory.name)
            if (swapped == directory.name || parent.list()?.contains(swapped) == true) return false
            return File(parent, swapped).exists()
        }

        private fun swapCase(name: String): String = buildString(name.length) {
            name.forEach { char -> append(if (char.isUpperCase()) char.lowercaseChar() else if (char.isLowerCase()) char.uppercaseChar() else char) }
        }
    }
}
