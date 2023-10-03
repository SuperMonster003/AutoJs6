package org.autojs.autojs.runtime.api;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.autojs.autojs.runtime.ScriptRuntime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by SuperMonster003 on Aug 3, 2023.
 */
public class ScriptToast {

    private static final String TAG = ScriptToast.class.getSimpleName();
    private final Context mContext;
    private final ScriptRuntime mScriptRuntime;
    private final Handler mUiHandler;

    private static final ConcurrentHashMap<Toast, ScriptRuntime> pool = new ConcurrentHashMap<>();

    public ScriptToast(Context context, ScriptRuntime runtime) {
        mContext = context;
        mScriptRuntime = runtime;
        mUiHandler = mScriptRuntime.uiHandler;
    }

    // @Caution by SuperMonster003 on Oct 11, 2022.
    //  ! android.widget.ScriptToast.makeText() doesn't work well on Android API Level 28 (Android 9) [P].
    //  ! There hasn't been a solution for this so far.
    //  ! Tested devices:
    //  ! 1. SONY XPERIA XZ1 Compact (G8441)
    //  ! 2. Android Studio AVD (Android 9.0 x86)
    public void makeToast(String msg, boolean isLong, boolean isForcible) {
        synchronized (ScriptToast.class) {
            if (isForcible) {
                dismissAll();
            }
            Toast toast = Toast.makeText(mContext, msg, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
            Log.d(TAG + " new toast", toast.toString());
            toast.show();
            Log.d(TAG + " before put", pool.toString());
            pool.put(toast, mScriptRuntime);
            Log.d(TAG + " after put", pool.toString());
            addCallback(toast);
        }
    }

    private void addCallback(Toast toast) {
        // FIXME by SuperMonster003 on Aug 3, 2023.
        //  ! A more graceful way is needed.
        // @Hint by SuperMonster003 on Aug 3, 2023.
        //  ! It is proved that Toast.Callback for Android API Level 30+
        //  ! was not a good replacement.
        mUiHandler.postDelayed(() -> dismiss(toast), getAccumulatedDuration());
    }

    private long getAccumulatedDuration() {
        var sum = 200L;
        for (Map.Entry<Toast, ScriptRuntime> entry : pool.entrySet()) {
            Toast toast = entry.getKey();
            sum += toast.getDuration() == Toast.LENGTH_SHORT ? 2_000L : 3_500L;
        }
        return sum;
    }

    private void dismiss(Toast aim) {
        Log.d(TAG + " before dismiss", pool.toString());
        for (Map.Entry<Toast, ScriptRuntime> entry : pool.entrySet()) {
            Toast toast = entry.getKey();
            ScriptRuntime scriptRuntime = entry.getValue();
            if (scriptRuntime == mScriptRuntime && toast == aim) {
                toast.cancel();
                pool.remove(toast);
            }
        }
        Log.d(TAG + " after dismiss", pool.toString());
    }

    public void dismissAll() {
        Log.d(TAG + " before disAll", pool.toString());
        for (Map.Entry<Toast, ScriptRuntime> entry : pool.entrySet()) {
            Toast toast = entry.getKey();
            ScriptRuntime scriptRuntime = entry.getValue();
            if (scriptRuntime == mScriptRuntime) {
                toast.cancel();
                pool.remove(toast);
            }
        }
        Log.d(TAG + " after disAll", pool.toString());
    }

}
