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
        for (String semanticName : semanticNames) {
            if (JavaScriptSource.isBunFileName(semanticName)) {
                mode |= EXECUTION_MODE_BUN;
            } else if (JavaScriptSource.isNodeJsFileName(semanticName)) {
                mode |= EXECUTION_MODE_NODEJS;
            }
        }
        JavaScriptSource.validateExclusiveEngineMode(mode);
        return JavaScriptSource.resolveEngineName(mode);
    }

}
