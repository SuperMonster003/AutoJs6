package org.autojs.autojs.ui.main.drawer;

import java.io.IOException;

/**
 * Created by Stardust on 2017/8/25.
 */
public class DrawerMenuItem {


    public interface Action {
        void onClick(DrawerMenuItemViewHolder holder) throws IOException;
    }

    private final int mIcon;
    private final int mTitle;
    private final Action mAction;
    private boolean mAntiShake;
    private boolean mOnProgress;
    private boolean mSwitchEnabled;
    private boolean mSwitchChecked;
    private int mPrefKey;
    private int mNotificationCount;

    public DrawerMenuItem(int icon, int title, Action action) {
        mIcon = icon;
        mTitle = title;
        mAction = action;
    }

    public DrawerMenuItem(int icon, int title, int prefKey, Action action) {
        mIcon = icon;
        mTitle = title;
        mAction = action;
        if (prefKey == 0) {
            mAntiShake = true;
        }
        mPrefKey = prefKey;
        mSwitchEnabled = true;
    }

    public int getNotificationCount() {
        return mNotificationCount;
    }

    public void setNotificationCount(int notificationCount) {
        mNotificationCount = notificationCount;
    }

    public int getIcon() {
        return mIcon;
    }

    public int getTitle() {
        return mTitle;
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
