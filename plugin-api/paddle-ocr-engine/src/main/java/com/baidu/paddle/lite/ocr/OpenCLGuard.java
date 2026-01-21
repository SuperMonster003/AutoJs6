package com.baidu.paddle.lite.ocr;

import android.content.Context;
import android.os.Build;
import android.util.Log;

public final class OpenCLGuard {

    private static final String TAG = "OpenCLGuard";
    private static final String SP = "paddle_opencl_probe";
    private static final String KEY_CACHED_RES = "res_";
    private static final String KEY_CACHED_AT = "at_";
    // Timestamp of crash fuse.
    // zh-CN: 崩溃保险丝时间戳.
    private static final String KEY_LAST_FUSE = "fuse_";
    private static final long CACHE_TTL_MS = 24L * 60 * 60 * 1000; // 24h
    private static final long FUSE_MUTE_MS = 7L * 24 * 60 * 60 * 1000; // 7d

    private static final String FINGERPRINT = android.os.Build.FINGERPRINT;
    private static final String CACHE_KEY_RES = KEY_CACHED_RES + FINGERPRINT;
    private static final String CACHE_KEY_AT = KEY_CACHED_AT + FINGERPRINT;
    private static final String FUSE_KEY = KEY_LAST_FUSE + FINGERPRINT;

    private static final String[] CANDIDATES = new String[]{
            // Common Treble partitions.
            // zh-CN: 常见 Treble 分区.
            "/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/odm/lib64/libOpenCL.so",
            // Some vendors put OpenCL in GPU APEX/extension area (not standard, just try).
            // zh-CN: 部分厂商会把 OpenCL 放到 GPU APEX/扩展区 (并不标准, 仅做尝试).
            "/apex/com.android.hwext/lib64/libOpenCL.so",
            // Some devices put ICD in a proprietary directory (rare).
            // zh-CN: 部分设备把 ICD 放在专有目录 (罕见).
            "/vendor/lib64/egl/libOpenCL.so"
    };

    /**
     * Mark: About to initialize OpenCL (if APP crashes, it can be detected next time).
     * zh-CN: 标记: 准备开始初始化 OpenCL (若 APP 崩溃, 下次就能检测到).
     */
    public static void markInitStart(Context ctx) {
        ctx.getSharedPreferences(SP, 0).edit().putLong(FUSE_KEY, System.currentTimeMillis()).apply();
    }

    /**
     * Mark: OpenCL initialization has safely ended (regardless of success or failure).
     * zh-CN: 标记: OpenCL 初始化已安全结束 (无论成功或失败).
     */
    public static void markInitEnd(Context ctx) {
        ctx.getSharedPreferences(SP, 0).edit().remove(FUSE_KEY).apply();
    }

    /**
     * Whether to recommend enabling OpenCL (with cache + fuse + absolute path loading attempt).
     * zh-CN: 是否建议启用 OpenCL (带缓存 + 保险丝 + 绝对路径加载尝试).
     */
    public static boolean isOpenCLRuntimeAvailable(Context ctx) {
        // Crash fuse: last initialization did not end normally -> pause for 7 days.
        // zh-CN: 崩溃保险丝: 上次初始化未正常结束 -> 暂停 7 天.
        long fuseTs = ctx.getSharedPreferences(SP, 0).getLong(FUSE_KEY, 0L);
        if (fuseTs > 0 && (System.currentTimeMillis() - fuseTs) < FUSE_MUTE_MS) {
            Log.w(TAG, "[OpenCL] Fuse active, skip probing.");
            return false;
        }

        // Only try in 64-bit process + arm64 device.
        // zh-CN: 只在 64-bit 进程 + arm64 设备尝试.
        boolean isArm64Device = false;
        try {
            String[] abis64 = Build.SUPPORTED_64_BIT_ABIS;
            if (abis64 != null) for (String abi : abis64) {
                if ("arm64-v8a".equalsIgnoreCase(abi)) {
                    isArm64Device = true;
                    break;
                }
            }
        } catch (Throwable ignore) {
            /* Ignored. */
        }
        boolean is64Process = System.getProperty("os.arch", "").contains("64");
        if (!(isArm64Device && is64Process)) {
            Log.i(TAG, "[OpenCL] Not arm64/64-bit process, skip. dev=" + isArm64Device + " proc=" + is64Process);
            return false;
        }

        // Read cache.
        // zh-CN: 读取缓存.
        final var sp = ctx.getSharedPreferences(SP, 0);
        long cachedAt = sp.getLong(CACHE_KEY_AT, 0L);
        if (cachedAt > 0 && (System.currentTimeMillis() - cachedAt) < CACHE_TTL_MS) {
            boolean cached = sp.getBoolean(CACHE_KEY_RES, false);
            Log.i(TAG, "[OpenCL] use cached=" + cached);
            return cached;
        }

        // 1) Absolute path existence.
        // zh-CN: 1) 绝对路径存在性.
        String hitPath = null;
        for (String p : CANDIDATES) {
            try {
                if (new java.io.File(p).exists()) {
                    hitPath = p;
                    break;
                }
            } catch (Throwable ignore) {
            }
        }

        // 2) Try loading (absolute path first, then loadLibrary("OpenCL")).
        // zh-CN: 2) 尝试加载 (绝对路径优先, 其次 loadLibrary("OpenCL")).
        boolean loadOk = false;
        // 2.1. Absolute path dlopen.
        // zh-CN: 绝对路径 dlopen.
        if (hitPath != null) {
            try {
                System.load(hitPath);
                loadOk = true;
                Log.i(TAG, "[OpenCL] System.load hit: " + hitPath);
            } catch (Throwable t) {
                Log.i(TAG, "[OpenCL] System.load failed: " + hitPath + " -> " + t.getClass().getSimpleName());
            }
        }
        // 2.2. Regular link name.
        // zh-CN: 常规链接名.
        if (!loadOk) {
            try {
                System.loadLibrary("OpenCL");
                loadOk = true;
                Log.i(TAG, "[OpenCL] loadLibrary(\"OpenCL\") ok.");
            } catch (Throwable t) {
                Log.i(TAG, "[OpenCL] loadLibrary(\"OpenCL\") failed: " + t.getMessage());
            }
        }

        int probe = -999;
        if (loadOk) {
            try {
                probe = OpenCLProbe.nativeProbeOpenCL();
            } catch (Throwable t) {
                Log.i(TAG, "[OpenCL] nativeProbeOpenCL error: " + t.getMessage());
            }
        }
        // At least 1 platform.
        // zh-CN: 至少 1 个平台.
        boolean available = loadOk && probe >= 1;
        Log.i(TAG, "[OpenCL] available=" + available + " (loadOk=" + loadOk + ", platforms=" + probe + ")");

        // Write cache.
        // zh-CN: 写缓存.
        sp.edit().putBoolean(CACHE_KEY_RES, available).putLong(CACHE_KEY_AT, System.currentTimeMillis()).apply();
        return available;
    }
}