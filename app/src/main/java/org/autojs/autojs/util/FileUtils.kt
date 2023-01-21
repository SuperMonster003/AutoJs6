package org.autojs.autojs.util

import java.io.File
import java.util.Arrays
import java.util.Locale

/**
 * Created by Stardust on 2017/3/31.
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

    enum class TYPE(typeName: String) {

        DIRECTORY("/"),
        JAVASCRIPT("js"),
        AUTO("auto", "R"),
        JSON("json"),
        XML("xml"),
        PROJECT("project.json", "✲"),
        APK("apk"),
        UNKNOWN("?"),
        ;

        var typeName: String
            private set

        private var mAlias: String? = null

        constructor(type: String, alias: String) : this(type) {
            mAlias = alias
        }

        init {
            this.typeName = typeName.lowercase(Locale.getDefault())
        }

        val extension
            get() = when (typeName.matches("\\w+".toRegex())) {
                true -> typeName.split("\\W".toRegex()).last { it.isNotEmpty() }
                else -> ""
            }
        val alias
            get() = mAlias ?: typeName

    }
}