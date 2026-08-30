package org.autojs.autojs.runtime.api;

import android.graphics.Bitmap;

import androidx.annotation.Keep;

import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.plugin.imagequant.ImageQuantPluginHost;
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PngQuantBridge {

    public static final class Options {

        public static final int DEFAULT_MAX_COLORS = 256;
        public static final int DEFAULT_SPEED = 8;
        public static final int DEFAULT_MIN_QUALITY = 0;
        public static final float DEFAULT_DITHERING_LEVEL = 0.0F;
        public static final int DEFAULT_POSTERIZE_BITS = 0;
        public static final boolean DEFAULT_PRESERVE_ALPHA = true;
        public static final long DEFAULT_MAX_PIXELS = 16_000_000L;
        public static final long DEFAULT_MAX_MEMORY_BYTES = 256L * 1024L * 1024L;

        private final int maxColors;
        private final int speed;
        private final int minQuality;
        private final int maxQuality;
        private final float ditheringLevel;
        private final int posterizeBits;
        private final boolean preserveAlpha;
        private final long maxPixels;
        private final long maxMemoryBytes;
        private final boolean resourceLimitsRequired;

        /**
         * Creates an immutable options snapshot. Range normalization is performed at the native
         * boundary so Java, Rhino, and direct JNI callers share exactly the same behavior.
         */
        public Options(
                int maxColors,
                int speed,
                int minQuality,
                int maxQuality,
                float ditheringLevel,
                int posterizeBits
        ) {
            this(
                    maxColors,
                    speed,
                    minQuality,
                    maxQuality,
                    ditheringLevel,
                    posterizeBits,
                    DEFAULT_PRESERVE_ALPHA,
                    DEFAULT_MAX_PIXELS,
                    DEFAULT_MAX_MEMORY_BYTES,
                    false
            );
        }

        public Options(
                int maxColors,
                int speed,
                int minQuality,
                int maxQuality,
                float ditheringLevel,
                int posterizeBits,
                boolean preserveAlpha
        ) {
            this(
                    maxColors,
                    speed,
                    minQuality,
                    maxQuality,
                    ditheringLevel,
                    posterizeBits,
                    preserveAlpha,
                    DEFAULT_MAX_PIXELS,
                    DEFAULT_MAX_MEMORY_BYTES,
                    false
            );
        }

        /** Creates options with explicit v4 pixel and additional working-memory budgets. */
        public Options(
                int maxColors,
                int speed,
                int minQuality,
                int maxQuality,
                float ditheringLevel,
                int posterizeBits,
                boolean preserveAlpha,
                long maxPixels,
                long maxMemoryBytes
        ) {
            this(
                    maxColors,
                    speed,
                    minQuality,
                    maxQuality,
                    ditheringLevel,
                    posterizeBits,
                    preserveAlpha,
                    maxPixels,
                    maxMemoryBytes,
                    true
            );
        }

        private Options(
                int maxColors,
                int speed,
                int minQuality,
                int maxQuality,
                float ditheringLevel,
                int posterizeBits,
                boolean preserveAlpha,
                long maxPixels,
                long maxMemoryBytes,
                boolean resourceLimitsRequired
        ) {
            this.maxColors = maxColors;
            this.speed = speed;
            this.minQuality = minQuality;
            this.maxQuality = maxQuality;
            this.ditheringLevel = ditheringLevel;
            this.posterizeBits = posterizeBits;
            this.preserveAlpha = preserveAlpha;
            this.maxPixels = maxPixels;
            this.maxMemoryBytes = maxMemoryBytes;
            this.resourceLimitsRequired = resourceLimitsRequired;
        }

        public int getMaxColors() {
            return maxColors;
        }

        public int getSpeed() {
            return speed;
        }

        public int getMinQuality() {
            return minQuality;
        }

        public int getMaxQuality() {
            return maxQuality;
        }

        public float getDitheringLevel() {
            return ditheringLevel;
        }

        public int getPosterizeBits() {
            return posterizeBits;
        }

        public boolean isPreserveAlpha() {
            return preserveAlpha;
        }

        public long getMaxPixels() {
            return maxPixels;
        }

        public long getMaxMemoryBytes() {
            return maxMemoryBytes;
        }

        private boolean requiresResourceControlApi() {
            return resourceLimitsRequired;
        }
    }

    /** Encoded PNG data together with the palette quality measured by libimagequant. */
    @Keep
    public static final class Result {

        private final byte[] bytes;
        private final int quality;
        private final double quantizationError;
        private final long peakWorkingMemoryBytes;

        /** Called from the optional result JNI entry point. */
        public Result(byte[] bytes, int quality, double quantizationError) {
            this(bytes, quality, quantizationError, -1L);
        }

        /** Called from the v4 resource-control JNI entry point. */
        public Result(
                byte[] bytes,
                int quality,
                double quantizationError,
                long peakWorkingMemoryBytes
        ) {
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            this.quality = quality;
            this.quantizationError = quantizationError;
            this.peakWorkingMemoryBytes = peakWorkingMemoryBytes;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public int getSize() {
            return bytes.length;
        }

        public int getQuality() {
            return quality;
        }

        public double getQuantizationError() {
            return quantizationError;
        }

        /** Peak additional working memory accounted by v4, or {@code -1} on an older runtime. */
        public long getPeakWorkingMemoryBytes() {
            return peakWorkingMemoryBytes;
        }
    }

    /** Metadata returned when encoded PNG bytes are streamed to a caller-owned descriptor. */
    @Keep
    public static final class OutputResult {

        private final long size;
        private final int quality;
        private final double quantizationError;
        private final long peakWorkingMemoryBytes;

        /** Called from the optional file-descriptor JNI entry point. */
        public OutputResult(long size, int quality, double quantizationError) {
            this(size, quality, quantizationError, -1L);
        }

        /** Called from the v4 resource-control JNI entry point. */
        public OutputResult(
                long size,
                int quality,
                double quantizationError,
                long peakWorkingMemoryBytes
        ) {
            if (size < 0L) {
                throw new IllegalArgumentException("size must not be negative");
            }
            this.size = size;
            this.quality = quality;
            this.quantizationError = quantizationError;
            this.peakWorkingMemoryBytes = peakWorkingMemoryBytes;
        }

        public long getSize() {
            return size;
        }

        public int getQuality() {
            return quality;
        }

        public double getQuantizationError() {
            return quantizationError;
        }

        /** Peak additional working memory accounted by v4, or {@code -1} on an older runtime. */
        public long getPeakWorkingMemoryBytes() {
            return peakWorkingMemoryBytes;
        }
    }

    /** A strict minimum quality could not be achieved with the requested color budget. */
    @Keep
    public static final class QualityTooLowException extends IOException {

        public static final String CODE = "PNG_QUANTIZATION_QUALITY_TOO_LOW";

        public QualityTooLowException(String message) {
            super(message);
        }

        public String getCode() {
            return CODE;
        }
    }

    /** A v4 pixel or additional working-memory budget rejected the operation safely. */
    @Keep
    public static final class ResourceLimitException extends RuntimeException {

        public static final String CODE = "PNG_QUANTIZATION_RESOURCE_LIMIT";
        public static final String PIXEL_LIMIT = "PIXEL_LIMIT";
        public static final String MEMORY_LIMIT = "MEMORY_LIMIT";
        public static final String ALLOCATION_FAILED = "ALLOCATION_FAILED";

        private final String reason;
        private final long actualPixels;
        private final long maxPixels;
        private final long requiredMemoryBytes;
        private final long maxMemoryBytes;

        /** Called from the v4 resource-control JNI entry point. */
        public ResourceLimitException(
                String reason,
                long actualPixels,
                long maxPixels,
                long requiredMemoryBytes,
                long maxMemoryBytes
        ) {
            super(buildMessage(reason, actualPixels, maxPixels, requiredMemoryBytes, maxMemoryBytes));
            this.reason = Objects.requireNonNull(reason, "reason");
            this.actualPixels = actualPixels;
            this.maxPixels = maxPixels;
            this.requiredMemoryBytes = requiredMemoryBytes;
            this.maxMemoryBytes = maxMemoryBytes;
        }

        private static String buildMessage(
                String reason,
                long actualPixels,
                long maxPixels,
                long requiredMemoryBytes,
                long maxMemoryBytes
        ) {
            if (PIXEL_LIMIT.equals(reason)) {
                return "Image has " + actualPixels + " pixels; configured maximum is " + maxPixels;
            }
            if (MEMORY_LIMIT.equals(reason)) {
                return "Image quantization requires at least " + requiredMemoryBytes +
                        " bytes of additional working memory; configured maximum is " +
                        maxMemoryBytes;
            }
            return "Image quantization could not allocate memory within the configured " +
                    maxMemoryBytes + " byte working-memory budget";
        }

        public String getCode() {
            return CODE;
        }

        public String getReason() {
            return reason;
        }

        public long getActualPixels() {
            return actualPixels;
        }

        public long getMaxPixels() {
            return maxPixels;
        }

        public long getRequiredMemoryBytes() {
            return requiredMemoryBytes;
        }

        public long getMaxMemoryBytes() {
            return maxMemoryBytes;
        }
    }

    /** The caller, script lifecycle, or interrupted quantization thread requested cancellation. */
    @Keep
    public static final class QuantizationCancelledException extends CancellationException {

        public static final String CODE = "PNG_QUANTIZATION_CANCELLED";

        public QuantizationCancelledException(String message) {
            super(message);
        }

        public String getCode() {
            return CODE;
        }
    }

    /** Thread-safe cancellation handle accepted by the v4 quantization overloads. */
    public static final class CancellationToken {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        /** Called repeatedly from native code; an interrupted script thread also cancels work. */
        @Keep
        public boolean isCancellationRequested() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }
    }

    /**
     * Quantizes a 32-bit RGBA image to an 8-bit indexed PNG and returns the encoded bytes.<br>
     * <p>
     * The routine converts the input buffer (RGBA8888) to a palette-based image through
     * <em>libimagequant</em> and then writes it out as PNG8. The PNG/Deflate stage that follows
     * is <b>strictly lossless</b>&mdash;the stream produced by this method can always be inflated
     * back to the same 8-bit palette data byte-for-byte.<br>
     * <p>
     * The potentially visible degradation comes solely from the <b>color quantization</b> step:
     * ~16.7 M true-color values are mapped to ≤256 palette entries. How noticeable this mapping is
     * depends on both the source image and the {@code quality} you request.
     * <ul>
     *     <li>{@code quality} represents the upper limit of <i>Mean Square Error × 10</i> accepted
     *     by libimagequant (range 0–100). Lower numbers allow larger error, producing smaller files.</li>
     *     <li>Because many graphics are well represented with 256 colors, even an extremely low
     *     {@code quality} such as {@code 1} may look almost identical to the original while shrinking
     *     to 5–20 % of its former size.</li>
     *     <li>No further lossy step exists during PNG writing; the format itself does not permit it.</li>
     * </ul>
     * <p><b>zh-CN:</b><br>
     * 该方法把 32 位 RGBA 图像量化为 8 位索引色 PNG, 并返回编码后的字节数组.<br>
     * PNG 的 Deflate 压缩阶段完全<strong>无损</strong>; 图像失真来自量化过程: 32 位真彩被映射到
     * ≤256 色调色板, 失真程度由 {@code quality} 决定.
     * <ul>
     *     <li>{@code quality} 取值 0–100, 对应 libimagequant 允许的 (MSE×10) 上限; 数值越小, 容许误差越大, 文件越小.</li>
     *     <li>许多图片用 256 色即可精准表示, 因此即便 {@code quality}=1 也可能与原图几乎无差别, 体积却可缩至原来的 5–20 %.</li>
     *     <li>PNG 规范不支持在压缩阶段引入有损处理, 因此不会像 JPEG 那样再出现马赛克等失真.</li>
     * </ul>
     *
     * @return palette-based PNG bytes (may contain transparency)
     */
    public static byte[] quantize(Bitmap bitmap, int quality) {
        return quantizeLegacy(bitmap, quality, new CancellationToken(), false);
    }

    /** Quantizes with an explicit v4 cancellation handle. */
    public static byte[] quantize(
            Bitmap bitmap,
            int quality,
            CancellationToken cancellationToken
    ) {
        return quantizeLegacy(bitmap, quality, cancellationToken, true);
    }

    /** Quantizes a bitmap with the optional named-parameter API exposed by compatible runtimes. */
    public static byte[] quantize(Bitmap bitmap, Options options) {
        return quantizeOptions(bitmap, options, new CancellationToken(), false);
    }

    /** Quantizes named options with an explicit v4 cancellation handle. */
    public static byte[] quantize(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken
    ) {
        return quantizeOptions(bitmap, options, cancellationToken, true);
    }

    /** Quantizes a bitmap and returns both encoded data and measured palette quality. */
    public static Result quantizeWithResult(Bitmap bitmap, Options options) throws QualityTooLowException {
        return quantizeResult(bitmap, options, new CancellationToken(), false);
    }

    /** Returns quality/resource metrics and accepts an explicit v4 cancellation handle. */
    public static Result quantizeWithResult(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken
    ) throws QualityTooLowException {
        return quantizeResult(bitmap, options, cancellationToken, true);
    }

    /**
     * Quantizes directly to the caller-owned descriptor without constructing a complete PNG byte
     * array. The descriptor remains open and its current offset advances by {@link OutputResult#getSize()}.
     */
    public static OutputResult quantizeToFileDescriptor(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            Options options
    ) throws QualityTooLowException {
        return quantizeFileDescriptor(
                bitmap,
                fileDescriptor,
                options,
                new CancellationToken(),
                false
        );
    }

    /** Streams to a descriptor with an explicit v4 cancellation handle. */
    public static OutputResult quantizeToFileDescriptor(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            Options options,
            CancellationToken cancellationToken
    ) throws QualityTooLowException {
        return quantizeFileDescriptor(bitmap, fileDescriptor, options, cancellationToken, true);
    }

    static byte[] quantizeForScriptRuntime(
            Bitmap bitmap,
            int quality,
            CancellationToken cancellationToken
    ) {
        return quantizeLegacy(bitmap, quality, cancellationToken, false);
    }

    static byte[] quantizeForScriptRuntime(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken
    ) {
        return quantizeOptions(bitmap, options, cancellationToken, false);
    }

    static Result quantizeWithResultForScriptRuntime(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken
    ) throws QualityTooLowException {
        return quantizeResult(bitmap, options, cancellationToken, false);
    }

    static OutputResult quantizeToFileDescriptorForScriptRuntime(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            Options options,
            CancellationToken cancellationToken
    ) throws QualityTooLowException {
        return quantizeFileDescriptor(bitmap, fileDescriptor, options, cancellationToken, false);
    }

    private static byte[] quantizeLegacy(
            Bitmap bitmap,
            int quality,
            CancellationToken cancellationToken,
            boolean cancellationSupportRequired
    ) {
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        throwIfCancelled(cancellationToken);
        var context = GlobalAppContext.get();
        var runtime = ImageQuantPluginHost.ensureLoaded(context);
        if (supportsResourceControl(runtime)) {
            var options = new Options(
                    Options.DEFAULT_MAX_COLORS,
                    Options.DEFAULT_SPEED,
                    quality,
                    quality,
                    Options.DEFAULT_DITHERING_LEVEL,
                    Options.DEFAULT_POSTERIZE_BITS,
                    Options.DEFAULT_PRESERVE_ALPHA
            );
            return nativeQuantizeControlled(bitmap, options, cancellationToken).getBytes();
        }
        if (cancellationSupportRequired) ImageQuantPluginHost.ensureResourceControlApi(context);
        return nativeQuantize(bitmap, quality);
    }

    private static byte[] quantizeOptions(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken,
            boolean cancellationSupportRequired
    ) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        throwIfCancelled(cancellationToken);
        var context = GlobalAppContext.get();
        var runtime = ImageQuantPluginHost.ensureLoaded(context);
        if (supportsResourceControl(runtime)) {
            return nativeQuantizeControlled(bitmap, options, cancellationToken).getBytes();
        }
        if (options.requiresResourceControlApi() || cancellationSupportRequired) {
            ImageQuantPluginHost.ensureResourceControlApi(context);
        }
        if (!options.preserveAlpha) {
            ImageQuantPluginHost.ensureColorIoApi(context);
            return nativeQuantizeWithColorOptions(
                    bitmap,
                    options.maxColors,
                    options.speed,
                    options.minQuality,
                    options.maxQuality,
                    options.ditheringLevel,
                    options.posterizeBits,
                    false
            ).getBytes();
        }
        ImageQuantPluginHost.ensureOptionsApi(context);
        return nativeQuantizeWithOptions(
                bitmap,
                options.maxColors,
                options.speed,
                options.minQuality,
                options.maxQuality,
                options.ditheringLevel,
                options.posterizeBits
        );
    }

    private static Result quantizeResult(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken,
            boolean cancellationSupportRequired
    ) throws QualityTooLowException {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        throwIfCancelled(cancellationToken);
        var context = GlobalAppContext.get();
        var runtime = ImageQuantPluginHost.ensureLoaded(context);
        if (supportsResourceControl(runtime)) {
            return nativeQuantizeControlled(bitmap, options, cancellationToken);
        }
        if (options.requiresResourceControlApi() || cancellationSupportRequired) {
            ImageQuantPluginHost.ensureResourceControlApi(context);
        }
        if (!options.preserveAlpha) {
            ImageQuantPluginHost.ensureColorIoApi(context);
            return nativeQuantizeWithColorOptions(
                    bitmap,
                    options.maxColors,
                    options.speed,
                    options.minQuality,
                    options.maxQuality,
                    options.ditheringLevel,
                    options.posterizeBits,
                    false
            );
        }
        ImageQuantPluginHost.ensureQuantizationResultApi(context);
        return nativeQuantizeWithResult(
                bitmap,
                options.maxColors,
                options.speed,
                options.minQuality,
                options.maxQuality,
                options.ditheringLevel,
                options.posterizeBits
        );
    }

    private static OutputResult quantizeFileDescriptor(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            Options options,
            CancellationToken cancellationToken,
            boolean cancellationSupportRequired
    ) throws QualityTooLowException {
        Objects.requireNonNull(fileDescriptor, "fileDescriptor");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        throwIfCancelled(cancellationToken);
        var context = GlobalAppContext.get();
        var runtime = ImageQuantPluginHost.ensureColorIoApi(context);
        if (supportsResourceControl(runtime)) {
            return nativeQuantizeToFileDescriptorWithResourceControls(
                    bitmap,
                    fileDescriptor,
                    options.maxColors,
                    options.speed,
                    options.minQuality,
                    options.maxQuality,
                    options.ditheringLevel,
                    options.posterizeBits,
                    options.preserveAlpha,
                    options.maxPixels,
                    options.maxMemoryBytes,
                    cancellationToken
            );
        }
        if (options.requiresResourceControlApi() || cancellationSupportRequired) {
            ImageQuantPluginHost.ensureResourceControlApi(context);
        }
        return nativeQuantizeToFileDescriptor(
                bitmap,
                fileDescriptor,
                options.maxColors,
                options.speed,
                options.minQuality,
                options.maxQuality,
                options.ditheringLevel,
                options.posterizeBits,
                options.preserveAlpha
        );
    }

    private static Result nativeQuantizeControlled(
            Bitmap bitmap,
            Options options,
            CancellationToken cancellationToken
    ) {
        return nativeQuantizeWithResourceControls(
                bitmap,
                options.maxColors,
                options.speed,
                options.minQuality,
                options.maxQuality,
                options.ditheringLevel,
                options.posterizeBits,
                options.preserveAlpha,
                options.maxPixels,
                options.maxMemoryBytes,
                cancellationToken
        );
    }

    private static boolean supportsResourceControl(ImageQuantPluginHost.LoadedRuntime runtime) {
        return runtime.getOptionsApiVersion() >= ImageQuantPluginContract.RESOURCE_CONTROL_API_VERSION;
    }

    private static void throwIfCancelled(CancellationToken cancellationToken) {
        if (cancellationToken.isCancellationRequested()) {
            throw new QuantizationCancelledException("Image quantization was cancelled");
        }
    }

    /** Called only after the host has loaded a policy-approved native runtime. */
    public static String probeLoadedRuntime() {
        return nativeProbe();
    }

    private static native byte[] nativeQuantize(Bitmap bitmap, int quality);

    private static native byte[] nativeQuantizeWithOptions(
            Bitmap bitmap,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits
    );

    private static native Result nativeQuantizeWithResult(
            Bitmap bitmap,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits
    ) throws QualityTooLowException;

    private static native Result nativeQuantizeWithColorOptions(
            Bitmap bitmap,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits,
            boolean preserveAlpha
    );

    private static native OutputResult nativeQuantizeToFileDescriptor(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits,
            boolean preserveAlpha
    ) throws QualityTooLowException;

    private static native Result nativeQuantizeWithResourceControls(
            Bitmap bitmap,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits,
            boolean preserveAlpha,
            long maxPixels,
            long maxMemoryBytes,
            CancellationToken cancellationToken
    );

    private static native OutputResult nativeQuantizeToFileDescriptorWithResourceControls(
            Bitmap bitmap,
            FileDescriptor fileDescriptor,
            int maxColors,
            int speed,
            int minQuality,
            int maxQuality,
            float ditheringLevel,
            int posterizeBits,
            boolean preserveAlpha,
            long maxPixels,
            long maxMemoryBytes,
            CancellationToken cancellationToken
    );

    private static native String nativeProbe();

}
