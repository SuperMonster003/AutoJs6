package org.autojs.autojs.ui.widget;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import org.autojs.autojs.util.ViewUtils;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class SearchViewItem implements MenuItemCompat.OnActionExpandListener, SearchView.OnQueryTextListener {

    public interface QueryCallback {
        void summitQuery(String query);
    }

    private SearchView.OnQueryTextListener mQueryCallback;
    private final MenuItem mSearchMenuItem;
    private final SearchView mSearchView;
    private final Activity mActivity;

    public SearchViewItem(Activity activity, MenuItem searchMenuItem) {
        mActivity = activity;
        mSearchMenuItem = searchMenuItem;
        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        mSearchView = (SearchView) searchMenuItem.getActionView();
        if (mSearchView == null) {
            return;
        }
        mSearchView.setSubmitButtonEnabled(false);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        setColorsByThemeColorLuminance();
        MenuItemCompat.setOnActionExpandListener(searchMenuItem, this);
        mSearchView.setOnQueryTextListener(this);
    }

    public void setColorsByThemeColorLuminance() {
        if (mSearchView != null) {
            ViewUtils.setSearchViewColorsByThemeColorLuminance(mActivity, mSearchView);
        }
    }

    public void setQueryCallback(SearchView.OnQueryTextListener queryCallback) {
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
        mQueryCallback.onQueryTextSubmit(null);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (mQueryCallback != null) {
            mQueryCallback.onQueryTextSubmit(query);
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (mQueryCallback != null) {
            mQueryCallback.onQueryTextChange(newText);
        }
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
