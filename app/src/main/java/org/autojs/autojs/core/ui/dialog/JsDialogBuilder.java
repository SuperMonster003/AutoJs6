package org.autojs.autojs.core.ui.dialog;

import android.content.Context;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import org.autojs.autojs.core.eventloop.EventEmitter;
import org.autojs.autojs.core.looper.Loopers;
import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.tool.UiHandler;
import org.jetbrains.annotations.NotNull;
import org.mozilla.javascript.BaseFunction;

/**
 * Created by Stardust on Apr 17, 2018.
 * Modified by SuperMonster003 as of Mar 20, 2022.
 */
public class JsDialogBuilder extends MaterialDialog.Builder {

    private final EventEmitter mEmitter;
    private final UiHandler mUiHandler;
    private volatile Timer mTimer;
    private final Loopers mLoopers;
    private final ScriptRuntime mRuntime;
    private JsDialog mDialog;
    private volatile int mWaitId = -1;

    @Nullable
    public Object thread;

    public JsDialogBuilder(Context context, ScriptRuntime scriptRuntime) {
        super(context);
        mRuntime = scriptRuntime;
        mLoopers = scriptRuntime.loopers;
        mEmitter = new EventEmitter(scriptRuntime.bridges);
        mUiHandler = scriptRuntime.getUiHandler();
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
            mTimer.postDelayed(() -> mLoopers.doNotWaitWhenIdle(mWaitId), 0);
            emit("dismiss", dialog);
        });
        cancelListener(dialog -> emit("cancel", dialog));
    }

    public void onShowCalled() {
        mTimer = mRuntime.timers.getTimerForCurrentThread();
        mWaitId = mLoopers.waitWhenIdle();
    }

    public JsDialog getDialog() {
        return mDialog;
    }

    @Override
    public JsDialogBuilder theme(@NonNull @NotNull Theme theme) {
        super.theme(theme);
        return this;
    }

    public JsDialog buildDialog() {
        return mDialog = new JsDialog(this, mEmitter, mUiHandler);
    }

    public JsDialogBuilder once(String eventName, BaseFunction listener) {
        mEmitter.once(eventName, listener);
        return this;
    }

    public JsDialogBuilder on(String eventName, BaseFunction listener) {
        mEmitter.on(eventName, listener);
        return this;
    }

    public JsDialogBuilder addListener(String eventName, BaseFunction listener) {
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

    public JsDialogBuilder prependListener(String eventName, BaseFunction listener) {
        mEmitter.prependListener(eventName, listener);
        return this;
    }

    public JsDialogBuilder prependOnceListener(String eventName, BaseFunction listener) {
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

    public JsDialogBuilder removeListener(String eventName, BaseFunction listener) {
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
