package com.baidu.paddle.lite.ocr;

public final class OpenCLProbe {
    static {
        try { System.loadLibrary("opencl_probe"); } catch (Throwable ignore) {}
    }
    public static native int nativeProbeOpenCL();
}
