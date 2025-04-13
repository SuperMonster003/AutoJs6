package org.autojs.autojs.ui.main;

/**
 * Created by Stardust on Oct 25, 2017.
 */
public class QueryEvent {

    public static final QueryEvent CLEAR = new QueryEvent(null);
    public static final QueryEvent FIND_FORWARD = new QueryEvent(null);
    public static final QueryEvent FIND_BACKWARD = new QueryEvent("", true);

    private boolean mShouldCollapseSearchView = false;
    private final String mQuery;
    private final boolean mFindBackward;

    public QueryEvent(String query, boolean findBackward) {
        mQuery = query;
        mFindBackward = findBackward;
    }

    public QueryEvent(String query) {
        this(query, false);
    }

    public String getQuery() {
        return mQuery;
    }

    public void collapseSearchView() {
        mShouldCollapseSearchView = true;
    }

    public boolean shouldCollapseSearchView() {
        return mShouldCollapseSearchView;
    }

    public boolean isFindBackward() {
        return mFindBackward;
    }
}

