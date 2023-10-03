package org.autojs.autojs.core.console;

import static android.util.Log.ASSERT;
import static android.util.Log.DEBUG;
import static android.util.Log.ERROR;
import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static android.util.Log.WARN;
import static android.util.Log.d;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.autojs.autojs.tool.UiHandler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Stardust on 2017/10/22.
 */
@SuppressLint("ConstantLocale")
public class GlobalConsole extends ConsoleImpl {
    private static final String TAG = GlobalConsole.class.getSimpleName();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static final Logger LOGGER = Logger.getLogger(GlobalConsole.class);

    public GlobalConsole(UiHandler uiHandler) {
        super(uiHandler);
    }

    @Override
    @NonNull
    public String println(int level, @NonNull CharSequence charSequence) {
        String log = String.format(Locale.getDefault(), "%s/%s: %s",
                DATE_FORMAT.format(new Date()), getLevelChar(level), charSequence);
        LOGGER.log(toLog4jLevel(level), log);
        d(TAG, log);
        super.println(level, log);
        return log;
    }

    protected Priority toLog4jLevel(int level) {
        switch (level) {
            case VERBOSE, DEBUG -> {
                return Level.DEBUG;
            }
            case INFO -> {
                return Level.INFO;
            }
            case WARN -> {
                return Level.WARN;
            }
            case ERROR -> {
                return Level.ERROR;
            }
            case ASSERT -> {
                return Level.FATAL;
            }
            default -> throw new IllegalArgumentException(GlobalConsole.class.getSimpleName() + ".toLog4jLevel");
        }
    }

    protected String getLevelChar(int level) {
        return switch (level) {
            case VERBOSE -> "V";
            case DEBUG -> "D";
            case INFO -> "I";
            case WARN -> "W";
            case ERROR -> "E";
            case ASSERT -> "A";
            default -> "";
        };
    }

}
