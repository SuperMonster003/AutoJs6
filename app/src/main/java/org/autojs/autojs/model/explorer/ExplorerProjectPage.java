package org.autojs.autojs.model.explorer;

import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.project.ProjectConfig;

import java.io.File;

public class ExplorerProjectPage extends ExplorerDirPage {
    private final ProjectConfig mProjectConfig;

    public ExplorerProjectPage(PFile file, ExplorerPage parent, ProjectConfig projectConfig) {
        super(file, parent);
        mProjectConfig = projectConfig;
    }

    public ExplorerProjectPage(String path, ExplorerPage parent, ProjectConfig projectConfig) {
        super(path, parent);
        mProjectConfig = projectConfig;
    }

    public ExplorerProjectPage(File file, ExplorerPage parent, ProjectConfig projectConfig) {
        super(file, parent);
        mProjectConfig = projectConfig;
    }

    public ProjectConfig getProjectConfig() {
        return mProjectConfig;
    }

    @Override
    public ExplorerFileItem rename(String newName) {
        return new ExplorerProjectPage(getFile().renameTo(newName), getParent(), mProjectConfig);
    }

}
