package com.stardust.autojs.rhino;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created by Stardust on 2017/4/5.
 */
public class AndroidClassLoader extends org.autojs.autojs.rhino.AndroidClassLoader {
    public AndroidClassLoader(@NotNull ClassLoader parent, @NotNull File cacheDir) {
        super(parent, cacheDir);
    }
}
