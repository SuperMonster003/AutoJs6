package org.autojs.autojs.ui.floating;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import org.autojs.autojs.app.AppLevelThemeDialogBuilder;
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataItem;
import org.autojs.autojs.core.accessibility.WindowInfo.Companion.WindowInfoDataSummary;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.DialogListViewBinding;

import java.util.Comparator;
import java.util.List;

/**
 * Created by SuperMonster003 on May 19, 2024.
 */
public class WindowSwitchingDialog extends AppLevelThemeDialogBuilder {

    private final List<WindowInfoDataSummary> mItemList;
    private final Adapter mAdapter;
    private final Context mContext;

    @Nullable
    public Function2<? super WindowSwitchingDialog, ? super Integer, Unit> itemsClickCallback;

    public WindowSwitchingDialog(@NonNull Context context, List<WindowInfoDataSummary> itemList) {
        super(context);

        mItemList = itemList;
        mContext = context;

        DialogListViewBinding binding = DialogListViewBinding.inflate(LayoutInflater.from(context));
        ViewGroup root = binding.getRoot();

        RecyclerView recyclerView = binding.list;

        title(context.getString(R.string.text_available_windows) + " [ ×" + itemList.size() + " ]");
        negativeText(R.string.dialog_button_dismiss);

        customView(root, false);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        mAdapter = new Adapter();
        recyclerView.setAdapter(mAdapter);
    }

    public void sortItems(Comparator<WindowInfoDataSummary> comparator) {
        mItemList.sort(comparator);
        mAdapter.notifyDataSetChanged();
    }

    public List<WindowInfoDataSummary> getItemList() {
        return mItemList;
    }

    private class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(v -> {
                if (itemsClickCallback != null) {
                    // int position = mRecyclerView.getChildLayoutPosition(itemView);
                    int position = getBindingAdapterPosition();
                    itemsClickCallback.invoke(WindowSwitchingDialog.this, position);
                }
            });
        }

    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.window_switching_dialog_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {

            TextView titleLabel = holder.itemView.findViewById(R.id.title_label);
            TextView titleValue = holder.itemView.findViewById(R.id.title_value);
            TextView typeLabel = holder.itemView.findViewById(R.id.type_label);
            TextView typeValue = holder.itemView.findViewById(R.id.type_value);
            TextView orderValue = holder.itemView.findViewById(R.id.order_value);
            TextView packageLabel = holder.itemView.findViewById(R.id.package_label);
            TextView packageValue = holder.itemView.findViewById(R.id.package_value);
            TextView rootNodeLabel = holder.itemView.findViewById(R.id.root_node_label);
            TextView rootNodeValue = holder.itemView.findViewById(R.id.root_node_value);

            WindowInfoDataItem titleItem = mItemList.get(position).getTitle();
            WindowInfoDataItem typeItem = mItemList.get(position).getType();
            WindowInfoDataItem orderItem = mItemList.get(position).getOrder();
            WindowInfoDataItem packageNameItem = mItemList.get(position).getPackageName();
            WindowInfoDataItem rootNodeItem = mItemList.get(position).getRootNode();

            titleLabel.setText(titleItem.getLabel());
            titleValue.setText(titleItem.getValue());
            typeLabel.setText(typeItem.getLabel());
            typeValue.setText(typeItem.getValue());
            orderValue.setText(orderItem.getValue());
            packageLabel.setText(packageNameItem.getLabel());
            packageValue.setText(packageNameItem.getValue());
            rootNodeLabel.setText(rootNodeItem.getLabel());
            rootNodeValue.setText(rootNodeItem.getValue());

            // @Caution by SuperMonster003 on May 19, 2024.
            //  ! Do not set `textIsSelectable` true,
            //  ! as TextView will be not clickable within RecyclerView.
            //  ! zh-CN:
            //  ! 勿将 `textIsSelectable` 设为 true,
            //  ! TextView 会因为其位于 RecyclerView 之内而不可点击.
            //  # textView.setTextIsSelectable(true);

        }

        @Override
        public int getItemCount() {
            return mItemList.size();
        }

    }

}