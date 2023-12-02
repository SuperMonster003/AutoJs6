package org.autojs.autojs.core.ui.inflater;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * Created by Stardust on Nov 3, 2017.
 */
public interface ImageLoader {

    interface BitmapCallback {
        void onLoaded(Bitmap bitmap);
    }

    interface DrawableCallback {
        void onLoaded(Drawable drawable);
    }

    void loadInto(ImageView view, Uri uri);

    void loadIntoBackground(View view, Uri uri);

    @Nullable Drawable load(View view, Uri uri);

    void load(View view, Uri uri, DrawableCallback callback);

    void load(View view, Uri uri, BitmapCallback callback);

}
