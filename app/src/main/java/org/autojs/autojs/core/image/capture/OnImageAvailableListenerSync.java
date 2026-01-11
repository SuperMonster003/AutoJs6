package org.autojs.autojs.core.image.capture;

import android.media.ImageReader;

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 * Modified by SuperMonster003 as of Jan 11, 2026.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
public record OnImageAvailableListenerSync(ScreenCapturer screenCapturer) implements ImageReader.OnImageAvailableListener {

    public void onImageAvailable(ImageReader imageReader) {
        // Use the actual callback parameter ImageReader.
        // zh-CN: 使用回调参数中的实际 ImageReader, 避免 refreshImageReader 后因引用过期导致无法唤醒等待线程.
        screenCapturer.setImageListenerSync(imageReader);
    }

}
