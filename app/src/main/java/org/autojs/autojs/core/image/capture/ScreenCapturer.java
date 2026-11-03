package org.autojs.autojs.core.image.capture;

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
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.core.image.ImageWrapper;
import org.autojs.autojs.runtime.api.ScreenMetrics;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on May 17, 2017.
 * Modified by SuperMonster003 as of Jan 11, 2026.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
public class ScreenCapturer {

    private static final String TAG = ScreenCapturer.class.getSimpleName();

    private static final Pattern PATTERN_BUFFER_FORMAT_EXCEPTION = Pattern.compile("buffer format ([0-9a-zA-Z]+) doesn't match");

    // Reduce total wait budget and use shorter waits to fail fast on abandoned BufferQueue.
    // zh-CN: 降低总等待预算并使用更短的等待粒度, 以在 BufferQueue 已被 abandoned 等场景下更快失败.
    private static final long CAPTURE_TOTAL_TIMEOUT_MS = 1200;

    // Use smaller wait slices for faster convergence.
    // zh-CN: 使用更小的等待切片以更快收敛.
    private static final long IMAGE_AVAILABLE_WAIT_SLICE_MS = 60;

    // A still screen composes no new frame. Past this wait without one, refresh the display once so that it composes
    // the current content, instead of spending the whole budget.
    // zh-CN: 静止画面不会合成新帧. 等待超过此时长仍无新帧时, 刷新一次 display 使其合成当前内容, 而不是耗尽全部预算.
    private static final long EARLY_HEALING_AT_MS = 250;

    // Use a dedicated thread for ImageReader callbacks to avoid deadlock when capture() blocks.
    // zh-CN: 使用独立线程处理 ImageReader 回调, 避免 capture() 阻塞时与回调线程相同导致的 "自锁式等待".
    private final HandlerThread mImageCallbackThread;
    private final Handler mImageCallbackHandler;

    // Listen to display changes even when AutoJs6 is in background (no foreground Activity).
    // zh-CN: 即使 AutoJs6 在后台 (无前台 Activity), 也通过 DisplayListener 监听显示变化, 避免配置事件丢失.
    private final DisplayManager mDisplayManager;
    private final DisplayManager.DisplayListener mDisplayListener;

    public static final int ORIENTATION_AUTO = Configuration.ORIENTATION_UNDEFINED; // 0
    public static final int ORIENTATION_LANDSCAPE = Configuration.ORIENTATION_LANDSCAPE; // 1
    public static final int ORIENTATION_PORTRAIT = Configuration.ORIENTATION_PORTRAIT; // 2
    public static final int ORIENTATION_NONE = -1;

    private volatile Image mUnderUsingImage;
    private final int mScreenDensity;
    private final Handler mHandler;
    private final int mOrientation;
    private final Object mImageAvailableLock = new Object();
    private final Options mOptions;
    private volatile ImageReader mImageReader;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private volatile boolean mRecordingSurface;
    private Surface mRecordingInput;
    private OnScreenCaptureAvailableListener mOnScreenCaptureAvailableListener;
    private int mDetectedOrientation;
    private int mAppliedOrientation = ORIENTATION_AUTO;
    private int mPixelFormat = PixelFormat.RGBA_8888;
    private volatile boolean mImageAvailable = false;
    private volatile boolean mShouldRefreshVirtualDisplayOnNextCapture = false;
    private volatile boolean mReleased = false;
    private boolean mDisplayListenerRegistered = false;
    private boolean mEventBusRegistered = false;
    private Runnable mOnMediaProjectionStopListener;

    private final MediaProjection.Callback mMediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            handleMediaProjectionStop();
        }
    };

    public ScreenCapturer(Context context, Intent data, Options options, Handler handler) {
        mOptions = options;
        mHandler = handler;
        mScreenDensity = options.density;
        mOrientation = options.orientation;

        mImageCallbackThread = new HandlerThread("ScreenCapturer-ImageReader");
        mImageCallbackThread.start();
        mImageCallbackHandler = new Handler(mImageCallbackThread.getLooper());

        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);

        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                /* Ignored. */
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                /* Ignored. */
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // Only care about DEFAULT_DISPLAY.
                // zh-CN: 只关心 DEFAULT_DISPLAY.
                if (displayId != Display.DEFAULT_DISPLAY) return;
                if (mOrientation != ORIENTATION_AUTO) return;

                // Mark refresh for next capture to survive background state.
                // zh-CN: 标记下一次 capture 刷新, 用于后台状态下避免错过配置变化事件.
                refreshDetectedOrientation();
                if (mOptions.isAsync) {
                    mHandler.post(() -> refreshVirtualDisplay(mDetectedOrientation, false));
                } else {
                    mShouldRefreshVirtualDisplayOnNextCapture = true;
                }
            }
        };

        try {
            // Register on a non-blocking handler.
            // zh-CN: 使用不易被阻塞的 handler 注册监听.
            mDisplayManager.registerDisplayListener(mDisplayListener, mImageCallbackHandler);
            mDisplayListenerRegistered = true;

            mMediaProjection = ((MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE))
                    .getMediaProjection(Activity.RESULT_OK, (Intent) data.clone());
            mMediaProjection.registerCallback(mMediaProjectionCallback, mHandler);

            refreshDetectedOrientation();
            refreshVirtualDisplay(mOrientation == ORIENTATION_AUTO ? mDetectedOrientation : mOrientation, true);

            EventBus.getDefault().register(this);
            mEventBusRegistered = true;
        } catch (RuntimeException | Error t) {
            release(true);
            throw t;
        }
    }

    private void refreshDetectedOrientation() {
        // Use rotation from the natural orientation of the display instead of (w/h) of a window.
        // zh-CN: 依据显示相对其自然方向的 rotation 判断方向, 而不是窗口的 (w/h).
        mDetectedOrientation = ScreenMetrics.isDisplayLandscape() ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
    }

    private int getTargetOrientation() {
        return mOrientation == ORIENTATION_AUTO ? mDetectedOrientation : mOrientation;
    }

    private int getExpectedWidthByDetectedOrientation() {
        // Keep consistent with refreshVirtualDisplay() sizing logic.
        // zh-CN: 与 refreshVirtualDisplay() 的尺寸计算保持一致.
        return ScreenMetrics.getOrientationAwareScreenWidth(mDetectedOrientation);
    }

    private int getExpectedHeightByDetectedOrientation() {
        // Keep consistent with refreshVirtualDisplay() sizing logic.
        // zh-CN: 与 refreshVirtualDisplay() 的尺寸计算保持一致.
        return ScreenMetrics.getOrientationAwareScreenHeight(mDetectedOrientation);
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

    private Image acquireLatestImage(long deadlineUptimeMillis) {
        if (mReleased || mImageReader == null) {
            return null;
        }

        // Always wait for a fresh frame in sync mode.
        // zh-CN: 同步模式下每次都等待一帧新图.
        if (!mOptions.isAsync) {
            synchronized (mImageAvailableLock) {
                mImageAvailable = false;
            }
        }

        // Try several times but never exceed the given deadline.
        // zh-CN: 做有限次数重试, 但绝不超过 deadline 指定的总时间预算.
        for (int i = 0; i < 20; i++) {
            long now = SystemClock.uptimeMillis();
            if (now >= deadlineUptimeMillis) return null;

            long remain = deadlineUptimeMillis - now;
            waitForImageAvailable(Math.min(IMAGE_AVAILABLE_WAIT_SLICE_MS, remain));

            try {
                ImageReader imageReader = mImageReader;
                if (mReleased || imageReader == null) return null;
                Image img = imageReader.acquireLatestImage();
                if (img != null) return ImageWrapper.trackMediaImage(img);
            } catch (UnsupportedOperationException ex) {
                Integer pixelFormat = getPixelFormat(ex);
                if (pixelFormat != null) {
                    setPixelFormat(pixelFormat);
                    if (!mOptions.isAsync) {
                        synchronized (mImageAvailableLock) {
                            mImageAvailable = false;
                        }
                    }
                    continue;
                }
                throw ex;
            } catch (IllegalStateException ex) {
                if (mReleased) return null;
                throw ex;
            }

            // Reset and wait again.
            // zh-CN: 重置标记并继续等待下一帧.
            if (!mOptions.isAsync) {
                synchronized (mImageAvailableLock) {
                    mImageAvailable = false;
                }
            }
        }

        return null;
    }

    private void waitForImageAvailable(long timeoutMillis) {
        if (!mImageAvailable) {
            synchronized (mImageAvailableLock) {
                if (!mImageAvailable) {
                    try {
                        mImageAvailableLock.wait(timeoutMillis);
                    } catch (InterruptedException ex) {
                        throw new ScriptInterruptedException();
                    }
                }
            }
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
        if (mReleased || mMediaProjection == null) {
            return;
        }

        refreshImageReader(width, height);
        if (mReleased || mImageReader == null) {
            return;
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                ScreenCapturer.class.getSimpleName(),
                width,
                height,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                null,
                null
        );
    }

    private void refreshImageReader(int width, int height) {
        if (mReleased) {
            return;
        }

        if (mImageReader != null) {
            // Detach the producer before closing its consumer. Some devices otherwise keep
            // the virtual display attached to an abandoned BufferQueue after rotation.
            if (mVirtualDisplay != null) mVirtualDisplay.setSurface(null);
            mImageReader.close();
        }
        synchronized (mImageAvailableLock) {
            mImageAvailable = false;
        }
        int maxImages = mOptions.isAsync ? 1 : 3;
        mImageReader = ImageReader.newInstance(width, height, mPixelFormat, maxImages);

        // Always dispatch ImageReader callbacks on the dedicated thread.
        // zh-CN: 始终在独立线程分发 ImageReader 回调.
        setImageListener(mImageCallbackHandler);
    }

    private synchronized void refreshVirtualDisplay(int orientation, boolean isInit) {
        if (mReleased || mRecordingSurface) {
            return;
        }

        int width;
        int height;
        if (orientation == ORIENTATION_NONE) {
            width = mOptions.width;
            height = mOptions.height;
        } else {
            width = ScreenMetrics.getOrientationAwareScreenWidth(orientation);
            height = ScreenMetrics.getOrientationAwareScreenHeight(orientation);
        }

        // Reuse the same VirtualDisplay for the lifetime of this MediaProjection.
        // Android 14+ rejects creating a second VirtualDisplay on one projection.
        if (mVirtualDisplay == null) {
            if (isInit) {
                initVirtualDisplay(width, height, mScreenDensity);
                mAppliedOrientation = orientation;
            }
            return;
        }

        refreshImageReader(width, height);
        if (mReleased || mImageReader == null || mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.resize(width, height, mScreenDensity);
        mVirtualDisplay.setSurface(mImageReader.getSurface());
        mAppliedOrientation = orientation;
    }

    private void setImageListener(Handler handler) {
        ImageReader.OnImageAvailableListener o = mOptions.isAsync
                ? new OnImageAvailableListenerAsync(this)
                : new OnImageAvailableListenerSync(this);
        mImageReader.setOnImageAvailableListener(o, handler);
    }

    public void setImageListenerAsync(ImageReader imageReader) {
        var listener = mOnScreenCaptureAvailableListener;
        if (listener == null) return;
        try (Image img = imageReader.acquireLatestImage()) {
            // acquireLatestImage may return null (no available frames/competition/switching).
            // zh-CN: acquireLatestImage 可能返回 null (无可用帧/竞争/切换中).
            if (img == null) return;
            ImageWrapper.trackMediaImage(img);
            listener.onCaptureAvailable(img);
        } catch (IllegalStateException e) {
            // Ignore the current frame if ImageReader/VirtualDisplay is switching or closed.
            // zh-CN: 当 ImageReader/VirtualDisplay 正在切换或已关闭时可能抛出, 忽略本帧.
        }
    }

    public void setImageListenerSync(ImageReader imageReader) {
        // Always notify for the current ImageReader.
        // zh-CN: 只要回调来自当前 ImageReader, 就直接唤醒等待线程, 避免信号丢失.
        if (imageReader != mImageReader) return;
        synchronized (mImageAvailableLock) {
            mImageAvailable = true;
            mImageAvailableLock.notifyAll();
        }
    }

    private void setPixelFormat(int pixelFormat) {
        if (mReleased || mImageReader == null || mVirtualDisplay == null) {
            return;
        }

        mPixelFormat = pixelFormat;
        refreshImageReader(mImageReader.getWidth(), mImageReader.getHeight());
        if (mImageReader != null && mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(mImageReader.getSurface());
        }
    }

    @Nullable
    public Image capture() {
        if (mRecordingSurface) throw new IllegalStateException("Stop screen recording before capturing an image.");
        if (mReleased) {
            return null;
        }

        if (mOptions.isAsync) {
            throw new IllegalStateException("capture() is not available in async mode");
        }

        final long start = SystemClock.uptimeMillis();
        final long deadline = start + CAPTURE_TOTAL_TIMEOUT_MS;

        // For AUTO mode, do a best-effort self-check before acquiring the frame.
        // zh-CN: AUTO 模式下, 在取帧之前做一次尽力自检, 发现画布尺寸不匹配则主动刷新 VirtualDisplay.
        if (mOrientation == ORIENTATION_AUTO) {
            refreshDetectedOrientation();

            int expectedWidth = getExpectedWidthByDetectedOrientation();
            int expectedHeight = getExpectedHeightByDetectedOrientation();

            // ImageReader size represents the "canvas" size of VirtualDisplay.
            // zh-CN: ImageReader 尺寸代表 VirtualDisplay 的 "画布" 尺寸.
            if (mImageReader != null
                && (mImageReader.getWidth() != expectedWidth || mImageReader.getHeight() != expectedHeight)) {
                refreshVirtualDisplay(mDetectedOrientation, false);
                mShouldRefreshVirtualDisplayOnNextCapture = false;
            }
        }

        if (mShouldRefreshVirtualDisplayOnNextCapture) {
            mShouldRefreshVirtualDisplayOnNextCapture = false;
            refreshVirtualDisplay(mDetectedOrientation, false);
        }

        // Retry a few times to skip transitional frames after resizing/switching,
        // but respect the total timeout budget.
        // zh-CN: 在 resize/切换后重试少量次数以跳过过渡帧, 但必须遵守总超时预算.
        boolean healed = false;
        for (int i = 0; i < 5; i++) {
            long now = SystemClock.uptimeMillis();
            if (now >= deadline) break;

            // Heal early, in every orientation mode: a still screen or a bad pipeline (e.g. BufferQueue abandoned)
            // gives no frame. The first wait stops at the healing point, otherwise it would take the whole budget.
            // Refreshing reuses the VirtualDisplay: Android 14+ rejects a second one on the same projection.
            // zh-CN: 在所有方向模式下尽早自愈: 静止画面或已失效的管线 (如 BufferQueue abandoned) 都不会给出新帧.
            // 首次等待止于自愈时间点, 否则会耗尽全部预算. 刷新时复用 VirtualDisplay: Android 14+ 拒绝在同一投影上创建第二个.
            if (!healed && (now - start) >= EARLY_HEALING_AT_MS) {
                healed = true;
                refreshDetectedOrientation();
                refreshVirtualDisplay(getTargetOrientation(), false);
            }

            Image acquireLatestImage = acquireLatestImage(healed ? deadline : Math.min(deadline, start + EARLY_HEALING_AT_MS));
            if (acquireLatestImage == null) continue;

            if (mOrientation == ORIENTATION_AUTO) {
                int expectedWidth = getExpectedWidthByDetectedOrientation();
                int expectedHeight = getExpectedHeightByDetectedOrientation();
                if (acquireLatestImage.getWidth() != expectedWidth || acquireLatestImage.getHeight() != expectedHeight) {
                    // Drop mismatched frame and refresh display once more.
                    // zh-CN: 丢弃尺寸不匹配的帧, 并再次刷新 display.
                    acquireLatestImage.close();
                    refreshVirtualDisplay(mDetectedOrientation, false);
                    continue;
                }
            }

            if (mUnderUsingImage != null) {
                mUnderUsingImage.close();
            }
            mUnderUsingImage = acquireLatestImage;
            return mUnderUsingImage;
        }

        // If timed out, refresh the existing display once to recover from "no-frame" bad state.
        refreshDetectedOrientation();
        refreshVirtualDisplay(getTargetOrientation(), false);
        mShouldRefreshVirtualDisplayOnNextCapture = false;

        // Do not return stale cached image when no valid frame is available.
        // zh-CN: 当无法获取到有效帧时, 不要返回旧缓存帧.
        return null;
    }

    public Options getOptions() {
        return mOptions;
    }

    /** Switch the existing display to an encoder; never reuse a consent token for a second display. */
    public synchronized void setRecordingSurface(Surface surface, int width, int height) {
        if (mReleased || mVirtualDisplay == null) throw new IllegalStateException("Screen capture has stopped.");
        if (mRecordingSurface) throw new IllegalStateException("Screen recording is already active.");
        mRecordingSurface = true;
        mRecordingInput = surface;
        mVirtualDisplay.setSurface(null);
        mVirtualDisplay.resize(width, height, mScreenDensity);
        mVirtualDisplay.setSurface(surface);
    }

    public synchronized void restoreCaptureSurface() {
        if (!mRecordingSurface) return;
        mRecordingSurface = false;
        mRecordingInput = null;
        if (mReleased) return;
        refreshDetectedOrientation();
        refreshVirtualDisplay(getTargetOrientation(), false);
        mShouldRefreshVirtualDisplayOnNextCapture = false;
    }

    public synchronized void detachRecordingSurface() {
        if (mRecordingSurface && mVirtualDisplay != null) mVirtualDisplay.setSurface(null);
    }

    public android.graphics.Point getCaptureDimensions() {
        if (mOrientation == ORIENTATION_NONE) return new android.graphics.Point(mOptions.width, mOptions.height);
        int orientation = mOrientation;
        if (orientation == ORIENTATION_AUTO) {
            orientation = ScreenMetrics.isDisplayLandscape() ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
        }
        return new android.graphics.Point(ScreenMetrics.getOrientationAwareScreenWidth(orientation), ScreenMetrics.getOrientationAwareScreenHeight(orientation));
    }

    public synchronized void replaceRecordingSurface(Surface surface, int width, int height) {
        if (mReleased || !mRecordingSurface || mVirtualDisplay == null) throw new IllegalStateException("Recording display is unavailable.");
        // A fresh consumer avoids OEM pipelines retaining stale buffer geometry across rotation.
        mVirtualDisplay.setSurface(null);
        mVirtualDisplay.resize(width, height, mScreenDensity);
        mRecordingInput = surface;
        mVirtualDisplay.setSurface(mRecordingInput);
    }

    public synchronized MediaProjection getAudioProjection() {
        if (mReleased || mMediaProjection == null) throw new IllegalStateException("Screen capture has stopped.");
        return mMediaProjection;
    }

    @Subscribe
    public void onConfigurationChanged(Configuration configuration) {
        if (mOrientation != ORIENTATION_AUTO) return;

        // Always schedule refresh for AUTO mode.
        // zh-CN: AUTO 模式下收到配置变化事件时总是安排下一次 capture 刷新.
        refreshDetectedOrientation();
        if (mOptions.isAsync) {
            mHandler.post(() -> refreshVirtualDisplay(mDetectedOrientation, false));
        } else {
            mShouldRefreshVirtualDisplayOnNextCapture = true;
        }
    }

    public void release() {
        release(true);
    }

    public boolean isReleased() {
        return mReleased;
    }

    public void setOnMediaProjectionStopListener(@Nullable Runnable listener) {
        mOnMediaProjectionStopListener = listener;
    }

    private void handleMediaProjectionStop() {
        if (!release(false)) {
            return;
        }

        Runnable listener = mOnMediaProjectionStopListener;
        if (listener != null) {
            listener.run();
        }
    }

    /**
     * Releases everything this capturer owns, in one go and in this order: the virtual display (the producer), the
     * ImageReader (its consumer) and the last acquired image, the MediaProjection (callback, then the projection itself
     * unless the system already stopped it), the display listener, the ImageReader callback thread and the EventBus
     * subscription. Every step runs even if an earlier one throws, so a failing step cannot leave the callback thread
     * or a listener behind (roadmap H5). The screen capture foreground service is shared by all scripts and is stopped
     * by the owner of the consent request (Images.releaseScreenCapturer) once no capturer uses it any more.
     * zh-CN: 一次性按以下顺序释放本采集器拥有的全部资源: 虚拟显示 (生产者), ImageReader (其消费者) 与最后取得的图像, MediaProjection
     * (先回调, 再停止投影本身, 系统已停止时除外), 显示监听器, ImageReader 回调线程与 EventBus 订阅. 即使前一步抛出异常, 后续步骤也会执行,
     * 因此失败的步骤不会遗留回调线程或监听器 (路线图 H5). 截屏前台服务由全部脚本共用, 由授权请求的持有方 (Images.releaseScreenCapturer) 在不再有采集器使用时停止.
     */
    private boolean release(boolean stopMediaProjection) {
        final MediaProjection mediaProjection;
        final VirtualDisplay virtualDisplay;
        final ImageReader imageReader;
        final Image underUsingImage;
        final boolean unregisterDisplayListener;
        final boolean unregisterEventBus;

        synchronized (this) {
            if (mReleased) {
                return false;
            }

            mReleased = true;
            mediaProjection = mMediaProjection;
            mMediaProjection = null;
            virtualDisplay = mVirtualDisplay;
            mVirtualDisplay = null;
            imageReader = mImageReader;
            mImageReader = null;
            underUsingImage = mUnderUsingImage;
            mUnderUsingImage = null;
            unregisterDisplayListener = mDisplayListenerRegistered;
            mDisplayListenerRegistered = false;
            unregisterEventBus = mEventBusRegistered;
            mEventBusRegistered = false;
        }

        if (virtualDisplay != null) {
            releaseStep("virtual display", virtualDisplay::release);
        }
        if (imageReader != null) {
            releaseStep("image reader", imageReader::close);
        }
        if (underUsingImage != null) {
            releaseStep("image", underUsingImage::close);
        }
        if (mediaProjection != null) {
            releaseStep("projection callback", () -> mediaProjection.unregisterCallback(mMediaProjectionCallback));
            if (stopMediaProjection) {
                releaseStep("media projection", mediaProjection::stop);
            }
        }
        if (unregisterDisplayListener) {
            releaseStep("display listener", () -> mDisplayManager.unregisterDisplayListener(mDisplayListener));
        }
        releaseStep("callback thread", mImageCallbackThread::quitSafely);
        if (unregisterEventBus) {
            releaseStep("event bus", () -> EventBus.getDefault().unregister(this));
        }

        return true;
    }

    private static void releaseStep(String what, Runnable step) {
        try {
            step.run();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to release the " + what + " of the screen capturer", t);
        }
    }

    public void setImageCaptureCallback(OnScreenCaptureAvailableListener onScreenCaptureAvailableListener) {
        mOnScreenCaptureAvailableListener = onScreenCaptureAvailableListener;
    }

    /**
     * Last resort only: while registered, the display listener and the EventBus subscription keep an unreleased capturer
     * reachable, so this runs after a missed release only when registration itself failed. Script runtimes release their
     * capturer on exit (Images.releaseScreenCapturer).
     * zh-CN: 仅作兜底: 登记期间显示监听器与 EventBus 订阅使未释放的采集器保持可达, 因此只有登记本身失败时漏掉的释放才会走到这里.
     * 脚本运行时在退出时释放其采集器 (Images.releaseScreenCapturer).
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!mReleased) {
                Log.w(TAG, "A screen capturer was finalized without being released");
                release();
            }
        } finally {
            super.finalize();
        }
    }

}
