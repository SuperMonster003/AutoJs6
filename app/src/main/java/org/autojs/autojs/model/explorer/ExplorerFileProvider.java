package org.autojs.autojs.model.explorer;

import org.autojs.autojs.core.pref.keys.ExplorerKeys;
import org.autojs.autojs.pio.PFile;

import java.io.FileFilter;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class ExplorerFileProvider implements ExplorerProvider {

    private final FileFilter mFileFilter;

    public ExplorerFileProvider(FileFilter fileFilter) {
        mFileFilter = fileFilter;
    }

    public ExplorerFileProvider() {
        this(null);
    }

    @Override
    public Single<? extends ExplorerPage> getExplorerPage(ExplorerPage page) {
        ExplorerPage parent = page.getParent();
        String path = page.getPath();
        return listFiles(new PFile(path))
                .collectInto(createExplorerPage(path, parent), (p, file) -> {
                    if (file.isDirectory()) {
                        p.addChild(new ExplorerDirPage(file, p));
                    } else {
                        p.addChild(new ExplorerFileItem(file, p));
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    protected ExplorerDirPage createExplorerPage(String path, ExplorerPage parent) {
        return new ExplorerDirPage(path, parent);
    }

    protected Observable<PFile> listFiles(PFile directory) {
        return Observable.fromCallable(() -> {
                    boolean isShowHidden = ExplorerKeys.isHiddenFilesShown();
                    PFile[] files;
                    try {
                        if (mFileFilter == null) {
                            files = directory.listFiles(isShowHidden);
                        } else {
                            files = directory.listFiles(mFileFilter, isShowHidden);
                        }
                    } catch (SecurityException error) {
                        throw new ExplorerDirectoryAccessException(directory.getPath(),
                                ExplorerDirectoryAccessException.Reason.ACCESS_DENIED, error);
                    }
                    if (files == null) {
                        throw ExplorerDirectoryAccessException.forFailedListing(directory.getPath());
                    }
                    return files;
                })
                .flatMap(Observable::fromArray);
    }
}
