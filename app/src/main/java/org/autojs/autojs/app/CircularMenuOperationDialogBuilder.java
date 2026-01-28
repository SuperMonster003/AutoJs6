package org.autojs.autojs.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.OperationDialogItemBinding;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
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

    // Simple cache to avoid repeated calculations during dialog display (especially currentPackage/currentActivity).
    // zh-CN: 简单缓存, 避免对话框展示期间重复计算 (尤其是 currentPackage/currentActivity).
    private final Map<Integer, String> mSubtitleCache = new ConcurrentHashMap<>();

    // Token sequence to prevent misaligned updates caused by RecyclerView reuse.
    // zh-CN: Token 序列, 防止 RecyclerView 复用导致错位更新.
    private final AtomicLong mSubtitleRequestSeq = new AtomicLong(0);

    public CircularMenuOperationDialogBuilder(@NonNull Context context) {
        super(context);
        RecyclerView operations = new RecyclerView(context);
        operations.setLayoutManager(new LinearLayoutManager(context));
        operations.setAdapter(new RecyclerView.Adapter<ViewHolder>() {
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

                // Cancel old task first (critical when holder is reused).
                // zh-CN: 先取消旧任务 (holder 复用时很关键).
                if (holder.subtitleFuture != null) {
                    holder.subtitleFuture.cancel(true);
                    holder.subtitleFuture = null;
                }

                if (subtitleSupplier != null) {
                    holder.binding.subtitle.setVisibility(View.VISIBLE);
                    holder.binding.subtitle.setTextColor(context.getColor(R.color.day_night));

                    String cached = mSubtitleCache.get(position);
                    if (cached != null) {
                        holder.binding.subtitle.setText(cached);
                    } else {
                        holder.binding.subtitle.setText(context.getString(R.string.ellipsis_six));

                        // Record the token for this bind, validate it in the callback to avoid misaligned updates.
                        // zh-CN: 记录本次 bind 的 token, 回调时校验避免错位更新.
                        final long requestToken = mSubtitleRequestSeq.incrementAndGet();
                        holder.subtitleRequestToken = requestToken;

                        holder.subtitleFuture = SUBTITLE_EXECUTOR.submit(() -> {
                            String subtitle;
                            try {
                                subtitle = subtitleSupplier.get();
                            } catch (Exception e) {
                                subtitle = "[ " + context.getString(R.string.error_an_error_occurred) + " ]";
                            }
                            if (subtitle == null || subtitle.isEmpty()) {
                                subtitle = "[ " + context.getString(R.string.text_no_content) + " ]";
                            }

                            final String finalSubtitle = subtitle;

                            // Cache the result (valid for the lifetime of this dialog).
                            // zh-CN: 缓存结果 (本次对话框生命周期内有效).
                            mSubtitleCache.put(position, finalSubtitle);

                            // Return to main thread to update, and token validation is required.
                            // zh-CN: 回到主线程更新, 同时需要做 token 校验.
                            holder.itemView.post(() -> {
                                if (holder.subtitleRequestToken != requestToken) return;
                                // Prevent holder from being recycled or position from being invalid.
                                // zh-CN: 防止 holder 已被回收, 或 position 已失效.
                                if (holder.getBindingAdapterPosition() == RecyclerView.NO_POSITION) return;
                                holder.binding.subtitle.setText(finalSubtitle);
                            });
                        });
                    }
                } else {
                    holder.binding.subtitle.setVisibility(View.GONE);
                }

                holder.binding.icon.setImageResource(mIcons.get(position));
                holder.binding.icon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.day_night)));
            }

            @Override
            public void onViewRecycled(@NonNull ViewHolder holder) {
                super.onViewRecycled(holder);
                // Cancel task on recycle to avoid meaningless background work and callbacks that may update the wrong holder.
                // zh-CN: 回收时取消任务, 避免无意义的后台工作以及回调可能会更新错误的 holder.
                if (holder.subtitleFuture != null) {
                    holder.subtitleFuture.cancel(true);
                    holder.subtitleFuture = null;
                }
            }

            @Override
            public int getItemCount() {
                return mOnClickListeners.size();
            }
        });
        customView(operations, false);
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

        public Future<?> subtitleFuture;

        public long subtitleRequestToken;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = OperationDialogItemBinding.bind(itemView);
        }

    }

}