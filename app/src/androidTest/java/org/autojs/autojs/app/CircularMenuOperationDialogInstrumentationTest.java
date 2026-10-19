package org.autojs.autojs.app;

import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs6.R;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class CircularMenuOperationDialogInstrumentationTest {

    private final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final ArrayList<MaterialDialog> dialogs = new ArrayList<>();
    private final ArrayList<CountDownLatch> gates = new ArrayList<>();

    @After
    public void cleanUp() {
        gates.forEach(CountDownLatch::countDown);
        instrumentation.runOnMainSync(() -> dialogs.forEach(MaterialDialog::dismiss));
        instrumentation.waitForIdleSync();
    }

    @Test
    public void slowSubtitleAllowsMenuToShowAndOtherItemsToBeClicked() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = gate();
        AtomicInteger clicks = new AtomicInteger();
        MaterialDialog[] dialog = new MaterialDialog[1];
        instrumentation.runOnMainSync(() -> {
            dialog[0] = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Slow", () -> {
                        assertNotSame(Looper.getMainLooper(), Looper.myLooper());
                        entered.countDown();
                        await(release);
                        return "Loaded";
                    })
                    .item(R.drawable.ic_home_black_48dp, "Action", v -> clicks.incrementAndGet())
                    .build();
            show(dialog[0]);
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        waitOnMain(() -> holder(dialog[0], 1) != null);
        instrumentation.runOnMainSync(() -> {
            assertTrue(dialog[0].isShowing());
            assertEquals(dialog[0].getContext().getString(R.string.ellipsis_six), holder(dialog[0], 0).binding.subtitle.getText().toString());
            holder(dialog[0], 1).itemView.performClick();
            assertEquals(1, clicks.get());
        });
        release.countDown();
        waitOnMain(() -> hasSubtitle(dialog[0], 0, "Loaded"));
    }

    @Test
    public void recyclingRowsKeepsOneRequestAndDoesNotWriteIntoAnotherItem() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = gate();
        CountDownLatch updated = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        AtomicReference<RecyclerView.Adapter<CircularMenuOperationDialogBuilder.ViewHolder>> adapter = new AtomicReference<>();
        CircularMenuOperationDialogBuilder.ViewHolder[] holders = new CircularMenuOperationDialogBuilder.ViewHolder[2];
        instrumentation.runOnMainSync(() -> {
            MaterialDialog dialog = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Slow", () -> {
                        queries.incrementAndGet();
                        entered.countDown();
                        await(release);
                        return "Result";
                    })
                    .item(R.drawable.ic_home_black_48dp, "No subtitle")
                    .build();
            dialogs.add(dialog);
            RecyclerView list = (RecyclerView) dialog.getCustomView();
            // The dialog builder owns this adapter and only creates its own ViewHolder.
            @SuppressWarnings("unchecked")
            RecyclerView.Adapter<CircularMenuOperationDialogBuilder.ViewHolder> dialogAdapter =
                    (RecyclerView.Adapter<CircularMenuOperationDialogBuilder.ViewHolder>) list.getAdapter();
            adapter.set(dialogAdapter);
            adapter.get().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                    assertSame(Looper.getMainLooper(), Looper.myLooper());
                    assertEquals(0, positionStart);
                    assertEquals(1, itemCount);
                    assertNotNull(payload);
                    updated.countDown();
                }
            });
            holders[0] = adapter.get().createViewHolder(list, 0);
            holders[1] = adapter.get().createViewHolder(list, 0);
            adapter.get().bindViewHolder(holders[0], 0);
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            adapter.get().onViewRecycled(holders[0]);
            adapter.get().bindViewHolder(holders[0], 1);
            adapter.get().bindViewHolder(holders[1], 0);
            adapter.get().onBindViewHolder(holders[1], 0);
        });
        release.countDown();
        assertTrue(updated.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, queries.get());
            assertEquals("No subtitle", holders[0].binding.title.getText().toString());
            assertEquals(View.GONE, holders[0].binding.subtitle.getVisibility());
            adapter.get().onBindViewHolder(holders[1], 0);
            assertEquals("Result", holders[1].binding.subtitle.getText().toString());
        });
    }

    @Test
    public void dismissCancelsRequestsAndIgnoresLateResultsAfterReopening() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = gate();
        CountDownLatch returned = new CountDownLatch(1);
        AtomicInteger staleUpdates = new AtomicInteger();
        AtomicInteger dismissals = new AtomicInteger();
        MaterialDialog[] dialog = new MaterialDialog[2];
        instrumentation.runOnMainSync(() -> {
            dialog[0] = newBuilder().item(R.drawable.ic_text_fields_black_48dp, "Old", () -> {
                entered.countDown();
                // Simulate an IPC call that can still return after cancellation.
                for (;;) {
                    try {
                        if (release.await(5, TimeUnit.SECONDS)) break;
                        throw new AssertionError("Late result was not released");
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                    }
                }
                returned.countDown();
                return "Stale";
            }).dismissListener(d -> dismissals.incrementAndGet()).build();
            ((RecyclerView) dialog[0].getCustomView()).getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                    staleUpdates.incrementAndGet();
                }
            });
            show(dialog[0]);
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            dialog[0].dismiss();
            dialog[1] = newBuilder().item(R.drawable.ic_text_fields_black_48dp, "New", (Supplier<String>) () -> "Fresh").build();
            show(dialog[1]);
        });
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        release.countDown();
        assertTrue(returned.await(5, TimeUnit.SECONDS));
        waitOnMain(() -> hasSubtitle(dialog[1], 0, "Fresh"));
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, dismissals.get());
            assertEquals(0, staleUpdates.get());
        });
    }

    @Test
    public void failedAndEmptyQueriesReplaceLoadingPlaceholders() {
        MaterialDialog[] dialog = new MaterialDialog[1];
        instrumentation.runOnMainSync(() -> {
            dialog[0] = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Failure", (Supplier<String>) () -> {
                        throw new IllegalStateException("Unavailable");
                    })
                    .item(R.drawable.ic_text_fields_black_48dp, "Empty", (Supplier<String>) () -> "")
                    .build();
            show(dialog[0]);
        });
        waitOnMain(() -> hasSubtitle(dialog[0], 0, "[ " + dialog[0].getContext().getString(R.string.error_an_error_occurred) + " ]")
                && hasSubtitle(dialog[0], 1, "[ " + dialog[0].getContext().getString(R.string.text_no_content) + " ]"));
    }

    @Test
    public void menuWaitsForSubtitlesBeforeShowing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = gate();
        MaterialDialog[] dialog = new MaterialDialog[1];
        instrumentation.runOnMainSync(() -> {
            CircularMenuOperationDialogBuilder builder = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Slow", () -> {
                        entered.countDown();
                        await(release);
                        return "Loaded";
                    })
                    .item(R.drawable.ic_home_black_48dp, "Action");
            dialog[0] = builder.build();
            dialogs.add(dialog[0]);
            builder.showWhenSubtitlesLoaded(5000, () -> true);
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertFalse(dialog[0].isShowing()));
        release.countDown();
        waitOnMain(() -> dialog[0].isShowing() && hasSubtitle(dialog[0], 0, "Loaded"));
    }

    @Test
    public void lateSubtitleAfterTimeoutMovesRowsBelowWithoutAnimation() {
        CountDownLatch release = gate();
        MaterialDialog[] dialog = new MaterialDialog[1];
        int[] initialHeight = new int[1];
        instrumentation.runOnMainSync(() -> {
            CircularMenuOperationDialogBuilder builder = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Slow", () -> {
                        await(release);
                        return "Loaded\nSecond line";
                    })
                    .item(R.drawable.ic_home_black_48dp, "Below");
            dialog[0] = builder.build();
            dialogs.add(dialog[0]);
            builder.showWhenSubtitlesLoaded(50, () -> true);
        });
        waitOnMain(() -> dialog[0].isShowing() && holder(dialog[0], 1) != null && holder(dialog[0], 0).itemView.getHeight() > 0);
        instrumentation.runOnMainSync(() -> {
            assertTrue(hasSubtitle(dialog[0], 0, dialog[0].getContext().getString(R.string.ellipsis_six)));
            initialHeight[0] = holder(dialog[0], 0).itemView.getHeight();
        });
        release.countDown();
        waitOnMain(() -> hasSubtitle(dialog[0], 0, "Loaded\nSecond line") && holder(dialog[0], 0).itemView.getHeight() > initialHeight[0]);
        instrumentation.runOnMainSync(() -> {
            View below = holder(dialog[0], 1).itemView;
            assertEquals(0f, below.getTranslationY(), 0f);
            assertEquals(holder(dialog[0], 0).itemView.getBottom(), below.getTop());
        });
    }

    @Test
    public void menuNoLongerWantedIsNotShownAndCancelsLoading() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = gate();
        AtomicInteger dismissals = new AtomicInteger();
        MaterialDialog[] dialog = new MaterialDialog[1];
        instrumentation.runOnMainSync(() -> {
            CircularMenuOperationDialogBuilder builder = newBuilder()
                    .item(R.drawable.ic_text_fields_black_48dp, "Slow", () -> {
                        entered.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            interrupted.countDown();
                        }
                        return "Stale";
                    });
            builder.dismissListener(d -> dismissals.incrementAndGet());
            dialog[0] = builder.build();
            dialogs.add(dialog[0]);
            builder.showWhenSubtitlesLoaded(50, () -> false);
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        instrumentation.runOnMainSync(() -> {
            assertFalse(dialog[0].isShowing());
            assertEquals(1, dismissals.get());
        });
    }

    private CircularMenuOperationDialogBuilder newBuilder() {
        return new CircularMenuOperationDialogBuilder(instrumentation.getTargetContext());
    }

    private void show(MaterialDialog dialog) {
        dialogs.add(dialog);
        DialogUtils.showAdaptive(dialog);
    }

    private CircularMenuOperationDialogBuilder.ViewHolder holder(MaterialDialog dialog, int position) {
        return (CircularMenuOperationDialogBuilder.ViewHolder) ((RecyclerView) dialog.getCustomView()).findViewHolderForAdapterPosition(position);
    }

    private boolean hasSubtitle(MaterialDialog dialog, int position, String expected) {
        var holder = holder(dialog, position);
        return holder != null && expected.contentEquals(holder.binding.subtitle.getText());
    }

    private CountDownLatch gate() {
        CountDownLatch gate = new CountDownLatch(1);
        gates.add(gate);
        return gate;
    }

    private static void await(CountDownLatch gate) {
        try {
            assertTrue(gate.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
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
        fail("Menu did not reach the expected state");
    }
}
