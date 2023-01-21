package org.autojs.autojs.project;

import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.script.JavaScriptFileSource;

import java.io.File;

public class ProjectLauncher {

    private final String mProjectDir;
    private final File mMainScriptFile;
    private final ProjectConfig mProjectConfig;

    public ProjectLauncher(String projectDir) {
        mProjectDir = projectDir;
        mProjectConfig = ProjectConfig.fromProjectDir(projectDir);
        mMainScriptFile = new File(mProjectDir, mProjectConfig.getMainScriptFile());
    }

    public void launch(ScriptEngineService service) {
        ExecutionConfig config = new ExecutionConfig();
        config.setWorkingDirectory(mProjectDir);
        config.getScriptConfig().setFeatures(mProjectConfig.getFeatures());
        service.execute(new JavaScriptFileSource(mMainScriptFile), config);
    }

}
