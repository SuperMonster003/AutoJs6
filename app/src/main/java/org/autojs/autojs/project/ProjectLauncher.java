package org.autojs.autojs.project;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.engine.NativeNodeEmbeddedProjectRunner;
import org.autojs.autojs.engine.NodeProjectLaunchDiagnostics;
import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.script.NodeProjectLaunchDescriptor;
import org.autojs.autojs.script.NodeProjectLaunchSource;
import org.autojs.autojs.script.NodeProjectLaunchOptions;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs6.R;

import java.io.File;

public class ProjectLauncher {

    private static final String TAG = "ProjectLauncher";

    @NonNull
    private final String mProjectDir;

    @NonNull
    private final File mMainScriptFile;

    @NonNull
    private final File mProjectRoot;

    private final boolean mNodeProject;

    @Nullable
    private final ProjectConfig mProjectConfig;

    public ProjectLauncher(@NonNull String projectDir) {
        mProjectDir = projectDir;
        mProjectConfig = ProjectConfig.fromProjectDir(projectDir);
        mProjectRoot = new File(mProjectDir);
        File nodeProjectEntry = NativeNodeEmbeddedProjectRunner.resolveProjectEntryFileIfNodeProject(mProjectRoot);
        mNodeProject = nodeProjectEntry != null;
        mMainScriptFile = nodeProjectEntry == null
                ? new File(mProjectDir, mProjectConfig == null ? ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME : mProjectConfig.getMainScriptFileName())
                : nodeProjectEntry;
    }

    public void launch(ScriptEngineService service) {
        if (!mMainScriptFile.exists()) {
            throw new RuntimeException(service.getLanguageContext().getString(R.string.error_project_main_script_file_with_abs_path_does_not_exist, mMainScriptFile.getAbsolutePath()));
        }
        ScriptLaunchSourceFactory.Launch launch = createMainScriptLaunch();
        ExecutionConfig config = launch.createExecutionConfig();
        if (mProjectConfig != null) {
            config.getScriptConfig().setFeatures(mProjectConfig.getFeatures());
        }
        service.execute(launch.getSource(), config);
    }

    @NonNull
    private ScriptLaunchSourceFactory.Launch createMainScriptLaunch() {
        if (!mNodeProject) {
            return ScriptLaunchSourceFactory.createPathLaunch(
                    mMainScriptFile.getPath(),
                    null,
                    mProjectDir,
                    mProjectDir
            );
        }
        NodeProjectLaunchOptions launchOptions;
        try {
            launchOptions = NodeProjectLaunchDescriptor.readOptionsOrDefault(mProjectRoot);
        } catch (RuntimeException e) {
            logNodeProjectLaunchFailure("descriptor", null, null, e);
            throw e;
        }
        ScriptLaunchSourceFactory.Launch launch;
        try {
            launch = ScriptLaunchSourceFactory.createNodeProjectLaunch(
                    mProjectRoot,
                    mMainScriptFile,
                    "main",
                    launchOptions,
                    null,
                    mProjectDir
            );
        } catch (RuntimeException e) {
            logNodeProjectLaunchFailure("launch_source", null, null, e);
            throw e;
        }
        NodeProjectLaunchSource launchSource = launch.getNodeProjectLaunchSource();
        NativeNodeEmbeddedProjectRunner.ProjectRequest request;
        try {
            request = launch.buildNodeProjectRequestOrNull();
        } catch (RuntimeException e) {
            logNodeProjectLaunchFailure("project_request", launchSource, null, e);
            throw e;
        }
        try {
            if (request != null) {
                NativeNodeEmbeddedProjectRunner.INSTANCE.resolveProjectLaunchPlanWithDescriptor(request);
            }
        } catch (RuntimeException e) {
            logNodeProjectLaunchFailure("project_request", launchSource, request, e);
            throw e;
        }
        return launch;
    }

    private void logNodeProjectLaunchFailure(
            @NonNull String stage,
            @Nullable NodeProjectLaunchSource launchSource,
            @Nullable NativeNodeEmbeddedProjectRunner.ProjectRequest request,
            @NonNull Throwable error
    ) {
        Log.e(
                TAG,
                NodeProjectLaunchDiagnostics.launchFailureMarker(
                        stage,
                        mProjectRoot,
                        mMainScriptFile,
                        launchSource,
                        request,
                        error
                ),
                error
        );
    }

}
