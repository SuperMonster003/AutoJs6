package org.autojs.autojs.app;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Mar 24, 2017.
 */
public class FragmentPagerAdapterBuilder {

    public interface OnFragmentInstantiateListener {
        void onInstantiate(int pos, Fragment fragment);
    }

    private final List<Fragment> mFragments = new ArrayList<>();
    private final List<String> mTitles = new ArrayList<>();
    private final WeakReference<FragmentActivity> mActivity;

    public FragmentPagerAdapterBuilder(FragmentActivity activity) {
        mActivity = new WeakReference<>(activity);
    }

    public FragmentPagerAdapterBuilder add(Fragment fragment, String title) {
        mFragments.add(fragment);
        mTitles.add(title);
        return this;
    }

    public FragmentPagerAdapterBuilder add(Fragment fragment, int titleResId) {
        return add(fragment, mActivity.get().getString(titleResId));
    }

    public StoredFragmentPagerAdapter build() {
        return new StoredFragmentPagerAdapter(mActivity.get().getSupportFragmentManager()) {
            @NonNull
            @Override
            public Fragment getItem(int position) {
                return mFragments.get(position);
            }

            @Override
            public int getCount() {
                return mFragments.size();
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return mTitles.get(position);
            }
        };
    }

    public abstract static class StoredFragmentPagerAdapter extends FragmentPagerAdapter {

        private final SparseArray<Fragment> mStoredFragments = new SparseArray<>();
        private OnFragmentInstantiateListener mOnFragmentInstantiateListener;

        public StoredFragmentPagerAdapter(FragmentManager fm) {
            // Retain every page instance, but only resume the page that is actually visible.
            // Hidden pages must not receive onResume callbacks or start expensive refresh work
            // merely because their host Activity returned to the foreground.
            // zh-CN: 保留所有页面实例, 但仅恢复实际可见的页面.
            // 隐藏页面不得仅因宿主 Activity 回到前台就收到 onResume 或启动高开销刷新.
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            mStoredFragments.put(position, fragment);
            if (mOnFragmentInstantiateListener != null) {
                mOnFragmentInstantiateListener.onInstantiate(position, fragment);
        }
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            mStoredFragments.remove(position);
            super.destroyItem(container, position, object);
    }

        public Fragment getStoredFragment(int position) {
            return mStoredFragments.get(position);
    }

        public void setOnFragmentInstantiateListener(OnFragmentInstantiateListener onFragmentInstantiateListener) {
            mOnFragmentInstantiateListener = onFragmentInstantiateListener;
        }
    }
}
