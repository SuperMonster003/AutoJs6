package org.autojs.autojs.model.explorer;

import androidx.annotation.NonNull;

import org.autojs.autojs.pio.PFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public class ExplorerDirPage extends ExplorerFileItem implements ExplorerPage {

    private List<ExplorerItem> mChildren = Collections.emptyList();

    public ExplorerDirPage(PFile file, ExplorerPage parent) {
        super(file, parent);
    }

    public ExplorerDirPage(String path, ExplorerPage parent) {
        super(path, parent);
    }

    public ExplorerDirPage(File file, ExplorerPage parent) {
        super(file, parent);
    }

    public void copyChildren(ExplorerPage g) {
        ensureChildListWritable();
        mChildren.clear();
        mChildren.addAll(((ExplorerDirPage) g).mChildren);
    }

    private void ensureChildListWritable() {
        if (mChildren == Collections.EMPTY_LIST) {
            mChildren = new ArrayList<>();
        }
    }

    @Override
    public ExplorerFileItem rename(String newName) {
        return new ExplorerDirPage(getFile().renameTo(newName), getParent());
    }

    protected int indexOf(ExplorerItem child){
        int i = 0;
        for(ExplorerItem item : mChildren){
            if(item.getPath().equals(child.getPath())){
                return i;
            }
            i++;
        }
        return -1;
    }

    public void updateChild(ExplorerItem oldItem, ExplorerItem newItem) {
        int i = indexOf(oldItem);
        if (i < 0) {
            return;
        }
        mChildren.set(i, newItem);
    }

    public void removeChild(ExplorerItem item) {
        int i = indexOf(item);
        if(i < 0){
            return;
        }
        mChildren.remove(i);
    }

    public void addChild(ExplorerItem item) {
        ensureChildListWritable();
        mChildren.add(item);
    }

    @NonNull
    @Override
    public Iterator<ExplorerItem> iterator() {
        return mChildren.iterator();
    }

    @Override
    public void forEach(@NonNull Consumer<? super ExplorerItem> action) {
        mChildren.forEach(action);
    }

    @NonNull
    @Override
    public Spliterator<ExplorerItem> spliterator() {
        return mChildren.spliterator();
    }

    public static ExplorerDirPage createRoot(String path){
        return new ExplorerDirPage(path, null);
    }

    @NonNull
    public static ExplorerPage createRoot(File directory) {
        return new ExplorerDirPage(directory, null);
    }
}
