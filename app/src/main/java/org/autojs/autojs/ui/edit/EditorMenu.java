package org.autojs.autojs.ui.edit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.android.schedulers.AndroidSchedulers;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.extension.MaterialDialogExtensions;
import org.autojs.autojs.model.indices.AndroidClass;
import org.autojs.autojs.model.indices.ClassSearchingItem;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.ConsoleUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.autojs.autojs.util.StringUtils.key;

/**
 * Created by Stardust on Sep 28, 2017.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint("CheckResult")
public class EditorMenu {

    private final EditorView mEditorView;
    private final Context mContext;
    private final CodeEditor mEditor;

    public EditorMenu(EditorView editorView) {
        mEditorView = editorView;
        mContext = editorView.getContext();
        mEditor = editorView.editor;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_log) {
            return ConsoleUtils.launch(mContext);
        }
        if (itemId == R.id.action_force_stop) {
            return tryDoing(mEditorView::forceStop);
        }
        return onEditOptionsSelected(item)
               || onJumpOptionsSelected(item)
               || onMoreOptionsSelected(item)
               || onDebugOptionsSelected(item);
    }

    private boolean onDebugOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_breakpoint) {
            mEditor.addOrRemoveBreakpointAtCurrentLine();
            return true;
        }
        if (itemId == R.id.action_launch_debugger) {
            var builder = new NotAskAgainDialog.Builder(mEditorView.getContext(), "editor.debug.long_click_hint")
                    .title(R.string.text_prompt)
                    .content(R.string.hint_long_click_run_to_debug)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default);
            MaterialDialogExtensions.widgetThemeColor(builder);
            builder.show();
            return tryDoing(mEditorView::debug);
        }
        if (itemId == R.id.action_remove_all_breakpoints) {
            mEditor.removeAllBreakpoints();
            return true;
        }
        return false;
    }

    private boolean onJumpOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_jump_to_line) {
            jumpToLine();
            return true;
        }
        if (itemId == R.id.action_jump_to_start) {
            mEditor.jumpToStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_end) {
            mEditor.jumpToEnd();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_start) {
            mEditor.jumpToLineStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_end) {
            mEditor.jumpToLineEnd();
            return true;
        }
        return false;
    }

    private boolean onMoreOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_console) {
            return tryDoing(mEditorView::showConsole);
        }
        if (itemId == R.id.action_import_java_class) {
            importJavaPackageOrClass();
            return true;
        }
        if (itemId == R.id.action_editor_text_size) {
            return tryDoing(mEditorView::selectTextSize);
        }
        if (itemId == R.id.action_editor_pinch_to_zoom) {
            setPinchToZoomStrategy();
            return true;
        }
        if (itemId == R.id.action_editor_fx_symbols_settings) {
            startSymbolsSettingsActivity();
            return true;
        }
        if (itemId == R.id.action_editor_theme) {
            return tryDoing(mEditorView::selectEditorTheme);
        }
        if (itemId == R.id.action_open_by_other_apps) {
            return tryDoing(mEditorView::openByOtherApps);
        }
        if (itemId == R.id.action_file_details) {
            showFileDetails();
            return true;
        }
        if (itemId == R.id.action_build_apk) {
            startBuildApkActivity();
            return true;
        }
        return false;
    }

    private void importJavaPackageOrClass() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> new ClassSearchDialogBuilder(mContext)
                        .setQuery(s)
                        .itemClick((dialog, item, pos) -> showClassSearchingItem(dialog, item))
                        .title(R.string.text_find_java_classes)
                        .show());
    }

    private void showClassSearchingItem(MaterialDialog dialog, ClassSearchingItem item) {
        String title;
        String desc;
        if (item instanceof ClassSearchingItem.ClassItem) {
            AndroidClass androidClass = ((ClassSearchingItem.ClassItem) item).getAndroidClass();
            title = androidClass.getClassName();
            desc = androidClass.getFullName();
        } else {
            title = ((ClassSearchingItem.PackageItem) item).getPackageName();
            desc = title;
        }
        new MaterialDialog.Builder(mContext)
                .title(title)
                .content(desc)
                .neutralText(R.string.text_view_docs)
                .neutralColorRes(R.color.dialog_button_hint)
                .negativeText(R.string.text_en_import)
                .negativeColorRes(R.color.dialog_button_hint)
                .positiveText(R.string.text_copy)
                .positiveColorRes(R.color.dialog_button_hint)
                .onPositive((ignored, which) -> {
                    ClipboardUtils.setClip(mContext, desc);
                    ViewUtils.showToast(mContext, R.string.text_already_copied_to_clip);
                    dialog.dismiss();
                })
                .onNegative((ignored, which) -> {
                    // @Overwrite by SuperMonster003 on Jul 28, 2024.
                    //  ! Adaptive to any circumstances by parsing a line number
                    //  ! with JavaScriptFileSource.parseExecutionMode.
                    //  ! zh-CN:
                    //  ! 使用 JavaScriptFileSource.parseExecutionMode 解析行号以适配任何情况.
                    //  !
                    //  # if (mEditor.getText().startsWith(JavaScriptSource.EXECUTION_MODE_UI_PREFIX)) {
                    //  #     mEditor.insert(1, item.getImportText() + ";\n");
                    //  # } else {
                    //  #     mEditor.insert(0, item.getImportText() + ";\n");
                    //  # }
                    var executionInfo = JavaScriptFileSource.parseExecutionMode(mEditor.getText());
                    mEditor.insert(executionInfo.getLineno(), item.getImportText() + ";\n");
                })
                .onNeutral((ignored, which) -> IntentUtils.browse(
                        mContext,
                        item.getUrl(),
                        new ToastExceptionHolder(mContext)
                ))
                .onAny((ignored, which) -> dialog.dismiss())
                .show();
    }

    private void startBuildApkActivity() {
        BuildActivity.launch(mContext, mEditorView.uri.getPath());
    }

    private void setPinchToZoomStrategy() {
        String key = key(R.string.key_editor_pinch_to_zoom_strategy);

        String defItemKey = key(R.string.default_key_editor_pinch_to_zoom_strategy);
        String itemKey = Pref.getString(key, defItemKey);

        List<String> itemKeys = Arrays.asList(mContext.getResources().getStringArray(R.array.keys_editor_pinch_to_zoom_strategy));

        int defSelectedIndex = Math.max(0, itemKeys.indexOf(itemKey));

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_pinch_to_zoom)
                .items(R.array.values_editor_pinch_to_zoom_strategy)
                .itemsCallbackSingleChoice(defSelectedIndex, (dialog, itemView, which, text) -> {
                    String newKey = itemKeys.get(which);
                    if (!Objects.equals(newKey, itemKey)) {
                        Pref.putString(key, newKey);
                        mEditorView.editor.notifyPinchToZoomStrategyChanged(newKey);
                    }
                    return true;
                })

                // TODO by SuperMonster003 on Oct 17, 2022.

                // .neutralText(R.string.dialog_button_details)
                // .neutralColorRes(R.color.dialog_button_hint)
                // .onNeutral((dialog, which) -> {
                //     // Hint dialog.
                // })
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> dialog.dismiss())
                .autoDismiss(false);

        MaterialDialogExtensions.choiceWidgetThemeColor(builder);

        // TODO by SuperMonster003 on Oct 17, 2022.
        //  ! Implementation for "scale view".
        //  ! zh-CN: 实现 "scale view".
        makeEditorPinchToZoomScaleViewUnderDev(builder, itemKeys.indexOf(key(R.string.key_editor_pinch_to_zoom_scale_view)));
    }

    private void makeEditorPinchToZoomScaleViewUnderDev(MaterialDialog.Builder builder, int i) {
        builder.itemsDisabledIndices(i);
        MaterialDialog built = builder.build();
        assert built.getItems() != null;
        built.getItems().set(i, built.getItems().get(i) + " (" + builder.getContext().getString(R.string.text_under_development).toLowerCase(Language.getPrefLanguage().getLocale()) + ")");
        built.show();
    }

    private void startSymbolsSettingsActivity() {
        // TODO by SuperMonster003 on Oct 16, 2022.

        // new SymbolsSettingsActivity.IntentBuilder(mContext)
        //         .extra(mEditorView.getUri().getPath())
        //         .start();

        ViewUtils.showToast(mContext, R.string.text_under_development_content);
    }

    private boolean onEditOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_or_replace) {
            findOrReplace();
            return true;
        }
        if (itemId == R.id.action_copy_all) {
            copyAll();
            return true;
        }
        if (itemId == R.id.action_copy_line) {
            return copyLine();
        }
        if (itemId == R.id.action_delete_line) {
            return deleteLine();
        }
        if (itemId == R.id.action_paste) {
            return paste();
        }
        if (itemId == R.id.action_clear) {
            return tryDoing(() -> mEditor.setText(""));
        }
        if (itemId == R.id.action_comment) {
            return tryDoing(mEditor.commentHelper::handle);
        }
        if (itemId == R.id.action_beautify) {
            return tryDoing(mEditorView::beautifyCode);
        }
        return false;
    }

    private void jumpToLine() {
        mEditor.getLineCount()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showJumpDialog);
    }

    private void showJumpDialog(final int lineCount) {
        String hint = "1 - " + lineCount;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_jump_to_line)
                .input(hint, "", (dialog, input) -> {
                    if (TextUtils.isEmpty(input)) {
                        return;
                    }
                    try {
                        int line = Math.max(Math.min(Integer.parseInt(input.toString()), lineCount), 1);
                        mEditor.jumpTo(line - 1, 0);
                    } catch (NumberFormatException ignored) {
                        /* Ignored. */
                    }
                })
                .inputType(InputType.TYPE_CLASS_NUMBER);
        builder.positiveColorRes(R.color.dialog_button_attraction);
        builder.negativeText(R.string.dialog_button_cancel);
        builder.negativeColorRes(R.color.dialog_button_default);
        MaterialDialogExtensions.widgetThemeColor(builder);
        builder.show();
    }

    private void showFileDetails() {
        var path = mEditorView.uri.getPath();
        EditableFileInfoDialogManager.showEditableFileInfoDialog(mContext, new File(path), mEditor::getText);
    }

    protected boolean copyLine() {
        return tryDoing(mEditor::copyLine);
    }

    protected boolean deleteLine() {
        return tryDoing(mEditor::deleteLine);
    }

    protected boolean paste() {
        return tryDoing(() -> {
            CharSequence clip = getClip();
            if (clip != null) {
                mEditor.insert(clip.toString());
            }
        });
    }

    @Nullable
    private CharSequence getClip() {
        return ClipboardUtils.getClip(mContext);
    }

    private void findOrReplace() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> {
                    FindOrReplaceDialogBuilder builder = new FindOrReplaceDialogBuilder(mContext, mEditorView)
                            .setQueryIfNotEmpty(s);
                    builder.positiveColorRes(R.color.dialog_button_attraction);
                    builder.show();
                });
    }

    private void copyAll() {
        ClipboardUtils.setClip(mContext, mEditor.getText());
        ViewUtils.showSnack(mEditorView, R.string.text_already_copied_to_clip);
    }

    private boolean tryDoing(Runnable callable) {
        try {
            callable.run();
            return true;
        } catch (Exception ignore) {
            /* Ignored. */
        }
        return false;
    }

}
