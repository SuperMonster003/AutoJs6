package org.autojs.autojs.runtime.api;

import android.graphics.Bitmap;

import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.plugin.imagequant.ImageQuantPluginHost;

import java.util.Objects;

public class PngQuantBridge {

    public static final class Options {

        public static final int DEFAULT_MAX_COLORS = 256;
        public static final int DEFAULT_SPEED = 8;
        public static final float DEFAULT_DITHERING_LEVEL = 0.0F;
        public static final int DEFAULT_POSTERIZE_BITS = 0;

        private final int maxColors;
        private final int speed;
        private final int minQuality;
        private final int maxQuality;
        private final float ditheringLevel;
        private final int posterizeBits;

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
            this.maxColors = maxColors;
            this.speed = speed;
            this.minQuality = minQuality;
            this.maxQuality = maxQuality;
            this.ditheringLevel = ditheringLevel;
            this.posterizeBits = posterizeBits;
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
        var context = GlobalAppContext.get();
        ImageQuantPluginHost.ensureLoaded(context);
        return nativeQuantize(bitmap, quality);
    }

    /** Quantizes a bitmap with the optional named-parameter API exposed by compatible runtimes. */
    public static byte[] quantize(Bitmap bitmap, Options options) {
        Objects.requireNonNull(options, "options");
        var context = GlobalAppContext.get();
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

    private static native String nativeProbe();

}
