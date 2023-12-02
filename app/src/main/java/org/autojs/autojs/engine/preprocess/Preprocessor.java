package org.autojs.autojs.engine.preprocess;

import java.io.IOException;
import java.io.Reader;

/**
 * Created by Stardust on May 15, 2017.
 */
public interface Preprocessor {

    Reader preprocess(Reader reader) throws IOException;
}
