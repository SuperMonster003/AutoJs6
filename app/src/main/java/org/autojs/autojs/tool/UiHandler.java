package org.autojs.autojs.tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on 2017/5/2.
 */
public class UiHandler extends Handler {

    private final Context mContext;

    public UiHandler(Context context) {
        super(Looper.getMainLooper());
        mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    public void toast(final String message) {
        post(() -> ViewUtils.showToast(mContext, message));
    }

    public void toast(final int resId) {
        post(() -> ViewUtils.showToast(mContext, resId));
    }

}
