package org.autojs.autojs.core.inputevent;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import org.autojs.autojs.core.record.inputevent.TouchCoordinateMapper;
import org.autojs.autojs.runtime.api.AbstractShell;
import org.autojs.autojs.engine.RootAutomatorEngine;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.api.Shell;
import org.autojs.autojs.runtime.api.WrappedShizuku;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.util.RootUtils;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.autojs.autojs.core.inputevent.InputEventCodes.*;

/**
 * Created by Stardust on Jul 16, 2017.
 * Modified by SuperMonster003 as of May 12, 2022.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 7, 2026.
 */
public class RootAutomator implements Shell.Callback {

    private static final String LOG_TAG = "RootAutomator";

    public static final byte DATA_TYPE_SLEEP = 0;
    public static final byte DATA_TYPE_EVENT = 1;
    public static final byte DATA_TYPE_EVENT_SYNC_REPORT = 2;
    public static final byte DATA_TYPE_EVENT_TOUCH_X = 3;
    public static final byte DATA_TYPE_EVENT_TOUCH_Y = 4;

    private static final long READY_TIMEOUT = 5000;

    @Nullable
    private ScreenMetrics mScreenMetrics;
    @Nullable
    private final Shell mShell;
    private final boolean mUseShizukuBackend;
    @Nullable
    private final String mShizukuDevicePath;
    @Nullable
    private final TouchCoordinateMapper mTouchCoordinateMapper;
    private final StringBuilder mShizukuCommandBuffer = new StringBuilder();
    private int mDefaultId = 0;
    private final AtomicInteger mTracingId = new AtomicInteger(1);
    private final SparseIntArray mSlotIdMap = new SparseIntArray();
    private final Object mReadyLock = new Object();
    private volatile boolean mReady = false;
    @Nullable
    private volatile String mStartupError;
    private final Context mContext;

    public RootAutomator(Context context, boolean waitForReady) throws IOException {
        this(context, waitForReady ? READY_TIMEOUT : -1);
    }

    public RootAutomator(Context context, long waitForReadyTimeout) throws IOException {
        mContext = context;
        String deviceNameOrPath = resolveDeviceNameOrPath();
        String devicePathForShizuku = resolveDevicePathForShizuku(deviceNameOrPath);
        boolean rootAvailable = RootUtils.isRootAvailable();
        if (rootAvailable) {
            mUseShizukuBackend = false;
            mShizukuDevicePath = null;
            mTouchCoordinateMapper = null;
            mShell = new Shell(true);
            mShell.setCallback(this);
        } else if (canUseShizukuBackend(devicePathForShizuku)) {
            mUseShizukuBackend = true;
            mShizukuDevicePath = devicePathForShizuku;
            mShell = null;
            mTouchCoordinateMapper = new TouchCoordinateMapper(context.getApplicationContext());
            mTouchCoordinateMapper.updateTouchDevice(parseDeviceNumberFromPath(devicePathForShizuku));
            markReady();
        } else {
            if (WrappedShizuku.INSTANCE.isOperational()) {
                throw new IOException("Shizuku is available but cannot access a writable input device for RootAutomator");
            }
            throw new IOException("RootAutomator requires root access or operational Shizuku access");
        }
        waitForReady(waitForReadyTimeout);
    }

    public void sendEvent(int type, int code, int value) throws IOException {
        waitForReady(READY_TIMEOUT);
        if (mUseShizukuBackend) {
            sendEventViaShizuku(type, code, value);
        } else {
            sendEventInternal(type, code, value);
        }
    }

    private void sendEventInternal(int type, int code, int value) {
        if (mShell != null) {
            mShell.exec(type + " " + code + " " + value);
        }
    }

    private void sendEventViaShizuku(int type, int code, int value) throws IOException {
        if (TextUtils.isEmpty(mShizukuDevicePath)) {
            throw new IOException("RootAutomator Shizuku backend has no valid input device path");
        }
        mShizukuCommandBuffer.append("sendevent ")
                .append(quoteShellArg(mShizukuDevicePath))
                .append(" ")
                .append(type)
                .append(" ")
                .append(code)
                .append(" ")
                .append(value)
                .append("\n");
        if (type == EV_SYN && (code == SYN_REPORT || code == SYN_MT_REPORT)) {
            flushShizukuCommandBuffer();
        }
    }

    private void flushShizukuCommandBuffer() throws IOException {
        if (mShizukuCommandBuffer.length() == 0) {
            return;
        }
        String commandBatch = mShizukuCommandBuffer.toString();
        mShizukuCommandBuffer.setLength(0);
        try {
            AbstractShell.Result result = WrappedShizuku.INSTANCE.execCommand(mContext, commandBatch);
            if (result.code != 0) {
                throw new IOException("Shizuku sendevent failed: code="
                        + result.code
                        + ", error="
                        + result.error);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Shizuku sendevent failed", t);
        }
    }

    private void waitForReady(long timeout) throws IOException {
        if (timeout < 0 || mReady) {
            return;
        }
        final long startAt = SystemClock.uptimeMillis();
        synchronized (mReadyLock) {
            while (!mReady && mStartupError == null) {
                long elapsed = SystemClock.uptimeMillis() - startAt;
                long remaining = timeout - elapsed;
                if (remaining <= 0) {
                    break;
                }
                try {
                    mReadyLock.wait(remaining);
                } catch (InterruptedException e) {
                    exit();
                    throw new ScriptInterruptedException();
                }
            }
        }
        if (!mReady) {
            String reason = mStartupError != null ? mStartupError : "RootAutomator is not ready";
            throw new IOException(reason);
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
        int scaledX = scaleX(x);
        if (mUseShizukuBackend && mTouchCoordinateMapper != null) {
            scaledX = mTouchCoordinateMapper.mapScreenXToRaw(scaledX);
        }
        sendEvent(3, 53, scaledX);
    }

    private int scaleX(int x) {
        if (mScreenMetrics == null)
            return x;
        return mScreenMetrics.scaleX(x);
    }

    public void touchY(int y) throws IOException {
        int scaledY = scaleY(y);
        if (mUseShizukuBackend && mTouchCoordinateMapper != null) {
            scaledY = mTouchCoordinateMapper.mapScreenYToRaw(scaledY);
        }
        sendEvent(3, 54, scaledY);
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
        tap(x, y, mDefaultId);
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
        if (mUseShizukuBackend) {
            flushShizukuCommandBuffer();
            return;
        }
        int interval = 20;
        int maxTryTimes = 3;

        sleep(interval);
        sendEventInternal(0xffff, 0xffff, 0xefefefef);

        while (maxTryTimes-- > 0) {
            sleep(interval);
            mShell.exec("exit");
        }

        sleep(interval);
        if (mShell != null) {
            mShell.exit();
        }
    }

    @Override
    public void onOutput(String str) {
        if (!TextUtils.isEmpty(str)) {
            String[] lines = str.split("\\r?\\n");
            for (String line : lines) {
                inspectPotentialStartupError(line);
            }
        }
    }

    @Override
    public void onNewLine(String line) {
        inspectPotentialStartupError(line);
    }

    @Override
    public void onInitialized() {
        if (mUseShizukuBackend) {
            markReady();
            return;
        }
        String path = RootAutomatorEngine.getExecutablePath(mContext);
        // @Reference to ozobiozobi (https://github.com/ozobiozobi) by SuperMonster003 on Mar 10, 2025.
        //  ! https://github.com/aiselp/AutoX/commit/8fe5d674f080c0ab109ce13f7cabd98795c22a1f#diff-dc753defa5bc4d7d6fab4f2e59a219ce7b89d7bcff2c9585f87c96964081ad72R290-R291
        String deviceNameOrPath = resolveDeviceNameOrPath();
        if (TextUtils.isEmpty(deviceNameOrPath)) {
            setStartupError("Failed to resolve a valid touch device path for RootAutomator");
            return;
        }
        String quotedExecutablePath = quoteShellArg(path);
        String quotedDeviceNameOrPath = quoteShellArg(deviceNameOrPath);
        Log.d(LOG_TAG, "deviceNameOrPath: " + deviceNameOrPath);
        mShell.exec("chmod 777 " + quotedExecutablePath);
        String command = String.format(Locale.getDefault(),
                "%s -d %s -sw %d -sh %d", quotedExecutablePath, quotedDeviceNameOrPath,
                ScreenMetrics.getDeviceScreenWidth(),
                ScreenMetrics.getDeviceScreenHeight());
        if (mShell != null) {
            mShell.exec(command);
        }
        markReady();
    }

    @Nullable
    private String resolveDeviceNameOrPath() {
        String byEngine = RootAutomatorEngine.getDeviceNameOrPath(mContext, InputDevices.getTouchDeviceName());
        if (isValidDeviceNameOrPath(byEngine)) {
            return byEngine;
        }
        int touchDeviceId = InputDevices.getTouchDeviceId();
        if (touchDeviceId >= 0) {
            RootAutomatorEngine.setTouchDevice(touchDeviceId);
            return "/dev/input/event" + touchDeviceId;
        }
        String byName = InputDevices.getTouchDeviceName();
        if (isValidDeviceNameOrPath(byName)) {
            return byName;
        }
        return null;
    }

    @Nullable
    private String resolveDevicePathForShizuku(@Nullable String deviceNameOrPath) {
        if (deviceNameOrPath != null && deviceNameOrPath.startsWith("/dev/input/event")) {
            return deviceNameOrPath;
        }
        int touchDeviceId = InputDevices.getTouchDeviceId();
        if (touchDeviceId >= 0) {
            RootAutomatorEngine.setTouchDevice(touchDeviceId);
            return "/dev/input/event" + touchDeviceId;
        }
        return null;
    }

    private boolean canUseShizukuBackend(@Nullable String devicePath) {
        if (!WrappedShizuku.INSTANCE.isOperational() || TextUtils.isEmpty(devicePath)) {
            return false;
        }
        try {
            AbstractShell.Result result = WrappedShizuku.INSTANCE.execCommand(
                    mContext,
                    "test -w " + quoteShellArg(devicePath)
            );
            if (result.code == 0) {
                return true;
            }
            Log.w(LOG_TAG, "Shizuku backend is unavailable for device path " + devicePath + ": " + result.error);
        } catch (Throwable t) {
            Log.w(LOG_TAG, "Failed to verify Shizuku backend", t);
        }
        return false;
    }

    private boolean isValidDeviceNameOrPath(@Nullable String value) {
        return !TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim());
    }

    private String quoteShellArg(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private int parseDeviceNumberFromPath(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return -1;
        }
        int end = path.length() - 1;
        while (end >= 0 && Character.isDigit(path.charAt(end))) {
            end--;
        }
        if (end == path.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(end + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void markReady() {
        synchronized (mReadyLock) {
            Log.d(LOG_TAG, "notify ready");
            mReady = true;
            mReadyLock.notifyAll();
        }
    }

    private void inspectPotentialStartupError(@Nullable String line) {
        if (TextUtils.isEmpty(line) || mReady) {
            return;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("no such file")
                || lower.contains("permission denied")
                || lower.contains("not found")
                || lower.contains("invalid argument")
                || lower.contains("failed")) {
            setStartupError(line);
        }
    }

    private void setStartupError(String message) {
        synchronized (mReadyLock) {
            if (mStartupError == null) {
                mStartupError = message;
            }
            mReadyLock.notifyAll();
        }
    }

    @Override
    public void onInterrupted(InterruptedException e) {
        /* Empty body. */
    }
}

