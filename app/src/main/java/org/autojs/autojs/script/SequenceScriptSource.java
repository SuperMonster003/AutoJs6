package org.autojs.autojs.script;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.io.ConcatReader;

import java.io.Reader;
import java.io.StringReader;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class SequenceScriptSource extends JavaScriptSource {

    private String mScript;
    private final JavaScriptSource mSecondScriptSource;
    private final JavaScriptSource mFirstScriptSource;

    public SequenceScriptSource(String name, JavaScriptSource firstScriptSource, JavaScriptSource secondScriptSource) {
        super(name);
        mSecondScriptSource = secondScriptSource;
        mFirstScriptSource = firstScriptSource;
    }

    @NonNull
    public JavaScriptSource getFirstScriptSource() {
        return mFirstScriptSource;
    }

    @NonNull
    public JavaScriptSource getSecondScriptSource() {
        return mSecondScriptSource;
    }

    @NonNull
    @Override
    public String getScript() {
        concatScriptsIfNeeded();
        return mScript;
    }

    private void concatScriptsIfNeeded() {
        if (mScript != null)
            return;
        mScript = mFirstScriptSource.getScript() + mSecondScriptSource.getScript();
    }

    @Nullable
    @Override
    public Reader getScriptReader() {
        if (mScript != null)
            return new StringReader(mScript);
        return new ConcatReader(mFirstScriptSource.getNonNullScriptReader(), mSecondScriptSource.getNonNullScriptReader());
    }

    @NonNull
    @Override
    public String getEngineName() {
        if (ENGINE_BUN.equals(mFirstScriptSource.getEngineName()) ||
                ENGINE_BUN.equals(mSecondScriptSource.getEngineName())) {
            throw new IllegalArgumentException(
                    "Bun launch source does not support AutoJs6 preExecuteScript"
            );
        }
        String resolved = super.getEngineName();
        if (ENGINE_BUN.equals(resolved)) {
            throw new IllegalArgumentException(
                    "Bun launch source does not support AutoJs6 preExecuteScript"
            );
        }
        return resolved;
    }

    @NonNull
    @Override
    public String toString() {
        return mSecondScriptSource.toString();
    }

}
