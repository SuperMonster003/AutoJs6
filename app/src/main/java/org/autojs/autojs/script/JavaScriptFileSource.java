package org.autojs.autojs.script;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on Apr 2, 2017.
 */
public class JavaScriptFileSource extends JavaScriptSource {

    private static final String PROJECT_CONFIG_NAME = "project.json";
    private static final Pattern PROJECT_TYPE_PATTERN = Pattern.compile(
            "\"(?:type|projectType)\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final File mFile;
    private String mScript;
    private String mEngineName;
    private Boolean mNodeProjectType;
    private File mNearestProjectConfigFile;
    private boolean mNearestProjectConfigFileResolved = false;
    private boolean mCustomsName = false;

    public JavaScriptFileSource(File file) {
        super(PFiles.getNameWithoutExtension(file.getName()));
        mFile = file;
    }

    public JavaScriptFileSource(@NonNull String path) {
        this(new File(path));
    }

    public JavaScriptFileSource(@NonNull String name, File file) {
        super(name);
        mCustomsName = true;
        mFile = file;
    }

    @NonNull
    @Override
    public String getScript() {
        if (mScript == null)
            mScript = PFiles.read(mFile);
        return mScript;
    }

    @Override
    protected int parseExecutionMode() {
        short flags = EncryptedScriptFileHeader.getHeaderFlags(mFile);
        if (flags == EncryptedScriptFileHeader.FLAG_INVALID_FILE) {
            int mode = super.parseExecutionMode();
            if (isNodeJsExtension()) {
                mode |= EXECUTION_MODE_NODEJS;
            }
            return mode;
        }
        return flags;
    }

    @Override
    public String getEngineName() {
        if (mEngineName != null) {
            return mEngineName;
        }
        int executionMode = getExecutionMode();
        if ((executionMode & EXECUTION_MODE_NODEJS) != 0) {
            return mEngineName = ENGINE_NODEJS;
        }
        if ((executionMode & EXECUTION_MODE_RHINO) != 0) {
            return mEngineName = ENGINE;
        }
        return mEngineName = isNodeProjectType() ? ENGINE_NODEJS : ENGINE;
    }

    @Override
    public Reader getScriptReader() {
        try {
            return new FileReader(mFile);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    public File getFile() {
        return mFile;
    }

    @Nullable
    public File getNodeProjectRoot() {
        if (!isNodeProjectType()) {
            return null;
        }
        File projectConfig = findNearestProjectConfigFile();
        if (projectConfig == null) {
            return null;
        }
        File parent = projectConfig.getParentFile();
        if (parent == null) {
            return null;
        }
        try {
            return parent.getCanonicalFile();
        } catch (Exception ignored) {
            return parent.getAbsoluteFile();
        }
    }

    @NonNull
    @Override
    public String toString() {
        if (mCustomsName) {
            return super.toString();
        }
        return mFile.toString();
    }

    private boolean isNodeJsExtension() {
        return JavaScriptSource.isNodeJsFileName(getSemanticFile().getName());
    }

    private boolean isNodeProjectType() {
        if (mNodeProjectType != null) {
            return mNodeProjectType;
        }
        File projectConfig = findNearestProjectConfigFile();
        if (projectConfig == null) {
            return mNodeProjectType = false;
        }
        String type = readProjectType(projectConfig);
        return mNodeProjectType = "node".equalsIgnoreCase(type);
    }

    @Nullable
    private String readProjectType(File projectConfigFile) {
        try {
            String json = PFiles.read(projectConfigFile);
            Matcher matcher = PROJECT_TYPE_PATTERN.matcher(json);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (value != null) {
                    return value.trim();
                }
            }
        } catch (Exception ignored) {
            /* Ignored. */
        }
        return null;
    }

    @Nullable
    private File findNearestProjectConfigFile() {
        if (mNearestProjectConfigFileResolved) {
            return mNearestProjectConfigFile;
        }
        mNearestProjectConfigFileResolved = true;
        File dir = getSemanticFile().getParentFile();
        while (dir != null) {
            File projectConfig = new File(dir, PROJECT_CONFIG_NAME);
            if (projectConfig.isFile()) {
                return mNearestProjectConfigFile = projectConfig;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    @NonNull
    private File getSemanticFile() {
        String overridden = getOverriddenFullPath();
        if (overridden != null && !overridden.isBlank()) {
            return new File(overridden);
        }
        return mFile;
    }
}
