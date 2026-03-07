package org.autojs.autojs.event;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.core.accessibility.AccessibilityService;
import org.autojs.autojs.core.accessibility.OnKeyListener;
import org.autojs.autojs.core.inputevent.InputEventObserver;
import org.autojs.autojs.core.inputevent.ShellKeyObserver;
import org.autojs.autojs.core.pref.Pref;

/**
 * Created by Stardust on Aug 14, 2017.
 */
public class GlobalKeyObserver implements OnKeyListener, ShellKeyObserver.KeyListener {

    public interface OnVolumeDownListener {
        void onVolumeDown();
    }

    private static final EventDispatcher.Event<OnVolumeDownListener> VOLUME_DOWN_EVENT = OnVolumeDownListener::onVolumeDown;
    private static final String LOG_TAG = "GlobalKeyObserver";
    private static GlobalKeyObserver sSingleton;
    private final EventDispatcher<OnVolumeDownListener> mVolumeDownEventDispatcher;
    private boolean mVolumeDownFromShell, mVolumeDownFromAccessibility;
    private boolean mVolumeUpFromShell, mVolumeUpFromAccessibility;

    private GlobalKeyObserver(Context applicationContext) {
        mVolumeDownEventDispatcher = new EventDispatcher<>();
        AccessibilityService.Companion.getStickOnKeyObserver().addListener(this);
        ShellKeyObserver observer = new ShellKeyObserver();
        observer.setKeyListener(this);
        InputEventObserver io = InputEventObserver.getGlobal(applicationContext);
        io.addListener(observer);
        // Starts getevent asynchronously with a delay to avoid blocking the first frame.
        // zh-CN: 将底层 getevent 的启动改为异步, 并稍作延迟, 避免阻塞首帧.
        new Handler(applicationContext.getMainLooper()).postDelayed(io::ensureObservedAsync, 240);
    }

    public static void initIfNeeded(Context applicationContext) {
        if (isVolumeControlEnabled()) makeSureSingletonInitialized(applicationContext);
    }

    public static void init(Context applicationContext) {
        makeSureSingletonInitialized(applicationContext);
    }

    public static GlobalKeyObserver getSingleton(Context applicationContext) {
        makeSureSingletonInitialized(applicationContext);
        return sSingleton;
    }

    private static void makeSureSingletonInitialized(Context applicationContext) {
        if (sSingleton == null) {
            sSingleton = new GlobalKeyObserver(applicationContext);
        }
    }

    public void onVolumeUp() {
        Log.d(LOG_TAG, "onVolumeUp at " + System.currentTimeMillis());
        if (Pref.isUseVolumeControlRunningEnabled()) {
            AutoJs.getInstance().getScriptEngineService().stopAllAndToast();
        }
    }

    public void onVolumeDown() {
        Log.d(LOG_TAG, "onVolumeDown at " + System.currentTimeMillis());
        mVolumeDownEventDispatcher.dispatchEvent(VOLUME_DOWN_EVENT);
    }

    public void addVolumeDownListener(OnVolumeDownListener listener) {
        mVolumeDownEventDispatcher.addListener(listener);
    }

    public boolean removeVolumeDownListener(OnVolumeDownListener listener) {
        return mVolumeDownEventDispatcher.removeListener(listener);
    }

    @Override
    public void onKeyEvent(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mVolumeDownFromShell) {
                mVolumeDownFromShell = false;
                return;
            }
            mVolumeDownFromAccessibility = true;
            onVolumeDown();
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (mVolumeUpFromShell) {
                mVolumeUpFromShell = false;
                return;
            }
            mVolumeUpFromAccessibility = true;
            onVolumeUp();
        }
    }

    @Override
    public void onKeyDown(String keyName) {
        /* Empty body. */
    }

    @Override
    public void onKeyUp(String keyName) {
        if ("KEY_VOLUMEUP".equals(keyName)) {
            if (mVolumeUpFromAccessibility) {
                mVolumeUpFromAccessibility = false;
                return;
            }
            mVolumeUpFromShell = true;
            onVolumeUp();
        } else if ("KEY_VOLUMEDOWN".equals(keyName)) {
            if (mVolumeDownFromAccessibility) {
                mVolumeDownFromAccessibility = false;
                return;
            }
            mVolumeDownFromShell = true;
            onVolumeDown();
        }
    }

    private static boolean isVolumeControlEnabled() {
        return Pref.isUseVolumeControlRunningEnabled() || Pref.isUseVolumeControlRecordEnabled();
    }
}
