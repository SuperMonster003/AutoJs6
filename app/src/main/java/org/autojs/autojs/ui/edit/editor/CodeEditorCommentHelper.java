package org.autojs.autojs.ui.edit.editor;

public interface CodeEditorCommentHelper {

    void handle();

    void comment();

    void uncomment();

    boolean isCommented();

    default void toggle() {
        if (isCommented()) {
            uncomment();
        } else {
            comment();
        }
    }

}
