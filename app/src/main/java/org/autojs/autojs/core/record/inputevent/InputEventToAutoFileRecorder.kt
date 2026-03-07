package org.autojs.autojs.core.record.inputevent

import android.content.Context
import android.os.SystemClock
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
    private var mRecordStartMillis = 0L
    private var mFirstEventWritten = false
    private var mPendingSyncReport = false
    private var mTouchDevice = -1
    private val mTouchCoordinateMapper = TouchCoordinateMapper(context.applicationContext)
    private var mDataOutputStream: DataOutputStream
    private var mTmpFile: File? = null

    init {
        try {
            mTmpFile = File(context.cacheDir, "${System.currentTimeMillis()}.auto").also {
                it.deleteOnExit()
                mDataOutputStream = DataOutputStream(FileOutputStream(it))
            }
            updateTouchDevice(RootAutomatorEngine.getTouchDeviceId(context.applicationContext))
            writeFileHeader()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    override fun startImpl() {
        super.startImpl()
        mRecordStartMillis = SystemClock.elapsedRealtime()
        mFirstEventWritten = false
        mLastEventTime = 0.0
        mPendingSyncReport = false
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
        val device = parseDeviceNumber(event.device)
        val type = event.type.toLong(16).toShort()
        val code = event.code.toLong(16).toShort()
        val value = event.value.toLong(16).toInt()
        if (isTouchDeviceCandidate(type.toInt(), code.toInt())) {
            updateTouchDevice(device)
        }
        if (device != mTouchDevice) {
            return
        }
        appendDelayBeforeEvent(event.time)
        if (type.toInt() == InputEventCodes.EV_ABS) {
            if (isTouchCoordinateCode(code.toInt())) {
                writeTouch(code, mapTouchValue(code, value))
                mPendingSyncReport = true
                return
            }
        }
        if (type.toInt() == InputEventCodes.EV_SYN && code.toInt() == InputEventCodes.SYN_REPORT && value == 0) {
            writeSyncReport()
            mPendingSyncReport = false
            return
        }
        mDataOutputStream.writeByte(RootAutomator.DATA_TYPE_EVENT.toInt())
        mDataOutputStream.writeShort(type.toInt())
        mDataOutputStream.writeShort(code.toInt())
        mDataOutputStream.writeInt(value)
        mPendingSyncReport = true
        Log.d(LOG_TAG, "write event: $event")
    }

    @Throws(IOException::class)
    private fun appendDelayBeforeEvent(eventTime: Double) {
        if (!mFirstEventWritten) {
            val initialDelayMillis = (SystemClock.elapsedRealtime() - mRecordStartMillis).coerceAtLeast(1L)
            writeSleep(initialDelayMillis.toInt())
            mFirstEventWritten = true
            mLastEventTime = eventTime
            return
        }
        if (mLastEventTime == 0.0) {
            mLastEventTime = eventTime
            return
        }
        val deltaSeconds = eventTime - mLastEventTime
        if (deltaSeconds > 0.001) {
            writePendingSyncReportIfNeeded()
            writeSleep((1000L * deltaSeconds).toInt())
        }
        mLastEventTime = eventTime
    }

    @Throws(IOException::class)
    private fun writePendingSyncReportIfNeeded() {
        if (!mPendingSyncReport) {
            return
        }
        writeSyncReport()
        mPendingSyncReport = false
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

    private fun mapTouchValue(code: Short, rawValue: Int) = when (code.toInt()) {
        InputEventCodes.ABS_MT_POSITION_X,
        InputEventCodes.ABS_X -> mTouchCoordinateMapper.mapX(rawValue)
        InputEventCodes.ABS_MT_POSITION_Y,
        InputEventCodes.ABS_Y -> mTouchCoordinateMapper.mapY(rawValue)
        else -> rawValue
    }

    private fun isTouchCoordinateCode(code: Int): Boolean {
        return code == InputEventCodes.ABS_MT_POSITION_X
                || code == InputEventCodes.ABS_MT_POSITION_Y
                || code == InputEventCodes.ABS_X
                || code == InputEventCodes.ABS_Y
    }

    private fun isTouchDeviceCandidate(type: Int, code: Int): Boolean {
        if (type == InputEventCodes.EV_ABS) {
            return code == InputEventCodes.ABS_X
                    || code == InputEventCodes.ABS_Y
                    || code in InputEventCodes.ABS_MT_SLOT..InputEventCodes.ABS_MT_TOOL_Y
        }
        return type == InputEventCodes.EV_KEY
                && (code == InputEventCodes.BTN_TOUCH || code == InputEventCodes.BTN_TOOL_FINGER)
    }

    private fun updateTouchDevice(device: Int) {
        if (device < 0 || mTouchDevice == device) {
            return
        }
        mTouchDevice = device
        setTouchDevice(device)
        mTouchCoordinateMapper.updateTouchDevice(device)
    }

    @Throws(IOException::class)
    private fun writeTouch(code: Short, value: Int) {
        if (code.toInt() == InputEventCodes.ABS_MT_POSITION_X || code.toInt() == InputEventCodes.ABS_X) {
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
            writePendingSyncReportIfNeeded()
            mDataOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val LOG_TAG = "InputEventToAutoFileRec"
    }
}
