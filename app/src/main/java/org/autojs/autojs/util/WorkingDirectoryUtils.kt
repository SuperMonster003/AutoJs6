package org.autojs.autojs.util

import org.autojs.autojs.annotation.MinSize
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.EnvironmentUtils.externalStorageDirectory
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import java.io.File

object WorkingDirectoryUtils {

    var histories: LinkedHashSet<String>
        get() = Pref.getLinkedHashSet(R.string.key_working_directory_histories)
        private set(value) = Pref.putLinkedHashSet(R.string.key_working_directory_histories, value)

    @JvmStatic
    var path: String
        get() = Pref.getString(R.string.key_working_directory, str(R.string.default_value_working_directory)).let {
            File(externalStorageDirectory, it).path
        }
        set(path) {
            var scriptDirPath = path
            if (path.startsWith(externalStoragePath)) {
                scriptDirPath = toRelativePath(path)
            }
            val dirKey = key(R.string.key_working_directory)
            Pref.putString(dirKey, scriptDirPath)
        }

    @JvmStatic
    val relativePath
        get() = path.let {
            when (it.startsWith(externalStoragePath)) {
                true -> toRelativePath(it)
                else -> it
            }
        }

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
        val dirKey = key(R.string.key_working_directory)
        if (Pref.containsKey(dirKey)) {
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
        selectedDir?.let { Pref.putString(dirKey, it) }
    }

    @JvmStatic
    fun toRelativePath(scriptDirPath: String) = when {
        scriptDirPath.startsWith(externalStoragePath) -> {
            scriptDirPath.substring(externalStoragePath.length)
        }
        else -> scriptDirPath
    }

    @JvmStatic
    fun addIntoHistories(history: CharSequence) {
        histories = linkedSetOf(toRelativePath(history.toString())).apply { addAll(histories) }
    }

    fun removeFromHistories(history: CharSequence) {
        histories = histories.apply { remove(toRelativePath(history.toString())) }
    }

}