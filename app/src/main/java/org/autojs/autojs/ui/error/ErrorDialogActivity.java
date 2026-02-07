package org.autojs.autojs.ui.error;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.util.Objects;

/**
 * A simple activity to show error dialogs when a context is not available or valid
 * for showing dialogs directly.
 * zh-CN: 一个简单的 Activity，用于在上下文不可用或无效而无法直接显示对话框时显示错误对话框.
 */
public class ErrorDialogActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_POSITIVE_BUTTON_TEXT = "positive_button_text";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get extras from intent.
        // zh-CN: 从 Intent 获取附加数据.
        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String positiveButtonText = intent.getStringExtra(EXTRA_POSITIVE_BUTTON_TEXT);

        // If no positive button text provided, use default dismiss text.
        // zh-CN: 如果没有提供确认按钮文本，则使用默认的关闭文本.
        if (positiveButtonText == null) {
            positiveButtonText = getString(R.string.dialog_button_dismiss);
        }

        String content = Objects.requireNonNullElse(message, "");

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title(Objects.requireNonNullElse(title, getString(R.string.text_prompt)))
                .content(content)
                .positiveText(positiveButtonText)
                .positiveColorRes(R.color.dialog_button_default)
                .autoDismiss(false)
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .onPositive((dialog, which) -> dialog.dismiss())
                .dismissListener(dialog -> finish());

        if (!content.isBlank()) {
            builder.neutralText(R.string.text_copy_all);
            builder.neutralColorRes(R.color.dialog_button_hint);
            builder.onNeutral((dialog, which) -> {
                ClipboardUtils.setClip(dialog.getContext(), content);
                ViewUtils.showSnack(dialog.getView(), R.string.text_already_copied_to_clip, false);
            });
        }

        var materialDialog = builder.show();

        DialogUtils.makeTextCopyable(materialDialog, materialDialog.getContentView());
    }

    /**
     * Helper method to show an error dialog from any context.
     * zh-CN: 用于从任何上下文显示错误对话框的辅助方法.
     */
    public static void showErrorDialog(Context context, int titleRes, String message) {
        Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(EXTRA_TITLE, context.getString(titleRes));
        intent.putExtra(EXTRA_MESSAGE, message);
        IntentUtils.startSafely(intent, context);
    }

    /**
     * Helper method to show an error dialog from any context with custom positive button text.
     * zh-CN: 用于从任何上下文显示带有自定义确认按钮文本的错误对话框的辅助方法.
     */
    public static void showErrorDialog(Context context, int titleRes, String message, int positiveButtonTextRes) {
        Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(EXTRA_TITLE, context.getString(titleRes));
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_POSITIVE_BUTTON_TEXT, context.getString(positiveButtonTextRes));
        IntentUtils.startSafely(intent, context);
    }

}
