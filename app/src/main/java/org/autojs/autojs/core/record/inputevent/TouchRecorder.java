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
    private final boolean mOwnsObserver;

    public TouchRecorder(Context context, InputEventObserver observer) {
        mContext = context;
        mInputEventObserver = observer;
        mOwnsObserver = false;
    }

    /**
     * Observes input events with an observer of its own, which runs only while recording: its getevent process ends when
     * the recording stops, and the next recording with this recorder (after {@link #reset()}) starts it again
     * (docs/shizuku-oom/ROADMAP.md P3.2).
     * zh-CN: 使用自建的观察器监听输入事件, 它只在录制期间运行: 录制停止时结束其 getevent 进程, 用本对象的下一次录制
     * (经 {@link #reset()}) 重新启动 (docs/shizuku-oom/ROADMAP.md P3.2).
     */
    public TouchRecorder(Context context) {
        mContext = context;
        mInputEventObserver = new InputEventObserver(context);
        mOwnsObserver = true;
    }

    @Override
    protected void startImpl() {
        mInputEventRecorder = createInputEventRecorder();
        if (mOwnsObserver) {
            // A recording restarted through reset() without stop() still has the observer running.
            // zh-CN: 未经 stop() 而由 reset() 重新开始的录制, 其观察器仍在运行.
            mInputEventObserver.recycle();
            mInputEventObserver.observe();
        }
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
        if (mOwnsObserver) {
            mInputEventObserver.recycle();
        }
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
