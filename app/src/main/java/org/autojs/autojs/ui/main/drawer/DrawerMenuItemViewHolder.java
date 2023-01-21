package org.autojs.autojs.ui.main.drawer;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.ui.widget.PrefSwitch;
import org.autojs.autojs.ui.widget.SwitchCompat;
import org.autojs.autojs6.R;

import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

/**
 * Created by Stardust on 2017/12/10.
 */
public class DrawerMenuItemViewHolder extends BindableViewHolder<DrawerMenuItem> {

    public static final long CLICK_TIMEOUT = 540;
    @BindView(R.id.sw)
    PrefSwitch mSwitchCompat;

    @BindView(R.id.progress_bar)
    MaterialProgressBar mProgressBar;

    @BindView(R.id.icon)
    ImageView mIcon;

    @BindView(R.id.title)
    TextView mTitle;

    @BindView(R.id.subtitle)
    TextView mSubtitle;

    private boolean mAntiShake;
    private long mLastClickMillis;
    private DrawerMenuItem mDrawerMenuItem;

    public DrawerMenuItemViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        mSwitchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                onClick();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        itemView.setOnClickListener(v -> {
            if (mSwitchCompat.getVisibility() == VISIBLE) {
                mSwitchCompat.toggle();
            } else {
                try {
                    onClick();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void bind(DrawerMenuItem item, int position) {
        mDrawerMenuItem = item;
        mIcon.setImageResource(item.getIcon());
        mTitle.setText(item.getTitle());

        String subtitle = item.getSubtitle();
        mSubtitle.setText(subtitle);
        mSubtitle.setVisibility(subtitle != null ? VISIBLE : GONE);

        mAntiShake = item.antiShake();
        setSwitch(item);
        setProgress(item.isProgress());
    }

    private void setSwitch(DrawerMenuItem item) {
        if (!item.isSwitchEnabled()) {
            mSwitchCompat.setVisibility(GONE);
            return;
        }
        mSwitchCompat.setVisibility(VISIBLE);
        int prefKey = item.getPrefKey();
        if (prefKey == DrawerMenuItem.DEFAULT_PREFERENCE_KEY) {
            mSwitchCompat.setChecked(item.isChecked(), false);
            mSwitchCompat.setPrefKey(null);
        } else {
            mSwitchCompat.setPrefKey(itemView.getResources().getString(prefKey));
        }
    }

    private void onClick() throws IOException {
        mDrawerMenuItem.setChecked(mSwitchCompat.isChecked());
        if (mAntiShake && (System.currentTimeMillis() - mLastClickMillis < CLICK_TIMEOUT)) {
            mSwitchCompat.setChecked(!mSwitchCompat.isChecked(), false);
            return;
        }
        mLastClickMillis = System.currentTimeMillis();
        if (mDrawerMenuItem != null) {
            mDrawerMenuItem.performAction(this);
        }
    }

    private void setProgress(boolean onProgress) {
        mProgressBar.setVisibility(onProgress ? VISIBLE : GONE);
        mIcon.setVisibility(onProgress ? GONE : VISIBLE);
        mSwitchCompat.setEnabled(!onProgress);
        itemView.setEnabled(!onProgress);
    }

    public SwitchCompat getSwitchCompat() {
        return mSwitchCompat;
    }

}
