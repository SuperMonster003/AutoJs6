package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.autojs.autojs.core.inputevent.InputDevices
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.AutoFileSource
import org.autojs.autojs6.R
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Created by Stardust on 2017/8/1.
 * Modified by SuperMonster003 as of Sep 10, 2022.
 */
class RootAutomatorEngine @JvmOverloads constructor(private val mContext: Context, deviceNameOrPath: String? = InputDevices.getTouchDeviceName()) : ScriptEngine.AbstractScriptEngine<AutoFileSource?>() {

    private val mDeviceNameOrPath: String?
    private var mThread: Thread? = null
    private var mExecutablePath: String? = null
    private var mPid: String? = null
    private var mProcess: Process? = null

    init {
        mDeviceNameOrPath = getDeviceNameOrPath(mContext, deviceNameOrPath)
    }

    fun execute(autoFile: String) {
        Log.d(LOG_TAG, "exec: $autoFile")

        mExecutablePath = getExecutablePath(mContext)

        val commands = arrayOf(
            "chmod 755 $mExecutablePath",
            // to run root_automator
            "\"$mExecutablePath\" \"$autoFile\" -d \"$mDeviceNameOrPath\" &",
            // to print the root_automator pid
            "echo $!",
            // to exit su
            "exit",
            // to exit shell
            "exit",
        )
        try {
            Runtime.getRuntime().exec(AbstractShell.COMMAND_SU).also {
                mProcess = it
                executeCommands(it, commands)
                mPid = readPid(it)
                it.waitFor()
            }
        } catch (e: IOException) {
            if (e.message?.contains("\\bsu\\b[\\s\\S]+[Nn]o such file or dir\\w*".toRegex()) == true) {
                throw Exception(mContext.getString(R.string.error_may_not_have_root_access_to_run_auto_file))
            }
            throw ScriptException(e)
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        } finally {
            mProcess?.destroy()
            mProcess = null
        }
    }

    @Throws(IOException::class)
    private fun readPid(process: Process?): String? {
        val reader = BufferedReader(InputStreamReader(process!!.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val matcher = PID_PATTERN.matcher(line!!)
            if (matcher.find()) {
                return matcher.group()
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun executeCommands(process: Process?, commands: Array<String>) {
        DataOutputStream(process!!.outputStream).let { os ->
            commands.toList().forEach {
                os.write(it.toByteArray())
                os.writeBytes("\n")
            }
            os.flush()
        }
    }

    override fun put(name: String, value: Any) {}

    override fun execute(scriptSource: AutoFileSource?): Any? {
        scriptSource?.let { execute(it.file.absolutePath) }
        return null
    }

    override fun forceStop() {
        mThread!!.interrupt()
        if (mPid != null) {
            ProcessShell.exec("kill $mPid", true)
        }
    }

    override fun init() {
        mThread = Thread.currentThread()
    }

    @Synchronized
    override fun destroy() {
        super.destroy()
    }

    companion object {

        const val VERSION = 1

        private val KEY_TOUCH_DEVICE = RootAutomatorEngine::class.java.name + ".touch_device"
        private val PID_PATTERN = Pattern.compile("\\d{2,}")
        private var sTouchDevice = -1

        private const val LOG_TAG = "RootAutomatorEngine"
        private const val ROOT_AUTOMATOR_EXECUTABLE_ASSET = "binary/root_automator"

        @JvmStatic
        fun getDeviceNameOrPath(context: Context?, deviceNameOrPath: String?): String? {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context!!)
            if (sTouchDevice < 0) {
                val prefTouchDevice = getDefaultTouchDevice(context).also { setTouchDevice(it) }
                if (prefTouchDevice < 0) {
                    return deviceNameOrPath
                }
            }
            prefs.edit().putInt(KEY_TOUCH_DEVICE, sTouchDevice).apply()
            return "/dev/input/event$sTouchDevice"
        }

        @JvmStatic
        fun getExecutablePath(context: Context): String {
            return File(context.cacheDir, "root_automator").absolutePath.also {
                PFiles.copyAsset(context, ROOT_AUTOMATOR_EXECUTABLE_ASSET, it)
            }
        }

        @JvmStatic
        fun setTouchDevice(device: Int) {
            sTouchDevice = device
        }

        @JvmStatic
        fun getTouchDevice(context: Context?) = when (sTouchDevice >= 0) {
            true -> sTouchDevice
            else -> getDefaultTouchDevice(context)
        }

        private fun getDefaultTouchDevice(context: Context?): Int {
            return PreferenceManager.getDefaultSharedPreferences(context!!).getInt(KEY_TOUCH_DEVICE, -1)
        }

    }

}