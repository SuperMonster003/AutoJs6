package org.autojs.autojs.model.explorer;

public interface ExplorerPage extends ExplorerItem, Iterable<ExplorerItem> {

    void copyChildren(ExplorerPage page);

    void updateChild(ExplorerItem oldItem, ExplorerItem newItem);

    void removeChild(ExplorerItem item);

    void addChild(ExplorerItem item);

}
