package org.autojs.autojs.script

import org.autojs.autojs.util.FileUtils
import java.io.Serializable

/**
 * Created by Stardust on 2017/4/2.
 * Modified by SuperMonster003 as of Oct 3, 2022.
 */
abstract class ScriptSource(val name: String) : Serializable {

    private var mPrefix: String? = null

    val extension = FileUtils.TYPE.JAVASCRIPT.extension

    val fullName = "$name.$extension"

    fun getPrefix() = mPrefix

    fun setPrefix(prefix: String) {
        mPrefix = prefix
    }

    abstract val engineName: String?

}