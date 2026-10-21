package org.autojs.autojs.ui.edit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import org.autojs.autojs.model.indices.AndroidClass;
import org.autojs.autojs.model.indices.ClassSearchingItem;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.core.pref.keys.DialogStateKeys;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.edit.editor.command.EditorCommandDispatcher;
import org.autojs.autojs.ui.edit.editor.engine.ScriptEditorEngine;
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.DialogUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;
/**
 * Created by Stardust on Sep 28, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 8, 2026.
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "unused"})
@SuppressLint("CheckResult")
public class EditorMenu {

    private final EditorView mEditorView;
    private final Context mContext;

    public EditorMenu(EditorView editorView) {
        this(editorView, false);
    }

    public EditorMenu(EditorView editorView, boolean readOnly) {
        mEditorView = editorView;
        mContext = editorView.getContext();
    }

    public void prepareOptionsMenu(Menu menu) {
        if (menu == null) {
            return;
        }
        prepareReadOnlyMenu(menu);
        setReadOnlySensitiveMenuItemsVisible(menu, !mEditorView.isReadOnly());
        MenuItem foldingItem = menu.findItem(R.id.action_code_folding);
        if (foldingItem != null) {
            ScriptEditorEngine editor = currentEditor();
            foldingItem.setVisible(editor.getSupportsCodeFolding());
            foldingItem.setEnabled(editor.isReady() && !editor.isLoadingText());
        }
        MenuItem encodingItem = menu.findItem(R.id.action_file_encoding);
        if (encodingItem != null) {
            encodingItem.setEnabled(mEditorView.canChangeFileEncoding());
        }
        MenuItem fullscreenItem = menu.findItem(R.id.action_fullscreen_mode);
        if (fullscreenItem != null) {
            fullscreenItem.setCheckable(true);
            fullscreenItem.setChecked(mEditorView.isFullscreenMode());
        }
        MenuItem previousCursorItem = menu.findItem(R.id.action_jump_to_previous_cursor);
        if (previousCursorItem != null) {
            previousCursorItem.setVisible(mEditorView.supportsPreviousCursorJump());
            previousCursorItem.setEnabled(mEditorView.hasPreviousCursorPosition());
        }
        // Offered only while the installed 3-Block Compose plugin ships the visual designer (1.2.0+).
        setMenuItemVisible(menu, R.id.action_open_in_three_block_compose_designer, mEditorView.isThreeBlockComposeDesignerAvailable());
    }

    private void setReadOnlySensitiveMenuItemsVisible(Menu menu, boolean mutable) {
        setMenuItemVisible(menu, R.id.action_find_or_replace, mutable);
        setMenuItemVisible(menu, R.id.action_paste, mutable);
        setMenuItemVisible(menu, R.id.action_delete_line, mutable);
        setMenuItemVisible(menu, R.id.action_clear, mutable);
        setMenuItemVisible(menu, R.id.action_comment, mutable);
        setMenuItemVisible(menu, R.id.action_beautify, mutable);
        setMenuItemVisible(menu, R.id.action_debug, mutable);
        setMenuItemVisible(menu, R.id.action_import_java_class, mutable);
    }

    private void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private ScriptEditorEngine currentEditor() {
        return mEditorView.editor;
    }

    private EditorCommandDispatcher commandDispatcher() {
        return mEditorView.getCommandDispatcher();
    }

    private void prepareReadOnlyMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_ace_read_only_mode);
        if (item == null) {
            return;
        }
        boolean visible = !mEditorView.isReadOnlyLockedByIntent();
        boolean readOnly = mEditorView.isReadOnly();
        item.setVisible(visible);
        item.setEnabled(mEditorView.canToggleReadOnlyMode());
        item.setCheckable(true);
        item.setChecked(readOnly);
        item.setTitle(R.string.text_ace_read_only_mode);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        boolean handled;
        if (itemId == R.id.action_log) {
            handled = tryDoing(mEditorView::openEditorLogByPreference);
        } else if (itemId == R.id.action_force_stop) {
            handled = tryDoing(mEditorView::forceStop);
        } else if (itemId == R.id.action_editor_settings) {
            mEditorView.showEditorSettingsDialog();
            handled = true;
        } else if (itemId == R.id.action_fullscreen_mode) {
            mEditorView.toggleFullscreenMode();
            handled = true;
        } else if (itemId == R.id.action_file_encoding) {
            handled = tryDoing(mEditorView::showEncodingDialog);
        } else {
            handled = onEditOptionsSelected(item)
                    || onJumpOptionsSelected(item)
                    || onMoreOptionsSelected(item)
                    || onDebugOptionsSelected(item);
        }
        if (handled) {
            mEditorView.recordAceActionForMenuItem(itemId);
        }
        return handled;
    }

    private boolean onDebugOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_breakpoint) {
            commandDispatcher().addOrRemoveBreakpointAtCurrentLine();
            return true;
        }
        if (itemId == R.id.action_launch_debugger) {
            var builder = new NotAskAgainDialog.Builder(mEditorView.getContext(), DialogStateKeys.EDITOR_DEBUG_LONG_CLICK_HINT)
                    .title(R.string.text_prompt)
                    .content(R.string.hint_long_click_run_to_debug)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default);
            DialogUtils.widgetThemeColor(builder);
            builder.show();
            return tryDoing(mEditorView::debug);
        }
        if (itemId == R.id.action_remove_all_breakpoints) {
            commandDispatcher().removeAllBreakpoints();
            return true;
        }
        return false;
    }

    private boolean onJumpOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_jump_to_line) {
            return tryDoing(mEditorView::showJumpToLineDialog);
        }
        if (itemId == R.id.action_jump_to_start) {
            commandDispatcher().jumpToStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_end) {
            commandDispatcher().jumpToEnd();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_start) {
            commandDispatcher().jumpToLineStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_end) {
            commandDispatcher().jumpToLineEnd();
            return true;
        }
        if (itemId == R.id.action_jump_to_previous_cursor) {
            mEditorView.jumpToPreviousCursor();
            return true;
        }
        return false;
    }

    private boolean onMoreOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_console) {
            return tryDoing(mEditorView::showConsole);
        }
        if (itemId == R.id.action_log) {
            mEditorView.openEditorLogByPreference();
            return true;
        }
        if (itemId == R.id.action_import_java_class) {
            return tryDoing(mEditorView::showJavaClassSearchDialog);
        }
        if (itemId == R.id.action_ace_read_only_mode) {
            return tryDoing(mEditorView::toggleReadOnlyMode);
        }
        if (itemId == R.id.action_copy_ace_diagnostics) {
            return tryDoing(mEditorView::copyAceDiagnosticsToClipboard);
        }
        if (itemId == R.id.action_open_by_other_apps) {
            return tryDoing(mEditorView::openByOtherApps);
        }
        if (itemId == R.id.action_open_in_three_block_compose_designer) {
            return tryDoing(mEditorView::openInThreeBlockComposeDesigner);
        }
        if (itemId == R.id.action_file_details) {
            return tryDoing(mEditorView::showFileDetailsDialog);
        }
        if (itemId == R.id.action_version_history) {
            showHistory();
            return true;
        }
        if (itemId == R.id.action_build_apk) {
            return tryDoing(mEditorView::startBuildApkActivity);
        }
        return false;
    }

    private void showHistory() {
        mEditorView.showVersionHistoryDialog();
    }

    private void importJavaPackageOrClass() {
        new ClassSearchDialogBuilder(mContext)
                .setQuery(currentEditor().getSelectedTextSnapshot())
                .itemClick((dialog, item, pos) -> showClassSearchingItem(dialog, item))
                .title(R.string.text_find_java_classes)
                .show();
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
                    ScriptEditorEngine editor = currentEditor();
                    var executionInfo = JavaScriptFileSource.parseExecutionMode(editor.getText());
                    commandDispatcher().insert(executionInfo.getLineno(), item.getImportText() + ";\n");
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
        Uri uri = mEditorView.getUri();
        String path;
        if (uri == null) {
            path = null;
        } else {
            path = uri.getPath();
        }
        if (TextUtils.isEmpty(path)) {
            DialogUtils.buildAndShowAdaptive(new MaterialDialog.Builder(mContext)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_package_application_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)::build);
            return;
        }
        BuildActivity.launch(mContext, path);
    }

    private boolean onEditOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_or_replace) {
            return tryDoing(mEditorView::showFindOrReplaceDialog);
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
            return tryDoing(() -> commandDispatcher().clear());
        }
        if (itemId == R.id.action_comment) {
            return tryDoing(() -> commandDispatcher().toggleComment());
        }
        if (itemId == R.id.action_beautify) {
            return tryDoing(() -> commandDispatcher().beautifyCode());
        }
        if (itemId == R.id.action_unfold_all) {
            return commandDispatcher().unfoldAll();
        }
        if (itemId == R.id.action_fold_all) {
            return commandDispatcher().foldAll();
        }
        return false;
    }

    private void jumpToLine() {
        showJumpDialog(currentEditor().getLineCountSnapshot());
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
                        commandDispatcher().jumpTo(line - 1, 0);
                    } catch (NumberFormatException ignored) {
                        /* Ignored. */
                    }
                })
                .inputType(InputType.TYPE_CLASS_NUMBER);
        builder.positiveColorRes(R.color.dialog_button_attraction);
        builder.negativeText(R.string.dialog_button_cancel);
        builder.negativeColorRes(R.color.dialog_button_default);
        DialogUtils.widgetThemeColor(builder);
        builder.show();
    }

    private void showFileDetails() {
        Uri uri = mEditorView.getUri();
        String path;
        if (uri != null) {
            path = uri.getPath();
        } else {
            path = null;
        }
        if (TextUtils.isEmpty(path)) {
            DialogUtils.buildAndShowAdaptive(new MaterialDialog.Builder(mContext)
                    .title(R.string.text_prompt)
                    .content(R.string.error_unable_to_display_file_details_as_current_file_path_is_unknown)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)::build);
            return;
        }
        EditableFileInfoDialogManager.showEditableFileInfoDialog(mContext, new File(path), () -> currentEditor().getText());
    }

    protected boolean copyLine() {
        return tryDoing(() -> commandDispatcher().copyLine());
    }

    protected boolean deleteLine() {
        return tryDoing(() -> commandDispatcher().deleteLine());
    }

    protected boolean paste() {
        return tryDoing(() -> {
            CharSequence clip = getClip();
            if (clip != null) {
                commandDispatcher().insert(clip);
            }
        });
    }

    @Nullable
    private CharSequence getClip() {
        return ClipboardUtils.getClip(mContext);
    }

    private void findOrReplace() {
        String selectedText = currentEditor().getSelectedTextSnapshot();
                    // Prefer restoring last search (even after exit search mode).
                    // zh-CN: 优先恢复 "上一次搜索" (即使已退出搜索模式), 避免 selection (命中内容) 覆盖关键词.
        String preferredQuery = mEditorView.getPreferredSearchQueryForDialogOrNull();
        boolean preferredRegex = mEditorView.getPreferredSearchUsingRegexForDialog();

        FindOrReplaceDialogBuilder builder = new FindOrReplaceDialogBuilder(mContext, mEditorView);

                    // Only override regex checkbox when we are restoring an actual previous query.
                    // Otherwise keep the persisted checkbox state from SharedPreferences (restoreState()).
                    //
                    // zh-CN: 仅当我们确实在恢复 "上一次 query" 时才覆盖 regex 勾选状态;
                    // 否则使用 SharedPreferences 中持久化的勾选状态.
        if (preferredQuery != null) {
            builder.setUsingRegex(preferredRegex);
        }

        builder.setQueryIfNotEmpty(preferredQuery != null ? preferredQuery : selectedText);

        builder.positiveColorRes(R.color.dialog_button_attraction);
        builder.show();
    }

    private void copyAll() {
        ClipboardUtils.setClip(mContext, currentEditor().getText());
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
