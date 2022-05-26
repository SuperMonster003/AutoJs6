package com.stardust.autojs.core.image.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;

import com.stardust.app.OnActivityResultDelegate;

/**
 * Created by Stardust on 2017/5/17.
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
            onResult(resultCode, data);
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
