package org.autojs.autojs.ui.widget;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import org.autojs.autojs.theme.ThemeColorManager;
import org.autojs.autojs6.R;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class SearchViewItem implements MenuItemCompat.OnActionExpandListener, SearchView.OnQueryTextListener {

    public interface QueryCallback {
        void summitQuery(String query);
    }

    private QueryCallback mQueryCallback;
    private final MenuItem mSearchMenuItem;
    private final Activity mActivity;
    private EditText mTextview;
    private ImageView mCloseButtonView;
    private ImageView mSearchGoButtonView;

    public SearchViewItem(Activity activity, MenuItem searchMenuItem) {
        mActivity = activity;
        mSearchMenuItem = searchMenuItem;
        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        if (searchView == null) {
            return;
        }
        searchView.setSubmitButtonEnabled(true);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        mTextview = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        mCloseButtonView = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        mSearchGoButtonView = searchView.findViewById(androidx.appcompat.R.id.search_go_btn);
        initThemeColors();
        MenuItemCompat.setOnActionExpandListener(searchMenuItem, this);
        searchView.setOnQueryTextListener(this);
    }

    public void initThemeColors() {
        boolean isThemeColorLuminanceLight = ThemeColorManager.isLuminanceLight();
        int fullColor = mActivity.getColor(isThemeColorLuminanceLight ? R.color.day_full : R.color.night_full);
        int hintColor = mActivity.getColor(isThemeColorLuminanceLight ? R.color.day : R.color.night);

        if (mTextview != null) {
            mTextview.setTextColor(fullColor);
            mTextview.setHintTextColor(hintColor);
        }
        if (mCloseButtonView != null) {
            mCloseButtonView.setColorFilter(fullColor);
        }
        if (mSearchGoButtonView != null) {
            mSearchGoButtonView.setColorFilter(fullColor);
        }
    }

    public void setQueryCallback(QueryCallback queryCallback) {
        mQueryCallback = queryCallback;
    }

    public void setVisible(boolean visible) {
        mSearchMenuItem.setVisible(visible);
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        if (mQueryCallback == null) {
            return true;
        }
        mQueryCallback.summitQuery(null);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (mQueryCallback == null) {
            return true;
        }
        mQueryCallback.summitQuery(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    public void expand() {
        mSearchMenuItem.expandActionView();
    }

    public boolean isExpanded() {
        return mSearchMenuItem.isActionViewExpanded();
    }

    public void collapse() {
        mSearchMenuItem.collapseActionView();
    }

    public boolean isCollapsed() {
        return !isExpanded();
    }

}
