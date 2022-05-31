package org.autojs.autojs.tool;

import android.content.Intent;

import com.stardust.app.GlobalAppContext;

import org.autojs.autojs.ui.log.LogActivity_;

public class ConsoleTool {

    public static boolean launch() {
        try {
            LogActivity_.intent(GlobalAppContext.get())
                    .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .start();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
