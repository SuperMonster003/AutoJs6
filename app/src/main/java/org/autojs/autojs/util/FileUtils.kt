package org.autojs.autojs.util

import java.io.File
import java.util.Arrays
import java.util.Locale

/**
 * Created by Stardust on Mar 31, 2017.
 */
object FileUtils {

    @JvmOverloads
    fun sort(files: Array<File>, comparator: Comparator<File>, ascending: Boolean = true) {
        if (ascending) {
            Arrays.sort(files, comparator)
        } else {
            Arrays.sort(files) { o1: File, o2: File -> comparator.compare(o2, o1) }
        }
    }

    @JvmOverloads
    fun sort(files: List<File?>, comparator: Comparator<File?>, ascending: Boolean = true) {
        if (ascending) {
            files.sortedWith(comparator)
        } else {
            files.sortedWith { o1: File?, o2: File? -> comparator.compare(o2, o1) }
        }
    }

    enum class TYPE(val typeName: String, private val iconName: String? = null) {

        DIRECTORY(File.separator),
        JAVASCRIPT("js"),
        AUTO("auto", "R"),
        JSON("json"),
        XML("xml"),
        PROJECT("project.json", "✲"),
        APK("apk"),
        UNKNOWN("?"),
        ;

        val iconText
            get() = iconName ?: typeName

        val extension
            get() = when (Regex("[\\w.]+").containsMatchIn(typeName)) {
                true -> typeName.split("[^\\w.]".toRegex()).last { it.isNotEmpty() }
                else -> ""
            }

    }

}