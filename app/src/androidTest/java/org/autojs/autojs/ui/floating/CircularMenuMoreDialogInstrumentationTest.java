package org.autojs.autojs.ui.floating;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs6.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

/**
 * Opens the real floating button "More" menu and checks that no row moves after the first drawn frame.
 * zh-CN: 打开真实的浮动按钮 "更多" 菜单, 检查首帧绘制之后各条目不再移动.
 */
public class CircularMenuMoreDialogInstrumentationTest {

    private static final String TAG = CircularMenuMoreDialogInstrumentationTest.class.getSimpleName();
    private static final long OBSERVE_MILLIS = 1000;

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private boolean menuWasShowing;
    private MaterialDialog dialog;

    @Before
    public void showFloatingButton() throws Exception {
        shell("appops set " + instrumentation.getTargetContext().getPackageName() + " SYSTEM_ALERT_WINDOW allow");
        instrumentation.runOnMainSync(() -> {
            menuWasShowing = FloatyWindowManger.isCircularMenuShowing();
            FloatyWindowManger.showCircularMenuIfNeeded(instrumentation.getTargetContext());
        });
        waitOnMain(() -> {
            CircularMenu menu = FloatyWindowManger.getCircularMenu();
            return menu != null && menu.mWindow.mCircularActionMenu != null && menu.mWindow.mCircularActionMenu.isAttachedToWindow();
        });
    }

    @After
    public void restoreFloatingButton() {
        instrumentation.runOnMainSync(() -> {
            if (dialog != null) {
                dialog.dismiss();
            }
            if (!menuWasShowing) {
                FloatyWindowManger.hideCircularMenu(false);
            }
        });
        instrumentation.waitForIdleSync();
    }

    @Test
    public void rowsKeepTheirPositionsAfterTheMenuAppears() throws Exception {
        CircularMenu menu = FloatyWindowManger.getCircularMenu();
        assertNotNull(menu);
        instrumentation.runOnMainSync(() -> menu.mWindow.expand());
        SystemClock.sleep(500);

        List<int[]> frames = new ArrayList<>();
        List<String> subtitles = new ArrayList<>();
        long[] clickedAt = new long[1];
        long[] firstFrameAt = new long[1];
        Field dialogField = CircularMenu.class.getDeclaredField("mMenuOperationDialog");
        dialogField.setAccessible(true);
        instrumentation.runOnMainSync(() -> {
            clickedAt[0] = SystemClock.uptimeMillis();
            menu.mWindow.mCircularActionMenu.findViewById(R.id.action_menu_more).performClick();
            try {
                dialog = (MaterialDialog) dialogField.get(menu);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            RecyclerView rows = (RecyclerView) dialog.getCustomView();
            assertNotNull(rows);
            // Registered before the dialog is shown, so the very first frame is recorded too.
            // zh-CN: 在对话框显示前注册, 以便首帧也被记录.
            rows.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (rows.getChildCount() == 0) {
                        return true;
                    }
                    if (frames.isEmpty()) {
                        firstFrameAt[0] = SystemClock.uptimeMillis();
                    }
                    int[] tops = new int[rows.getChildCount()];
                    StringBuilder texts = new StringBuilder();
                    for (int i = 0; i < tops.length; i++) {
                        View child = rows.getChildAt(i);
                        tops[i] = Math.round(child.getTop() + child.getTranslationY());
                        TextView subtitle = child.findViewById(R.id.subtitle);
                        if (subtitle.getVisibility() == View.VISIBLE) {
                            texts.append('[').append(subtitle.getText()).append(']');
                        }
                    }
                    frames.add(tops);
                    subtitles.add(texts.toString());
                    return true;
                }
            });
        });

        waitOnMain(() -> !frames.isEmpty());
        SystemClock.sleep(OBSERVE_MILLIS);

        instrumentation.runOnMainSync(() -> {
            String placeholder = "[" + dialog.getContext().getString(R.string.ellipsis_six) + "]";
            Log.i(TAG, "shown after " + (firstFrameAt[0] - clickedAt[0]) + " ms, frames=" + frames.size()
                    + ", placeholder in first frame=" + subtitles.get(0).contains(placeholder)
                    + ", rows " + Arrays.toString(frames.get(0)) + " -> " + Arrays.toString(frames.get(frames.size() - 1)));
            Log.d(TAG, "subtitles " + subtitles.get(0) + " -> " + subtitles.get(subtitles.size() - 1));
            int[] first = frames.get(0);
            for (int i = 1; i < frames.size(); i++) {
                if (!Arrays.equals(first, frames.get(i))) {
                    fail("Rows moved at frame " + i + ": " + Arrays.toString(first) + " -> " + Arrays.toString(frames.get(i))
                            + ", subtitles " + subtitles.get(0) + " -> " + subtitles.get(i));
                }
            }
        });
    }

    private void shell(String command) throws IOException {
        try (FileInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) {
                /* Drain the output so that the command finishes. */
            }
        }
    }

    private void waitOnMain(BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        boolean[] satisfied = new boolean[1];
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> satisfied[0] = condition.getAsBoolean());
            if (satisfied[0]) return;
            SystemClock.sleep(20);
        }
        fail("Floating button did not reach the expected state");
    }
}
