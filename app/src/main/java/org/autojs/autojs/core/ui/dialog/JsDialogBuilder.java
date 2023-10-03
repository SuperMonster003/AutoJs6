package org.autojs.autojs.core.ui.dialog;

import android.content.Context;
import android.widget.EditText;

import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.core.eventloop.EventEmitter;
import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.tool.UiHandler;

/**
 * Created by Stardust on 2018/4/17.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 * Modified by aiselp as of Jun 10, 2023.
 */
public class JsDialogBuilder extends MaterialDialog.Builder {

    private final EventEmitter mEmitter;
    private final UiHandler mUiHandler;
    private final Timer mTimer;
    private final Loopers mLoopers;
    private JsDialog mDialog;
    private volatile Loopers.AsyncTask task;

    public JsDialogBuilder(Context context, ScriptRuntime runtime) {
        super(context);
        mTimer = runtime.timers.getTimerForCurrentThread();
        mLoopers = runtime.loopers;
        mEmitter = new EventEmitter(runtime.bridges);
        mUiHandler = runtime.uiHandler;
        setUpEvents();
    }

    private void setUpEvents() {
        showListener(dialog -> emit("show", dialog));
        onAny((dialog, which) -> {
            switch (which) {
                case NEUTRAL -> {
                    emit("neutral", dialog);
                    emit("any", "neutral", dialog);
                }
                case NEGATIVE -> {
                    emit("negative", dialog);
                    emit("any", "negative", dialog);
                }
                case POSITIVE -> {
                    EditText editText = dialog.getInputEditText();
                    if (editText != null) {
                        emit("input", editText.getText().toString());
                    }
                    emit("positive", dialog);
                    emit("any", "positive", dialog);
                }
            }
        });
        dismissListener(dialog -> {
            mTimer.postDelayed(() -> mLoopers.removeAsyncTask(task), 0);
            emit("dismiss", dialog);
        });
        cancelListener(dialog -> emit("cancel", dialog));
    }

    public void onShowCalled() {
        mTimer.postDelayed(() -> task = mLoopers.createAndAddAsyncTask("js-dialog"), 0);
    }

    public JsDialog getDialog() {
        return mDialog;
    }

    public JsDialog buildDialog() {
        mDialog = new JsDialog(this, mEmitter, mUiHandler);
        return mDialog;
    }

    public JsDialogBuilder once(String eventName, Object listener) {
        mEmitter.once(eventName, listener);
        return this;
    }

    public JsDialogBuilder on(String eventName, Object listener) {
        mEmitter.on(eventName, listener);
        return this;
    }

    public JsDialogBuilder addListener(String eventName, Object listener) {
        mEmitter.addListener(eventName, listener);
        return this;
    }

    public boolean emit(String eventName, Object... args) {
        return mEmitter.emit(eventName, args);
    }

    public String[] eventNames() {
        return mEmitter.eventNames();
    }

    public int listenerCount(String eventName) {
        return mEmitter.listenerCount(eventName);
    }

    public Object[] listeners(String eventName) {
        return mEmitter.listeners(eventName);
    }

    public JsDialogBuilder prependListener(String eventName, Object listener) {
        mEmitter.prependListener(eventName, listener);
        return this;
    }

    public JsDialogBuilder prependOnceListener(String eventName, Object listener) {
        mEmitter.prependOnceListener(eventName, listener);
        return this;
    }

    public JsDialogBuilder removeAllListeners() {
        mEmitter.removeAllListeners();
        return this;
    }

    public JsDialogBuilder removeAllListeners(String eventName) {
        mEmitter.removeAllListeners(eventName);
        return this;
    }

    public JsDialogBuilder removeListener(String eventName, Object listener) {
        mEmitter.removeListener(eventName, listener);
        return this;
    }

    public JsDialogBuilder setMaxListeners(int n) {
        mEmitter.setMaxListeners(n);
        return this;
    }

    public int getMaxListeners() {
        return mEmitter.getMaxListeners();
    }

    public static int defaultMaxListeners() {
        return EventEmitter.defaultMaxListeners();
    }

}
