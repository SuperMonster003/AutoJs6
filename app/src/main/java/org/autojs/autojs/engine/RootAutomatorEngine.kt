package org.autojs.autojs.engine

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.core.inputevent.InputDevices
import org.autojs.autojs.core.pref.keys.MiscStateKeys
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
import kotlin.concurrent.thread

/**
 * Created by Stardust on Aug 1, 2017.
 * Modified by SuperMonster003 as of Sep 10, 2022.
 */
class RootAutomatorEngine @JvmOverloads constructor(private val mContext: Context, deviceNameOrPath: String? = InputDevices.touchDeviceName) : ScriptEngine.AbstractScriptEngine<AutoFileSource?>() {

    private val mDeviceNameOrPath: String? = getDeviceNameOrPath(mContext, deviceNameOrPath)
    private var mThread: Thread? = null
    private var mExecutablePath: String? = null
    private var mPid: String? = null
    private var mProcess: Process? = null

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

        private val PID_PATTERN = Pattern.compile("\\d{2,}")
        private var sTouchDeviceId = -1

        private const val LOG_TAG = "RootAutomatorEngine"
        private const val ROOT_AUTOMATOR_EXECUTABLE_ASSET = "binary/root_automator"

        @JvmStatic
        fun getDeviceNameOrPath(context: Context?, deviceNameOrPath: String?): String? {
            if (sTouchDeviceId < 0) {
                val prefTouchDevice = getDefaultTouchDevice(context!!).also { setTouchDevice(it) }
                if (prefTouchDevice < 0) {
                    return deviceNameOrPath
                }
            }
            MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.set(sTouchDeviceId)
            return "/dev/input/event$sTouchDeviceId"
        }

        @JvmStatic
        fun getExecutablePath(context: Context): String {
            return File(context.cacheDir, "root_automator").absolutePath.also {
                PFiles.copyAsset(context, ROOT_AUTOMATOR_EXECUTABLE_ASSET, it)
            }
        }

        @JvmStatic
        fun setTouchDevice(device: Int) {
            sTouchDeviceId = device
        }

        @JvmStatic
        fun getTouchDeviceId(context: Context) = when (sTouchDeviceId >= 0) {
            true -> sTouchDeviceId
            else -> getDefaultTouchDevice(context)
        }

        /**
         * The touch screen node for a recorder. On the main thread only a known node is returned, and an unknown one is
         * looked up off the main thread for later recordings, as the lookup runs commands as root or through Shizuku
         * (docs/shizuku-oom/ROADMAP.md D18). Recorders also take the node from the first touch event.
         * zh-CN: 供录制器使用的触摸屏节点. 主线程上只返回已知的节点, 未知时在主线程之外解析供之后的录制使用, 因为解析要以 root
         * 或经 Shizuku 执行命令 (docs/shizuku-oom/ROADMAP.md D18). 录制器也会从第一个触摸事件取得节点.
         */
        @JvmStatic
        fun getTouchDeviceIdForRecorder(context: Context) = getTouchDeviceIdForRecorder(
            onMainThread = Looper.myLooper() == Looper.getMainLooper(),
            resolve = { getTouchDeviceId(context) },
            runInBackground = { job -> thread(name = "TouchDeviceLookup", isDaemon = true) { job() } },
        )

        @Volatile
        private var sLookingUpTouchDevice = false

        @VisibleForTesting
        internal fun getTouchDeviceIdForRecorder(onMainThread: Boolean, resolve: () -> Int, runInBackground: (() -> Unit) -> Unit): Int {
            if (!onMainThread) {
                return resolve()
            }
            val known = if (sTouchDeviceId >= 0) sTouchDeviceId else MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get()
            if (known >= 0) {
                return known
            }
            if (!sLookingUpTouchDevice) {
                sLookingUpTouchDevice = true
                runInBackground {
                    try {
                        resolve()
                    } finally {
                        sLookingUpTouchDevice = false
                    }
                }
            }
            return -1
        }

        @Suppress("UNUSED_PARAMETER")
        private fun getDefaultTouchDevice(context: Context) = getCachedOrResolvedTouchDevice { InputDevices.touchDeviceId }

        // Only a resolved node is cached, so a failed lookup is tried again next time (docs/shizuku-oom/ROADMAP.md D17).
        // zh-CN: 只缓存解析成功的节点, 解析失败时下次重试 (docs/shizuku-oom/ROADMAP.md D17).
        @VisibleForTesting
        internal fun getCachedOrResolvedTouchDevice(resolve: () -> Int): Int {
            val cached = MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get()
            if (cached >= 0) {
                return cached
            }
            return resolve().also {
                if (it >= 0) {
                    MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.set(it)
                    setTouchDevice(it)
                }
            }
        }

    }

}