package org.autojs.autojs.project;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.engine.NodeProjectRunner;
import org.autojs.autojs.engine.NodeProjectLaunchDiagnostics;
import org.autojs.autojs.engine.ScriptEngineService;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.script.NodeProjectLaunchDescriptor;
import org.autojs.autojs.script.NodeProjectLaunchSource;
import org.autojs.autojs.script.NodeProjectLaunchOptions;
import org.autojs.autojs.script.PythonProjectLaunchAdmission;
import org.autojs.autojs.script.PythonProjectLaunchPolicy;
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
    private final PythonProjectLaunchAdmission mPythonProjectAdmission;

    @Nullable
    private final ProjectConfig mProjectConfig;

    public ProjectLauncher(@NonNull String projectDir) {
        mProjectDir = projectDir;
        mProjectConfig = ProjectConfig.fromProjectDir(projectDir);
        mProjectRoot = new File(mProjectDir);
        File legacyMain = new File(
                mProjectDir,
                mProjectConfig == null
                        ? ProjectConfig.DEFAULT_MAIN_SCRIPT_FILE_NAME
                        : mProjectConfig.getMainScriptFileName()
        );
        mPythonProjectAdmission = PythonProjectLaunchPolicy.inspect(mProjectRoot, legacyMain);
        File nodeProjectEntry = mPythonProjectAdmission == null
                ? NodeProjectRunner.resolveProjectEntryFileIfNodeProject(mProjectRoot)
                : null;
        mNodeProject = nodeProjectEntry != null;
        mMainScriptFile = mPythonProjectAdmission != null
                ? mPythonProjectAdmission.getEntryFile()
                : nodeProjectEntry == null ? legacyMain : nodeProjectEntry;
    }

    public void launch(ScriptEngineService service) {
        launch(service, null);
    }

    public void launchInteractive(ScriptEngineService service, @NonNull Context context) {
        launch(service, context);
    }

    private void launch(ScriptEngineService service, @Nullable Context interactiveContext) {
        if (!mMainScriptFile.exists()) {
            throw new RuntimeException(service.getLanguageContext().getString(R.string.error_project_main_script_file_with_abs_path_does_not_exist, mMainScriptFile.getAbsolutePath()));
        }
        ScriptLaunchSourceFactory.Launch launch = createMainScriptLaunch();
        ExecutionConfig config = launch.createExecutionConfig();
        if (mProjectConfig != null) {
            config.getScriptConfig().setFeatures(mProjectConfig.getFeatures());
        }
        if (mPythonProjectAdmission != null && interactiveContext != null) {
            Scripts.authorizeInteractivePythonExecution(config, interactiveContext);
        }
        service.execute(launch.getSource(), config);
    }

    @NonNull
    private ScriptLaunchSourceFactory.Launch createMainScriptLaunch() {
        if (mPythonProjectAdmission != null) {
            return ScriptLaunchSourceFactory.createPythonProjectLaunch(mPythonProjectAdmission, mProjectDir);
        }
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
        NodeProjectRunner.ProjectRequest request;
        try {
            request = launch.buildNodeProjectRequestOrNull();
        } catch (RuntimeException e) {
            logNodeProjectLaunchFailure("project_request", launchSource, null, e);
            throw e;
        }
        try {
            if (request != null) {
                NodeProjectRunner.INSTANCE.resolveProjectLaunchPlanWithDescriptor(request);
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
            @Nullable NodeProjectRunner.ProjectRequest request,
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
