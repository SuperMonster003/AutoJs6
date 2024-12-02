package org.autojs.autojs.core.record.inputevent

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.inputevent.InputEventCodes
import org.autojs.autojs.core.inputevent.InputEventObserver
import org.autojs.autojs.core.inputevent.RootAutomator
import org.autojs.autojs.engine.RootAutomatorEngine
import org.autojs.autojs.engine.RootAutomatorEngine.Companion.setTouchDevice
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.runtime.api.ScreenMetrics
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Created by Stardust on Aug 2, 2017.
 */
class InputEventToAutoFileRecorder(context: Context) : InputEventRecorder() {
    private var mLastEventTime = 0.0
    private var mTouchDevice = -1
    private var mDataOutputStream: DataOutputStream
    private var mTmpFile: File? = null

    init {
        try {
            mTmpFile = File(context.cacheDir, "${System.currentTimeMillis()}.auto").also {
                it.deleteOnExit()
                mDataOutputStream = DataOutputStream(FileOutputStream(it))
            }
            writeFileHeader()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Throws(IOException::class)
    private fun writeFileHeader() {
        mDataOutputStream.writeInt(0x00B87B6D)
        mDataOutputStream.writeInt(RootAutomatorEngine.VERSION)
        mDataOutputStream.writeInt(ScreenMetrics.deviceScreenWidth)
        mDataOutputStream.writeInt(ScreenMetrics.deviceScreenHeight)
        for (i in 0..239) {
            mDataOutputStream.writeByte(0)
        }
    }

    public override fun recordInputEvent(event: InputEventObserver.InputEvent) {
        try {
            convertEventOrThrow(event)
            Log.d(LOG_TAG, "recordInputEvent: $event")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun convertEventOrThrow(event: InputEventObserver.InputEvent) {
        if (mLastEventTime == 0.0) {
            mLastEventTime = event.time
        } else if (event.time - mLastEventTime > 0.001) {
            writeSleep((1000L * (event.time - mLastEventTime)).toInt())
            mLastEventTime = event.time
        }
        val device = parseDeviceNumber(event.device)
        val type = event.type.toLong(16).toShort()
        val code = event.code.toLong(16).toShort()
        val value = event.value.toLong(16).toInt()
        if (type.toInt() == InputEventCodes.EV_ABS) {
            if (code.toInt() == InputEventCodes.ABS_MT_POSITION_X || code.toInt() == InputEventCodes.ABS_MT_POSITION_Y) {
                mTouchDevice = device
                setTouchDevice(device)
                writeTouch(code, value)
                return
            }
        }
        if (type.toInt() == InputEventCodes.EV_SYN && code.toInt() == InputEventCodes.SYN_REPORT && value == 0) {
            writeSyncReport()
            return
        }
        if (device != mTouchDevice) {
            return
        }
        mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_EVENT.toInt())
        mDataOutputStream.writeShort(type.toInt())
        mDataOutputStream.writeShort(code.toInt())
        mDataOutputStream.writeInt(value)
        Log.d(LOG_TAG, "write event: $event")
    }

    @Throws(IOException::class)
    private fun writeSleep(millis: Int) {
        mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_SLEEP.toInt())
        mDataOutputStream.writeInt(millis)
        Log.d(LOG_TAG, "write sleep: $millis")
    }

    @Throws(IOException::class)
    private fun writeSyncReport() {
        mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_EVENT_SYNC_REPORT.toInt())
        Log.d(LOG_TAG, "write sync report")
    }

    @Throws(IOException::class)
    private fun writeTouch(code: Short, value: Int) {
        if (code.toInt() == InputEventCodes.ABS_MT_POSITION_X) {
            mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_EVENT_TOUCH_X.toInt())
            Log.d(LOG_TAG, "write touch x: $value")
        } else {
            mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_EVENT_TOUCH_Y.toInt())
            Log.d(LOG_TAG, "write touch y: $value")
        }
        mDataOutputStream.writeInt(value)
    }

    override fun getCode(): String? {
        return null
    }

    override fun getPath(): String {
        return mTmpFile!!.absolutePath
    }

    override fun stop() {
        super.stop()
        try {
            mDataOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val LOG_TAG = "InputEventToAutoFileRec"
    }
}