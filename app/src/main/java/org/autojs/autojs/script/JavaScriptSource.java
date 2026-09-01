package org.autojs.autojs.script;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.rhino.TokenStream;
import org.autojs.autojs.tool.MapBuilder;
import org.autojs.autojs.util.FileUtils;
import org.mozilla.javascript.Token;

import java.io.Reader;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Stardust on Aug 2, 2017.
 */
public abstract class JavaScriptSource extends ScriptSource {

    public static final String ENGINE = JavaScriptSource.class.getName() + ".Engine";
    public static final String ENGINE_NODEJS = "nodejs";
    public static final String ENGINE_BUN = "bun";

    public static final String EXECUTION_MODE_UI_PREFIX = "\"ui\";";

    public static final int EXECUTION_MODE_RAW = -0x0001;
    public static final int EXECUTION_MODE_NORMAL = 0x0000;
    public static final int EXECUTION_MODE_UI = 0x0001;
    public static final int EXECUTION_MODE_AUTO = 0x0002;
    public static final int EXECUTION_MODE_JSOX = 0x0004;
    public static final int EXECUTION_MODE_NODEJS = 0x0008;
    public static final int EXECUTION_MODE_UI_THREAD = 0x0010;
    public static final int EXECUTION_MODE_RHINO = 0x0020;
    public static final int EXECUTION_MODE_BUN = 0x0040;

    public static final Map<String, Integer> EXECUTION_MODES = new MapBuilder<String, Integer>()
            .put("ui", EXECUTION_MODE_UI)
            .put("auto", EXECUTION_MODE_AUTO)
            .put("jsox", EXECUTION_MODE_JSOX)
            .put("x", EXECUTION_MODE_JSOX)
            .put("nodejs", EXECUTION_MODE_NODEJS)
            .put("node", EXECUTION_MODE_NODEJS)
            .put("bun", EXECUTION_MODE_BUN)
            .put("ui-thread", EXECUTION_MODE_UI_THREAD)
            .put("rhino", EXECUTION_MODE_RHINO)
            .build();

    private static final int PARSING_MAX_TOKEN = 300;

    private int mExecutionMode = EXECUTION_MODE_RAW;

    public JavaScriptSource(String name) {
        super(name, FileUtils.TYPE.JAVASCRIPT.extension);
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
        String originPath = getOriginPath();
        if (originPath != null) {
            return originPath;
        }
        String originUri = getOriginUri();
        if (originUri != null) {
            return originUri;
        }
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
                int nextToken = ts.getToken();
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
        } catch (Exception ignored) {
            // Treat malformed or unreadable prologues as normal without exposing source text.
        }
        return new ExecutionInfo.Builder()
                .setMode(EXECUTION_MODE_NORMAL)
                .setLineno(0)
                .build();
    }

    /**
     * Streaming counterpart used by preflight checks that must not load an arbitrarily large
     * script into memory. TokenStream retains only its lexer buffer and this parser stops after
     * the same bounded token count as the String overload.
     */
    public static ExecutionInfo parseExecutionMode(Reader reader) {
        TokenStream ts = new TokenStream(reader, null, 1);
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
                String tokenString = ts.getString();
                int nextToken = ts.getToken();
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
        } catch (Exception ignored) {
            // Treat malformed prologues as normal without logging source text.
        }
        return new ExecutionInfo.Builder()
                .setMode(EXECUTION_MODE_NORMAL)
                .setLineno(0)
                .build();
    }

    private static int parseExecutionModeByModeStrings(String[] modeStrings) {
        int mode = EXECUTION_MODE_NORMAL;
        for (String modeString : modeStrings) {
            String niceModeString = modeString.toLowerCase(Locale.ROOT);
            Integer i = EXECUTION_MODES.get(niceModeString);
            if (i != null) {
                mode |= i;
            }
        }
        return mode;
    }

    public static String resolveEngineName(int executionMode) {
        validateExclusiveEngineMode(executionMode);
        if ((executionMode & EXECUTION_MODE_BUN) != 0) {
            return ENGINE_BUN;
        }
        return (executionMode & EXECUTION_MODE_NODEJS) != 0 ? ENGINE_NODEJS : ENGINE;
    }

    public static void validateExclusiveEngineMode(int executionMode) {
        int engineModes = executionMode & (
                EXECUTION_MODE_NODEJS |
                        EXECUTION_MODE_RHINO |
                        EXECUTION_MODE_BUN
        );
        if (Integer.bitCount(engineModes) > 1) {
            throw new IllegalArgumentException(
                    "A script cannot select more than one JavaScript engine: 0x" +
                            Integer.toHexString(engineModes)
            );
        }
    }

    public static boolean isBunFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (isTypeScriptDeclarationFileName(lowerCaseName)) {
            return false;
        }
        return lowerCaseName.endsWith(".bun.js") ||
                lowerCaseName.endsWith(".bun.ts") ||
                lowerCaseName.endsWith(".bun.tsx");
    }

    public static boolean isNodeJsFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (isTypeScriptDeclarationFileName(lowerCaseName)) {
            return false;
        }
        return lowerCaseName.endsWith(".mjs") ||
                lowerCaseName.endsWith(".cjs") ||
                lowerCaseName.endsWith(".mts") ||
                lowerCaseName.endsWith(".cts") ||
                lowerCaseName.endsWith(".node.js");
    }

    public static boolean isRhinoTypeScriptFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        return (lowerCaseName.endsWith(".ts") || lowerCaseName.endsWith(".tsx")) &&
                !isTypeScriptDeclarationFileName(lowerCaseName);
    }

    public static boolean isCompilerBackedTypeScriptFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (isTypeScriptDeclarationFileName(lowerCaseName)) {
            return false;
        }
        return lowerCaseName.endsWith(".ts") ||
                lowerCaseName.endsWith(".tsx") ||
                lowerCaseName.endsWith(".mts") ||
                lowerCaseName.endsWith(".cts");
    }

    public static boolean isUnsupportedSingleFileTypeScriptFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        return isTypeScriptDeclarationFileName(lowerCaseName);
    }

    public static boolean isJavaScriptFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (isTypeScriptDeclarationFileName(lowerCaseName)) {
            return false;
        }
        return lowerCaseName.endsWith(".js") ||
                lowerCaseName.endsWith(".mjs") ||
                lowerCaseName.endsWith(".cjs") ||
                lowerCaseName.endsWith(".mts") ||
                lowerCaseName.endsWith(".cts") ||
                lowerCaseName.endsWith(".ts") ||
                lowerCaseName.endsWith(".tsx");
    }

    public static boolean isTypeScriptDeclarationFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        return lowerCaseName.endsWith(".d.ts") ||
                lowerCaseName.endsWith(".d.mts") ||
                lowerCaseName.endsWith(".d.cts");
    }

    @Override
    public String getEngineName() {
        return resolveEngineName(getExecutionMode());
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
