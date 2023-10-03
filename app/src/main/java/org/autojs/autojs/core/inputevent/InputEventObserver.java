package org.autojs.autojs.core.inputevent;

import android.content.Context;

import androidx.annotation.NonNull;

import android.text.TextUtils;

import org.autojs.autojs6.R;
import org.autojs.autojs.core.record.inputevent.EventFormatException;
import org.autojs.autojs.runtime.api.Shell;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on 2017/8/4.
 */
public class InputEventObserver {

    public static class InputEvent {
        static final Pattern PATTERN = Pattern.compile("^\\[([^]]*)]\\s+([^:]*):\\s+([^\\s]*)\\s+([^\\s]*)\\s+([^\\s]*)\\s*$");

        static InputEvent parse(String eventStr) {
            Matcher matcher = PATTERN.matcher(eventStr);
            if (!matcher.matches()) {
                throw new EventFormatException(eventStr);
            }
            double time;
            try {
                time = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new EventFormatException(eventStr, e);
            }
            return new InputEvent(time, matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
        }


        public double time;
        public String device;
        public String type;
        public String code;
        public String value;

        public InputEvent(double time, String device, String type, String code, String value) {
            this.time = time;
            this.device = device;
            this.type = type;
            this.code = code;
            this.value = value;
        }


        @NonNull
        @Override
        public String toString() {
            return "Event{" +
                    "time=" + time +
                    ", device='" + device + '\'' +
                    ", type='" + type + '\'' +
                    ", code='" + code + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    public interface InputEventListener {
        void onInputEvent(@NonNull InputEvent e);
    }

    private final CopyOnWriteArrayList<InputEventListener> mInputEventListeners = new CopyOnWriteArrayList<>();
    private final Context mContext;
    private Shell mShell;

    public InputEventObserver(Context context) {
        mContext = context;
    }

    public static InputEventObserver initObserver(Context context) {
        var inputEventObserver = new InputEventObserver(context);
        inputEventObserver.observe();
        return inputEventObserver;
    }

    public void observe() {
        if (mShell != null)
            throw new IllegalStateException(mContext
                    .getString(R.string.error_function_called_more_than_once,
                            "InputEventObserver.observe"));
        mShell = new Shell(mContext, true);
        mShell.setCallback(new Shell.SimpleCallback() {
            @Override
            public void onNewLine(String str) {
                if (mShell.isInitialized()) {
                    onInputEvent(str);
                }
            }

            @Override
            public void onInitialized() {
                mShell.exec("getevent -t");
            }

        });
    }

    public void onInputEvent(String eventStr) {
        if (TextUtils.isEmpty(eventStr) || !eventStr.startsWith("["))
            return;
        try {
            InputEvent event = InputEvent.parse(eventStr);
            dispatchInputEvent(event);
        } catch (Exception ignored) {

        }
    }

    private void dispatchInputEvent(InputEvent event) {
        for (InputEventListener listener : mInputEventListeners) {
            listener.onInputEvent(event);
        }
    }

    public void addListener(InputEventListener listener) {
        mInputEventListeners.add(listener);
    }

    public boolean removeListener(InputEventListener listener) {
        return mInputEventListeners.remove(listener);
    }


    public void recycle() {
        mShell.exit();
    }


}
