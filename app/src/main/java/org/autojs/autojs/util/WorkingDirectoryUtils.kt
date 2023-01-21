package org.autojs.autojs.util

import org.autojs.autojs.annotation.MinSize
import org.autojs.autojs.pref.Language
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.util.EnvironmentUtils.externalStorageDirectory
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import java.io.File

object WorkingDirectoryUtils {

    private const val dirRes = R.string.default_value_working_directory
    private const val workingDirKeyRes = R.string.key_working_directory_histories

    var histories: LinkedHashSet<String>
        get() = Pref.getLinkedHashSet(workingDirKeyRes)
        private set(value) = Pref.setLinkedHashSet(workingDirKeyRes, value)

    @JvmStatic
    var path: String
        get() = Pref.getString(R.string.key_working_directory, str(R.string.default_value_working_directory))!!.let {
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

    private fun getDir(language: Language) = LocaleUtils.getResources(language.locale).getString(dirRes)

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
        //  ! If null, the first path of default paths will be used as a fallback.
        getRecommendedDefaultPaths().let { paths ->
            Pref.putString(dirKey, paths.map {
                File(externalStorageDirectory, it)
            }.filter {
                it.listFiles()?.isNotEmpty() ?: false
            }.maxByOrNull {
                it.lastModified()
            }?.path?.let { toRelativePath(it) } ?: paths.first())
        }
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