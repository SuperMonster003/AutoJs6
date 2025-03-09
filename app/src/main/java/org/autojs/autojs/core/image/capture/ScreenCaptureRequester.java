package org.autojs.autojs.core.image.capture;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 17777;

    private final Handler mHandler;

    @NonNull
    private WeakReference<Activity> mActivityRef = new WeakReference<>(null);
    private Callback mCallback;
    private Mediator mMediator;
    private ServiceConnection mServiceConnection;

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
        /* Empty body. */
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        super.onActivityCreated(activity, bundle);
        if (activity instanceof StartForResultActivity && mActivityRef.get() == null) {
            mHandler.removeCallbacksAndMessages(null);
            request(activity);
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
        //  ! 原代码在 ScreenCaptureRequesterImpl#requst 中 startService 会有时序问题, 此时用户还没授权完成时, Android 14 启动前台服务实测会崩溃.
        //  ! 改成 bindService 并等待 onServiceConnected 回调.
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
        //  ! Change it to bindService and wait for the onServiceConnected callback.
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            Context applicationContext = getApplication().getApplicationContext();
            applicationContext.startService(new Intent(applicationContext, ScreenCapturerForegroundService.class));
            var clazz = ScreenCapturerForegroundService.class;
            if (ForegroundServiceUtils.isRunning(applicationContext, clazz)) {
                onRequest(resultCode, data);
            } else {
                mServiceConnection = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        onRequest(resultCode, data);
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        /* Empty body. */
                    }
                };
                applicationContext.bindService(new Intent(applicationContext, clazz), mServiceConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        super.onActivityStopped(activity);
        if (activity == mActivityRef.get() && activity instanceof StartForResultActivity) {
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
                return;
            } catch (Exception e) {
                if (mCallback != null) {
                    mCallback.onRequestError(e);
                    recycle();
                }
                return;
            }
        }
        throw new IllegalStateException(ScreenCaptureRequester.class.getSimpleName() + "#request can be only call once");
    }

    private void request(Activity activity) {
        mActivityRef = new WeakReference<>(activity);
        try {
            MediaProjectionManager manager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            activity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION);
        } catch (Exception e) {
            if (mCallback != null) {
                mCallback.onRequestError(e);
                recycle();
            }
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
        mCallback = null;
        getApplication().unregisterActivityLifecycleCallbacks(this);
        mHandler.removeCallbacksAndMessages(null);
    }

    public void unbindService() {
        if (mServiceConnection != null) {
            getApplication().getApplicationContext().unbindService(mServiceConnection);
            mServiceConnection = null;
        }
    }

}
