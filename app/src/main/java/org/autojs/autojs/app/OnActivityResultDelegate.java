package org.autojs.autojs.app;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.util.SparseArray;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Stardust on Mar 5, 2017.
 */
public interface OnActivityResultDelegate {

    void onActivityResult(int requestCode, int resultCode, Intent data);

    interface DelegateHost {
        @NonNull
        Mediator getOnActivityResultDelegateMediator();
    }

    class Mediator implements OnActivityResultDelegate {

        private final SparseArray<OnActivityResultDelegate> mSpecialDelegate = new SparseArray<>();
        private final List<OnActivityResultDelegate> mDelegates = new CopyOnWriteArrayList<>();

        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            OnActivityResultDelegate delegate = mSpecialDelegate.get(requestCode);
            if (delegate != null) {
                delegate.onActivityResult(requestCode, resultCode, data);
            }
            for (OnActivityResultDelegate d : mDelegates) {
                d.onActivityResult(requestCode, resultCode, data);
            }
        }

        public void addDelegate(OnActivityResultDelegate delegate) {
            mDelegates.add(delegate);
        }

        public void addDelegate(int requestCode, OnActivityResultDelegate delegate) {
            mSpecialDelegate.put(requestCode, delegate);
        }

        public void removeDelegate(OnActivityResultDelegate delegate) {
            if (mDelegates.remove(delegate)) {
                int index = mSpecialDelegate.indexOfValue(delegate);
                if (index > -1) {
                    mSpecialDelegate.removeAt(index);
                }
            }
        }
    }

}
