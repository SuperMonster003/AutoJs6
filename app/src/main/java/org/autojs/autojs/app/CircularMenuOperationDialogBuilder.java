package org.autojs.autojs.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.OperationDialogItemBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Created by Stardust on Jun 26, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Jan 28, 2026.
 * Modified by SuperMonster003 as of Jan 28, 2026.
 */
public class CircularMenuOperationDialogBuilder extends AppLevelThemeDialogBuilder {

    private final ArrayList<View.OnClickListener> mOnClickListeners = new ArrayList<>();
    private final ArrayList<Integer> mIcons = new ArrayList<>();
    private final ArrayList<String> mTitles = new ArrayList<>();
    private final ArrayList<Supplier<String>> mSubtitleSuppliers = new ArrayList<>();

    private static final ExecutorService SUBTITLE_EXECUTOR = Executors.newFixedThreadPool(4);

    private static final Object SUBTITLE_PAYLOAD = new Object();

    // Requests belong to dialog items, not recycled views. All state is owned by the main thread.
    // zh-CN: 查询绑定到对话框条目而非复用的视图. 所有状态均由主线程管理.
    private final Map<Integer, String> mSubtitleCache = new HashMap<>();
    private final Map<Integer, FutureTask<String>> mSubtitleRequests = new HashMap<>();
    private final Handler mSubtitleHandler = new Handler(Looper.getMainLooper());
    private final RecyclerView.Adapter<ViewHolder> mAdapter;
    private final Runnable mRunPendingShow = this::runPendingShow;
    private boolean mSubtitleLoadingCancelled;
    private MaterialDialog mDialog;
    private Runnable mPendingShow;

    public CircularMenuOperationDialogBuilder(@NonNull Context context) {
        super(context);
        RecyclerView operations = new RecyclerView(context);
        operations.setLayoutManager(new LinearLayoutManager(context));
        // A subtitle that loads late may change its row height; move the rows below at once instead of sliding them down.
        // zh-CN: 较晚加载的副标题可能改变条目高度, 此时下方条目直接就位, 不以动画向下滑动.
        operations.setItemAnimator(null);
        mAdapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View itemView = LayoutInflater
                        .from(new ContextThemeWrapper(context, R.style.AppTheme))
                        .inflate(R.layout.operation_dialog_item, parent, false);
                return new ViewHolder(itemView);
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                holder.itemView.setOnClickListener(mOnClickListeners.get(position));

                holder.binding.title.setText(mTitles.get(position));
                holder.binding.title.setTextColor(context.getColor(R.color.day_night));

                var subtitleSupplier = mSubtitleSuppliers.get(position);

                if (subtitleSupplier != null) {
                    holder.binding.subtitle.setVisibility(View.VISIBLE);
                    holder.binding.subtitle.setTextColor(context.getColor(R.color.day_night));

                    String cached = mSubtitleCache.get(position);
                    if (cached != null) {
                        holder.binding.subtitle.setText(cached);
                    } else {
                        holder.binding.subtitle.setText(context.getString(R.string.ellipsis_six));

                        loadSubtitle(position, subtitleSupplier);
                    }
                } else {
                    holder.binding.subtitle.setVisibility(View.GONE);
                }

                holder.binding.icon.setImageResource(mIcons.get(position));
                holder.binding.icon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.day_night)));
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
                if (payloads.contains(SUBTITLE_PAYLOAD) && mSubtitleCache.containsKey(position)) {
                    holder.binding.subtitle.setText(mSubtitleCache.get(position));
                } else {
                    onBindViewHolder(holder, position);
                }
            }

            @Override
            public int getItemCount() {
                return mOnClickListeners.size();
            }
        };
        operations.setAdapter(mAdapter);
        customView(operations, false);
    }

    private void loadSubtitle(int position, Supplier<String> supplier) {
        if (mSubtitleLoadingCancelled || mSubtitleRequests.containsKey(position)) {
            return;
        }
        FutureTask<String> request = new FutureTask<>(() -> {
            String subtitle = supplier.get();
            return subtitle == null || subtitle.isEmpty()
                    ? "[ " + getContext().getString(R.string.text_no_content) + " ]"
                    : subtitle;
        }) {
            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                mSubtitleHandler.post(() -> {
                    if (mSubtitleLoadingCancelled || mSubtitleRequests.get(position) != this) {
                        return;
                    }
                    mSubtitleRequests.remove(position);
                    String subtitle;
                    try {
                        subtitle = get();
                    } catch (CancellationException e) {
                        return;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (ExecutionException e) {
                        subtitle = "[ " + getContext().getString(R.string.error_an_error_occurred) + " ]";
                    }
                    if (!subtitle.equals(mSubtitleCache.put(position, subtitle))) {
                        mAdapter.notifyItemChanged(position, SUBTITLE_PAYLOAD);
                    }
                    if (mPendingShow != null && areSubtitlesLoaded()) {
                        runPendingShow();
                    }
                });
            }
        };
        mSubtitleRequests.put(position, request);
        SUBTITLE_EXECUTOR.execute(request);
    }

    private boolean areSubtitlesLoaded() {
        for (int i = 0; i < mSubtitleSuppliers.size(); i++) {
            if (mSubtitleSuppliers.get(i) != null && !mSubtitleCache.containsKey(i)) {
                return false;
            }
        }
        return true;
    }

    private void runPendingShow() {
        Runnable show = mPendingShow;
        mPendingShow = null;
        mSubtitleHandler.removeCallbacks(mRunPendingShow);
        if (show != null) {
            show.run();
        }
    }

    private void cancelSubtitleLoading() {
        mSubtitleLoadingCancelled = true;
        mPendingShow = null;
        mSubtitleRequests.values().forEach(request -> request.cancel(true));
        mSubtitleRequests.clear();
        mSubtitleHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public MaterialDialog build() {
        MaterialDialog dialog = super.build();
        var listener = dismissListener;
        dialog.setOnDismissListener(d -> {
            cancelSubtitleLoading();
            if (listener != null) {
                listener.onDismiss(d);
            }
        });
        mDialog = dialog;
        return dialog;
    }

    /**
     * Shows the built dialog once every subtitle has loaded, or after timeoutMillis, so rows appear
     * with their final height instead of shifting after the dialog becomes visible.
     * If isWanted returns false by then, the dialog is not shown and is cleaned up as if dismissed.
     * Must be called on the main thread.
     * zh-CN: 全部副标题加载完成或超时后再显示已构建的对话框, 使条目以最终高度出现, 不在显示后发生位移.
     * 届时若 isWanted 返回 false, 则不再显示, 并按对话框关闭时的方式清理. 须在主线程调用.
     */
    public void showWhenSubtitlesLoaded(long timeoutMillis, @NonNull BooleanSupplier isWanted) {
        MaterialDialog dialog = mDialog;
        if (dialog == null) {
            throw new IllegalStateException("The dialog has not been built");
        }
        mPendingShow = () -> {
            if (isWanted.getAsBoolean()) {
                DialogUtils.showAdaptive(dialog);
                return;
            }
            cancelSubtitleLoading();
            if (dismissListener != null) {
                dismissListener.onDismiss(dialog);
            }
        };
        for (int i = 0; i < mSubtitleSuppliers.size(); i++) {
            var supplier = mSubtitleSuppliers.get(i);
            if (supplier != null && !mSubtitleCache.containsKey(i)) {
                loadSubtitle(i, supplier);
            }
        }
        if (areSubtitlesLoaded()) {
            runPendingShow();
        } else {
            mSubtitleHandler.postDelayed(mRunPendingShow, timeoutMillis);
        }
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, int titleRes) {
        return item(iconRes, getContext().getString(titleRes), null, null);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, int titleRes, View.OnClickListener l) {
        return item(iconRes, getContext().getString(titleRes), null, l);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String title) {
        return item(iconRes, title, null, null);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String title, Supplier<String> subtitleSupplier) {
        return item(iconRes, title, subtitleSupplier, null);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String title, View.OnClickListener l) {
        return item(iconRes, title, null, l);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String title, Supplier<String> subtitleSupplier, View.OnClickListener l) {
        mOnClickListeners.add(l);
        mIcons.add(iconRes);
        mTitles.add(title);
        mSubtitleSuppliers.add(subtitleSupplier);
        return this;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        @NonNull
        public final OperationDialogItemBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = OperationDialogItemBinding.bind(itemView);
        }

    }

}
