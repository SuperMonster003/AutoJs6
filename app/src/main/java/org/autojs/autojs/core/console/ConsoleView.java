package org.autojs.autojs.core.console;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.autojs.autojs.tool.MapBuilder;
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow;
import org.autojs.autojs.ui.log.LogActivity;
import org.autojs.autojs.util.DisplayUtils;
import org.autojs.autojs6.R;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * Created by Stardust on 2017/5/2.
 * <p>
 * TODO: 优化为无锁形式
 */
public class ConsoleView extends FrameLayout implements ConsoleImpl.LogListener {

    private final static int sRefreshInterval = 100;
    private final Map<Integer, Integer> mColors = new MapBuilder<Integer, Integer>().build();
    private ConsoleImpl mConsole;
    private LogActivity mLogActivity;
    private RecyclerView mLogListRecyclerView;
    private EditText mEditText;
    private ResizableExpandableFloatyWindow mWindow;
    private boolean mShouldStopRefresh = false;
    private final ArrayList<ConsoleImpl.LogEntry> mLogEntries = new ArrayList<>();

    private float mLastScaleFactor = 1;
    private float mLastTextSize = 0;

    private final float mMinTextSize = 8.0f;
    private final float mMaxTextSize = 56.0f;
    private boolean mIsPinchToZoomEnabled;

    public ConsoleView(Context context) {
        super(context);
        init(null);
    }

    public ConsoleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public ConsoleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(@Nullable AttributeSet attrs) {
        inflate(getContext(), R.layout.console_view, this);

        Map<Integer, Integer> logLevelColorResIdMap = getLogLevelMap();
        Map<Integer, Integer> logLevelColorIntMap = getLogLevelMap();

        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.ConsoleView);

            logLevelColorIntMap.put(Log.VERBOSE, ta.getColor(R.styleable.ConsoleView_color_verbose, Color.TRANSPARENT));
            logLevelColorIntMap.put(Log.DEBUG, ta.getColor(R.styleable.ConsoleView_color_debug, Color.TRANSPARENT));
            logLevelColorIntMap.put(Log.INFO, ta.getColor(R.styleable.ConsoleView_color_info, Color.TRANSPARENT));
            logLevelColorIntMap.put(Log.WARN, ta.getColor(R.styleable.ConsoleView_color_warn, Color.TRANSPARENT));
            logLevelColorIntMap.put(Log.ERROR, ta.getColor(R.styleable.ConsoleView_color_error, Color.TRANSPARENT));
            logLevelColorIntMap.put(Log.ASSERT, ta.getColor(R.styleable.ConsoleView_color_assert, Color.TRANSPARENT));

            ta.recycle();
        }

        for (Map.Entry<Integer, Integer> map : logLevelColorResIdMap.entrySet()) {
            int logLevel = map.getKey();
            int colorResKey = map.getValue();
            mColors.put(logLevel, getContext().getColor(colorResKey));
        }

        for (Map.Entry<Integer, Integer> map : logLevelColorIntMap.entrySet()) {
            int logLevel = map.getKey();
            int colorInt = map.getValue();
            if (colorInt != Color.TRANSPARENT) {
                mColors.put(logLevel, colorInt);
            }
        }

        ScaleGestureDetector mScaleGestureDetector = new ScaleGestureDetector(getContext(), getSimpleOnScaleGestureListener());

        mLogListRecyclerView = findViewById(R.id.log_list);
        mLogListRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mLogListRecyclerView.setAdapter(new Adapter());

        mLogListRecyclerView.setOnTouchListener((v, event) -> {
            if (!mIsPinchToZoomEnabled) {
                return super.onTouchEvent(event);
            }
            mScaleGestureDetector.onTouchEvent(event);
            return !mScaleGestureDetector.isInProgress() && super.onTouchEvent(event);
        });

        initEditText();
        initSubmitButton();
    }

    @NonNull
    private ScaleGestureDetector.SimpleOnScaleGestureListener getSimpleOnScaleGestureListener() {
        return new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float currentFactor = (float) (Math.floor(detector.getScaleFactor() * 10) / 10);
                if (mLastTextSize <= 0) {
                    mLastTextSize = getTextSize();
                }
                if (currentFactor > 0 && mLastScaleFactor != currentFactor) {
                    float currentTextSize = mLastTextSize + (currentFactor > mLastScaleFactor ? 1 : -1);
                    mLastTextSize = Math.max(mMinTextSize, Math.min(mMaxTextSize, currentTextSize));
                    setTextSize(mLastTextSize);

                    mLastScaleFactor = currentFactor;
                }
                return super.onScale(detector);
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                return super.onScaleBegin(detector);
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                mLastScaleFactor = 1.0f;
                super.onScaleEnd(detector);
            }
        };
    }

    public void setTextSize(float size) {
        mLastTextSize = size;
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setTextSize(size);
        }
    }

    public float getTextSize() {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            return adapter.getTextSize();
        }
        return /* default text size */ 14;
    }

    public void setTextColors(@NotNull Integer[] colors) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setTextColors(colors);
        }
    }

    public void setVerboseTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setVerboseTextColor(color);
        }
    }

    public void setDebugTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setDebugTextColor(color);
        }
    }

    public void setInfoTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setInfoTextColor(color);
        }
    }

    public void setWarnTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setWarnTextColor(color);
        }
    }

    public void setErrorTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setErrorTextColor(color);
        }
    }

    public void setAssertTextColor(int color) {
        Adapter adapter = (Adapter) mLogListRecyclerView.getAdapter();
        if (adapter != null) {
            adapter.setAssertTextColor(color);
        }
    }

    public void setPinchToZoomEnabled(boolean enabled) {
        mIsPinchToZoomEnabled = enabled;
    }

    protected Map<Integer, Integer> getLogLevelMap() {
        return new MapBuilder<Integer, Integer>()
                .put(Log.VERBOSE, R.color.console_view_verbose)
                .put(Log.DEBUG, R.color.console_view_debug)
                .put(Log.INFO, R.color.console_view_info)
                .put(Log.WARN, R.color.console_view_warn)
                .put(Log.ERROR, R.color.console_view_error)
                .put(Log.ASSERT, R.color.console_view_assert)
                .build();
    }

    private void initSubmitButton() {
        final Button submit = findViewById(R.id.submit);
        submit.setOnClickListener(v -> {
            CharSequence input = mEditText.getText();
            submitInput(input);
        });
    }

    private void submitInput(CharSequence input) {
        if (android.text.TextUtils.isEmpty(input)) {
            return;
        }
        if (mConsole.submitInput(input)) {
            mEditText.setText("");
        }
    }

    private void initEditText() {
        mEditText = findViewById(R.id.input);
        mEditText.setFocusableInTouchMode(true);
        LinearLayout mInputContainer = findViewById(R.id.input_container);
        OnClickListener listener = v -> {
            if (mWindow != null) {
                mWindow.requestWindowFocus();
                mEditText.requestFocus();
            }
        };
        mEditText.setOnClickListener(listener);
        mInputContainer.setOnClickListener(listener);
    }

    public void setConsole(ConsoleImpl console) {
        mConsole = console;
        mConsole.setConsoleView(this);
    }

    public void setLogActivity(LogActivity activity) {
        mLogActivity = activity;
    }

    public void export(String fileName) {
        if (mLogActivity != null) {
            mLogActivity.export(fileName);
        }
    }

    @Override
    public void onNewLog(ConsoleImpl.LogEntry logEntry) {

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mShouldStopRefresh = false;
        postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLog();
                if (!mShouldStopRefresh) {
                    postDelayed(this, sRefreshInterval);
                }
            }
        }, sRefreshInterval);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mShouldStopRefresh = true;
    }


    @Override
    public void onLogClear() {
        post(() -> {
            mLogEntries.clear();
            Objects.requireNonNull(mLogListRecyclerView.getAdapter()).notifyDataSetChanged();
        });
    }

    private void refreshLog() {
        if (mConsole == null)
            return;
        int oldSize = mLogEntries.size();
        ArrayList<ConsoleImpl.LogEntry> logEntries = mConsole.getLogEntries();
        synchronized (mConsole.getLogEntries()) {
            final int size = logEntries.size();
            if (size == 0) {
                return;
            }
            if (oldSize >= size) {
                return;
            }
            if (oldSize == 0) {
                mLogEntries.addAll(logEntries);
            } else {
                for (int i = oldSize; i < size; i++) {
                    mLogEntries.add(logEntries.get(i));
                }
            }
            Objects.requireNonNull(mLogListRecyclerView.getAdapter()).notifyItemRangeInserted(oldSize, size - 1);
            mLogListRecyclerView.scrollToPosition(size - 1);
        }
    }

    public void setWindow(ResizableExpandableFloatyWindow window) {
        mWindow = window;
    }

    public void showEditText() {
        post(() -> {
            mWindow.requestWindowFocus();
            // mInputContainer.setVisibility(VISIBLE);
            mEditText.requestFocus();
        });
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }

    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        private float textSize;

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.console_view_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ConsoleImpl.LogEntry logEntry = mLogEntries.get(position);

            TextView textView = holder.textView;

            textView.setText(logEntry.content);
            Integer color = mColors.get(logEntry.level);
            if (color != null) {
                textView.setTextColor(color);
            }
            if (textSize > 0) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
            } else {
                textSize = DisplayUtils.pxToSp(textView.getTextSize());
            }
            textView.setClickable(false);
            textView.setLongClickable(false);
            textView.setTextIsSelectable(false);
        }

        public void setTextSize(float size) {
            textSize = size;
            notifyDataSetChanged();
        }

        public float getTextSize() {
            return textSize;
        }

        public void setTextColors(Integer[] colors) {
            int[] levels = new int[]{Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR, Log.ASSERT};
            boolean isReplaced = false;
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] != null) {
                    mColors.replace(levels[i], colors[i]);
                    isReplaced = true;
                }
            }
            if (isReplaced) {
                notifyDataSetChanged();
            }
        }

        public void setVerboseTextColor(int color) {
            mColors.replace(Log.VERBOSE, color);
            notifyDataSetChanged();
        }

        public void setDebugTextColor(int color) {
            mColors.replace(Log.DEBUG, color);
            notifyDataSetChanged();
        }

        public void setInfoTextColor(int color) {
            mColors.replace(Log.INFO, color);
            notifyDataSetChanged();
        }

        public void setWarnTextColor(int color) {
            mColors.replace(Log.WARN, color);
            notifyDataSetChanged();
        }

        public void setErrorTextColor(int color) {
            mColors.replace(Log.ERROR, color);
            notifyDataSetChanged();
        }

        public void setAssertTextColor(int color) {
            mColors.replace(Log.ASSERT, color);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mLogEntries.size();
        }
    }

}
