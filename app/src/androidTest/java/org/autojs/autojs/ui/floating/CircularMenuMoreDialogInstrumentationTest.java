package org.autojs.autojs.ui.floating;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.runtime.api.WrappedShizuku;
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

    /** Rows and subtitles recorded before each frame of one opening of the menu. zh-CN: 一次打开菜单时每帧绘制前记录的条目位置与副标题. */
    private record Opening(long clickedAt, List<Long> times, List<int[]> rows, List<String> subtitles) {

        String report(String label, String placeholder) {
            int loaded = 0;
            while (loaded < subtitles.size() && subtitles.get(loaded).contains(placeholder)) loaded++;
            return label + ": shown after " + (times.get(0) - clickedAt) + " ms, "
                    + (loaded < times.size() ? "loaded after " + (times.get(loaded) - clickedAt) + " ms" : "not loaded") + ", "
                    + "placeholder in first frame=" + (loaded > 0) + ", frames=" + times.size() + ", "
                    + "rows " + Arrays.toString(rows.get(0)) + " -> " + Arrays.toString(rows.get(rows.size() - 1));
        }

        @Nullable
        String placeholderInFirstFrame(String placeholder) {
            return subtitles.get(0).contains(placeholder) ? "Placeholder in the first frame: " + subtitles.get(0) : null;
        }

        @Nullable
        String movement() {
            for (int i = 1; i < rows.size(); i++) {
                if (!Arrays.equals(rows.get(0), rows.get(i))) {
                    return "Rows moved at frame " + i + ": " + Arrays.toString(rows.get(0)) + " -> " + Arrays.toString(rows.get(i))
                            + ", subtitles " + subtitles.get(0) + " -> " + subtitles.get(i);
                }
            }
            return null;
        }
    }

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
        String placeholder = "[" + instrumentation.getTargetContext().getString(R.string.ellipsis_six) + "]";

        // Shizuku answers at once even in a process that has just started, so the first opening must show its final
        // subtitles too. Without Shizuku, the first opening may outwait root and fill in placeholders later, so it is
        // only reported. The second opening is what a running AutoJs6 shows.
        // zh-CN: 即使进程刚启动, Shizuku 也能立即返回结果, 因此首次打开同样须显示最终副标题. 没有 Shizuku 时, 首次打开可能等不到 root 的结果
        // 而稍后填入占位内容, 因此只报告. 第二次打开才是 AutoJs6 运行中的常态.
        boolean shizuku = WrappedShizuku.INSTANCE.isOperational();
        Opening first = openMoreMenu(menu);
        Opening second = openMoreMenu(menu);

        String[] failure = new String[1];
        instrumentation.runOnMainSync(() -> {
            Log.i(TAG, first.report("first opening", placeholder) + ", moved=" + (first.movement() != null) + ", shizuku=" + shizuku);
            Log.i(TAG, second.report("second opening", placeholder));
            Log.d(TAG, "subtitles " + second.subtitles().get(0) + " -> " + second.subtitles().get(second.subtitles().size() - 1));
            String firstFailure = null;
            if (shizuku) {
                firstFailure = first.placeholderInFirstFrame(placeholder);
                if (firstFailure == null) firstFailure = first.movement();
            }
            failure[0] = firstFailure != null ? "First opening: " + firstFailure : second.movement();
        });
        if (failure[0] != null) {
            fail(failure[0]);
        }
    }

    private Opening openMoreMenu(CircularMenu menu) throws Exception {
        instrumentation.runOnMainSync(() -> menu.mWindow.expand());
        SystemClock.sleep(500);

        Field dialogField = CircularMenu.class.getDeclaredField("mMenuOperationDialog");
        dialogField.setAccessible(true);
        Opening[] opening = new Opening[1];
        instrumentation.runOnMainSync(() -> {
            opening[0] = new Opening(SystemClock.uptimeMillis(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
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
            rows.getViewTreeObserver().addOnPreDrawListener(() -> {
                if (rows.getChildCount() == 0) {
                    return true;
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
                opening[0].times().add(SystemClock.uptimeMillis());
                opening[0].rows().add(tops);
                opening[0].subtitles().add(texts.toString());
                return true;
            });
        });

        waitOnMain(() -> !opening[0].rows().isEmpty());
        SystemClock.sleep(OBSERVE_MILLIS);
        instrumentation.runOnMainSync(() -> {
            dialog.dismiss();
            dialog = null;
        });
        instrumentation.waitForIdleSync();
        return opening[0];
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
