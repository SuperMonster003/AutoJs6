package org.autojs.autojs.script;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Reader;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class StringScriptSource extends JavaScriptSource {

    private final String mScript;

    public StringScriptSource(String name, String script) {
        super(name);
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

}
