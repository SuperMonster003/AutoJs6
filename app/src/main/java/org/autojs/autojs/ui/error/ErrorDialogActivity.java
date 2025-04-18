package org.autojs.autojs.ui.error;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.extension.MaterialDialogExtensions;
import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs6.R;

/**
 * A simple activity to show error dialogs when a context is not available or valid
 * for showing dialogs directly.
 */
public class ErrorDialogActivity extends BaseActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_POSITIVE_BUTTON_TEXT = "positive_button_text";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Get extras from intent
        Intent intent = getIntent();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        String positiveButtonText = intent.getStringExtra(EXTRA_POSITIVE_BUTTON_TEXT);

        // If no positive button text provided, use default dismiss text
        if (positiveButtonText == null) {
            positiveButtonText = getString(R.string.dialog_button_dismiss);
        }

        // Show dialog
        var materialDialog = new MaterialDialog.Builder(this)
                .title(title)
                .content(message)
                .positiveText(positiveButtonText)
                .positiveColorRes(R.color.dialog_button_default)
                .cancelable(true)
                .canceledOnTouchOutside(true)
                .onPositive((dialog, which) -> finish())
                .dismissListener(dialog -> finish())
                .show();

        MaterialDialogExtensions.makeTextCopyable(materialDialog, materialDialog.getContentView());
    }

    /**
     * Helper method to show an error dialog from any context
     */
    public static void showErrorDialog(android.content.Context context, int titleRes, String message) {
        Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(EXTRA_TITLE, context.getString(titleRes));
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Helper method to show an error dialog from any context with custom positive button text
     */
    public static void showErrorDialog(android.content.Context context, int titleRes, String message, int positiveButtonTextRes) {
        Intent intent = new Intent(context, ErrorDialogActivity.class);
        intent.putExtra(EXTRA_TITLE, context.getString(titleRes));
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_POSITIVE_BUTTON_TEXT, context.getString(positiveButtonTextRes));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
