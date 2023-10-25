package org.autojs.autojs.script;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.pref.Language;
import org.autojs.autojs.rhino.TokenStream;
import org.autojs.autojs.tool.MapBuilder;
import org.mozilla.javascript.Token;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * Created by Stardust on 2017/8/2.
 */
public abstract class JavaScriptSource extends ScriptSource {

    public static final String ENGINE = JavaScriptSource.class.getName() + ".Engine";

    public static final String EXECUTION_MODE_UI_PREFIX = "\"ui\";";

    public static final int EXECUTION_MODE_RAW = -0x1;
    public static final int EXECUTION_MODE_NORMAL = 0x0;
    public static final int EXECUTION_MODE_UI = 0x1;
    public static final int EXECUTION_MODE_AUTO = 0x2;
    public static final int EXECUTION_MODE_MODULE_CHEERIO = 0x10;

    private static final String LOG_TAG = "JavaScriptSource";

    private static final Map<String, Integer> EXECUTION_MODES = new MapBuilder<String, Integer>()
            .put("ui", EXECUTION_MODE_UI)
            .put("auto", EXECUTION_MODE_AUTO)
            .put("cheerio", EXECUTION_MODE_MODULE_CHEERIO)
            .build();

    private static final int PARSING_MAX_TOKEN = 300;

    private int mExecutionMode = EXECUTION_MODE_RAW;

    public JavaScriptSource(String name) {
        super(name);
    }

    @NonNull
    public abstract String getScript();

    @Nullable
    public abstract Reader getScriptReader();

    @NonNull
    public Reader getNonNullScriptReader() {
        Reader reader = getScriptReader();
        if (reader == null) {
            return new StringReader(getScript());
        }
        return reader;
    }

    @NonNull
    public String toString() {
        String prefix = getPrefix();
        return prefix == null ? getFullName() : prefix + getFullName();
    }

    public int getExecutionMode() {
        if (mExecutionMode == EXECUTION_MODE_RAW) {
            mExecutionMode = parseExecutionMode();
        }
        return mExecutionMode;
    }

    protected int parseExecutionMode() {
        String script = getScript();
        TokenStream ts = new TokenStream(new StringReader(script), null, 1);
        int token;
        int count = 0;
        try {
            while (count <= PARSING_MAX_TOKEN && (token = ts.getToken()) != Token.EOF) {
                count++;
                if (token == Token.EOL || token == Token.COMMENT) {
                    continue;
                }
                if (token != Token.STRING || ts.getTokenLength() < 3) {
                    break;
                }
                String tokenString = script.substring(ts.getTokenBeg() + 1, ts.getTokenEnd() - 1);
                if (ts.getToken() != Token.SEMI) {
                    break;
                }
                Log.d(LOG_TAG, "string = " + tokenString);
                return parseExecutionMode(tokenString.split("\\s*[,;|]\\s*|\\s+"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return EXECUTION_MODE_NORMAL;
    }

    private int parseExecutionMode(String[] modeStrings) {
        int mode = EXECUTION_MODE_NORMAL;
        for (String modeString : modeStrings) {
            String niceModeString = modeString.toLowerCase(Language.getPrefLanguage().getLocale());
            Integer i = EXECUTION_MODES.get(niceModeString);
            if (i != null) {
                mode |= i;
            }
        }
        return mode;
    }

    @Override
    public String getEngineName() {
        return ENGINE;
    }


}
