package org.autojs.autojs.pluginclient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import org.autojs.autojs.AutoJs;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.io.Zip;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.project.ProjectLauncher;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.ui.error.ErrorDialogActivity;
import org.autojs.autojs.util.MD5Utils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Stardust on May 11, 2017.
 * Modified by SuperMonster003 as of Sep 16, 2026.
 */
public class DevPluginResponseHandler implements Handler {

    private static final String TAG = "DevPluginResponseHandler";

    private final Context mContext;
    public static final String TYPE_COMMAND = DevPluginService.TYPE_COMMAND;
    public static final String TYPE_BYTES_COMMAND = DevPluginService.TYPE_BYTES_COMMAND;

    private final Router mRouter;

    private final HashMap<String, ScriptExecution> mScriptExecutions = new HashMap<>();
    private final File mCacheDir;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public DevPluginResponseHandler(Context context, File cacheDir) {
        mContext = context;
        mCacheDir = cacheDir;
        if (cacheDir.exists()) {
            if (cacheDir.isDirectory()) {
                PFiles.deleteFilesOfDir(cacheDir);
            } else {
                cacheDir.delete();
                cacheDir.mkdirs();
            }
        }
        mRouter = new Router.RootRouter("type")
                .handler(TYPE_COMMAND, new Router("command")
                        .handler("run", data -> {
                            String script = data.get("script").getAsString();
                            String name = getName(data);
                            String id = data.get("id").getAsString();
                            runScript(mContext, id, name, script);
                            return true;
                        })
                        .handler("stop", data -> {
                            String id = data.get("id").getAsString();
                            stopScript(id);
                            return true;
                        })
                        .handler("rerun", data -> {
                            String script = data.get("script").getAsString();
                            String name = getName(data);
                            String id = data.get("id").getAsString();
                            stopScript(id);
                            runScript(mContext, id, name, script);
                            return true;
                        })
                        .handler("save", data -> {
                            String script = data.get("script").getAsString();
                            String name = getName(data);
                            saveFile(name, getRelativePath(data), script);
                            return true;
                        })
                        .handler("stopAll", data -> {
                            AutoJs.getInstance().getScriptEngineService().stopAllAndToast();
                            return true;
                        }))
                .handler(TYPE_BYTES_COMMAND, new Router("bytes_command")
                        .handler("run_project", data -> {
                            launchProject(data.get("dir").getAsString());
                            return true;
                        })
                        .handler("save_project", data -> {
                            saveProject(data.get("name").getAsString(), data.get("dir").getAsString(), getDeletedFiles(data));
                            return true;
                        }));
    }

    @Override
    public boolean handle(JsonObject data) {
        return mRouter.handle(data);
    }

    /**
     * Unzip the archive into the per-project cache directory. Honors `override` (wipe the cache first so the
     * cache mirrors the extension's view of the project) and `deletedFiles` (remove files deleted on the PC).
     * zh-CN: 将归档解压到项目缓存目录. 尊重 `override` (先清空缓存, 使其与插件侧项目一致) 与 `deletedFiles` (删除 PC 侧已删除的文件).
     */
    public Observable<File> handleBytes(JsonObject data, JsonSocket.Bytes bytes) {
        JsonObject inner = data.get("data").getAsJsonObject();
        String id = inner.get("id").getAsString();
        String idMd5 = MD5Utils.md5(id);
        boolean override = inner.has("override") && inner.get("override").isJsonPrimitive() && inner.get("override").getAsBoolean();
        List<String> deletedFiles = getDeletedFiles(inner);
        return Observable
                .fromCallable(() -> {
                    File dir = new File(mCacheDir, idMd5);
                    if (override && dir.exists()) {
                        PFiles.deleteFilesOfDir(dir);
                    }
                    byte[] byteArray = bytes.byteString.toByteArray();
                    Zip.unzip(new ByteArrayInputStream(byteArray), dir);
                    deleteRelativeFiles(dir, deletedFiles);
                    return dir;
                })
                .subscribeOn(Schedulers.io());
    }

    private void runScript(Context mContext, String viewId, String name, String script) {
        if (TextUtils.isEmpty(name)) {
            name = "[" + viewId + "]";
        } else {
            name = PFiles.getNameWithoutExtension(name);
        }
        StringScriptSource scriptSource = new StringScriptSource(name, script);

        // scriptSource.setPrefix(mContext.getString(R.string.text_remote) + "$");
        scriptSource.setPrefix("$remote/");

        mScriptExecutions.put(viewId, Scripts.run(mContext, scriptSource));
    }

    private void launchProject(String dir) {
        try {
            new ProjectLauncher(dir).launch(AutoJs.getInstance().getScriptEngineService());
        } catch (Exception e) {
            e.printStackTrace();
            String message = e.getMessage();
            if (message == null) {
                ViewUtils.showToast(mContext, R.string.text_invalid_project, true);
            } else {
                // Use ErrorDialogActivity instead of showing MaterialDialog directly
                // This avoids issues when calling dialogs from non-UI threads or when Activity is not visible.
                // zh-CN:
                // 使用 ErrorDialogActivity 代替直接显示 MaterialDialog,
                // 这样可以避免在非 UI 线程或 Activity 不可见状态下调用对话框的问题.
                ErrorDialogActivity.showErrorDialog(
                        mContext.getApplicationContext(),
                        R.string.text_invalid_project,
                        message,
                        R.string.dialog_button_dismiss
                );
            }
        }
    }

    private void stopScript(String viewId) {
        ScriptExecution execution = mScriptExecutions.get(viewId);
        if (execution != null) {
            execution.getEngine().forceStop();
            mScriptExecutions.remove(viewId);
        }
    }

    private String getName(JsonObject data) {
        JsonElement element = data.get("name");
        if (element == null || element instanceof JsonNull) {
            return null;
        }
        return element.getAsString();
    }

    /**
     * Optional workspace-relative path (forward slashes) sent by extension 1.1.0+ so that directory structure
     * is preserved when saving to the working directory (VSCode extension issue #37). Returns null if unsafe.
     * zh-CN: 插件 1.1.0+ 发送的相对工作区路径 (正斜杠), 用于按目录结构保存到工作目录 (VSCode 插件 issue #37). 不安全时返回 null.
     */
    private String getRelativePath(JsonObject data) {
        JsonElement element = data.get("path");
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return sanitizeRelativePath(element.getAsString());
    }

    private static String sanitizeRelativePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return null;
            }
        }
        return normalized;
    }

    private static List<String> getDeletedFiles(JsonObject data) {
        List<String> result = new ArrayList<>();
        JsonElement element = data.get("deletedFiles");
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive()) {
                String sanitized = sanitizeRelativePath(item.getAsString());
                if (sanitized != null) {
                    result.add(sanitized);
                }
            }
        }
        return result;
    }

    private static void deleteRelativeFiles(File baseDir, List<String> relativePaths) {
        for (String relativePath : relativePaths) {
            File target = new File(baseDir, relativePath);
            if (target.exists()) {
                boolean deleted = target.isDirectory() ? PFiles.deleteRecursively(target) : target.delete();
                Log.d(TAG, "delete " + target.getPath() + ": " + deleted);
                pruneEmptyParents(baseDir, target.getParentFile());
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void pruneEmptyParents(File baseDir, File dir) {
        while (dir != null && !dir.equals(baseDir) && dir.getPath().startsWith(baseDir.getPath())) {
            String[] children = dir.list();
            if (children == null || children.length > 0) {
                return;
            }
            dir.delete();
            dir = dir.getParentFile();
        }
    }

    private void saveFile(String name, String relativePath, String content) {
        if (TextUtils.isEmpty(name)) {
            name = getUntitledTitle();
        }
        name = PFiles.getName(name);

        // @Comment by SuperMonster003 on Jun 1, 2022.
        //  ! Keep the original extension name of source file.
        //  ! zh-CN: 源文件保留其原始扩展名.
        //  # name = PFiles.getNameWithoutExtension(name);
        //  # if (!name.endsWith(".js")) {
        //  #     name = name + ".js";
        //  # }

        File file = new File(WorkingDirectoryUtils.getPath(), relativePath != null ? relativePath : name);
        PFiles.ensureDir(file.getPath());
        PFiles.write(file, content);
        ViewUtils.showToast(mContext, R.string.text_remote_file_saved_to_local_storage_successfully, true);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void saveProject(String name, String dir, List<String> deletedFiles) {
        if (TextUtils.isEmpty(name)) {
            name = getUntitledTitle();
        }
        name = PFiles.getName(name);

        File toDir = new File(WorkingDirectoryUtils.getPath(), name);

        Observable
                .fromCallable(() -> {
                    copyDir(new File(dir), toDir);
                    // Mirror deletions from the PC (VSCode extension issue #29).
                    // zh-CN: 同步 PC 侧的删除操作 (VSCode 插件 issue #29).
                    deleteRelativeFiles(toDir, deletedFiles);
                    return toDir.getPath();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dest -> {
                            ViewUtils.showToast(mContext, mContext.getString(R.string.text_remote_project_saved_to_local_storage_successfully), true);
                            Explorers.workspace().refreshAll();
                        },
                        err -> {
                            var msg = mContext.getString(R.string.text_failed_to_save_remote_project_to_local_storage);
                            var e = err.getMessage();
                            if (e != null) msg += "\n" + e;

                            ErrorDialogActivity.showErrorDialog(
                                    mContext.getApplicationContext(),
                                    R.string.text_failed,
                                    msg,
                                    R.string.dialog_button_confirm
                            );
                        }
                );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void copyDir(File fromDir, File toDir) throws IOException {
        toDir.mkdirs();
        File[] files = fromDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                copyDir(file, new File(toDir, file.getName()));
            } else {
                try (FileInputStream fis = new FileInputStream(file);
                     FileOutputStream fos = new FileOutputStream(new File(toDir, file.getName()))) {
                    PFiles.write(fis, fos, false);
                } catch (FileNotFoundException e) {
                    throw e;
                }
            }
        }
    }

    private String getUntitledTitle() {
        return "untitled" + "-" + System.currentTimeMillis();
    }

}
