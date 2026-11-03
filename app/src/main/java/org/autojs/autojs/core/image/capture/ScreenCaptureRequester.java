package org.autojs.autojs.core.image.capture;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs.app.SimpleActivityLifecycleCallbacks;
import org.autojs.autojs.core.activity.StartForResultActivity;
import org.autojs.autojs.util.ForegroundServiceUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeoutException;

/**
 * Created by Stardust on May 17, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 20, 2023.
public class ScreenCaptureRequester extends SimpleActivityLifecycleCallbacks implements StartForResultActivity.Callback, OnActivityResultDelegate {

    private static final long ACTIVITY_CREATE_TIMEOUT = 5000L;
    private static final long FOREGROUND_SERVICE_START_TIMEOUT = 5000L;
    private static final long FOREGROUND_SERVICE_CHECK_INTERVAL = 50L;
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 17777;

    private final Handler mHandler;

    @NonNull
    private WeakReference<Activity> mActivityRef = new WeakReference<>(null);
    private Callback mCallback;
    private Mediator mMediator;
    private boolean mWaitingForForegroundService;
    private static final Object SERVICE_USERS_LOCK = new Object();
    private static int sServiceUsers;
    private boolean mServiceUser;

    public ScreenCaptureRequester() {
        super();
        mHandler = new Handler(Looper.getMainLooper());
    }

    public interface Callback {

        void onRequestError(Throwable t);

        void onRequestResult(int resultCode, @Nullable Intent intent);

    }

    @Override
    public void onActivityCreate(StartForResultActivity startForResultActivity) {
        // This callback belongs to our proxy. An application-wide lifecycle callback can
        // otherwise attach a concurrent request to a different StartForResultActivity.
        if (startForResultActivity != null && mCallback != null) {
            mHandler.removeCallbacksAndMessages(null);
            request(startForResultActivity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        super.onActivityPaused(activity);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        // @Hint by kvii (https://github.com/kvii) on Oct 23, 2024.
        //  ! 按照官方文档 (https://developer.android.com/reference/android/media/projection/MediaProjectionManager), 启动媒体投影的示例流程如下:
        //  ! 1. AndroidManifest.xml 声明 mediaProjection 类型前台服务
        //  ! 2. 通过调用 MediaProjectionManager#createScreenCaptureIntent() 创建 intent 并传递给 Activity#startActivityForResult(Intent, int)
        //  ! 3. 在得到用户授权后, 回调 Activity#onActivityResult 中, 使用 ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION 类型启动前台服务
        //  ! 4. 再通过 MediaProjectionManager#getMediaProjection(int, Intent) 得到 MediaProjection
        //  ! 5. 通过 MediaProjection#createVirtualDisplay 调用启动媒体投影的屏幕捕获会话
        //  ! 总结就是, 要保证: 先授权, 再启动服务, 再录屏的顺序.
        //  !
        //  ! ScreenCaptureRequesterImpl#request 中 startService 的原始代码可能存在时序问题, 如果用户尚未完成授权, 在 Android 14 上启动前台服务将会崩溃.
        //  ! 应在获得同意后启动, 并等待服务实际进入前台状态.
        //  !
        //  ! en-US (translated by JetBrains AI Assistant on Jul 28, 2024):
        //  !
        //  ! According to the official documentation (https://developer.android.com/reference/android/media/projection/MediaProjectionManager),
        //  ! the example process for starting media projection is as follows:
        //  ! 1. Declare the mediaProjection type foreground service in AndroidManifest.xml
        //  ! 2. Create an intent by calling MediaProjectionManager#createScreenCaptureIntent() and pass it to Activity#startActivityForResult(Intent, int)
        //  ! 3. Upon receiving user authorization, in the callback Activity#onActivityResult, use the ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION type to start the foreground service
        //  ! 4. Then call MediaProjectionManager#getMediaProjection(int, Intent) to get MediaProjection
        //  ! 5. Call MediaProjection#createVirtualDisplay to start the screen capture session for media projection
        //  ! In summary, ensure the sequence: authorize first, then start the service, and finally start the screen recording.
        //  !
        //  ! The original code in ScreenCaptureRequesterImpl#request's startService may have timing issues,
        //  ! if the user has not yet completed authorization, starting the foreground service on Android 14 will crash.
        //  ! Start it after consent, and wait until the service actually enters foreground state.

        if (requestCode != REQUEST_CODE_MEDIA_PROJECTION) {
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            onRequest(resultCode, data);
            return;
        }
        var clazz = ScreenCapturerForegroundService.class;
        synchronized (SERVICE_USERS_LOCK) {
            if (!mServiceUser) {
                mServiceUser = true;
                ++sServiceUsers;
            }
        }
        Context applicationContext = getApplication().getApplicationContext();
        Intent serviceIntent = new Intent(applicationContext, clazz);
        ScreenCapturerForegroundService.clearForegroundStartFailure();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent);
            } else {
                applicationContext.startService(serviceIntent);
            }
        } catch (RuntimeException e) {
            onRequestError(e);
            return;
        }
        mWaitingForForegroundService = true;
        waitForForegroundService(applicationContext, clazz, resultCode, data,
                SystemClock.uptimeMillis() + FOREGROUND_SERVICE_START_TIMEOUT);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        super.onActivityDestroyed(activity);
        // A full-screen Android consent UI stops its caller while awaiting a decision.
        // Only destruction, an actual result, or explicit cancellation ends the request.
        if (activity == mActivityRef.get() && activity instanceof StartForResultActivity && !mWaitingForForegroundService) {
            if (mCallback != null) {
                mCallback.onRequestResult(0, null);
                recycle();
            }
        }
    }

    public void request(Context context, Callback callback) {
        if (mCallback == null) {
            mCallback = callback;
            getApplication().registerActivityLifecycleCallbacks(this);
            Activity activity = getActivity(context);
            if (activity instanceof DelegateHost host) {
                mMediator = host.getOnActivityResultDelegateMediator();
                mMediator.addDelegate(this);
                request(activity);
                return;
            }
            try {
                StartForResultActivity.start(context, this);
                scheduleActivityCreateTimeout();
            } catch (Exception e) {
                if (mCallback != null) {
                    mCallback.onRequestError(e);
                    recycle();
                }
            }
            return;
        }
        throw new IllegalStateException(ScreenCaptureRequester.class.getSimpleName() + "#request can be only call once");
    }

    private void request(Activity activity) {
        mActivityRef = new WeakReference<>(activity);
        try {
            MediaProjectionManager manager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            // Ask for the entire default display: on Android 14+ the dialog may otherwise offer sharing a single app,
            // whose frames do not map to screen coordinates and stop while that app is hidden.
            // zh-CN: 申请整个默认显示: 否则 Android 14+ 的对话框可能提供仅共享单个应用, 其画面与屏幕坐标不对应, 且该应用不可见时停止更新.
            Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    : manager.createScreenCaptureIntent();
            activity.startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION);
        } catch (Exception e) {
            if (mCallback != null) {
                mCallback.onRequestError(e);
                recycle();
            }
        }
    }

    private void waitForForegroundService(Context context, Class<?> clazz, int resultCode, @Nullable Intent data, long deadlineUptimeMillis) {
        if (mCallback == null) {
            return;
        }
        RuntimeException foregroundStartFailure = ScreenCapturerForegroundService.getForegroundStartFailure();
        if (foregroundStartFailure != null) {
            onRequestError(foregroundStartFailure);
            return;
        }
        if (ScreenCapturerForegroundService.isForegroundStarted() || ForegroundServiceUtils.isRunning(context, clazz)) {
            onRequest(resultCode, data);
            return;
        }
        if (SystemClock.uptimeMillis() >= deadlineUptimeMillis) {
            onRequestError(new TimeoutException("Start screen capture foreground service timeout (" + FOREGROUND_SERVICE_START_TIMEOUT + "ms)"));
            return;
        }
        mHandler.postDelayed(() -> waitForForegroundService(context, clazz, resultCode, data, deadlineUptimeMillis),
                FOREGROUND_SERVICE_CHECK_INTERVAL);
    }

    private void onRequestError(@NonNull Throwable t) {
        unbindService();
        if (mCallback != null) {
            mCallback.onRequestError(t);
            recycle();
        }
    }

    private void onRequest(int resultCode, @Nullable Intent data) {
        if (mCallback != null) {
            mCallback.onRequestResult(resultCode, data);
            recycle();
        }
    }

    private Activity getActivity(Context context) {
        while (!(context instanceof Activity)) {
            if (context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
            } else {
                return null;
            }
        }
        return (Activity) context;
    }

    private void scheduleActivityCreateTimeout() {
        mHandler.postDelayed(() -> scheduleActivityCreateTimeout(ScreenCaptureRequester.this), ACTIVITY_CREATE_TIMEOUT);
    }

    private static void scheduleActivityCreateTimeout(ScreenCaptureRequester screenCaptureRequester) {
        Callback callback = screenCaptureRequester.getCallback();
        if (callback != null) {
            callback.onRequestError(new TimeoutException("Start activity to request screen capture timeout (" + ACTIVITY_CREATE_TIMEOUT + "ms). Make sure that the app is in foreground"));
            screenCaptureRequester.recycle();
        }
    }

    @NonNull
    private static Application getApplication() {
        return AutoJs.getInstance().getApplication();
    }

    private Callback getCallback() {
        return mCallback;
    }

    private void recycle() {
        if (mMediator != null) {
            mMediator.removeDelegate(this);
            mMediator = null;
        }
        if (mActivityRef.get() != null) {
            Activity activityRef = mActivityRef.get();
            if (activityRef instanceof StartForResultActivity) {
                activityRef.finish();
            }
            mActivityRef = new WeakReference<>(null);
        }
        mWaitingForForegroundService = false;
        mCallback = null;
        getApplication().unregisterActivityLifecycleCallbacks(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    public void unbindService() {
        synchronized (SERVICE_USERS_LOCK) {
            if (!mServiceUser) return;
            mServiceUser = false;
            --sServiceUsers;
            stopForegroundServiceIfUnused(getApplication().getApplicationContext());
        }
    }

    /** Cancels this request's activity and callbacks, without cancelling other capturers. */
    public void cancel() {
        mHandler.post(() -> {
            Activity activity = mActivityRef.get();
            if (activity instanceof StartForResultActivity) {
                // Finishing only the proxy leaves its system consent activity on screen.
                activity.finishActivity(REQUEST_CODE_MEDIA_PROJECTION);
            }
            recycle();
            unbindService();
        });
    }

    public static void stopForegroundServiceIfUnused(Context context) {
        synchronized (SERVICE_USERS_LOCK) {
            if (sServiceUsers == 0) {
                context.stopService(new Intent(context, ScreenCapturerForegroundService.class));
            }
        }
    }

}
