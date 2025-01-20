package org.autojs.autojs.project;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs6.R;

import java.io.File;

public class ProjectLauncher {

    @NonNull
    private final String mProjectDir;

    @NonNull
    private final File mMainScriptFile;

    @Nullable
    private final ProjectConfig mProjectConfig;

    public ProjectLauncher(@NonNull String projectDir) {
        mProjectDir = projectDir;
        mProjectConfig = ProjectConfig.fromProjectDir(projectDir);
        mMainScriptFile = new File(mProjectDir, mProjectConfig == null ? ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME : mProjectConfig.getMainScriptFileName());
    }

    public void launch(ScriptEngineService service) {
        ExecutionConfig config = new ExecutionConfig();
        config.setWorkingDirectory(mProjectDir);
        if (mProjectConfig != null) {
            config.getScriptConfig().setFeatures(mProjectConfig.getFeatures());
        }
        if (!mMainScriptFile.exists()) {
            throw new RuntimeException(service.getLanguageContext().getString(R.string.error_project_main_script_file_with_abs_path_does_not_exist, mMainScriptFile.getAbsolutePath()));
        }
        service.execute(new JavaScriptFileSource(mMainScriptFile), config);
    }

}
