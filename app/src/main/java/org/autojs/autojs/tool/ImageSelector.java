package org.autojs.autojs.tool;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.autojs.autojs.app.OnActivityResultDelegate;
import org.autojs.autojs6.R;

import java.lang.ref.WeakReference;

/**
 * Created by Stardust on Mar 5, 2017.
 */
public class ImageSelector implements OnActivityResultDelegate {

    public interface ImageSelectorCallback {
        void onImageSelected(ImageSelector selector, Uri uri);
    }

    private static final int REQUEST_CODE = "LOVE HONMUA".hashCode() >> 16;
    private final WeakReference<Activity> mActivityRef;
    private final ImageSelectorCallback mCallback;
    private final Mediator mMediator;
    private boolean mDisposable;

    public ImageSelector(Activity activity, OnActivityResultDelegate.Mediator mediator, ImageSelectorCallback callback) {
        mediator.addDelegate(REQUEST_CODE, this);
        mActivityRef = new WeakReference<>(activity);
        mCallback = callback;
        mMediator = mediator;
    }

    public void select() {
        mActivityRef.get().startActivityForResult(Intent.createChooser(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), mActivityRef.get().getString(R.string.text_select_image)),
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
