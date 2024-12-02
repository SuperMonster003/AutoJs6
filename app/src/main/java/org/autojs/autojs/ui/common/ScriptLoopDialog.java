package org.autojs.autojs.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.DialogScriptLoopBinding;

/**
 * Created by Stardust on Jul 8, 2017.
 */
public class ScriptLoopDialog {

    private final ScriptFile mScriptFile;
    private final MaterialDialog mDialog;

    private final EditText mLoopTimes;
    private final EditText mLoopInterval;
    private final EditText mLoopDelay;

    public ScriptLoopDialog(Context context, ScriptFile file) {
        mScriptFile = file;

        DialogScriptLoopBinding binding = DialogScriptLoopBinding.inflate(LayoutInflater.from(context));
        LinearLayout view = binding.getRoot();

        mLoopTimes = binding.loopTimes;
        mLoopInterval = binding.loopInterval;
        mLoopDelay = binding.loopDelay;

        mDialog = new MaterialDialog.Builder(context)
                .title(R.string.text_run_repeatedly)
                .customView(view, true)
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> startScriptRunningLoop())
                .build();
    }

    private void startScriptRunningLoop() {
        try {
            int loopTimes = Integer.parseInt(mLoopTimes.getText().toString());
            float loopInterval = Float.parseFloat(mLoopInterval.getText().toString());
            float loopDelay = Float.parseFloat(mLoopDelay.getText().toString());
            Scripts.runRepeatedly(mScriptFile, loopTimes, (long) (1000L * loopDelay), (long) (loopInterval * 1000L));
        } catch (NumberFormatException e) {
            ViewUtils.showToast(mDialog.getContext(), R.string.text_number_format_error, true);
        }
    }

    public ScriptLoopDialog windowType(int windowType) {
        Window window = mDialog.getWindow();
        if (window != null) {
            window.setType(windowType);
        }
        return this;
    }

    public void show() {
        DialogUtils.showDialog(mDialog);
    }

}
