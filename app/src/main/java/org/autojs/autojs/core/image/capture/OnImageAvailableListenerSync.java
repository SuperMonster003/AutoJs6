package org.autojs.autojs.core.image.capture;

import android.media.ImageReader;

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
public class OnImageAvailableListenerSync implements ImageReader.OnImageAvailableListener {

    public final ScreenCapturer mScreenCapturer;
    public final ImageReader mImageReader;

    public OnImageAvailableListenerSync(ScreenCapturer screenCapturer, ImageReader imageReader) {
        this.mScreenCapturer = screenCapturer;
        this.mImageReader = imageReader;
    }

    public final void onImageAvailable(ImageReader imageReader) {
        mScreenCapturer.setImageListenerSync(mImageReader);
    }

}
