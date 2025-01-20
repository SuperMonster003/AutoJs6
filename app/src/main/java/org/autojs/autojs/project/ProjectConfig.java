package org.autojs.autojs.project;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import org.autojs.autojs.apkbuilder.keystore.KeyStore;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.util.JsonUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Stardust on Jan 24, 2018.
 */
public class ProjectConfig {

    public static final String CONFIG_FILE_NAME = "project.json";

    private static final Gson sGson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @SerializedName("name")
    private String mName;

    @SerializedName("versionName")
    private String mVersionName;

    @SerializedName("versionCode")
    private int mVersionCode = -1;

    @SerializedName("packageName")
    private String mPackageName;

    @SerializedName("main")
    private String mMainScriptFile;

    @Nullable
    @SerializedName(value = "assets", alternate = {"asset", "assetList"})
    private List<String> mAssets = new ArrayList<>();

    @SerializedName("launchConfig")
    private LaunchConfig mLaunchConfig = new LaunchConfig();

    @SerializedName("build")
    private BuildInfo mBuildInfo = new BuildInfo();

    @SerializedName("icon")
    private String mIconPath;

    private transient Callable<Bitmap> mIconBitmapGetter;

    @Nullable
    @SerializedName(value = "abis", alternate = {"abi", "abiList"})
    private List<String> mAbis = new ArrayList<>();

    @Nullable
    @SerializedName(value = "libs", alternate = {"lib", "libList"})
    private List<String> mLibs = new ArrayList<>();

    @SerializedName("permissions")
    private List<String> mPermissions = new ArrayList<>();

    @SerializedName("signatureSchemes")
    private String mSignatureSchemes = "V1 + V2";

    @Nullable
    private transient KeyStore mKeyStore = null;

    @SerializedName("scripts")
    private final Map<String, ScriptConfig> mScriptConfigs = new HashMap<>();

    @SerializedName(value = "useFeatures", alternate = {"useFeature", "useFeatureList"})
    private List<String> mFeatures = new ArrayList<>();

    @SerializedName("ignoredDirs")
    private final List<File> mIgnoredDirs = new ArrayList<>();

    @Nullable
    private transient String mSourcePath = null;

    public static ProjectConfig fromJson(String json) {
        if (json == null) {
            return null;
        }
        ProjectConfig config = sGson.fromJson(json, ProjectConfig.class);
        if (!isValid(config)) {
            return null;
        }
        return config;
    }

    private static boolean isValid(ProjectConfig config) {
        if (TextUtils.isEmpty(config.getName())) {
            return false;
        }
        if (TextUtils.isEmpty(config.getPackageName())) {
            return false;
        }
        if (TextUtils.isEmpty(config.getVersionName())) {
            return false;
        }
        if (TextUtils.isEmpty(config.getMainScriptFile())) {
            return false;
        }
        return config.getVersionCode() != -1;
    }

    public static ProjectConfig fromAssets(Context context, String path) {
        try {
            return fromJson(PFiles.read(context.getAssets().open(path)));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static ProjectConfig fromFile(String path) {
        String fileContents = null;
        try {
            fileContents = PFiles.read(path);
            return fromJson(fileContents);
        } catch (Exception e1) {
            if (fileContents == null) return null;
            try {
                return fromJson(JsonUtils.repairJson(fileContents));
            } catch (Exception e2) {
                return tryReadCrucialData(fileContents, path);
            }
        }
    }

    private static ProjectConfig tryReadCrucialData(String s, String jsonFilePath) {
        ProjectConfig projectConfig = new ProjectConfig();
        LaunchConfig launchConfig = new LaunchConfig();
        BuildInfo buildInfo = new BuildInfo();

        Pattern namePattern = Pattern.compile(stringPattern("name"));
        Pattern versionNamePattern = Pattern.compile(stringPattern("versionName"));
        Pattern versionCodePattern = Pattern.compile(numberPattern("versionCode"));
        Pattern packageNamePattern = Pattern.compile(stringPattern("packageName"));
        Pattern mainPattern = Pattern.compile(stringPattern("main"));
        Pattern iconPattern = Pattern.compile(stringPattern("icon"));

        Pattern assetsPattern = Pattern.compile(listPattern("asset"));
        Pattern abisPattern = Pattern.compile(listPattern("abi"));
        Pattern libsPattern = Pattern.compile(listPattern("lib"));
        Pattern useFeaturesPattern = Pattern.compile(listPattern("useFeature"));

        Pattern buildTimePattern = Pattern.compile(numberPattern("buildTime"));
        Pattern buildNumberPattern = Pattern.compile(numberPattern("buildNumber"));
        Pattern buildIdPattern = Pattern.compile(stringPattern("buildId"));

        Pattern launchConfigHideLogsPattern = Pattern.compile(booleanPattern("hideLogs"));
        Pattern launchConfigLogsVisiblePattern = Pattern.compile(booleanPattern("logsVisible"));

        Pattern launchConfigDisplaySplashPattern = Pattern.compile(booleanPattern("displaySplash"));
        Pattern launchConfigSplashVisiblePattern = Pattern.compile(booleanPattern("splashVisible"));

        setFieldIfMatches(namePattern, s, projectConfig::setName);
        setFieldIfMatches(versionNamePattern, s, projectConfig::setVersionName);
        setFieldForIntIfMatches(versionCodePattern, s, projectConfig::setVersionCode);
        setFieldIfMatches(packageNamePattern, s, projectConfig::setPackageName);
        setFieldIfMatches(mainPattern, s, projectConfig::setMainScriptFile);
        setFieldIfMatches(iconPattern, s, projectConfig::setIconPath);

        setListIfMatches(assetsPattern, s, projectConfig::setAssets);
        setListIfMatches(abisPattern, s, projectConfig::setAbis);
        setListIfMatches(libsPattern, s, projectConfig::setLibs);
        setListIfMatches(useFeaturesPattern, s, projectConfig::setFeatures);

        setFieldForIntIfMatches(buildTimePattern, s, buildInfo::setBuildTime);
        setFieldForIntIfMatches(buildNumberPattern, s, buildInfo::setBuildNumber);
        setFieldIfMatches(buildIdPattern, s, buildInfo::setBuildId);

        setFieldForBooleanIfMatches(launchConfigHideLogsPattern, s, value -> launchConfig.setLogsVisible(!value));
        setFieldForBooleanIfMatches(launchConfigLogsVisiblePattern, s, launchConfig::setLogsVisible); /* 优先. */

        setFieldForBooleanIfMatches(launchConfigDisplaySplashPattern, s, launchConfig::setSplashVisible);
        setFieldForBooleanIfMatches(launchConfigSplashVisiblePattern, s, launchConfig::setSplashVisible); /* 优先. */

        if (projectConfig.getName() == null || projectConfig.getName().isBlank()) {
            if (jsonFilePath.endsWith(CONFIG_FILE_NAME)) {
                File parentFile = new File(jsonFilePath).getParentFile();
                if (parentFile != null) {
                    projectConfig.setName(parentFile.getName());
                }
            }
        }

        projectConfig.setBuildInfo(buildInfo);
        projectConfig.setLaunchConfig(launchConfig);

        return projectConfig;
    }

    @NotNull
    @Language("RegExp")
    private static String listPattern(String name) {
        return "\"" + parseNamePattern(name) + "(s|List)?\"\\s*:\\s*\\[([^\"]*)]";
    }

    @NotNull
    @Language("RegExp")
    private static String numberPattern(String name) {
        return "\"" + parseNamePattern(name) + "\"\\s*:\\s*\"?(\\d+)\"?";
    }

    @NotNull
    @Language("RegExp")
    private static String booleanPattern(String name) {
        return "\"" + parseNamePattern(name) + "\"\\s*:\\s*\"?(true|false)\"?";
    }

    @NotNull
    @Language("RegExp")
    private static String stringPattern(String name) {
        return "\"" + parseNamePattern(name) + "\"\\s*:\\s*\"((?:[^\"]|(?<=\\\\)\")*?)(?<!\\\\)\"";
    }

    @NotNull
    private static String parseNamePattern(String name) {
        return Pattern.quote(name.replaceAll("(?<=[a-z])([A-Z]+)", "(?:$1|_$1)"));
    }

    private static void setFieldIfMatches(Pattern pattern, String s, java.util.function.Consumer<String> setter) {
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            setter.accept(matcher.group(1));
        }
    }

    private static void setFieldForIntIfMatches(Pattern pattern, String s, java.util.function.IntConsumer setter) {
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            setter.accept(Integer.parseInt(matcher.group(1)));
        }
    }

    private static void setFieldForBooleanIfMatches(Pattern pattern, String s, java.util.function.Consumer<Boolean> setter) {
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            setter.accept(Boolean.getBoolean(matcher.group(1)));
        }
    }

    private static void setListIfMatches(Pattern pattern, String s, java.util.function.Consumer<List<String>> setter) {
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            String content = matcher.group(1);
            if (content == null) {
                setter.accept(Collections.emptyList());
            } else {
                List<String> list = Arrays.asList(content.split("\\s*,\\s*"));
                setter.accept(list);
            }
        }
    }

    public static boolean isProject(ExplorerPage page) {
        // @Hint by SuperMonster003 on Dec 2, 2024.
        //  ! It is considered a valid project regardless of whether project.json
        //  ! contains the necessary information or can be parsed correctly.
        //  ! zh-CN: 无论 project.json 是否包含必要信息或是否可以正常解析, 都认为是一个有效项目.
        //  !
        //  # return fromProjectDir(page.getPath()) != null;
        String path = page.getPath();
        String pathname = configFileOfDir(path);
        return new File(pathname).exists();
    }

    @Nullable
    public static ProjectConfig fromProjectDir(String path) {
        return fromFile(configFileOfDir(path));
    }

    public static String configFileOfDir(String projectDir) {
        return PFiles.join(projectDir, CONFIG_FILE_NAME);
    }

    public BuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    public void setBuildInfo(BuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    public String getName() {
        return mName;
    }

    public ProjectConfig setName(String name) {
        mName = name;
        return this;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public ProjectConfig setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public ProjectConfig setVersionCode(int versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public ProjectConfig setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    @NonNull
    public String getMainScriptFile() {
        return mMainScriptFile != null ? mMainScriptFile : "main.js";
    }

    public ProjectConfig setMainScriptFile(String mainScriptFile) {
        mMainScriptFile = mainScriptFile;
        return this;
    }

    public Map<String, ScriptConfig> getScriptConfigs() {
        return mScriptConfigs;
    }

    public List<String> getAssets() {
        if (mAssets == null) {
            mAssets = Collections.emptyList();
        }
        return mAssets;
    }

    public boolean addAsset(String assetRelativePath) {
        if (mAssets == null) {
            mAssets = new ArrayList<>();
        }
        for (String asset : mAssets) {
            if (new File(asset).equals(new File(assetRelativePath))) {
                return false;
            }
        }
        mAssets.add(assetRelativePath);
        return true;
    }

    public void setAssets(List<String> assets) {
        mAssets = assets;
    }

    public LaunchConfig getLaunchConfig() {
        return mLaunchConfig;
    }

    public void setLaunchConfig(@NonNull LaunchConfig launchConfig) {
        mLaunchConfig = launchConfig;
    }

    public String toJson() {
        return sGson.toJson(this);
    }

    public String getIconPath() {
        return mIconPath;
    }

    public ProjectConfig setIconPath(String iconPath) {
        mIconPath = iconPath;
        mIconBitmapGetter = () -> BitmapFactory.decodeFile(iconPath);
        return this;
    }

    public Callable<Bitmap> getIconBitmapGetter() {
        return mIconBitmapGetter;
    }

    public ProjectConfig setIconGetter(@Nullable Callable<Bitmap> getter) {
        mIconBitmapGetter = getter;
        return this;
    }

    public List<String> getAbis() {
        if (mAbis == null) {
            setAbis(Collections.emptyList());
        }
        return mAbis;
    }

    public ProjectConfig setAbis(@Nullable List<String> abis) {
        mAbis = abis;
        return this;
    }

    public List<String> getLibs() {
        if (mLibs == null) {
            setLibs(Collections.emptyList());
        }
        return mLibs;
    }

    public ProjectConfig setLibs(@Nullable List<String> libs) {
        mLibs = libs;
        return this;
    }

    public String getBuildDir() {
        return "build";
    }

    @NonNull
    public List<String> getFeatures() {
        if (mFeatures == null) {
            mFeatures = Collections.emptyList();
        }
        return mFeatures;
    }

    public void setFeatures(List<String> features) {
        mFeatures = features;
    }

    public ScriptConfig getScriptConfig(String path) {
        ScriptConfig config = mScriptConfigs.get(path);
        if (config == null) {
            config = new ScriptConfig();
        }
        if (mFeatures.isEmpty()) {
            return config;
        }
        ArrayList<String> features = new ArrayList<>(config.getFeatures());
        for (String feature : mFeatures) {
            if (!features.contains(feature)) {
                features.add(feature);
            }
        }
        config.setFeatures(features);
        return config;
    }

    public List<File> getIgnoredDirs() {
        return mIgnoredDirs;
    }

    public ProjectConfig ignoredDir(File ignoredDir) {
        mIgnoredDirs.add(ignoredDir);
        return this;
    }

    @Nullable
    public String getSourcePath() {
        return mSourcePath;
    }

    public ProjectConfig setSourcePath(@Nullable String sourcePath) {
        mSourcePath = sourcePath;
        return this;
    }

    public List<String> getPermissions() {
        return mPermissions;
    }

    public ProjectConfig setPermissions(List<String> permissions) {
        mPermissions = permissions;
        return this;
    }

    public String getSignatureSchemes() {
        return mSignatureSchemes;
    }

    public ProjectConfig setSignatureSchemes(String signatureSchemes) {
        mSignatureSchemes = signatureSchemes;
        return this;
    }

    @Nullable
    public KeyStore getKeyStore() {
        return mKeyStore;
    }

    public ProjectConfig setKeyStore(@Nullable KeyStore keyStore) {
        mKeyStore = keyStore;
        return this;
    }
}
