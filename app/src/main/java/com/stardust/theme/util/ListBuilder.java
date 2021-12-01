package com.stardust.theme.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on 2017/3/5.
 */

public class ListBuilder<T> {

    private List<T> mList;

    public ListBuilder() {
        this(new ArrayList<T>());
    }

    public ListBuilder(List<T> list) {
        mList = list;
    }

    public ListBuilder<T> add(T entry) {
        mList.add(entry);
        return this;
    }

    public List<T> list() {
        return mList;
    }
}
