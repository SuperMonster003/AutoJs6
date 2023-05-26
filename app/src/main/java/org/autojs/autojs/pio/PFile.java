package org.autojs.autojs.pio;

import androidx.annotation.NonNull;

import org.autojs.autojs.util.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Stardust on 2017/10/19.
 */
public class PFile extends File {

    private String mSimplifyPath;
    private String mSimplifiedName;
    private String mExtension;

    public PFile(@NonNull String pathname) {
        super(pathname);
        init();
    }

    public PFile(String parent, @NonNull String child) {
        super(parent, child);
        init();
    }

    public PFile(File parent, @NonNull String child) {
        super(parent, child);
        init();
    }

    public PFile(@NonNull URI uri) {
        super(uri);
        init();
    }

    public static CharSequence getFullDateString(long date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return dateFormat.format(new Date(date));
    }


    private void init() {
        if (isDirectory()) {
            mSimplifiedName = getName();
        } else {
            mSimplifiedName = PFiles.getNameWithoutExtension(getName());
        }
        mSimplifyPath = PFiles.getElegantPath(getPath());
    }


    @NonNull
    public PFile renameTo(String newName) {
        PFile newFile = new PFile(getParent(), newName);
        if (renameTo(newFile)) {
            return newFile;
        } else {
            return this;
        }
    }

    @NonNull
    public PFile renameWithoutExt(String newName) {
        PFile newFile = isDirectory() ? new PFile(getParent(), newName) :
                new PFile(getParent(), newName + "." + getExtension());
        if (renameTo(newFile)) {
            return newFile;
        } else {
            return this;
        }
    }

    public String getExtension() {
        if (mExtension == null) {
            mExtension = PFiles.getExtension(getName());
        }
        return mExtension;
    }

    public String getSimplifiedPath() {
        return mSimplifyPath;
    }

    @Override
    public PFile getParentFile() {
        String p = this.getParent();
        if (p == null) {
            return null;
        }
        return new PFile(p);
    }

    @Override
    public PFile[] listFiles() {
        return listFiles((FilenameFilter) null, true);
    }

    @Override
    public PFile[] listFiles(FilenameFilter filter) {
        return listFiles(filter, true);
    }

    @Override
    public PFile[] listFiles(FileFilter filter) {
        return listFiles(filter, true);
    }

    public PFile[] listFiles(boolean isShowHidden) {
        return listFiles((FilenameFilter) null, isShowHidden);
    }

    public PFile[] listFiles(FilenameFilter filter, boolean isShowHidden) {
        String[] ss = list();
        if (ss == null) {
            return null;
        }
        ArrayList<PFile> files = new ArrayList<>();
        for (String s : ss) {
            if (canAddHidden(s, isShowHidden) && (filter == null || filter.accept(this, s))) {
                files.add(new PFile(this, s));
            }
        }
        return files.toArray(new PFile[0]);
    }

    public PFile[] listFiles(FileFilter filter, boolean isShowHidden) {
        String[] ss = list();
        if (ss == null) {
            return null;
        }
        ArrayList<PFile> files = new ArrayList<>();
        for (String s : ss) {
            PFile f = new PFile(this, s);
            if (canAddHidden(f, isShowHidden) && (filter == null || filter.accept(f))) {
                files.add(f);
            }
        }
        return files.toArray(new PFile[0]);
    }

    private boolean canAddHidden(PFile file, boolean override) {
        return override || !file.isHidden();
    }

    private boolean canAddHidden(String fileName, boolean override) {
        return override || !fileName.startsWith(".");
    }

    public String getSimplifiedName() {
        return mSimplifiedName;
    }

    public boolean moveTo(PFile to) {
        return renameTo(new File(to, getName()));
    }

    public boolean isProject() {
        return getName().equals(FileUtils.TYPE.PROJECT.getTypeName());
    }

}
