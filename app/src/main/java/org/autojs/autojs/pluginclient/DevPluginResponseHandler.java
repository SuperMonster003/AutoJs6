package org.autojs.autojs.pluginclient;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.autojs.autojs.AutoJs;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.io.Zip;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.project.ProjectLauncher;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.util.MD5Utils;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs.util.WorkingDirectoryUtils;
import org.autojs.autojs6.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on 2017/5/11.
 */
public class DevPluginResponseHandler implements Handler {

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
                        .handler("save", data -> {
                            String script = data.get("script").getAsString();
                            String name = getName(data);
                            saveFile(name, script);
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
                            saveProject(data.get("name").getAsString(), data.get("dir").getAsString());
                            return true;
                        }));
    }

    @Override
    public boolean handle(JsonObject data) {
        return mRouter.handle(data);
    }

    public Observable<File> handleBytes(JsonObject data, JsonSocket.Bytes bytes) {
        String id = data.get("data").getAsJsonObject().get("id").getAsString();
        String idMd5 = MD5Utils.md5(id);
        return Observable
                .fromCallable(() -> {
                    File dir = new File(mCacheDir, idMd5);
                    byte[] byteArray = bytes.byteString.toByteArray();
                    Zip.unzip(new ByteArrayInputStream(byteArray), dir);
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
            ViewUtils.showToast(mContext, R.string.text_invalid_project, true);
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
        if (element instanceof JsonNull) {
            return null;
        }
        return element.getAsString();
    }

    private void saveFile(String name, String content) {
        if (TextUtils.isEmpty(name)) {
            name = getUntitledTitle();
        }
        name = PFiles.getName(name);

        // @Comment by SuperMonster003 on Jun 1, 2022.
        //  ! Keep the original extension name of source file.
        // name = PFiles.getNameWithoutExtension(name);
        // if (!name.endsWith(".js")) {
        //     name = name + ".js";
        // }

        File file = new File(WorkingDirectoryUtils.getPath(), name);
        PFiles.ensureDir(file.getPath());
        PFiles.write(file, content);
        ViewUtils.showToast(mContext, R.string.text_remote_file_saved_to_local_storage_successfully, true);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void saveProject(String name, String dir) {
        if (TextUtils.isEmpty(name)) {
            name = getUntitledTitle();
        }
        name = PFiles.getName(name);

        File toDir = new File(WorkingDirectoryUtils.getPath(), name);

        Observable
                .fromCallable(() -> {
                    copyDir(new File(dir), toDir);
                    return toDir.getPath();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        dest -> ViewUtils.showToast(mContext, mContext.getString(R.string.text_remote_project_saved_to_local_storage_successfully), true),
                        err -> {
                            var msg = mContext.getString(R.string.text_failed_to_save_remote_project_to_local_storage);
                            var e = err.getMessage();
                            if (e != null) msg += "\n" + e;

                            new MaterialDialog.Builder(mContext)
                                    .title(R.string.text_failed)
                                    .content(msg)
                                    .positiveText(R.string.dialog_button_confirm)
                                    .show();
                        }
                );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void copyDir(File fromDir, File toDir) throws FileNotFoundException {
        toDir.mkdirs();
        File[] files = fromDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                copyDir(file, new File(toDir, file.getName()));
            } else {
                FileOutputStream fos = new FileOutputStream(new File(toDir, file.getName()));
                PFiles.write(new FileInputStream(file), fos, true);
            }
        }
    }

    private String getUntitledTitle() {
        return "untitled" + "-" + System.currentTimeMillis();
    }

}
