package org.autojs.autojs.core.image.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on May 17, 2017.
 * Modified by SuperMonster003 as of May 19, 2022.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
public class ScreenCapturer {

    private static final Pattern PATTERN_BUFFER_FORMAT_EXCEPTION = Pattern.compile("buffer format ([0-9a-zA-Z]+) doesn't match");

    public static final int ORIENTATION_AUTO = Configuration.ORIENTATION_UNDEFINED; // 0
    public static final int ORIENTATION_LANDSCAPE = Configuration.ORIENTATION_LANDSCAPE; // 1
    public static final int ORIENTATION_PORTRAIT = Configuration.ORIENTATION_PORTRAIT; // 2
    public static final int ORIENTATION_NONE = -1;

    private volatile Image mUnderUsingImage;
    private final int mScreenDensity;
    private final Handler mHandler;
    private final Context mContext;
    private final int mOrientation;
    private final Object mImageAvailableLock = new Object();
    private final Options mOptions;
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private OnScreenCaptureAvailableListener mOnScreenCaptureAvailableListener;
    private int mDetectedOrientation;
    private int mPixelFormat = PixelFormat.RGBA_8888;
    private volatile boolean mImageAvailable = false;
    private boolean mShouldRefreshVirtualDisplayOnNextCapture = false;

    public ScreenCapturer(Context context, Intent data, Options options, Handler handler) {
        mContext = context;
        mOptions = options;
        mHandler = handler;
        mMediaProjection = ((MediaProjectionManager) mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(Activity.RESULT_OK, (Intent) data.clone());
        mScreenDensity = options.density;
        mOrientation = options.orientation;
        refreshVirtualDisplay(mOrientation == ORIENTATION_AUTO ? mDetectedOrientation : mOrientation, true);
        refreshDetectedOrientation();
        EventBus.getDefault().register(this);
    }

    private void refreshDetectedOrientation() {
        mDetectedOrientation = mContext.getResources().getConfiguration().orientation;
    }

    public record Options(int width, int height, int orientation, int density, boolean isAsync) {
        @NonNull
        @Override
        public String toString() {
            return MessageFormat.format(
                    "Options'{'width={0}, height={1}, orientation={2}, density={3}, isAsync={4}'}'",
                    width, height, orientation, density, isAsync);
        }
    }

    public interface OnScreenCaptureAvailableListener {
        void onCaptureAvailable(Image image);
    }

    private Image acquireLatestImage() {
        waitForImageAvailable();
        try {
            return mImageReader.acquireLatestImage();
        } catch (UnsupportedOperationException ex) {
            Integer pixelFormat = getPixelFormat(ex);
            if (pixelFormat != null) {
                setPixelFormat(pixelFormat);
                waitForImageAvailable();
                return mImageReader.acquireLatestImage();
            }
            throw ex;
        }
    }

    @Nullable
    private Integer getPixelFormat(UnsupportedOperationException ex) {
        String message = ex.getMessage();
        if (message != null) {
            Matcher matcher = PATTERN_BUFFER_FORMAT_EXCEPTION.matcher(message);
            if (matcher.find()) {
                final String group = matcher.group(1);
                if (group != null && group.startsWith("0x")) {
                    return Integer.parseInt(group.substring(2), 16);
                }
            }
        }
        return null;
    }

    private void initVirtualDisplay(int width, int height, int screenDensity) {
        refreshImageReader(width, height);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mMediaProjection.registerCallback(new MediaProjection.Callback() {
                /* Empty body. */
            }, mHandler);
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(ScreenCapturer.class.getSimpleName(), width, height, screenDensity, 16, mImageReader.getSurface(), null, null);
    }

    private void refreshImageReader(int width, int height) {
        if (mImageReader != null) {
            mImageReader.close();
        }
        synchronized (mImageAvailableLock) {
            mImageAvailable = false;
        }
        int maxImages = mOptions.isAsync ? 1 : 3;
        mImageReader = ImageReader.newInstance(width, height, mPixelFormat, maxImages);
        setImageListener(mHandler);
    }

    private void refreshVirtualDisplay(int orientation, boolean isInit) {
        AtomicInteger width = new AtomicInteger();
        AtomicInteger height = new AtomicInteger();
        if (orientation == ORIENTATION_NONE) {
            width.set(mOptions.width);
            height.set(mOptions.height);
        } else {
            width.set(ScreenMetrics.getOrientationAwareScreenWidth(orientation));
            height.set(ScreenMetrics.getOrientationAwareScreenHeight(orientation));
        }
        if (mVirtualDisplay == null) {
            if (isInit) {
                initVirtualDisplay(width.get(), height.get(), mScreenDensity);
            }
        } else {
            refreshImageReader(width.get(), height.get());
            mVirtualDisplay.setSurface(mImageReader.getSurface());
            mVirtualDisplay.resize(width.get(), height.get(), mScreenDensity);
        }
    }

    private void setImageListener(Handler handler) {
        ImageReader.OnImageAvailableListener o = mOptions.isAsync
                ? new OnImageAvailableListenerAsync(this)
                : new OnImageAvailableListenerSync(this, mImageReader);
        mImageReader.setOnImageAvailableListener(o, handler);
    }

    public void setImageListenerAsync(ImageReader imageReader) {
        if (mOnScreenCaptureAvailableListener != null) {
            Image acquireLatestImage = imageReader.acquireLatestImage();
            mOnScreenCaptureAvailableListener.onCaptureAvailable(acquireLatestImage);
            acquireLatestImage.close();
        }
    }

    public void setImageListenerSync(ImageReader imageReader) {
        imageReader.setOnImageAvailableListener(null, null);
        if (!mImageAvailable && imageReader == mImageReader) {
            synchronized (mImageAvailableLock) {
                mImageAvailable = true;
                mImageAvailableLock.notifyAll();
            }
        }
    }

    private void setPixelFormat(int pixelFormat) {
        mPixelFormat = pixelFormat;
        refreshImageReader(mImageReader.getWidth(), mImageReader.getHeight());
        mVirtualDisplay.setSurface(mImageReader.getSurface());
    }

    private void waitForImageAvailable() {
        if (!mImageAvailable) {
            synchronized (mImageAvailableLock) {
                if (!mImageAvailable) {
                    try {
                        mImageAvailableLock.wait();
                    } catch (InterruptedException ex) {
                        throw new ScriptInterruptedException();
                    }
                }
            }
        }
    }

    @Nullable
    public Image capture() {
        if (mOptions.isAsync) {
            throw new IllegalStateException("capture() is not available in async mode");
        }
        if (mShouldRefreshVirtualDisplayOnNextCapture) {
            mShouldRefreshVirtualDisplayOnNextCapture = false;
            refreshVirtualDisplay(mDetectedOrientation, false);
        }
        Image acquireLatestImage = acquireLatestImage();
        if (acquireLatestImage != null) {
            if (mUnderUsingImage != null) {
                mUnderUsingImage.close();
            }
            mUnderUsingImage = acquireLatestImage;
        }
        return mUnderUsingImage;
    }

    public Options getOptions() {
        return mOptions;
    }

    @Subscribe
    public void onConfigurationChanged(Configuration configuration) {
        if (mOrientation == ORIENTATION_AUTO && mDetectedOrientation != configuration.orientation) {
            refreshDetectedOrientation();
            if (mOptions.isAsync) {
                mHandler.post(() -> refreshVirtualDisplay(mDetectedOrientation, false));
            } else {
                mShouldRefreshVirtualDisplayOnNextCapture = true;
            }
        }
    }

    public void release() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mImageReader != null) {
            mImageReader.close();
        }
        if (mUnderUsingImage != null) {
            mUnderUsingImage.close();
        }
        EventBus.getDefault().unregister(this);
    }

    public void setImageCaptureCallback(OnScreenCaptureAvailableListener onScreenCaptureAvailableListener) {
        mOnScreenCaptureAvailableListener = onScreenCaptureAvailableListener;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

}