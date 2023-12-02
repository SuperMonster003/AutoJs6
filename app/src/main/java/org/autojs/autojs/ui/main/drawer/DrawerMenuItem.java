package org.autojs.autojs.ui.main.drawer;

import androidx.annotation.Nullable;

import java.io.IOException;

/**
 * Created by Stardust on Aug 25, 2017.
 */
public class DrawerMenuItem {

    public interface Action {
        void onClick(DrawerMenuItemViewHolder holder) throws IOException;
    }

    public static final int DEFAULT_DIALOG_CONTENT = 0;

    public static final int DEFAULT_PREFERENCE_KEY = 0;

    private final int mIcon;
    private final int mTitle;
    private String mSubtitle;
    @Nullable
    private CharSequence mContent;
    @Nullable
    private Action mAction;
    private boolean mIsHidden;
    private boolean mAntiShake;
    private boolean mOnProgress;
    private boolean mSwitchEnabled;
    private boolean mSwitchChecked;
    private int mPrefKey;

    public DrawerMenuItem(int icon, int title) {
        mIcon = icon;
        mTitle = title;
    }

    public DrawerMenuItem(int icon, int title, int prefKey) {
        mIcon = icon;
        mTitle = title;
        if (prefKey == DEFAULT_PREFERENCE_KEY) {
            mAntiShake = true;
        }
        mPrefKey = prefKey;
        mSwitchEnabled = true;
    }

    public void setAction(Action action) {
        mAction = action;
    }

    public void setContent(CharSequence content) {
        mContent = content;
    }

    public void setHidden(boolean isHidden) {
        mIsHidden = isHidden;
    }

    public void setSubtitle(String subtitle) {
        mSubtitle = subtitle;
    }

    public boolean isHidden() {
        return mIsHidden;
    }

    public int getIcon() {
        return mIcon;
    }

    public int getTitle() {
        return mTitle;
    }

    public String getSubtitle() {
        return mSubtitle;
    }

    public CharSequence getContent() {
        return mContent;
    }

    public boolean antiShake() {
        return mAntiShake;
    }

    public boolean isSwitchEnabled() {
        return mSwitchEnabled;
    }

    public void setChecked(boolean checked) {
        mSwitchChecked = checked;
    }

    public boolean isChecked() {
        return mSwitchChecked;
    }

    public boolean isProgress() {
        return mOnProgress;
    }

    public void setProgress(boolean onProgress) {
        mOnProgress = onProgress;
    }

    public int getPrefKey() {
        return mPrefKey;
    }

    public void performAction(DrawerMenuItemViewHolder holder) throws IOException {
        if (mAction != null)
            mAction.onClick(holder);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DrawerMenuItem that = (DrawerMenuItem) o;

        return mTitle == that.mTitle;
    }

    @Override
    public int hashCode() {
        return mTitle;
    }

}
