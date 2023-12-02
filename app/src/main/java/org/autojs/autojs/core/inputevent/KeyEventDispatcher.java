package org.autojs.autojs.core.inputevent;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Stardust on Aug 6, 2017.
 */
public class KeyEventDispatcher {

    public interface OnKeyListener {
        void onVolumeUp();

        void onVolumeDown();

        void onCamera();
    }

    private static final KeyEventDispatcher sSingleton = new KeyEventDispatcher();

    private final CopyOnWriteArrayList<OnKeyListener> mOnKeyListeners = new CopyOnWriteArrayList<>();

    public static KeyEventDispatcher getSingleton() {
        return sSingleton;
    }

    public void addOnKeyListener(OnKeyListener listener) {
        mOnKeyListeners.add(listener);
    }

    public boolean removeOnKeyListener(OnKeyListener listener) {
        return mOnKeyListeners.remove(listener);
    }

    public void notifyVolumeUp() {
        for (OnKeyListener listener : mOnKeyListeners) {
            listener.onVolumeUp();
        }
    }

    public void notifyVolumeDown() {
        for (OnKeyListener listener : mOnKeyListeners) {
            listener.onVolumeDown();
        }
    }

    public void notifyCarema() {
        for (OnKeyListener listener : mOnKeyListeners) {
            listener.onCamera();
        }
    }


}
