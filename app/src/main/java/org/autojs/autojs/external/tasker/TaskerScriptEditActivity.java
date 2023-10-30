package org.autojs.autojs.external.tasker;

import static org.autojs.autojs.ui.edit.EditorView.EXTRA_CONTENT;
import static org.autojs.autojs.ui.edit.EditorView.EXTRA_NAME;
import static org.autojs.autojs.ui.edit.EditorView.EXTRA_RUN_ENABLED;
import static org.autojs.autojs.ui.edit.EditorView.EXTRA_SAVE_ENABLED;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.autojs.autojs.ui.BaseActivity;
import org.autojs.autojs.ui.edit.EditorView;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.util.Observers;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.databinding.ActivityTaskerScriptEditBinding;

import io.reactivex.android.schedulers.AndroidSchedulers;

/**
 * Created by Stardust on 2017/4/5.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
public class TaskerScriptEditActivity extends BaseActivity {

    public static final int REQUEST_CODE = 10016;

    private EditorView mEditorView;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityTaskerScriptEditBinding binding = ActivityTaskerScriptEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mEditorView = binding.editorView;
        mEditorView.handleIntent(getIntent()
                        .putExtra(EXTRA_RUN_ENABLED, false)
                        .putExtra(EXTRA_SAVE_ENABLED, false))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Observers.emptyConsumer(), ex -> {
                    ViewUtils.showToast(TaskerScriptEditActivity.this, ex.getMessage(), true);
                    finish();
                });
        setToolbarAsBack(mEditorView.name);
    }

    public static void edit(Activity activity, String title, String summary, String content) {
        activity.startActivityForResult(new Intent(activity, TaskerScriptEditActivity.class)
                .putExtra(EXTRA_CONTENT, content)
                .putExtra("summary", summary)
                .putExtra(EXTRA_NAME, title), REQUEST_CODE);
    }

    @Override
    public void finish() {
        CodeEditor editor = mEditorView.editor;
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_CONTENT, editor.getText()));
        TaskerScriptEditActivity.super.finish();
    }

    @Override
    protected void onDestroy() {
        mEditorView.destroy();
        super.onDestroy();
    }

}
