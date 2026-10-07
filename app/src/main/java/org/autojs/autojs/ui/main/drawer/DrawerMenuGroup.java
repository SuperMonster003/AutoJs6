package org.autojs.autojs.ui.main.drawer;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Created by Stardust on Aug 25, 2017.
 */
public class DrawerMenuGroup extends DrawerMenuItem {

    @DrawableRes
    private final int mActionIcon;
    @StringRes
    private final int mActionDescription;
    @Nullable
    private final Runnable mAction;

    public DrawerMenuGroup(int title) {
        this(title, 0, 0, null);
    }

    /**
     * A group title with an icon button at its end, for example to open a wizard of the group.
     * zh-CN: 末尾带图标按钮的分组标题, 例如用于打开该分组的向导.
     */
    public DrawerMenuGroup(int title, @DrawableRes int actionIcon, @StringRes int actionDescription, @Nullable Runnable action) {
        super(0, title);
        mActionIcon = actionIcon;
        mActionDescription = actionDescription;
        mAction = action;
    }

    public boolean hasAction() {
        return mAction != null && mActionIcon != 0;
    }

    @DrawableRes
    public int getActionIcon() {
        return mActionIcon;
    }

    @StringRes
    public int getActionDescription() {
        return mActionDescription;
    }

    public void performGroupAction() {
        if (mAction != null) {
            mAction.run();
        }
    }

}
