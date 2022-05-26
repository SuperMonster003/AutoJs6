package org.autojs.autojs.ui.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.widget.Toast;

import com.afollestad.materialdialogs.prefs.MaterialListPreference;
import com.stardust.app.GlobalAppContext;

import org.autojs.autojs6.R;

@SuppressWarnings("unused")
public class SetHiddenFilesPreference extends MaterialListPreference {

    public SetHiddenFilesPreference(Context context) {
        super(context);
    }

    public SetHiddenFilesPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SetHiddenFilesPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SetHiddenFilesPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            GlobalAppContext.toast(R.string.text_refresh_explorer_may_needed, Toast.LENGTH_LONG);
        }
        super.onClick(dialog, which);
    }
}
