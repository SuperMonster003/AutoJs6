package org.autojs.autojs.ui.edit.debug;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.MaterialDialog;

import org.autojs.autojs.core.pref.keys.MiscStateKeys;
import org.autojs.autojs6.R;

public class CodeEvaluateDialogBuilder extends MaterialDialog.Builder {

    private CodeEvaluator mCodeEvaluator;
    private TextView mResult;
    private EditText mCode;

    public CodeEvaluateDialogBuilder(@NonNull Context context) {
        super(context);
        setupViews();
        mCode.setText(MiscStateKeys.CODE_EVALUATE_CODE.get());
    }

    private void setupViews() {
        View view = View.inflate(context, R.layout.dialog_code_evaluate, null);
        customView(view, true);
        mResult = view.findViewById(R.id.result);
        mCode = view.findViewById(R.id.code);
        positiveText(R.string.text_execute);
        negativeText(R.string.text_close);
        autoDismiss(false);
        onNegative((dialog, which) -> dialog.dismiss());
        onPositive(((dialog, which) -> {
            Editable code = mCode.getText();
            if (!TextUtils.isEmpty(code)) {
                String codeStr = code.toString();
                MiscStateKeys.CODE_EVALUATE_CODE.set(codeStr);
                mResult.setText(mCodeEvaluator.eval(codeStr));
            }
        }));
    }

    public CodeEvaluateDialogBuilder codeEvaluator(CodeEvaluator evaluator) {
        mCodeEvaluator = evaluator;
        return this;
    }

}
