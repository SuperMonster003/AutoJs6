package org.autojs.autojs.util

/**
 * Created by Stardust on Aug 3, 2017.
 */
object ProcessUtils {

    @JvmStatic
    fun getProcessPid(process: Process) = try {
        process.javaClass.getDeclaredField("pid").run {
            isAccessible = true
            get(process)?.let { it as Int }
        }
    } catch (e: Exception) {
        null.also { e.printStackTrace() }
    } ?: -1

}