package org.autojs.autojs.ui.common;

import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.app.DialogUtils;
import com.stardust.app.GlobalAppContext;

import org.autojs.autojs6.R;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Stardust on 2017/7/8.
 */
public class ScriptLoopDialog {

    private final ScriptFile mScriptFile;
    private final MaterialDialog mDialog;

    @BindView(R.id.loop_times)
    EditText mLoopTimes;

    @BindView(R.id.loop_interval)
    EditText mLoopInterval;

    @BindView(R.id.loop_delay)
    EditText mLoopDelay;


    public ScriptLoopDialog(Context context, ScriptFile file) {
        mScriptFile = file;
        View view = View.inflate(context, R.layout.dialog_script_loop, null);
        mDialog = new MaterialDialog.Builder(context)
                .title(R.string.text_run_repeatedly)
                .customView(view, true)
                .positiveText(R.string.text_ok)
                .onPositive((dialog, which) -> startScriptRunningLoop())
                .build();
        ButterKnife.bind(this, view);
    }

    private void startScriptRunningLoop() {
        try {
            int loopTimes = Integer.parseInt(mLoopTimes.getText().toString());
            float loopInterval = Float.parseFloat(mLoopInterval.getText().toString());
            float loopDelay = Float.parseFloat(mLoopDelay.getText().toString());
            Scripts.INSTANCE.runRepeatedly(mScriptFile, loopTimes, (long) (1000L * loopDelay), (long) (loopInterval * 1000L));
        } catch (NumberFormatException e) {
            GlobalAppContext.toast(R.string.text_number_format_error, Toast.LENGTH_LONG);
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
