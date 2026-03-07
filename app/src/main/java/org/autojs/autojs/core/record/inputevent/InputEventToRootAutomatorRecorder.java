package org.autojs.autojs.core.record.inputevent;

import android.content.Context;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.inputevent.InputEventCodes;
import org.autojs.autojs.core.inputevent.InputEventObserver;
import org.autojs.autojs.engine.RootAutomatorEngine;
import org.autojs.autojs.runtime.api.ScreenMetrics;

/**
 * Created by Stardust on Aug 1, 2017.
 */
public class InputEventToRootAutomatorRecorder extends InputEventRecorder {

    private double mLastEventTime;
    private long mRecordStartMillis;
    private boolean mFirstEventWritten;
    private boolean mPendingSyncReport;
    private final StringBuilder mCode = new StringBuilder();
    private int mTouchDevice = -1;
    private final TouchCoordinateMapper mTouchCoordinateMapper;

    public InputEventToRootAutomatorRecorder() {
        this(GlobalAppContext.get());
    }

    public InputEventToRootAutomatorRecorder(@NonNull Context context) {
        Context applicationContext = context.getApplicationContext();
        mTouchCoordinateMapper = new TouchCoordinateMapper(applicationContext);
        updateTouchDevice(RootAutomatorEngine.getTouchDeviceId(applicationContext));
        mCode.append("var ra = new RootAutomator();\n")
                .append("ra.setScreenMetrics(")
                .append(ScreenMetrics.getDeviceScreenWidth()).append(", ")
                .append(ScreenMetrics.getDeviceScreenHeight()).append(");\n");
    }

    @Override
    protected void startImpl() {
        super.startImpl();
        mRecordStartMillis = SystemClock.elapsedRealtime();
        mFirstEventWritten = false;
        mLastEventTime = 0;
        mPendingSyncReport = false;
    }

    @Override
    public void recordInputEvent(@NonNull InputEventObserver.InputEvent event) {
        int device = parseDeviceNumber(event.device);
        int type = (int) Long.parseLong(event.type, 16);
        int code = (int) Long.parseLong(event.code, 16);
        int value = (int) Long.parseLong(event.value, 16);
        if (isTouchDeviceCandidate(type, code)) {
            updateTouchDevice(device);
        }
        if (device != mTouchDevice) {
            return;
        }
        appendDelayBeforeEvent(event.time);
        if (type == InputEventCodes.EV_ABS) {
            if (isTouchCoordinateCode(code)) {
                onTouch(code, value);
                mPendingSyncReport = true;
                return;
            }
        }
        if (type == InputEventCodes.EV_SYN && code == InputEventCodes.SYN_REPORT && value == 0) {
            mCode.append("ra.sendSync();\n");
            mPendingSyncReport = false;
            return;
        }
        mCode.append("ra.sendEvent(");
        mCode.append(type).append(", ")
                .append(code).append(", ")
                .append(value).append(");\n");
        mPendingSyncReport = true;
    }

    private void appendDelayBeforeEvent(double eventTime) {
        if (!mFirstEventWritten) {
            long initialDelayMillis = Math.max(1L, SystemClock.elapsedRealtime() - mRecordStartMillis);
            mCode.append("sleep(").append(initialDelayMillis).append(");\n");
            mFirstEventWritten = true;
            mLastEventTime = eventTime;
            return;
        }
        if (mLastEventTime == 0) {
            mLastEventTime = eventTime;
            return;
        }
        double deltaSeconds = eventTime - mLastEventTime;
        if (deltaSeconds > 0.001) {
            appendPendingSyncIfNeeded();
            mCode.append("sleep(").append((long) (1000L * deltaSeconds)).append(");\n");
        }
        mLastEventTime = eventTime;
    }

    private void appendPendingSyncIfNeeded() {
        if (!mPendingSyncReport) {
            return;
        }
        mCode.append("ra.sendSync();\n");
        mPendingSyncReport = false;
    }

    private boolean isTouchCoordinateCode(int code) {
        return code == InputEventCodes.ABS_MT_POSITION_X
                || code == InputEventCodes.ABS_MT_POSITION_Y
                || code == InputEventCodes.ABS_X
                || code == InputEventCodes.ABS_Y;
    }

    private boolean isTouchDeviceCandidate(int type, int code) {
        if (type == InputEventCodes.EV_ABS) {
            return code == InputEventCodes.ABS_X
                    || code == InputEventCodes.ABS_Y
                    || (code >= InputEventCodes.ABS_MT_SLOT && code <= InputEventCodes.ABS_MT_TOOL_Y);
        }
        return type == InputEventCodes.EV_KEY
                && (code == InputEventCodes.BTN_TOUCH || code == InputEventCodes.BTN_TOOL_FINGER);
    }

    private void updateTouchDevice(int device) {
        if (device < 0 || mTouchDevice == device) {
            return;
        }
        mTouchDevice = device;
        RootAutomatorEngine.setTouchDevice(device);
        mTouchCoordinateMapper.updateTouchDevice(device);
    }

    private void onTouch(int code, int value) {
        if (code == InputEventCodes.ABS_MT_POSITION_X || code == InputEventCodes.ABS_X) {
            mCode.append("ra.touchX(").append(mTouchCoordinateMapper.mapX(value)).append(");\n");
        } else if (code == InputEventCodes.ABS_MT_POSITION_Y || code == InputEventCodes.ABS_Y) {
            mCode.append("ra.touchY(").append(mTouchCoordinateMapper.mapY(value)).append(");\n");
        }
    }

    public String getCode() {
        return mCode.toString();
    }

    @Override
    public void stop() {
        super.stop();
        appendPendingSyncIfNeeded();
        mCode.append("ra.exit();");
    }

}
