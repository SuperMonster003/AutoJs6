package org.autojs.autojs.runtime.api;

import org.autojs.autojs.pio.PFileInterface;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.tool.Func1;
import org.autojs.autojs.util.EnvironmentUtils;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Created by Stardust on Jan 23, 2018.
 * Modified by SuperMonster003 as of May 26, 2022.
 */
public class Files {

    private final ScriptRuntime mRuntime;

    public Files(ScriptRuntime runtime) {
        mRuntime = runtime;
    }

    // FIXME by Stardust on Oct 16, 2018.
    //  ! Is not correct in sub-directory?
    public String path(String relativePath) {
        String cwd = cwd();
        if (cwd == null || relativePath == null || relativePath.startsWith(File.separator))
            return relativePath;
        File f = new File(cwd);
        String[] paths = relativePath.split(Pattern.quote(File.separator));
        for (String path : paths) {
            if (path.equals("."))
                continue;
            if (path.equals("..")) {
                f = f.getParentFile();
                continue;
            }
            f = new File(f, path);
        }
        String path = f.getPath();
        return relativePath.endsWith(File.separator) ? path + File.separator : path;
    }

    public String cwd() {
        return mRuntime.engines.myEngine().cwd();
    }

    public PFileInterface open(String path, String mode, String encoding, int bufferSize) {
        return PFiles.open(path(path), mode, encoding, bufferSize);
    }

    public Object open(String path, String mode, String encoding) {
        return PFiles.open(path(path), mode, encoding);
    }

    public Object open(String path, String mode) {
        return PFiles.open(path(path), mode);
    }

    public Object open(String path) {
        return PFiles.open(path(path));
    }

    public boolean create(String path) {
        return PFiles.create(path(path));
    }

    public boolean createIfNotExists(String path) {
        return PFiles.createIfNotExists(path(path));
    }

    public boolean createWithDirs(String path) {
        return PFiles.createWithDirs(path(path));
    }

    public boolean exists(String path) {
        return PFiles.exists(path(path));
    }

    public boolean ensureDir(String path) {
        return PFiles.ensureDir(path(path));
    }

    public String read(String path, String encoding) {
        return PFiles.read(path(path), encoding);
    }


    public String read(String path) {
        return PFiles.read(path(path));
    }

    public String readAssets(String path, String encoding) {
        try {
            return PFiles.read(mRuntime.getUiHandler().getApplicationContext().getAssets().open(path), encoding);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String readAssets(String path) {
        return readAssets(path, "UTF-8");
    }

    public byte[] readBytes(String path) {
        return PFiles.readBytes(path(path));
    }

    public void write(String path, String text) {
        PFiles.write(path(path), text);
    }

    public void write(String path, String text, String encoding) {
        PFiles.write(path(path), text, encoding);
    }

    public void append(String path, String text) {
        PFiles.append(path(path), text);
    }

    public void append(String path, String text, String encoding) {
        PFiles.append(path(path), text, encoding);
    }

    public void appendBytes(String path, byte[] bytes) {
        PFiles.appendBytes(path(path), bytes);
    }

    public void writeBytes(String path, byte[] bytes) {
        PFiles.writeBytes(path(path), bytes);
    }

    public boolean copy(String pathFrom, String pathTo) {
        return PFiles.copy(path(pathFrom), path(pathTo));
    }

    public boolean renameWithoutExtension(String path, String newName) {
        return PFiles.renameWithoutExtension(path(path), newName);
    }

    public boolean rename(String path, String newName) {
        return PFiles.rename(path(path), newName);
    }

    public boolean move(String path, String newPath) {
        return PFiles.move(path(path), newPath);
    }

    public String getExtension(String fileName) {
        return PFiles.getExtension(fileName);
    }

    public String getName(String filePath) {
        return PFiles.getName(filePath);
    }

    public String getNameWithoutExtension(String filePath) {
        return PFiles.getNameWithoutExtension(filePath);
    }

    public boolean remove(String path) {
        return PFiles.remove(path(path));
    }

    public boolean removeDir(String path) {
        return PFiles.removeDir(path(path));
    }

    public String getSdcardPath() {
        return EnvironmentUtils.getExternalStoragePath();
    }

    public String[] listDir(String path) {
        return PFiles.listDir(path(path));
    }

    public String[] listDir(String path, Func1<String, Boolean> filter) {
        return PFiles.listDir(path(path), filter);
    }

    public boolean isFile(String path) {
        return PFiles.isFile(path(path));
    }

    public boolean isDir(String path) {
        return PFiles.isDir(path(path));
    }

    public boolean isEmptyDir(String path) {
        return PFiles.isEmptyDir(path(path));
    }

    public static String join(String parent, String... child) {
        return PFiles.join(parent, child);
    }

    public String getHumanReadableSize(long bytes) {
        return PFiles.getHumanReadableSize(bytes);
    }

    public String getSimplifiedPath(String path) {
        return PFiles.getElegantPath(path);
    }

}
