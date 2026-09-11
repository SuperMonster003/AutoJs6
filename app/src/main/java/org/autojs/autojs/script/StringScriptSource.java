package org.autojs.autojs.script;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Reader;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class StringScriptSource extends JavaScriptSource {

    private final String mScript;
    private final String mSemanticName;

    public StringScriptSource(String name, String script) {
        super(name);
        mSemanticName = name;
        mScript = script;
    }

    public StringScriptSource(String script) {
        this("Tmp", script);
    }

    @NonNull
    @Override
    public String getScript() {
        return mScript;
    }

    @Nullable
    @Override
    public Reader getScriptReader() {
        return null;
    }

    @NonNull
    @Override
    public String getEngineName() {
        int mode = getExecutionMode();
        String[] semanticNames = {mSemanticName, getOverriddenFullPath(), getOriginUri()};
        boolean hasQuickJsMarker = false;
        boolean hasBunMarker = false;
        boolean hasNodeJsMarker = false;
        boolean hasOverridableMjsMarker = false;
        for (String semanticName : semanticNames) {
            boolean isQuickJsMarker = JavaScriptSource.isQuickJsFileName(semanticName);
            hasQuickJsMarker |= isQuickJsMarker;
            hasBunMarker |= JavaScriptSource.isBunFileName(semanticName);
            if (!isQuickJsMarker && JavaScriptSource.isNodeJsFileName(semanticName)) {
                if (semanticName.toLowerCase(java.util.Locale.ROOT).endsWith(".mjs")) {
                    hasOverridableMjsMarker = true;
                } else {
                    hasNodeJsMarker = true;
                }
            }
        }
        if (hasQuickJsMarker) {
            mode |= EXECUTION_MODE_QUICKJS;
        }
        if (hasBunMarker) {
            mode |= EXECUTION_MODE_BUN;
        }
        if (hasNodeJsMarker ||
                (hasOverridableMjsMarker && (mode & EXECUTION_MODE_QUICKJS) == 0)) {
            mode |= EXECUTION_MODE_NODEJS;
        }
        JavaScriptSource.validateExclusiveEngineMode(mode);
        return JavaScriptSource.resolveEngineName(mode);
    }

}
