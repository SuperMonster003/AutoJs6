package org.autojs.autojs.core.opencv;

import org.autojs.autojs.core.cleaner.ICleaner;
import org.autojs.autojs.runtime.api.Images;

/**
 * Created by SuperMonster003 on Jan 5, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Jan 5, 2024.
public class MatCleaner implements ICleaner {

    private MatCleaner() {
        /* Empty body. */
    }

    public MatCleaner(Object object) {
        this();
    }

    @Override
    public void cleanup(long pointer) {
        try {
            Images.initOpenCvIfNeeded();
            Mat.n_release().invoke(null, pointer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
