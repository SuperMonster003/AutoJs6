package org.autojs.autojs.core.record.inputevent;

import android.content.Context;

import org.autojs.autojs.core.inputevent.InputEventObserver;
import org.autojs.autojs.core.record.Recorder;

/**
 * Created by Stardust on Mar 16, 2017.
 */
public class TouchRecorder extends Recorder.AbstractRecorder {

    private InputEventRecorder mInputEventRecorder;
    private final Context mContext;
    private final InputEventObserver mInputEventObserver;

    public TouchRecorder(Context context, InputEventObserver observer) {
        mContext = context;
        mInputEventObserver = observer;
    }

    public TouchRecorder(Context context) {
        mContext = context;
        mInputEventObserver = new InputEventObserver(context);
        mInputEventObserver.observe();
    }

    @Override
    protected void startImpl() {
        mInputEventRecorder = createInputEventRecorder();
        mInputEventObserver.addListener(mInputEventRecorder);
        mInputEventRecorder.start();
    }

    protected InputEventRecorder createInputEventRecorder() {
        return new InputEventToAutoFileRecorder(mContext);
    }

    @Override
    protected void pauseImpl() {
        super.pauseImpl();
        mInputEventRecorder.pause();
    }

    @Override
    protected void resumeImpl() {
        super.resumeImpl();
        mInputEventRecorder.resume();
    }

    @Override
    protected void stopImpl() {
        mInputEventRecorder.stop();
        mInputEventObserver.removeListener(mInputEventRecorder);
    }

    @Override
    public String getCode() {
        return mInputEventRecorder.getCode();
    }


    @Override
    public String getPath() {
        return mInputEventRecorder.getPath();
    }

    public void reset() {
        setState(STATE_NOT_START);
    }
}
