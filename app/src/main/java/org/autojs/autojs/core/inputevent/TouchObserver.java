package org.autojs.autojs.core.inputevent;

import androidx.annotation.NonNull;

/**
 * Created by Stardust on Jul 20, 2017.
 */
public class TouchObserver implements InputEventObserver.InputEventListener {

    public interface OnTouchEventListener {
        void onTouch(int x, int y);
    }

    private int mTouchX, mTouchY;
    private OnTouchEventListener mOnTouchEventListener;
    private int mLastTouchX = -1, mLastTouchY = -1;
    private final InputEventObserver mInputEventObserver;

    public TouchObserver(InputEventObserver observer) {
        mInputEventObserver = observer;
    }

    public void observe() {
        mInputEventObserver.addListener(this);
    }

    public void stop() {
        mInputEventObserver.removeListener(this);
    }

    public void setOnTouchEventListener(OnTouchEventListener onTouchEventListener) {
        mOnTouchEventListener = onTouchEventListener;
    }

    private void onTouch(int x, int y) {
        mTouchX = x;
        mTouchY = y;
        if (mOnTouchEventListener != null) {
            mOnTouchEventListener.onTouch(x, y);
        }
    }

    @Override
    public void onInputEvent(@NonNull InputEventObserver.InputEvent event) {
        // int device = InputEventRecorder.parseDeviceNumber(event.device);
        int type = (int) Long.parseLong(event.type, 16);
        int code = (int) Long.parseLong(event.code, 16);
        int value = (int) Long.parseLong(event.value, 16);
        if (type != InputEventCodes.EV_ABS) {
            return;
        }
        if (code == InputEventCodes.ABS_MT_POSITION_X) {
            onTouchX(value);
            return;
        }
        if (code == InputEventCodes.ABS_MT_POSITION_Y) {
            onTouchY(value);
            return;
        }
        if (mLastTouchX >= 0) {
            onTouch(mLastTouchX, mTouchY);
            mLastTouchX = -1;
            return;
        }
        if (mLastTouchY >= 0) {
            onTouch(mTouchX, mLastTouchY);
            mLastTouchY = -1;
        }
    }

    private void onTouchX(int value) {
        mLastTouchX = value;
    }

    private void onTouchY(int value) {
        if (mLastTouchX > 0) {
            onTouch(mLastTouchX, value);
        } else {
            mLastTouchY = value;
        }
    }

}
