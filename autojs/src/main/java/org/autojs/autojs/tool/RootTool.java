package org.autojs.autojs.tool;

import android.content.Context;
import android.content.res.Configuration;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;
import com.stardust.autojs.core.pref.Pref;
import com.stardust.autojs.core.util.ProcessShell;
import com.stericson.RootShell.RootShell;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * Created by Stardust on 2018/1/26.
 */
public class RootTool {

    private static String runtimeOverriddenRootMode = RootMode.AUTO_DETECT.key;

    public enum PointerLocation {
        ENABLED(1), DISABLED(0);

        public final int value;

        PointerLocation(int value) {
            this.value = value;
        }
    }

    public enum RootMode {
        FORCE_ROOT(getString(R.string.key_root_mode_force_root), getStringByLocal(R.string.entry_root_mode_force_root, "en-US")),
        FORCE_NON_ROOT(getString(R.string.key_root_mode_force_non_root), getStringByLocal(R.string.entry_root_mode_force_non_root, "en-US")),
        AUTO_DETECT(getString(R.string.key_root_mode_auto_detect), getStringByLocal(R.string.entry_root_mode_auto_detect, "en-US"));

        public final String key;

        public final String description;

        RootMode(String key, String description) {
            this.key = key;
            this.description = description;
        }

        public static RootMode getRootMode(@NonNull String key) {
            if (key.equals(FORCE_ROOT.key)) {
                return FORCE_ROOT;
            }
            if (key.equals(FORCE_NON_ROOT.key)) {
                return FORCE_NON_ROOT;
            }
            if (key.equals(AUTO_DETECT.key)) {
                return AUTO_DETECT;
            }
            throw new IllegalArgumentException(GlobalAppContext
                    .getString(R.string.error_illegal_argument,
                            "RootMode", key));
        }

        @NonNull
        @Override
        public String toString() {
            return MessageFormat.format("RootMode'{'value={0}, description=''{1}'''}'", key, description);
        }

    }

    public static void setRootMode(RootMode mode) {
        setRootMode(mode, false);
    }

    public static void setRootMode(RootMode mode, boolean isWriteInfoPreference) {
        if (isWriteInfoPreference) {
            Pref.INSTANCE.setRootMode(mode);
        } else {
            runtimeOverriddenRootMode = mode.key;
        }
    }

    public static void resetRuntimeOverriddenRootModeState() {
        runtimeOverriddenRootMode = RootMode.AUTO_DETECT.key;
    }

    public static RootMode getRootMode() {
        if (runtimeOverriddenRootMode.equals(RootMode.AUTO_DETECT.key)) {
            return Pref.INSTANCE.getRootMode();
        }
        return RootMode.getRootMode(runtimeOverriddenRootMode);
    }

    public static boolean isRootAvailable() {
        RootMode rootMode = getRootMode();
        return rootMode == RootMode.AUTO_DETECT
                ? getRootStateByRootShell()
                : rootMode == RootMode.FORCE_ROOT;
    }

    private static boolean getRootStateByRootShell() {
        try {
            return RootShell.isRootAvailable();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean togglePointerLocation() {
        try {
            if (isPointerLocationEnabled()) {
                setPointerLocationDisabled();
                if (isPointerLocationDisabled()) {
                    return true;
                }
            } else {
                setPointerLocationEnabled();
                if (isPointerLocationEnabled()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Ignored.
        }
        GlobalAppContext.toast(GlobalAppContext.get().getString(R.string.text_pointer_location_toggle_failed_with_hint), Toast.LENGTH_LONG);
        return false;
    }

    public static boolean isPointerLocationEnabled() {
        return getPointerLocationResult() == PointerLocation.ENABLED.value;
    }

    public static boolean isPointerLocationDisabled() {
        return getPointerLocationResult() == PointerLocation.DISABLED.value;
    }

    private static int getPointerLocationResult() {
        // @Caution by SuperMonster003 on Mar 2, 2022.
        //  ! Result of execCommand() contains a "\n" and its length() is 2.
        return Integer.parseInt(ProcessShell.execCommand("settings get system pointer_location", true).result.trim());
    }

    public static boolean setPointerLocationEnabled() {
        return setPointerLocationState(true);
    }

    public static boolean setPointerLocationDisabled() {
        return setPointerLocationState(false);
    }

    private static boolean setPointerLocationState(boolean isEnabled) {
        try {
            ProcessShell.execCommand("settings put system pointer_location " + (isEnabled ? 1 : 0), true);
        } catch (Exception ignored) {
            return false;
        }
        return true;
    }

    public static String getStringByLocal(int id, String locale) {
        Context context = GlobalAppContext.get();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(new Locale(locale));
        return context.createConfigurationContext(configuration).getResources().getString(id);
    }

    private static String getString(int resId) {
        return GlobalAppContext.getString(resId);
    }

}
