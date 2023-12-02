package org.autojs.autojs.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Stardust on May 2, 2017.
 */
public class UnderUseExecutors {

    private static final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    public static void execute(Runnable runnable) {
        mExecutor.execute(runnable);
    }

    public static ExecutorService getExecutor() {
        return mExecutor;
    }

}
