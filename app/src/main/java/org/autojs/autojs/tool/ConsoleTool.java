package org.autojs.autojs.tool;

import android.content.Intent;

import com.stardust.app.GlobalAppContext;

import org.autojs.autojs.ui.log.LogActivity_;

public class ConsoleTool {

    public static void launch() {
        LogActivity_.intent(GlobalAppContext.get())
                .flags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .start();
    }

}
