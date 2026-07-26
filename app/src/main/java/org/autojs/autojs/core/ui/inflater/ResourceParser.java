package org.autojs.autojs.core.ui.inflater;

import org.autojs.autojs.core.ui.inflater.util.Drawables;

/**
 * Created by Stardust on Jan 24, 2018.
 */
public class ResourceParser {

    private final Drawables mDrawables;
    private final AndroidResourceResolver mAndroidResources;

    public ResourceParser(Drawables drawables) {
        this(drawables, new AndroidResourceResolver());
    }

    public ResourceParser(Drawables drawables, AndroidResourceResolver androidResources) {
        mDrawables = drawables;
        mAndroidResources = androidResources;
        mDrawables.setAndroidResourceResolver(androidResources);
    }

    public Drawables getDrawables() {
        return mDrawables;
    }

    public AndroidResourceResolver getAndroidResources() {
        return mAndroidResources;
    }

}
