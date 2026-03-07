package com.bignerdranch.expandablerecyclerview.model;

import java.util.List;

/**
 * Simple implementation of the ParentListItem interface,
 * by default all items are not initially expanded.
 *
 * @param <C> Type of the Child Items held by the Parent.
 */
public class SimpleParent<C> implements Parent<C> {

    private List<C> mChildList;

    protected SimpleParent(List<C> childItemList) {
        mChildList = childItemList;
    }

    @Override
    public boolean isInitiallyExpanded() {
        return false;
    }

    @Override
    public List<C> getChildList() {
        return mChildList;
    }

    public void setChildList(List<C> childList) {
        mChildList = childList;
    }
}
