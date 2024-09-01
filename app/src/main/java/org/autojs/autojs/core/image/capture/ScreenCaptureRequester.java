package org.autojs.autojs.core.image.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;

import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs.util.ForegroundServiceUtils;

/**
 * Created by Stardust on May 17, 2017.
 */
public interface ScreenCaptureRequester {

    void request();

    void cancel();

    void setOnActivityResultCallback(Callback callback);

    interface Callback {

        void onRequestResult(int result, Intent data);

    }

    class ActivityScreenCaptureRequester extends AbstractScreenCaptureRequester implements ScreenCaptureRequester, OnActivityResultDelegate {

        private static final int REQUEST_CODE_MEDIA_PROJECTION = 17777;
        private final OnActivityResultDelegate.Mediator mMediator;
        private final Activity mActivity;

        public ActivityScreenCaptureRequester(Mediator mediator, Activity activity) {
            mMediator = mediator;
            mActivity = activity;
            mMediator.addDelegate(REQUEST_CODE_MEDIA_PROJECTION, this);
        }

        @Override
        public void request() {
            MediaProjectionManager manager = (MediaProjectionManager) mActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mActivity.startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            mResult = data;
            mMediator.removeDelegate(this);
            // 按照官方文档，https://developer.android.com/reference/android/media/projection/MediaProjectionManager，启动媒体投影的示例流程如下：
            // 1. AndroidManifest.xml声明mediaProjection类型前台服务
            // 2. 通过调用MediaProjectionManager#createScreenCaptureIntent()创建intent并传递给Activity#startActivityForResult(Intent, int)
            // 3. 在得到用户授权后，回调Activity#onActivityResult中，使用 ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION类型 启动前台服务
            // 4. 再通过MediaProjectionManager#getMediaProjection(int, Intent)得到MediaProjection
            // 5. 通过MediaProjection#createVirtualDisplay调用启动媒体投影的屏幕捕获会话
            // 总结就是，要保证：先授权，再启动服务，再录屏的顺序

            // 原代码在ScreenCaptureRequesterImpl#requst中startService会有时序问题，此时用户还没授权完成时，Android 14+启动前台服务实测会崩溃
            // 改成bindService并等待onServiceConnected回调
            ForegroundServiceUtils.requestReadyIfNeeded(mActivity.getApplicationContext(), ScreenCapturerForegroundService.class, () -> onResult(resultCode, data));
        }
    }

    abstract class AbstractScreenCaptureRequester implements ScreenCaptureRequester {

        protected Callback mCallback;

        protected Intent mResult;

        @Override
        public void setOnActivityResultCallback(Callback callback) {
            mCallback = callback;
        }

        public void onResult(int resultCode, Intent data) {
            mResult = data;
            if (mCallback != null)
                mCallback.onRequestResult(resultCode, data);
        }

        @Override
        public void cancel() {
            if (mResult != null)
                return;
            if (mCallback != null)
                mCallback.onRequestResult(Activity.RESULT_CANCELED, null);
        }
    }
}
