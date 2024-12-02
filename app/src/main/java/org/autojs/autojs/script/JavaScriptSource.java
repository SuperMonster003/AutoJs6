package org.autojs.autojs.script;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.rhino.TokenStream;
import org.autojs.autojs.tool.MapBuilder;
import org.mozilla.javascript.Token;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;

/**
 * Created by Stardust on Aug 2, 2017.
 */
public abstract class JavaScriptSource extends ScriptSource {

    public static final String ENGINE = JavaScriptSource.class.getName() + ".Engine";

    public static final String EXECUTION_MODE_UI_PREFIX = "\"ui\";";

    public static final int EXECUTION_MODE_RAW = -0x0001;
    public static final int EXECUTION_MODE_NORMAL = 0x0000;
    public static final int EXECUTION_MODE_UI = 0x0001;
    public static final int EXECUTION_MODE_AUTO = 0x0002;
    public static final int EXECUTION_MODE_JSOX = 0x0004;
    public static final int EXECUTION_MODE_MODULE_AXIOS = 0x0010;
    public static final int EXECUTION_MODE_MODULE_CHEERIO = 0x0020;
    public static final int EXECUTION_MODE_MODULE_DAYJS = 0x0040;
    public static final int EXECUTION_MODE_MODULE_I18N = 0x0080;

    private static final String LOG_TAG = "JavaScriptSource";

    public static final Map<String, Integer> EXECUTION_MODES = new MapBuilder<String, Integer>()
            .put("ui", EXECUTION_MODE_UI)
            .put("auto", EXECUTION_MODE_AUTO)
            .put("jsox", EXECUTION_MODE_JSOX)
            .put("x", EXECUTION_MODE_JSOX)
            .put("axios", EXECUTION_MODE_MODULE_AXIOS)
            .put("cheerio", EXECUTION_MODE_MODULE_CHEERIO)
            .put("dayjs", EXECUTION_MODE_MODULE_DAYJS)
            .put("i18n", EXECUTION_MODE_MODULE_I18N)
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
        return parseExecutionMode(getScript()).mode;
    }

    public static ExecutionInfo parseExecutionMode(String script) {
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
                Log.d(LOG_TAG, "token string: " + tokenString);
                int nextToken = ts.getToken();
                Log.d(LOG_TAG, "next token: " + nextToken);
                if (nextToken != Token.SEMI && nextToken != Token.EOL) {
                    break;
                }
                int mode = parseExecutionModeByModeStrings(tokenString.split("\\s*[,;|]\\s*|\\s+"));
                int lineno = ts.getLineno();
                return new ExecutionInfo.Builder()
                        .setMode(mode)
                        .setLineno(lineno)
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ExecutionInfo.Builder()
                .setMode(EXECUTION_MODE_NORMAL)
                .setLineno(0)
                .build();
    }

    private static int parseExecutionModeByModeStrings(String[] modeStrings) {
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

    public static class ExecutionInfo {

        private final int mode;
        private final int lineno;

        private ExecutionInfo(Builder builder) {
            this.mode = builder.mode;
            this.lineno = builder.lineno;
        }

        public int getMode() {
            return this.mode;
        }

        public int getLineno() {
            return this.lineno;
        }

        public static class Builder {
            private int mode;
            private int lineno;

            public Builder setMode(int mode) {
                this.mode = mode;
                return this;
            }

            public Builder setLineno(int lineno) {
                this.lineno = lineno;
                return this;
            }

            public ExecutionInfo build() {
                return new ExecutionInfo(this);
            }
        }
    }

}
