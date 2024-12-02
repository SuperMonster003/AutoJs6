package org.autojs.autojs.core.image.capture;

import android.media.ImageReader;

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
public class OnImageAvailableListenerAsync implements ImageReader.OnImageAvailableListener {

    public final ScreenCapturer mScreenCapturer;

    public OnImageAvailableListenerAsync(ScreenCapturer screenCapturer) {
        mScreenCapturer = screenCapturer;
    }

    public final void onImageAvailable(ImageReader imageReader) {
        mScreenCapturer.setImageListenerAsync(imageReader);
    }

}
