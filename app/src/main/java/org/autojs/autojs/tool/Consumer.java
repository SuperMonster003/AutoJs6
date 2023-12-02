package org.autojs.autojs.tool;

import androidx.annotation.Keep;

/**
 * Created by Stardust on Mar 10, 2017.
 */
@Keep
public interface Consumer<T> {

    void accept(T t);

}
