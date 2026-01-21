package org.autojs.autojs.util;

import androidx.annotation.NonNull;
import com.stericson.RootShell.RootShell;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs6.R;

import java.io.File;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

import static org.autojs.autojs.util.StringUtils.getStringByLanguageTag;
import static org.autojs.autojs.util.StringUtils.key;
import static org.autojs.autojs.util.StringUtils.str;

/**
 * Created by Stardust on Jan 26, 2018.
 * Modified by SuperMonster003 as of March 10, 2022.
 */
public class RootUtils {

    private static final String[] COMMON_SU_PATHS = new String[]{
            "/system/xbin/su",
            "/system/bin/su",
            "/vendor/bin/su",
            "/system_ext/bin/su",
            "/odm/bin/su",
            "/sbin/su",
            "/su/bin/su",
            "/system/su"
    };
    private static String runtimeOverriddenRootMode = RootMode.AUTO_DETECT.key;

    public static void setRootMode(RootMode mode, boolean isWriteInfoPreference) {
        if (isWriteInfoPreference) {
            Pref.setRootMode(mode);
        } else {
            runtimeOverriddenRootMode = mode.key;
        }
    }

    public static void resetRuntimeOverriddenRootModeState() {
        runtimeOverriddenRootMode = RootMode.AUTO_DETECT.key;
    }

    public static RootMode getRootMode() {
        if (runtimeOverriddenRootMode.equals(RootMode.AUTO_DETECT.key)) {
            return Pref.getRootMode();
        }
        return RootMode.getRootMode(runtimeOverriddenRootMode);
    }

    public static void setRootMode(RootMode mode) {
        setRootMode(mode, false);
    }

    public static boolean isRootAvailable() {
        RootMode rootMode = getRootMode();
        return rootMode == RootMode.AUTO_DETECT
                ? isSuPresentFast() || getRootStateWithRootShell()
                : rootMode == RootMode.FORCE_ROOT;
    }

    private static boolean isSuPresentFast() {
        try {
            // 1. Check common su paths.
            // zh-CN: 检查常见 su 路径.
            for (String path : COMMON_SU_PATHS) {
                if (isExecutableFile(path)) {
                    return true;
                }
            }

            // 2. Check su in PATH environment.
            // zh-CN: 检查 PATH 环境中的 su.
            String envPath = System.getenv("PATH");
            if (envPath != null && !envPath.isEmpty()) {
                String[] paths = envPath.split(":");
                Set<String> uniq = new HashSet<>();
                for (String p : paths) {
                    if (p == null || p.isEmpty()) continue;
                    if (!uniq.add(p)) continue;
                    String su = (p.endsWith("/") ? p : (p + "/")) + "su";
                    if (isExecutableFile(su)) {
                        return true;
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isExecutableFile(String absolutePath) {
        File f = new File(absolutePath);
        if (!(f.exists() && f.isFile())) return false;

        // Quick check using File.canExecute().
        // zh-CN: 先用 File.canExecute() 快速判定.
        if (f.canExecute()) {
            return true;
        }

        // Try an equivalent check using Os.access (using reflection to avoid class resolution issues).
        // zh-CN: 再尝试一次 Os.access 的等效判定 (使用反射避免类解析问题).
        try {
            Class<?> osClass = Class.forName("android.system.Os");
            Class<?> osConstantsClass = Class.forName("android.system.OsConstants");
            int xOk = osConstantsClass.getField("X_OK").getInt(null);
            osClass.getMethod("access", String.class, Integer.TYPE).invoke(null, absolutePath, xOk);
            return true;
        } catch (Throwable ignored) {
            // Reflection failed or class/method/field not found, consider as non-executable.
            // zh-CN: 反射失败或无此类/方法/字段, 视为不可执行.
        }
        return false;
    }

    private static boolean getRootStateWithRootShell() {
        try {
            return RootShell.isRootAvailable();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public enum RootMode {
        FORCE_ROOT(key(R.string.key_root_mode_force_root), getStringByLanguageTag(R.string.entry_root_mode_force_root, "en")),
        FORCE_NON_ROOT(key(R.string.key_root_mode_force_non_root), getStringByLanguageTag(R.string.entry_root_mode_force_non_root, "en")),
        AUTO_DETECT(key(R.string.key_root_mode_auto_detect), getStringByLanguageTag(R.string.entry_root_mode_auto_detect, "en"));

        public final String key;

        public final String description;

        RootMode(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public static RootMode getRootMode(@NonNull String key) {
            if (key.equals(FORCE_ROOT.key)) return FORCE_ROOT;
            if (key.equals(FORCE_NON_ROOT.key)) return FORCE_NON_ROOT;
            if (key.equals(AUTO_DETECT.key)) return AUTO_DETECT;
            throw new IllegalArgumentException(str(R.string.error_illegal_argument, "RootMode", key));
        }

        @NonNull
        @Override
        public String toString() {
            return MessageFormat.format("RootMode'{'key={0}, description=''{1}'''}'", key, description);
        }

    }


}
