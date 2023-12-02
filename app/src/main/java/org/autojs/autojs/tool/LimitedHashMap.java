package org.autojs.autojs.tool;

import java.util.LinkedHashMap;

/**
 * Created by Stardust on Mar 31, 2017.
 */
public class LimitedHashMap<K, V> extends LinkedHashMap<K, V> {

    private final int mMaxSize;

    public LimitedHashMap(int maxSize) {
        super(4, 0.75f, true);
        mMaxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        return size() > mMaxSize;
    }


}
