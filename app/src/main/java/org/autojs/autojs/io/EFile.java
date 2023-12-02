package org.autojs.autojs.io;

import androidx.annotation.NonNull;

import java.io.File;
import java.net.URI;

/**
 * Created by Stardust on Aug 19, 2017.
 */
public class EFile extends File {

    public EFile(@NonNull String pathname) {
        super(pathname);
    }

    public EFile(String parent, @NonNull String child) {
        super(parent, child);
    }

    public EFile(File parent, @NonNull String child) {
        super(parent, child);
    }

    public EFile(@NonNull URI uri) {
        super(uri);
    }
}
