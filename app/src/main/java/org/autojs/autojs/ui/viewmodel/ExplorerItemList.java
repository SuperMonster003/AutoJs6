package org.autojs.autojs.ui.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;

import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerSorter;
import org.autojs.autojs6.R;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by Stardust on 2017/9/30.
 */
public class ExplorerItemList {

    public static final int SORT_TYPE_NAME = 0x10;
    public static final int SORT_TYPE_TYPE = 0x20;
    public static final int SORT_TYPE_SIZE = 0x30;
    public static final int SORT_TYPE_DATE = 0x40;

    private final Context mContext;

    private SortConfig mSortConfig;
    private final ArrayList<ExplorerItem> mItems = new ArrayList<>();
    private final ArrayList<ExplorerPage> mItemGroups = new ArrayList<>();

    public ExplorerItemList(Context context) {
        mContext = context;
        mSortConfig = new SortConfig();
    }

    public boolean isDirSortedAscending() {
        return mSortConfig.isDirSortedAscending;
    }

    public boolean isFileSortedAscending() {
        return mSortConfig.isFileSortedAscending;
    }

    public int getDirSortType() {
        return mSortConfig.dirSortType;
    }

    public void setDirSortType(int sortType) {
        mSortConfig.dirSortType = sortType;
    }

    public int getFileSortType() {
        return mSortConfig.fileSortType;
    }

    public void setFileSortType(int sortType) {
        mSortConfig.fileSortType = sortType;
    }

    public void setDirSortedAscending(boolean dirSortedAscending) {
        mSortConfig.isDirSortedAscending = dirSortedAscending;
    }

    public void setFileSortedAscending(boolean fileSortedAscending) {
        mSortConfig.isFileSortedAscending = fileSortedAscending;
    }

    private Comparator<ExplorerItem> getComparator(int sortType) {
        switch (sortType) {
            case SORT_TYPE_NAME:
                return ExplorerSorter.NAME;
            case SORT_TYPE_DATE:
                return ExplorerSorter.DATE;
            case SORT_TYPE_SIZE:
                return ExplorerSorter.SIZE;
            case SORT_TYPE_TYPE:
                return ExplorerSorter.TYPE;
        }
        throw new IllegalArgumentException(mContext.getString(R.string.error_illegal_argument, "sortType", String.valueOf(sortType)));
    }

    public int groupCount() {
        return mItemGroups.size();
    }

    public int itemCount() {
        return mItems.size();
    }

    public void clear() {
        mItems.clear();
        mItemGroups.clear();
    }

    public void add(ExplorerItem item) {
        if (item instanceof ExplorerPage) {
            mItemGroups.add((ExplorerPage) item);
        } else {
            mItems.add(item);
        }
    }

    public void insertAtFront(ExplorerItem item) {
        if (item instanceof ExplorerPage) {
            mItemGroups.add(0, (ExplorerPage) item);
        } else {
            mItems.add(0, item);
        }
    }


    public int remove(ExplorerItem item) {
        if (item instanceof ExplorerPage) {
            return remove(mItemGroups, item);
        } else {
            return remove(mItems, item);
        }
    }

    public int update(ExplorerItem oldItem, ExplorerItem newItem) {
        if (oldItem instanceof ExplorerPage) {
            return update(mItemGroups, (ExplorerPage) oldItem, (ExplorerPage) newItem);
        } else {
            return update(mItems, oldItem, newItem);
        }
    }

    private <T> int update(ArrayList<T> list, T oldItem, T newItem) {
        int i = list.indexOf(oldItem);
        if (i >= 0) {
            list.set(i, newItem);
        }
        return i;
    }

    private <T> int remove(ArrayList<?> list, T o) {
        int i = list.indexOf(o);
        if (i >= 0) {
            list.remove(i);
        }
        return i;
    }

    public ExplorerPage getItemGroup(int i) {
        return mItemGroups.get(i);
    }

    public ExplorerItem getItem(int i) {
        return mItems.get(i);
    }

    public int count() {
        return mItems.size() + mItemGroups.size();
    }

    public void sortItemGroup(int sortType, boolean isFileSortedAscending) {
        setDirSortType(sortType);
        setFileSortedAscending(isFileSortedAscending);
        ExplorerSorter.sort(mItemGroups, getComparator(sortType), isFileSortedAscending);
    }

    public void sortFile(int sortType, boolean isFileSortedAscending) {
        setFileSortType(sortType);
        setFileSortedAscending(isFileSortedAscending);
        ExplorerSorter.sort(mItems, getComparator(sortType), isFileSortedAscending);
    }

    public void sort() {
        ExplorerSorter.sort(mItemGroups, getComparator(mSortConfig.dirSortType), mSortConfig.isDirSortedAscending);
        ExplorerSorter.sort(mItems, getComparator(mSortConfig.fileSortType), mSortConfig.isFileSortedAscending);
    }

    public SortConfig getSortConfig() {
        return mSortConfig;
    }

    public void setSortConfig(SortConfig sortConfig) {
        mSortConfig = sortConfig;
    }

    public ExplorerItemList cloneConfig() {
        ExplorerItemList list = new ExplorerItemList(mContext);
        list.mSortConfig = mSortConfig;
        return list;
    }

    public static class SortConfig {

        private static final String CLASS_NAME = SortConfig.class.getName();

        public int dirSortType = SORT_TYPE_NAME;
        public int fileSortType = SORT_TYPE_NAME;
        private boolean isDirSortedAscending = true;
        private boolean isFileSortedAscending = true;

        public int getDirSortType() {
            return dirSortType;
        }

        public void setDirSortType(int dirSortType) {
            this.dirSortType = dirSortType;
        }

        public void setDirSortedAscending(boolean dirSortedAscending) {
            isDirSortedAscending = dirSortedAscending;
        }

        public void setFileSortedAscending(boolean fileSortedAscending) {
            isFileSortedAscending = fileSortedAscending;
        }

        public void setFileSortType(int fileSortType) {
            this.fileSortType = fileSortType;
        }

        public void saveInto(SharedPreferences preferences) {
            preferences.edit()
                    .putInt(CLASS_NAME + "." + "file_sort_type", fileSortType)
                    .putInt(CLASS_NAME + "." + "dir_sort_type", dirSortType)
                    .putBoolean(CLASS_NAME + "." + "file_ascending", isFileSortedAscending)
                    .putBoolean(CLASS_NAME + "." + "dir_ascending", isDirSortedAscending)
                    .apply();
        }

        public static SortConfig from(SharedPreferences preferences) {
            SortConfig config = new SortConfig();
            config.setDirSortedAscending(preferences.getBoolean(CLASS_NAME + "." + "dir_ascending", true));
            config.setFileSortedAscending(preferences.getBoolean(CLASS_NAME + "." + "file_ascending", true));
            config.setDirSortType(preferences.getInt(CLASS_NAME + "." + "dir_sort_type", SORT_TYPE_NAME));
            config.setFileSortType(preferences.getInt(CLASS_NAME + "." + "file_sort_type", SORT_TYPE_NAME));
            return config;
        }
    }

}
