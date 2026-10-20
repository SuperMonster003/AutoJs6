package org.autojs.autojs.util

import org.autojs.autojs.annotation.MinSize
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.util.EnvironmentUtils.externalStorageDirectory
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.io.File

object WorkingDirectoryUtils {

    var history: LinkedHashSet<String>
        get() = Pref.getLinkedHashSet(R.string.key_working_directory_history)
        private set(value) = Pref.putLinkedHashSet(R.string.key_working_directory_history, value)

    @JvmStatic
    var path: String
        get() = ExplorerKeys.WORKING_DIRECTORY.get().let {
            WorkingDirectoryPathPolicy.resolveStored(it, externalStoragePath)
        }
        set(path) {
            val scriptDirPath = WorkingDirectoryPathPolicy.decodeAbsolute(path)?.let {
                WorkingDirectoryPathPolicy.encodeAbsolute(it, externalStoragePath)
            } ?: toRelativePath(path)
            ExplorerKeys.WORKING_DIRECTORY.set(scriptDirPath)
        }

    /** File-picker and Explorer results are already absolute, unlike legacy preference input. */
    @JvmStatic
    fun setAbsolutePath(path: String) {
        ExplorerKeys.WORKING_DIRECTORY.set(WorkingDirectoryPathPolicy.encodeAbsolute(path, externalStoragePath))
    }

    @JvmStatic
    val relativePath
        get() = toRelativePath(path)

    @MinSize(1)
    fun getRecommendedDefaultPaths() = getDefaultPathCandidates().map { getDir(it) }.toSet()

    @MinSize(1)
    private fun getDefaultPathCandidates() = mutableSetOf(
        Language.getPrefLanguage(),
        Language.AUTO,
    ).plus(Language.values().filter { File(externalStorageDirectory, getDir(it)).exists() })

    private fun getDir(language: Language) = LocaleUtils.getResources(language.locale).getString(R.string.default_value_working_directory)

    @JvmStatic
    fun determineIfNeeded() {
        val initKey = key(R.string.key_working_directory_initialized)
        if (Pref.containsKey(initKey)) {
            return
        }
        Pref.putBoolean(initKey, true)
        if (ExplorerKeys.WORKING_DIRECTORY.isStored()) {
            return
        }
        // @Hint by SuperMonster003 on Oct 14, 2022.
        //  ! Get the newest last modified and non-empty directory from default paths.
        //  ! If null, the first path of default paths will be used.
        //  ! zh-CN:
        //  ! 从默认路径中获取一个最新修改过的非空目录.
        //  ! 如果为空, 则使用默认路径的第一个.
        val newestNonEmptyDir = getRecommendedDefaultPaths()
            .map { File(externalStorageDirectory, it) }
            .filter { it.listFiles()?.isNotEmpty() ?: false }
            .maxByOrNull { it.lastModified() }
        val selectedDir = newestNonEmptyDir?.let {
            toRelativePath(it.path)
        } ?: getRecommendedDefaultPaths().firstOrNull()
        selectedDir?.let { ExplorerKeys.WORKING_DIRECTORY.set(it) }
    }

    @JvmStatic
    fun toRelativePath(scriptDirPath: String) = WorkingDirectoryPathPolicy.toRelativePath(scriptDirPath, externalStoragePath)

    internal fun resolveInputPath(input: String, selectedAbsolutePath: String? = null): String =
        WorkingDirectoryPathPolicy.resolveInput(input, externalStoragePath, selectedAbsolutePath)

    internal fun displayStoredPath(value: String): String = WorkingDirectoryPathPolicy.displayStored(value)

    internal fun decodeStoredAbsolutePath(value: String): String? = WorkingDirectoryPathPolicy.decodeAbsolute(value)

    internal fun isInternalStoragePath(value: String): Boolean = WorkingDirectoryPathPolicy.isWithinStorage(value, externalStoragePath)

    @JvmStatic
    fun addIntoHistory(item: CharSequence) {
        history = linkedSetOf(toRelativePath(item.toString())).apply { addAll(history) }
    }

    @JvmStatic
    fun addAbsoluteIntoHistory(path: String) {
        history = linkedSetOf(WorkingDirectoryPathPolicy.encodeAbsolute(path, externalStoragePath)).apply { addAll(history) }
    }

    fun removeFromHistory(item: CharSequence) {
        history = history.apply { remove(toRelativePath(item.toString())) }
    }

}
