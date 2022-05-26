package org.autojs.autojs.ui.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;

import com.afollestad.materialdialogs.prefs.MaterialListPreference;

import org.autojs.autojs.tool.RootTool;

@SuppressWarnings("unused")
public class SetRootModePreference extends MaterialListPreference {

    public SetRootModePreference(Context context) {
        super(context);
    }

    public SetRootModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SetRootModePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SetRootModePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            RootTool.resetRuntimeOverriddenRootModeState();
        }
        super.onClick(dialog, which);
    }
}
