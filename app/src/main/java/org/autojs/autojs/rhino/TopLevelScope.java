package org.autojs.autojs.rhino;

import org.mozilla.javascript.ImporterTopLevel;

public class TopLevelScope extends ImporterTopLevel {

    public TopLevelScope() {
        getGlobalThis().setParentScope(this);
    }
}
