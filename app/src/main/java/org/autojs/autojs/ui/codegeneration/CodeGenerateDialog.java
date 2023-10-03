package org.autojs.autojs.ui.codegeneration;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bignerdranch.expandablerecyclerview.ChildViewHolder;
import com.bignerdranch.expandablerecyclerview.ExpandableRecyclerAdapter;
import com.bignerdranch.expandablerecyclerview.ParentViewHolder;
import com.bignerdranch.expandablerecyclerview.model.Parent;

import org.autojs.autojs.app.AppLevelThemeDialogBuilder;
import org.autojs.autojs.app.DialogUtils;
import org.autojs.autojs.codegeneration.CodeGenerator;
import org.autojs.autojs.core.accessibility.NodeInfo;
import org.autojs.autojs.theme.util.ListBuilder;
import org.autojs.autojs.core.ui.widget.JsCheckBox;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs6.databinding.DialogCodeGenerateBinding;
import org.autojs.autojs6.databinding.DialogCodeGenerateOptionBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on 2017/11/6.
 */
public class CodeGenerateDialog extends AppLevelThemeDialogBuilder {
    private final List<OptionGroup> mOptionGroups = new ListBuilder<OptionGroup>()
            .add(new OptionGroup(R.string.text_options, false)
                    .addOption(R.string.text_using_id_selector, true)
                    .addOption(R.string.text_using_text_selector, true)
                    .addOption(R.string.text_using_desc_selector, true))
            .add(new OptionGroup(R.string.text_select)
                    .addOption(R.string.text_select_by_find_one, true)
                    .addOption(R.string.text_select_by_until_find)
                    .addOption(R.string.text_select_by_wait_for)
                    .addOption(R.string.text_select_by_exists))
            .add(new OptionGroup(R.string.text_action)
                    .addOption(R.string.text_act_by_click)
                    .addOption(R.string.text_act_by_long_click)
                    .addOption(R.string.text_act_by_set_text)
                    .addOption(R.string.text_act_by_scroll_forward)
                    .addOption(R.string.text_act_by_scroll_backward))
            .build();

    RecyclerView mOptionsRecyclerView;

    private final Context mServiceContext;
    private final NodeInfo mRootNode;
    private final NodeInfo mTargetNode;
    private final Adapter mAdapter;

    public CodeGenerateDialog(@NonNull Context serviceContext, @NonNull Context context, NodeInfo rootNode, NodeInfo targetNode) {
        super(context);

        DialogCodeGenerateBinding binding = DialogCodeGenerateBinding.inflate(LayoutInflater.from(context));
        LinearLayout view = binding.getRoot();

        mOptionsRecyclerView = binding.options;

        mRootNode = rootNode;
        mTargetNode = targetNode;
        mServiceContext = serviceContext;

        negativeText(R.string.text_cancel);
        positiveText(R.string.text_generate);
        onPositive(((dialog, which) -> generateCodeAndShow()));

        customView(view, false);
        mOptionsRecyclerView.setLayoutManager(new LinearLayoutManager(mServiceContext));
        mAdapter = new Adapter(mOptionGroups);
        mOptionsRecyclerView.setAdapter(mAdapter);
    }

    private void generateCodeAndShow() {
        String code = generateCode();
        AppLevelThemeDialogBuilder builder = new AppLevelThemeDialogBuilder(mServiceContext);
        if (code != null) {
            DialogUtils.showDialog(builder
                    .title(R.string.text_generated_code)
                    .content(code)
                    .negativeText(R.string.dialog_button_cancel)
                    .positiveText(R.string.dialog_button_copy)
                    .onPositive(((dialog, which) -> {
                        ClipboardUtils.setClip(mServiceContext, code);
                        ViewUtils.showToast(mServiceContext, R.string.text_already_copied_to_clip);
                    }))
                    .build());
        } else {
            DialogUtils.showDialog(builder
                    .title(R.string.text_prompt)
                    .content(R.string.text_failed_to_generate)
                    .positiveText(R.string.dialog_button_dismiss)
                    .build());
        }
    }

    private String generateCode() {
        CodeGenerator generator = new CodeGenerator(mRootNode, mTargetNode);
        OptionGroup settings = getOptionGroup(R.string.text_options);
        generator.setUsingId(settings.getOption(R.string.text_using_id_selector).checked);
        generator.setUsingText(settings.getOption(R.string.text_using_text_selector).checked);
        generator.setUsingDesc(settings.getOption(R.string.text_using_desc_selector).checked);
        generator.setSearchMode(getSearchMode());
        setAction(generator);
        return generator.generateCode();
    }

    private void setAction(CodeGenerator generator) {
        OptionGroup action = getOptionGroup(R.string.text_action);
        if (action.getOption(R.string.text_act_by_click).checked) {
            generator.setAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }
        if (action.getOption(R.string.text_act_by_long_click).checked) {
            generator.setAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
        }
        if (action.getOption(R.string.text_act_by_scroll_forward).checked) {
            generator.setAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        }
        if (action.getOption(R.string.text_act_by_scroll_backward).checked) {
            generator.setAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
        }
    }

    private int getSearchMode() {
        OptionGroup selectMode = getOptionGroup(R.string.text_select);
        if (!selectMode.getOption(R.string.text_select_by_find_one).checked) {
            if (selectMode.getOption(R.string.text_select_by_until_find).checked) {
                return CodeGenerator.UNTIL_FIND;
            }
            if (selectMode.getOption(R.string.text_select_by_wait_for).checked) {
                return CodeGenerator.WAIT_FOR;
            }
            if (selectMode.getOption(R.string.text_select_by_exists).checked) {
                return CodeGenerator.EXISTS;
            }
        }
        return CodeGenerator.FIND_ONE;
    }

    private OptionGroup getOptionGroup(int title) {
        for (OptionGroup group : mOptionGroups) {
            if (group.titleRes == title) {
                return group;
            }
        }
        throw new IllegalArgumentException();
    }

    private void uncheckOthers(int parentAdapterPosition, Option child) {
        boolean notify = false;
        for (Option other : child.group.options) {
            if (other != child) {
                if (other.checked) {
                    other.checked = false;
                    notify = true;
                }
            }
        }
        if (notify)
            mAdapter.notifyParentChanged(parentAdapterPosition);
    }

    private static class Option {
        int titleRes;
        boolean checked;
        OptionGroup group;

        Option(int titleRes, boolean checked) {
            this.titleRes = titleRes;
            this.checked = checked;
        }

    }

    class OptionViewHolder extends ChildViewHolder<Option> {

        TextView title;
        JsCheckBox checkBox;

        OptionViewHolder(@NonNull View itemView) {
            super(itemView);
            DialogCodeGenerateOptionBinding binding = DialogCodeGenerateOptionBinding.bind(itemView);

            title = binding.title;

            checkBox = binding.checkbox;
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> onCheckedChanged());

            itemView.setOnClickListener(view -> checkBox.toggle());
        }

        void onCheckedChanged() {
            getChild().checked = checkBox.isChecked();
            if (checkBox.isChecked() && getChild().group.titleRes != R.string.text_options)
                uncheckOthers(getParentAdapterPosition(), getChild());
        }

    }

    private static class OptionGroup implements Parent<Option> {
        int titleRes;
        List<Option> options = new ArrayList<>();
        private final boolean mInitialExpanded;


        OptionGroup(int titleRes, boolean initialExpanded) {
            this.titleRes = titleRes;
            mInitialExpanded = initialExpanded;
        }

        OptionGroup(int titleRes) {
            this(titleRes, true);
        }

        Option getOption(int titleRes) {
            for (Option option : options) {
                if (option.titleRes == titleRes) {
                    return option;
                }
            }
            throw new IllegalArgumentException();
        }

        @Override
        public List<Option> getChildList() {
            return options;
        }

        @Override
        public boolean isInitiallyExpanded() {
            return mInitialExpanded;
        }

        OptionGroup addOption(int titleRes) {
            return addOption(titleRes, false);
        }

        OptionGroup addOption(int res, boolean checked) {
            Option option = new Option(res, checked);
            option.group = this;
            options.add(option);
            return this;
        }
    }

    private static class OptionGroupViewHolder extends ParentViewHolder<OptionGroup, Option> {

        TextView title;
        ImageView icon;

        OptionGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            icon = itemView.findViewById(R.id.icon);
            itemView.setOnClickListener(view -> {
                if (isExpanded()) {
                    collapseView();
                } else {
                    expandView();
                }
            });
        }

        @Override
        public void onExpansionToggled(boolean expanded) {
            icon.setRotation(expanded ? -90 : 0);
        }
    }

    private class Adapter extends ExpandableRecyclerAdapter<OptionGroup, Option, OptionGroupViewHolder, OptionViewHolder> {

        public Adapter(@NonNull List<OptionGroup> parentList) {
            super(parentList);
        }

        @NonNull
        @Override
        public OptionGroupViewHolder onCreateParentViewHolder(@NonNull ViewGroup parentViewGroup, int viewType) {
            return new OptionGroupViewHolder(LayoutInflater.from(parentViewGroup.getContext())
                    .inflate(R.layout.dialog_code_generate_option_group, parentViewGroup, false));
        }

        @NonNull
        @Override
        public OptionViewHolder onCreateChildViewHolder(@NonNull ViewGroup childViewGroup, int viewType) {
            return new OptionViewHolder(LayoutInflater.from(childViewGroup.getContext())
                    .inflate(R.layout.dialog_code_generate_option, childViewGroup, false));
        }

        @Override
        public void onBindParentViewHolder(@NonNull OptionGroupViewHolder viewHolder, int parentPosition, @NonNull OptionGroup optionGroup) {
            viewHolder.title.setText(optionGroup.titleRes);
            viewHolder.icon.setRotation(viewHolder.isExpanded() ? 0 : -90);
        }

        @Override
        public void onBindChildViewHolder(@NonNull OptionViewHolder viewHolder, int parentPosition, int childPosition, @NonNull Option option) {
            viewHolder.title.setText(option.titleRes);
            viewHolder.checkBox.setChecked(option.checked, false);
        }

    }

}
