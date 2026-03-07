package org.autojs.autojs.core.inputevent;

import android.content.Context;

import androidx.annotation.NonNull;

import android.text.TextUtils;

import org.autojs.autojs6.R;
import org.autojs.autojs.core.record.inputevent.EventFormatException;
import org.autojs.autojs.runtime.api.AbstractShell;
import org.autojs.autojs.runtime.api.Shell;
import org.autojs.autojs.runtime.api.WrappedShizuku;
import org.autojs.autojs.util.RootUtils;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on Aug 4, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 7, 2026.
 */
public class InputEventObserver {

    private static InputEventObserver sGlobal;
    private static final int SHIZUKU_GETEVENT_BATCH_SIZE = 1;
    private static final long SHIZUKU_RETRY_DELAY = 120;

    private final CopyOnWriteArrayList<InputEventListener> mInputEventListeners = new CopyOnWriteArrayList<>();
    private final Context mContext;
    private Shell mShell;
    private Thread mShizukuObserverThread;
    private volatile boolean mShizukuObserverRunning;

    public InputEventObserver(Context context) {
        mContext = context;
    }

    public static InputEventObserver getGlobal(Context context) {
        if (sGlobal == null) {
            // noinspection UnnecessaryLocalVariable
            InputEventObserver observer = new InputEventObserver(context);
            sGlobal = observer;
            // @Hint by SuperMonster003 on Nov 16, 2025.
            //  ! If calling observe() here, it will synchronously create a Shell
            //  ! and block the main thread during app startup.
            //  ! Changed to lazy initialization, asynchronously call ensureObservedAsync()
            //  ! at appropriate time (like in GlobalKeyObserver constructor).
            //  ! zh-CN:
            //  ! 如果在此处调用 observe(), 会在应用启动时同步创建 Shell 并阻塞主线程.
            //  ! 修改为懒初始化, 在适当时机 (如 GlobalKeyObserver 构造函数中) 异步调用 ensureObservedAsync().
            //  # observer.observe();
        }
        return sGlobal;
    }

    public static class InputEvent {
        static final Pattern PATTERN = Pattern.compile("^\\[([^]]*)]\\s+([^:]*):\\s+(\\S*)\\s+(\\S*)\\s+(\\S*)\\s*$");

        static InputEvent parse(String eventStr) {
            Matcher matcher = PATTERN.matcher(eventStr);
            if (!matcher.matches()) {
                throw new EventFormatException(eventStr);
            }
            double time;
            try {
                String grouped = matcher.group(1);
                time = Double.parseDouble(grouped);
            } catch (NumberFormatException e) {
                throw new EventFormatException(eventStr, e);
            }
            return new InputEvent(time, matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
        }

        public double time;
        public String device;
        public String type;
        public String code;
        public String value;

        public InputEvent(double time, String device, String type, String code, String value) {
            this.time = time;
            this.device = device;
            this.type = type;
            this.code = code;
            this.value = value;
        }

        @NonNull
        @Override
        public String toString() {
            return "Event{" +
                    "time=" + time +
                    ", device='" + device + '\'' +
                    ", type='" + type + '\'' +
                    ", code='" + code + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    public interface InputEventListener {
        void onInputEvent(@NonNull InputEvent e);
    }

    public void observe() {
        if (isObserved()) {
            throw new IllegalStateException(mContext.getString(R.string.error_function_called_more_than_once, "InputEventObserver.observe"));
        }
        if (observeByShizuku()) {
            return;
        }
        observeByRoot();
    }

    private void observeByRoot() {
        mShell = new Shell(mContext, true);
        mShell.setCallback(new Shell.SimpleCallback() {
            @Override
            public void onNewLine(String str) {
                if (mShell.isInitialized()) {
                    onInputEvent(str);
                }
            }

            @Override
            public void onInitialized() {
                mShell.exec("getevent -t");
            }
        });
    }

    private boolean observeByShizuku() {
        if (!WrappedShizuku.INSTANCE.isOperational()) {
            return false;
        }
        mShizukuObserverRunning = true;
        mShizukuObserverThread = new Thread(() -> {
            while (mShizukuObserverRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    AbstractShell.Result result = WrappedShizuku.INSTANCE.execCommand(mContext, "getevent -t -c " + SHIZUKU_GETEVENT_BATCH_SIZE);
                    if (result != null && result.code == 0) {
                        String output = result.result;
                        if (!TextUtils.isEmpty(output)) {
                            for (String line : output.split("\\r?\\n")) {
                                onInputEvent(line);
                            }
                        }
                        continue;
                    }
                    if (!WrappedShizuku.INSTANCE.isOperational() && RootUtils.isRootAvailable()) {
                        mShizukuObserverRunning = false;
                        observeByRoot();
                        return;
                    }
                    Thread.sleep(SHIZUKU_RETRY_DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    try {
                        Thread.sleep(SHIZUKU_RETRY_DELAY);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "InputEventObserver-Shizuku");
        mShizukuObserverThread.start();
        return true;
    }

    private boolean isObserved() {
        return mShell != null || (mShizukuObserverThread != null && mShizukuObserverThread.isAlive());
    }

    // Ensure lazy start observation in background thread
    // to avoid blocking the main thread/app startup.
    // zh-CN: 确保在后台线程懒启动观察, 避免在主线程/应用启动期阻塞.
    public void ensureObservedAsync() {
        if (isObserved()) return;
        synchronized (this) {
            if (isObserved()) return;
            new Thread(() -> {
                try {
                    observe();
                } catch (Throwable ignored) {
                    // Do not throw exceptions to main thread to avoid affecting first screen;
                    // specific errors will be handled at the usage point.
                    // zh-CN: 启动失败时不抛到主线程, 避免影响首屏; 具体错误在使用处再处理.
                }
            }, "InputEventObserver-Init").start();
        }
    }

    public void onInputEvent(String eventStr) {
        if (!TextUtils.isEmpty(eventStr) && eventStr.startsWith("[")) {
            try {
                InputEvent event = InputEvent.parse(eventStr);
                dispatchInputEvent(event);
            } catch (Exception ignored) {
                /* Ignored. */
            }
        }
    }

    private void dispatchInputEvent(InputEvent event) {
        for (InputEventListener listener : mInputEventListeners) {
            listener.onInputEvent(event);
        }
    }

    public void addListener(InputEventListener listener) {
        mInputEventListeners.add(listener);
    }

    public boolean removeListener(InputEventListener listener) {
        return mInputEventListeners.remove(listener);
    }

    public void recycle() {
        mShizukuObserverRunning = false;
        Thread shizukuObserverThread = mShizukuObserverThread;
        if (shizukuObserverThread != null) {
            shizukuObserverThread.interrupt();
            mShizukuObserverThread = null;
        }
        if (mShell != null) {
            mShell.exit();
            mShell = null;
        }
    }

}
