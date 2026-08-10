package org.autojs.autojs.script

import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.WorkingDirectoryUtils
import java.io.Serializable

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Oct 3, 2022.
 */
abstract class ScriptSource @JvmOverloads constructor(
    private val sourceName: String,
    val extension: String = FileUtils.TYPE.JAVASCRIPT.extension,
) : Serializable {

    var overriddenFullPath: String? = null

    var originUri: String? = null

    val originPath: String?
        get() = overriddenFullPath

    var prefix: String? = null

    val name
        get() = overriddenFullPath?.let { PFiles.getNameWithoutExtension(it) } ?: sourceName

    val fullName
        get() = "$name.$extension"

    val fullPath: String
        get() = originPath ?: originUri ?: toString()

    val elegantPath: String
        get() {
            val niceFullPath = overriddenFullPath?.let { "$it [cache]" } ?: fullPath
            return PFiles.getElegantPath(niceFullPath, WorkingDirectoryUtils.path, true)
        }

    abstract val engineName: String?

}
