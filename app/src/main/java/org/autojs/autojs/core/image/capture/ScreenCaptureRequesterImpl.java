package org.autojs.autojs.core.image.capture;

import android.app.Activity;

import org.autojs.autojs.AbstractAutoJs;
import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs.util.ForegroundServiceUtils;

public class ScreenCaptureRequesterImpl extends ScreenCaptureRequester.AbstractScreenCaptureRequester {

    AbstractAutoJs mAutoJs;

    public ScreenCaptureRequesterImpl(AbstractAutoJs autojs) {
        super();
        mAutoJs = autojs;
    }

    @Override
    public void setOnActivityResultCallback(Callback callback) {
        super.setOnActivityResultCallback((result, data) -> {
            mResult = data;
            callback.onRequestResult(result, data);
        });
    }

    @Override
    public void request() {
        ForegroundServiceUtils.requestIfNeeded(mAutoJs.getContext(), ScreenCapturerForegroundService.class);

        Activity activity = mAutoJs.getAppUtils().getCurrentActivity();

        if (activity instanceof OnActivityResultDelegate.DelegateHost) {
            ScreenCaptureRequester requester = new ActivityScreenCaptureRequester(
                    ((OnActivityResultDelegate.DelegateHost) activity).getOnActivityResultDelegateMediator(), activity);
            requester.setOnActivityResultCallback(mCallback);
            requester.request();
        } else {
            ScreenCaptureRequestActivity.request(mAutoJs.getContext(), mCallback);
        }
    }
}
