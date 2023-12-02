package org.autojs.autojs.pio;

import java.io.IOException;

/**
 * Created by Stardust on Apr 1, 2017.
 */
public class UncheckedIOException extends RuntimeException {

    public UncheckedIOException(IOException cause) {
        super(cause);
    }

    @Override
    public synchronized IOException getCause() {
        return (IOException) super.getCause();
    }

}
