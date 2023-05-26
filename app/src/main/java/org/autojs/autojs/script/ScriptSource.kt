package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import java.io.Serializable

/**
 * Created by Stardust on 2017/4/2.
 * Modified by SuperMonster003 as of Oct 3, 2022.
 */
abstract class ScriptSource constructor(private val sourceName: String) : Serializable {

    var overriddenFullPath: String? = null

    var prefix: String? = null

    val extension = FileUtils.TYPE.JAVASCRIPT.extension

    val name
        get() = overriddenFullPath?.let { PFiles.getNameWithoutExtension(it) } ?: sourceName

    val fullName
        get() = "$name.$extension"

    val fullPath: String
        get() = toString()

    val elegantPath: String
        get() {
            val niceFullPath = overriddenFullPath?.let { "$it [cache]" } ?: fullPath
            return PFiles.getElegantPath(niceFullPath, WorkingDirectoryUtils.path, true)
        }

    abstract val engineName: String?

}