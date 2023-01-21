package org.autojs.autojs.tool;

import androidx.annotation.Keep;

/**
 * Created by Stardust on 2017/3/10.
 */
@Keep
public interface Consumer<T> {

    void accept(T t);

}
