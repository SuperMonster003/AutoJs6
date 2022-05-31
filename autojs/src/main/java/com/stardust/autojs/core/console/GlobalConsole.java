package com.stardust.autojs.core.console;

import android.annotation.SuppressLint;

import com.stardust.app.GlobalAppContext;
import com.stardust.autojs.R;
import com.stardust.util.UiHandler;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Stardust on 2017/10/22.
 */
@SuppressLint("ConstantLocale")
public class GlobalConsole extends ConsoleImpl {
    private static final String LOG_tAG = "GlobalConsole";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static final Logger LOGGER = Logger.getLogger(GlobalConsole.class);

    public GlobalConsole(UiHandler uiHandler) {
        super(uiHandler);
    }

    @Override
    public String println(int level, CharSequence charSequence) {
        String log = String.format(Locale.getDefault(), "%s/%s: %s",
                DATE_FORMAT.format(new Date()), getLevelChar(level), charSequence.toString());
        LOGGER.log(toLog4jLevel(level), log);
        android.util.Log.d(LOG_tAG, log);
        super.println(level, log);
        return log;
    }

    private Priority toLog4jLevel(int level) {
        switch (level) {
            case android.util.Log.VERBOSE:
            case android.util.Log.DEBUG:
                return Level.DEBUG;
            case android.util.Log.INFO:
                return Level.INFO;
            case android.util.Log.WARN:
                return Level.WARN;
            case android.util.Log.ERROR:
                return Level.ERROR;
            case android.util.Log.ASSERT:
                return Level.FATAL;
        }
        throw new IllegalArgumentException(GlobalAppContext.getString(R.string.error_illegal_argument, "level", level));
    }

    private String getLevelChar(int level) {
        return switch (level) {
            case android.util.Log.VERBOSE -> "V";
            case android.util.Log.DEBUG -> "D";
            case android.util.Log.INFO -> "I";
            case android.util.Log.WARN -> "W";
            case android.util.Log.ERROR -> "E";
            case android.util.Log.ASSERT -> "A";
            default -> "";
        };
    }

}
