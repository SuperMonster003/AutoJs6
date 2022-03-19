package org.autojs.autojs.tool;

import android.widget.Toast;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;
import com.stardust.autojs.core.util.ProcessShell;
import com.stericson.RootShell.RootShell;

/**
 * Created by Stardust on 2018/1/26.
 */

public class RootTool {

    private static final int POINTER_LOCATION_ENABLED = 1;
    private static final int POINTER_LOCATION_DISABLED = 0;

    public static boolean isRootAvailable() {
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
        return getPointerLocationResult() == POINTER_LOCATION_ENABLED;
    }

    public static boolean isPointerLocationDisabled() {
        return getPointerLocationResult() == POINTER_LOCATION_DISABLED;
    }

    private static int getPointerLocationResult() {
        // CAUTION
        //  ! result of execCommand() contains a "\n" and its length() is 2
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
}
