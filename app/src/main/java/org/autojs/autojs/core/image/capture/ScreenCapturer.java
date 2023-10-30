package org.autojs.autojs.core.image.capture;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.OrientationEventListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.lang.ThreadCompat;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.exception.ScriptException;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.autojs.autojs.util.ForegroundServiceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Stardust on 2017/5/17.
 * Modified by SuperMonster003 as of May 19, 2022.
 */
public class ScreenCapturer {

    private static final String TAG = ScreenCapturer.class.getSimpleName();

    public static final int ORIENTATION_AUTO = Configuration.ORIENTATION_UNDEFINED;
    public static final int ORIENTATION_LANDSCAPE = Configuration.ORIENTATION_LANDSCAPE;
    public static final int ORIENTATION_PORTRAIT = Configuration.ORIENTATION_PORTRAIT;

    // @Reference to TonyJiangWJ/Auto.js (https://github.com/TonyJiangWJ/Auto.js) on May 19, 2022.
    //  ! Snippet:
    //  ! private final ConcurrentHashMap<ScriptRuntime, Boolean> registeredRuntimes = new ConcurrentHashMap<>();
    private static final List<ScriptRuntime> mScriptRuntimes = Collections.synchronizedList(new ArrayList<>());

    private final MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private volatile Looper mImageAcquireLooper;
    private volatile Image mUnderUsingImage;
    private final AtomicReference<Image> mCachedImage = new AtomicReference<>();
    private volatile Exception mException;
    private final int mScreenDensity;
    private final Handler mHandler;
    private final Intent mData;
    private final Context mContext;
    private int mOrientation = -1;
    private int mDetectedOrientation;
    private OrientationEventListener mOrientationEventListener;
    private boolean mIsMediaProjectionStopped;

    @SuppressLint("StaticFieldLeak")
    private static ScreenCapturer INSTANCE_CACHE;

    private ScreenCapturer(Context context, Intent data, int orientation, int screenDensity, Handler handler) {
        mContext = context;
        mData = data;
        mScreenDensity = screenDensity;
        mHandler = handler;
        mProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        setMediaProjectionIfNeeded();
        setOrientation(orientation);
        observeOrientation();
    }

    public static ScreenCapturer getInstanceCache() {
        return INSTANCE_CACHE;
    }

    public static ScreenCapturer getInstance(Context context, Intent data, int orientation, int density, Handler handler, ScriptRuntime mScriptRuntime) {
        if (INSTANCE_CACHE == null) {
            INSTANCE_CACHE = new ScreenCapturer(context, data, orientation, density, handler);
        }
        addScriptRuntimeIfNeeded(mScriptRuntime);
        return INSTANCE_CACHE;
    }

    public static boolean hasScriptRuntime(ScriptRuntime scriptRuntime) {
        return mScriptRuntimes.contains(scriptRuntime);
    }

    private static void addScriptRuntimeIfNeeded(ScriptRuntime scriptRuntime) {
        if (!hasScriptRuntime(scriptRuntime)) {
            Log.d(TAG, "addScriptRuntimeIfNeeded: @" + Integer.toHexString(scriptRuntime.hashCode()));
            mScriptRuntimes.add(scriptRuntime);
        }
    }

    private static void removeScriptRuntimeIfNeeded(ScriptRuntime scriptRuntime) {
        while (hasScriptRuntime(scriptRuntime)) {
            Log.d(TAG, "removeScriptRuntimeIfNeeded: @" + Integer.toHexString(scriptRuntime.hashCode()));
            mScriptRuntimes.remove(scriptRuntime);
        }
    }

    private void setMediaProjectionIfNeeded() {
        if (!isMediaProjectionValid()) {
            mMediaProjection = getMediaProjection();
        }
    }

    public boolean isMediaProjectionValid() {
        return mMediaProjection != null && !mIsMediaProjectionStopped;
    }

    @NonNull
    private synchronized MediaProjection getMediaProjection() {
        ForegroundServiceUtils.request(mContext, ScreenCapturerForegroundService.class);
        MediaProjection mediaProjection = mProjectionManager.getMediaProjection(Activity.RESULT_OK, (Intent) mData.clone());

        mIsMediaProjectionStopped = false;

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "Media projection stopped: @" + Integer.toHexString(mediaProjection.hashCode()));
                mIsMediaProjectionStopped = true;
            }
        }, mHandler);

        return mediaProjection;
    }

    private void observeOrientation() {
        mOrientationEventListener = new OrientationEventListener(mContext) {
            @Override
            public void onOrientationChanged(int o) {
                int orientation = mContext.getResources().getConfiguration().orientation;
                if (mOrientation == ORIENTATION_AUTO && mDetectedOrientation != orientation) {
                    mDetectedOrientation = orientation;
                    try {
                        refreshVirtualDisplay(orientation);
                    } catch (Exception e) {
                        e.printStackTrace();
                        mException = e;
                    }
                }
            }
        };
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }
    }

    public void setOrientation(int orientation) {
        if (mOrientation != orientation) {
            mOrientation = orientation;
            mDetectedOrientation = mContext.getResources().getConfiguration().orientation;
            refreshVirtualDisplay(mOrientation == ORIENTATION_AUTO ? mDetectedOrientation : mOrientation);
        }
    }

    private void refreshVirtualDisplay(int orientation) {
        if (mImageAcquireLooper != null) {
            mImageAcquireLooper.quit();
        }
        if (mImageReader != null) {
            mImageReader.close();
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        int screenHeight = ScreenMetrics.getOrientationAwareScreenHeight(orientation);
        int screenWidth = ScreenMetrics.getOrientationAwareScreenWidth(orientation);
        initVirtualDisplay(screenWidth, screenHeight, mScreenDensity);
        startAcquireImageLoop();
    }

    @SuppressLint("WrongConstant")
    private void initVirtualDisplay(int width, int height, int screenDensity) {
        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        int max = 3;
        do {
            try {
                mVirtualDisplay = mMediaProjection.createVirtualDisplay(TAG,
                        width, height, screenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(), null, null);
                break;
            } catch (SecurityException e) {
                setMediaProjectionIfNeeded();
            }
        } while (--max > 0);
    }

    private void startAcquireImageLoop() {
        if (mHandler != null) {
            setImageListener(mHandler);
            return;
        }
        new Thread(() -> {
            Log.d(TAG, "AcquireImageLoop: start");
            Looper.prepare();
            mImageAcquireLooper = Looper.myLooper();
            setImageListener(new Handler());
            Looper.loop();
            Log.d(TAG, "AcquireImageLoop: stop");
        }).start();
    }

    private void setImageListener(Handler handler) {
        mImageReader.setOnImageAvailableListener(reader -> {
            try {
                Image oldCacheImage = mCachedImage.getAndSet(null);
                if (oldCacheImage != null) {
                    oldCacheImage.close();
                }
                mCachedImage.set(reader.acquireLatestImage());
            } catch (Exception e) {
                mException = e;
            }

        }, handler);
    }

    @Nullable
    public synchronized Image capture() {
        Exception e = mException;
        if (e != null) {
            mException = null;
            throw new ScriptException(e);
        }

        Thread thread = ThreadCompat.currentThread();
        while (!thread.isInterrupted()) {
            Image cachedImage = mCachedImage.getAndSet(null);
            if (cachedImage != null) {
                if (mUnderUsingImage != null) {
                    mUnderUsingImage.close();
                }
                mUnderUsingImage = cachedImage;
                return cachedImage;
            }
        }
        throw new ScriptInterruptedException();
    }

    public int getScreenDensity() {
        return mScreenDensity;
    }

    public void release() {
        Log.d(TAG, "release");

        INSTANCE_CACHE = null;

        if (mImageAcquireLooper != null) {
            mImageAcquireLooper.quit();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
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
        Image cachedImage = mCachedImage.getAndSet(null);
        if (cachedImage != null) {
            cachedImage.close();
        }
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
        }
        ScreenCapturerForegroundService.stop(mContext);
    }

    public void release(ScriptRuntime scriptRuntime) {
        removeScriptRuntimeIfNeeded(scriptRuntime);
        if (mScriptRuntimes.isEmpty()) {
            release();
        }
    }

}