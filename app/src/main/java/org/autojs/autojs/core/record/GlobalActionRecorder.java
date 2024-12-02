package org.autojs.autojs.core.record;

import android.content.Context;
import android.view.ContextThemeWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.core.record.inputevent.InputEventRecorder;
import org.autojs.autojs.core.record.inputevent.InputEventToAutoFileRecorder;
import org.autojs.autojs.core.record.inputevent.InputEventToRootAutomatorRecorder;
import org.autojs.autojs.core.record.inputevent.TouchRecorder;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.autojs.autojs.util.RhinoUtils.isBackgroundThread;
import static org.autojs.autojs.util.RhinoUtils.isMainThread;

/**
 * Created by Stardust on Aug 6, 2017.
 */
public class GlobalActionRecorder implements Recorder.OnStateChangedListener {

    private static GlobalActionRecorder sSingleton;
    private final CopyOnWriteArrayList<Recorder.OnStateChangedListener> mOnStateChangedListeners = new CopyOnWriteArrayList<>();
    private TouchRecorder mTouchRecorder;
    private final Context mContext;
    private boolean mDiscard = false;

    public static GlobalActionRecorder getSingleton(Context context) {
        if (sSingleton == null) {
            sSingleton = new GlobalActionRecorder(context);
        }
        return sSingleton;
    }

    public GlobalActionRecorder(Context context) {
        mContext = new ContextThemeWrapper(context.getApplicationContext(), R.style.AppTheme);
    }

    public void start() {
        if (mTouchRecorder == null) {
            mTouchRecorder = createTouchRecorder();
        }
        mTouchRecorder.reset();
        mDiscard = false;
        mTouchRecorder.setOnStateChangedListener(this);
        mTouchRecorder.start();
    }

    private TouchRecorder createTouchRecorder() {
        return new TouchRecorder(mContext) {
            @Override
            protected InputEventRecorder createInputEventRecorder() {
                if (Pref.rootRecordGeneratesBinary())
                    return new InputEventToAutoFileRecorder(mContext);
                else
                    return new InputEventToRootAutomatorRecorder();
            }
        };
    }

    public void pause() {
        mTouchRecorder.pause();
    }

    public void resume() {
        mTouchRecorder.resume();
    }

    public void stop() {
        mTouchRecorder.stop();
    }

    public String getCode() {
        return mTouchRecorder.getCode();
    }

    public String getPath() {
        return mTouchRecorder.getPath();
    }

    public int getState() {
        if (mTouchRecorder == null)
            return Recorder.STATE_NOT_START;
        return mTouchRecorder.getState();
    }

    public void addOnStateChangedListener(Recorder.OnStateChangedListener listener) {
        mOnStateChangedListeners.add(listener);
    }

    public void removeOnStateChangedListener(Recorder.OnStateChangedListener listener) {
        mOnStateChangedListeners.remove(listener);
    }

    @Override
    public void onStart() {
        if (Pref.isRecordToastEnabled()) {
            ViewUtils.showToast(mContext, R.string.text_start_record);
        }
        for (Recorder.OnStateChangedListener listener : mOnStateChangedListeners) {
            listener.onStart();
        }
    }

    @Override
    public void onStop() {
        if (Pref.isRecordToastEnabled()) {
            ViewUtils.showToast(mContext, R.string.text_record_stopped);
        }
        if (!mDiscard) {
            String code = getCode();
            if (code != null)
                handleRecordedScript(code);
            else
                handleRecordedFile(getPath());
        }
        for (Recorder.OnStateChangedListener listener : mOnStateChangedListeners) {
            listener.onStop();
        }
    }

    @Override
    public void onPause() {
        for (Recorder.OnStateChangedListener listener : mOnStateChangedListeners) {
            listener.onPause();
        }
    }

    @Override
    public void onResume() {
        for (Recorder.OnStateChangedListener listener : mOnStateChangedListeners) {
            listener.onResume();
        }
    }

    public void discard() {
        mDiscard = true;
        stop();
    }

    private void handleRecordedScript(final String script) {
        if (isMainThread()) {
            showRecordHandleDialog(script);
        } else {
            GlobalAppContext.post(() -> showRecordHandleDialog(script));
        }
    }

    private void handleRecordedFile(final String path) {
        if (isBackgroundThread()) {
            GlobalAppContext.post(() -> handleRecordedFile(path));
            return;
        }
        new ScriptOperations(mContext, null)
                .importFile(path)
                .subscribe();

    }

    private void showRecordHandleDialog(final String script) {
        DialogUtils.showDialog(new MaterialDialog.Builder(mContext)
                .title(R.string.text_record_finished)
                .content(R.string.content_way_of_output_for_recorded_script)
                .items(getString(R.string.text_new_file), getString(R.string.text_copy_to_clip))
                .itemsCallbackSingleChoice(0, (dialog, itemView, which, text) -> {
                    if (text.equals(mContext.getString(R.string.text_new_file))) {
                        new ScriptOperations(mContext, null)
                                .newScriptFileForScript(script);
                    } else if (text.equals(mContext.getString(R.string.text_copy_to_clip))) {
                        ClipboardUtils.setClip(mContext, script);
                        ViewUtils.showToast(mContext, R.string.text_already_copied_to_clip);
                    }
                    return false;
                })
                .negativeText(R.string.text_cancel)
                .positiveText(R.string.dialog_button_confirm)
                .canceledOnTouchOutside(false)
                .build());
    }

    private String getString(int res) {
        return mContext.getString(res);
    }

}
