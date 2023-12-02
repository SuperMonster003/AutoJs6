package org.autojs.autojs.ui.main;

import android.view.View;

import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.autojs.autojs.event.BackPressedHandler;

/**
 * Created by Stardust on Aug 22, 2017.
 */
public abstract class ViewPagerFragment extends Fragment implements BackPressedHandler {

    protected static final int ROTATION_GONE = -1;

    private final int mFabRotation;
    private FloatingActionButton mFab;
    private boolean mShown;
    private final View.OnClickListener mOnFabClickListener = v -> onFabClick((FloatingActionButton) v);

    public ViewPagerFragment(int fabRotation) {
        mFabRotation = fabRotation;
    }

    public void setFab(FloatingActionButton fab) {
        mFab = fab;
    }

    protected abstract void onFabClick(FloatingActionButton fab);

    @CallSuper
    public void onPageShow() {
        mShown = true;
        if (mFabRotation == ROTATION_GONE) {
            if (mFab.getVisibility() == View.VISIBLE) {
                mFab.hide();
            }
            mFab.setOnClickListener(null);
            return;
        }
        mFab.setOnClickListener(mOnFabClickListener);
        if (mFab.getVisibility() != View.VISIBLE) {
            mFab.setRotation(mFabRotation);
            mFab.show();
        } else if (Math.abs(mFab.getRotation() - mFabRotation) > 0.1f) {
            mFab.animate()
                    .rotation(mFabRotation)
                    .setDuration(300)
                    .setInterpolator(new FastOutSlowInInterpolator())
                    .start();
        }
    }


    @CallSuper
    public void onPageHide() {
        mShown = false;
    }

    public boolean isShown() {
        return mShown;
    }
}
