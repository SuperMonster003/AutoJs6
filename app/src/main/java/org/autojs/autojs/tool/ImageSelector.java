package org.autojs.autojs.tool;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Mar 5, 2017.
 */
public class ImageSelector implements OnActivityResultDelegate {

    public interface ImageSelectorCallback {
        void onImageSelected(ImageSelector selector, Uri uri);
    }

    private static final int REQUEST_CODE = "LOVE HONMUA".hashCode() >> 16;
    private final Activity mActivity;
    private final ImageSelectorCallback mCallback;
    private final Mediator mMediator;
    private boolean mDisposable;

    public ImageSelector(Activity activity, OnActivityResultDelegate.Mediator mediator, ImageSelectorCallback callback) {
        mediator.addDelegate(REQUEST_CODE, this);
        mActivity = activity;
        mCallback = callback;
        mMediator = mediator;
    }

    public void select() {
        mActivity.startActivityForResult(Intent.createChooser(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), mActivity.getString(R.string.text_select_image)),
                REQUEST_CODE);
    }

    public ImageSelector disposable() {
        mDisposable = true;
        return this;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mDisposable) {
            mMediator.removeDelegate(this);
        }
        if (data == null) {
            mCallback.onImageSelected(this, null);
            return;
        }
        mCallback.onImageSelected(this, data.getData());

    }

}
