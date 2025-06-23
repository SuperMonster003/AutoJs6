package org.autojs.autojs.core.inputevent;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import org.autojs.autojs.engine.RootAutomatorEngine;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.api.Shell;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.autojs.autojs.core.inputevent.InputEventCodes.*;

/**
 * Created by Stardust on Jul 16, 2017.
 * Modified by SuperMonster003 as of May 12, 2022.
 */
public class RootAutomator implements Shell.Callback {

    private static final String LOG_TAG = "RootAutomator";

    public static final byte DATA_TYPE_SLEEP = 0;
    public static final byte DATA_TYPE_EVENT = 1;
    public static final byte DATA_TYPE_EVENT_SYNC_REPORT = 2;
    public static final byte DATA_TYPE_EVENT_TOUCH_X = 3;
    public static final byte DATA_TYPE_EVENT_TOUCH_Y = 4;

    private static final long READY_TIMEOUT = 2000;

    @Nullable
    private ScreenMetrics mScreenMetrics;
    private final Shell mShell;
    private int mDefaultId = 0;
    private final AtomicInteger mTracingId = new AtomicInteger(1);
    private final SparseIntArray mSlotIdMap = new SparseIntArray();
    private final Object mReadyLock = new Object();
    private volatile boolean mReady = false;
    private final Context mContext;

    public RootAutomator(Context context, boolean waitForReady) throws IOException {
        this(context, waitForReady ? READY_TIMEOUT : -1);
    }

    public RootAutomator(Context context, long waitForReadyTimeout) throws IOException {
        mContext = context;
        mShell = new Shell(true);
        mShell.setCallback(this);
        waitForReady(waitForReadyTimeout);
    }

    public void sendEvent(int type, int code, int value) throws IOException {
        waitForReady(READY_TIMEOUT);
        sendEventInternal(type, code, value);
    }

    private void sendEventInternal(int type, int code, int value) {
        mShell.exec(type + " " + code + " " + value);
    }

    private void waitForReady(long timeout) throws IOException {
        if (timeout < 0 || mReady) {
            return;
        }
        synchronized (mReadyLock) {
            if (mReady) {
                return;
            }
            try {
                mReadyLock.wait(timeout);
            } catch (InterruptedException e) {
                exit();
                throw new ScriptInterruptedException();
            }
        }
    }

    public void touch(int x, int y) throws IOException {
        touchX(x);
        touchY(y);
    }

    public void setScreenMetrics(int width, int height) {
        if (mScreenMetrics == null) {
            mScreenMetrics = new ScreenMetrics();
        }
        mScreenMetrics.setScreenMetrics(width, height);
    }

    public void touchX(int x) throws IOException {
        sendEvent(3, 53, scaleX(x));
    }

    private int scaleX(int x) {
        if (mScreenMetrics == null)
            return x;
        return mScreenMetrics.scaleX(x);
    }

    public void touchY(int y) throws IOException {
        sendEvent(3, 54, scaleY(y));
    }

    public void sendSync() throws IOException {
        sendEvent(EV_SYN, SYN_REPORT, 0);
    }

    public void sendMtSync() throws IOException {
        sendEvent(EV_SYN, SYN_MT_REPORT, 0);
    }

    private int scaleY(int y) {
        if (mScreenMetrics == null)
            return y;
        return mScreenMetrics.scaleY(y);

    }

    public void tap(int x, int y, int id) throws IOException {
        touchDown(x, y, id);
        touchUp(id);
    }

    public void tap(int x, int y) throws IOException {
        sendEvent(x, y, mDefaultId);
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration, int id) throws IOException {
        long now = SystemClock.uptimeMillis();
        touchDown(x1, y1, id);
        long startTime = now;
        long endTime = startTime + duration;
        while (now < endTime) {
            long elapsedTime = now - startTime;
            float alpha = (float) elapsedTime / duration;
            touchMove((int) lerp(x1, x2, alpha), (int) lerp(y1, y2, alpha), id);
            now = SystemClock.uptimeMillis();
        }
        touchUp(id);
    }

    public void swipe(int x1, int y1, int x2, int y2, int duration) throws IOException {
        swipe(x1, y1, x2, y2, duration, mDefaultId);
    }

    public void swipe(int x1, int y1, int x2, int y2) throws IOException {
        swipe(x1, y1, x2, y2, 300, mDefaultId);
    }

    public void press(int x, int y, int duration, int id) throws IOException {
        touchDown(x, y, id);
        sleep(duration);
        touchUp(id);
    }

    public void press(int x, int y, int duration) throws IOException {
        press(x, y, duration, getDefaultId());
    }

    public void longPress(int x, int y, int id) throws IOException {
        press(x, y, ViewConfiguration.getLongPressTimeout() + 200, id);
    }

    public void longPress(int x, int y) throws IOException {
        press(x, y, ViewConfiguration.getLongPressTimeout() + 200, getDefaultId());
    }

    public void touchDown(int x, int y, int id) throws IOException {
        if (mSlotIdMap.size() == 0) {
            touchDown0(x, y, id);
            return;
        }
        int slotId = mSlotIdMap.size();
        mSlotIdMap.put(id, slotId);
        sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
        sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
        sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
        sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
        sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
        sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
        sendEvent(EV_SYN, SYN_REPORT, 0);
    }

    private void touchDown0(int x, int y, int id) throws IOException {
        mSlotIdMap.put(id, 0);
        sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mTracingId.getAndIncrement());
        sendEvent(EV_KEY, BTN_TOUCH, DOWN);
        // sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000001);
        sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
        sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
        // sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
        sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
        sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 5);
        sendEvent(EV_SYN, SYN_REPORT, 0);
    }

    public void touchDown(int x, int y) throws IOException {
        touchDown(x, y, mDefaultId);
    }

    public void touchUp(int id) throws IOException {
        int slotId;
        int i = mSlotIdMap.indexOfKey(id);
        if (i < 0) {
            slotId = 0;
        } else {
            slotId = mSlotIdMap.valueAt(i);
            mSlotIdMap.removeAt(i);
        }
        sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
        sendEvent(EV_ABS, ABS_MT_TRACKING_ID, 0xffffffff);
        if (mSlotIdMap.size() == 0) {
            sendEvent(EV_KEY, BTN_TOUCH, UP);
            // sendEvent(EV_KEY, BTN_TOOL_FINGER, 0x00000000);
        }
        sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
    }

    public void touchUp() throws IOException {
        touchUp(mDefaultId);
    }

    public void touchMove(int x, int y, int id) throws IOException {
        int slotId = mSlotIdMap.get(id, 0);
        sendEvent(EV_ABS, ABS_MT_SLOT, slotId);
        sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
        sendEvent(EV_ABS, ABS_MT_POSITION_X, scaleX(x));
        sendEvent(EV_ABS, ABS_MT_POSITION_Y, scaleY(y));
        sendEvent(EV_SYN, SYN_REPORT, 0x00000000);
    }

    public void touchMove(int x, int y) throws IOException {
        touchMove(x, y, mDefaultId);
    }

    public int getDefaultId() {
        return mDefaultId;
    }

    public void setDefaultId(int defaultId) {
        mDefaultId = defaultId;
    }

    private void sleep(long duration) throws IOException {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            exit();
            throw new ScriptInterruptedException();
        }
    }

    private static float lerp(float a, float b, float alpha) {
        return (b - a) * alpha + a;
    }

    public void exit() throws IOException {
        int interval = 20;
        int maxTryTimes = 3;

        sleep(interval);
        sendEventInternal(0xffff, 0xffff, 0xefefefef);

        while (maxTryTimes-- > 0) {
            sleep(interval);
            mShell.exec("exit");
        }

        sleep(interval);
        mShell.exit();
    }

    @Override
    public void onOutput(String str) {
        /* Empty body. */
    }

    @Override
    public void onNewLine(String line) {
        /* Empty body. */
    }

    @Override
    public void onInitialized() {
        String path = RootAutomatorEngine.getExecutablePath(mContext);
        // @Reference to ozobiozobi (https://github.com/ozobiozobi) by SuperMonster003 on Mar 10, 2025.
        //  ! https://github.com/aiselp/AutoX/commit/8fe5d674f080c0ab109ce13f7cabd98795c22a1f#diff-dc753defa5bc4d7d6fab4f2e59a219ce7b89d7bcff2c9585f87c96964081ad72R290-R291
        String deviceNameOrPath = "'" + RootAutomatorEngine.getDeviceNameOrPath(mContext, InputDevices.getTouchDeviceName()) + "'";
        Log.d(LOG_TAG, "deviceNameOrPath: " + deviceNameOrPath);
        mShell.exec("chmod 777 " + path);
        String command = String.format(Locale.getDefault(),
                "%s -d %s -sw %d -sh %d", path, deviceNameOrPath,
                ScreenMetrics.getDeviceScreenWidth(),
                ScreenMetrics.getDeviceScreenHeight());
        mShell.exec(command);
        synchronized (mReadyLock) {
            Log.d(LOG_TAG, "notify ready");
            mReady = true;
            mReadyLock.notifyAll();
        }
    }

    @Override
    public void onInterrupted(InterruptedException e) {
        /* Empty body. */
    }
}

