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

/**
 * Created by Stardust on 2017/6/26.
 */
public class CircularMenuOperationDialogBuilder extends AppLevelThemeDialogBuilder {

    private final ArrayList<View.OnClickListener> mOnClickListeners = new ArrayList<>();
    private final ArrayList<Integer> mIcons = new ArrayList<>();
    private final ArrayList<String> mTexts = new ArrayList<>();

    public CircularMenuOperationDialogBuilder(@NonNull Context context) {
        super(context);
        RecyclerView operations = new RecyclerView(context);
        operations.setLayoutManager(new LinearLayoutManager(context));
        operations.setAdapter(new RecyclerView.Adapter<ViewHolder>() {
            @NonNull
            @Override
            public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ViewHolder(LayoutInflater.from(new ContextThemeWrapper(context, R.style.AppTheme)).inflate(R.layout.operation_dialog_item, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
                holder.itemView.setOnClickListener(mOnClickListeners.get(position));
                holder.binding.text.setText(mTexts.get(position));
                holder.binding.text.setTextColor(context.getColor(R.color.day_night));
                holder.binding.icon.setImageResource(mIcons.get(position));
                holder.binding.icon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.day_night)));
            }

            @Override
            public int getItemCount() {
                return mOnClickListeners.size();
            }
        });
        customView(operations, false);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, int textRes) {
        return item(iconRes, textRes, null);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, int textRes, View.OnClickListener l) {
        return item(iconRes, getContext().getString(textRes), l);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String text) {
        return item(iconRes, text, null);
    }

    public CircularMenuOperationDialogBuilder item(int iconRes, String text, View.OnClickListener l) {
        mOnClickListeners.add(l);
        mIcons.add(iconRes);
        mTexts.add(text);
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