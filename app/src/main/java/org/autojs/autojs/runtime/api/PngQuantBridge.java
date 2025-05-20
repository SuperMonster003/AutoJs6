package org.autojs.autojs.runtime.api;

import android.graphics.Bitmap;

public class PngQuantBridge {

    static {
        System.loadLibrary("pngquant_bridge");
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
     *     <li>{@code quality} 取值 0–100, 对应 libimagequant 允许的 （MSE×10）上限; 数值越小, 容许误差越大, 文件越小.</li>
     *     <li>许多图片用 256 色即可精准表示, 因此即便 {@code quality}=1 也可能与原图几乎无差别, 体积却可缩至原来的 5–20 %.</li>
     *     <li>PNG 规范不支持在压缩阶段引入有损处理, 因此不会像 JPEG 那样再出现马赛克等失真.</li>
     * </ul>
     *
     * @return palette-based PNG bytes (may contain transparency)
     */
    public static native byte[] quantize(Bitmap bitmap, int quality);

}