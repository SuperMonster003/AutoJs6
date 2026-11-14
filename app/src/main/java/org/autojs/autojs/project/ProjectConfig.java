package org.autojs.autojs.project;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.annotations.SerializedName;
import org.autojs.autojs.annotation.DeserializedMethodName;
import org.autojs.autojs.annotation.SerializedNameCompatible;
import org.autojs.autojs.annotation.SerializedNameCompatible.With;
import org.autojs.autojs.apkbuilder.keystore.KeyStore;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.util.JsonUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Stardust on Jan 24, 2018.
 * Modified by SuperMonster003 as of Jan 6, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
public class ProjectConfig implements FuzzyDeserializer.OriginalJsonKeyAware {

    public static final String CONFIG_FILE_NAME = "project.json";
    public static final String DEFAULT_MAIN_SCRIPT_FILE_NAME = "main.js";

    private static final String TAG = "ProjectConfig";

    public static final List<String> DEFAULT_PERMISSIONS = Arrays.asList(
            "android.permission.WAKE_LOCK",
            "android.permission.INTERNET",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.SYSTEM_ALERT_WINDOW"
    );

    private static final Gson sGson = new GsonBuilder()
            .registerTypeAdapter(ProjectConfig.class, new FuzzyDeserializer<ProjectConfig>())
            .registerTypeAdapter(LaunchConfig.class, new FuzzyDeserializer<LaunchConfig>())
            .registerTypeAdapter(ScriptConfig.class, new FuzzyDeserializer<ScriptConfig>())
            .registerTypeAdapter(BuildInfo.class, new FuzzyDeserializer<BuildInfo>())
            .setStrictness(Strictness.LENIENT)
            .setPrettyPrinting()
            .create();

    /**
     * Remember original JSON keys to preserve them on serialization (canonicalKey -> originalKey).
     * zh-CN: 记录原始 JSON key, 用于序列化写回时保留原 key (canonicalKey -> originalKey).
     */
    private final transient Map<String, String> mOriginalJsonKeys = new LinkedHashMap<>();

    @SerializedName("excludeDirs")
    @SerializedNameCompatible(with = {
            @With(value = "ignoredDirs", target = {"AutoJs4", "AutoX"}),
            @With(value = "ignore", target = {"Unknown"}),
            @With(value = "excludedDirs"),
    })
    private final List<String> mExcludedDirs = new ArrayList<>();

    @SerializedName("name")
    @SerializedNameCompatible(with = {
            @With(value = "projectName"),
    })
    private String mName;

    @SerializedName("versionName")
    @SerializedNameCompatible(with = {
            @With(value = "version"),
    })
    private String mVersionName;

    @SerializedName("versionCode")
    private int mVersionCode = 1;

    @SerializedName("packageName")
    @SerializedNameCompatible(with = {
            @With(value = "package"),
    })
    private String mPackageName;

    @SerializedName("main")
    @SerializedNameCompatible(with = {
            @With(value = "mainName"),
            @With(value = "mainScript"),
            @With(value = "mainScriptName"),
            @With(value = "mainScriptFile"),
            @With(value = "mainScriptFileName"),
            @With(value = "mainFile"),
            @With(value = "mainFileName"),
    })
    private String mMainScriptFileName = DEFAULT_MAIN_SCRIPT_FILE_NAME;

    @Nullable
    @SerializedName("type")
    @SerializedNameCompatible(with = {
            @With(value = "projectType"),
    })
    private String mProjectType;

    @Nullable
    @SerializedName(value = "assets")
    @SerializedNameCompatible(with = {
            @With(value = "asset"),
            @With(value = "assetList"),
    })
    private List<String> mAssets = new ArrayList<>();

    @SerializedName("launchConfig")
    @SerializedNameCompatible(with = {
            @With(value = "launch"),
    })
    private LaunchConfig mLaunchConfig = new LaunchConfig();

    @SerializedName("build")
    @SerializedNameCompatible(with = {
            @With(value = "buildInfo"),
    })
    private BuildInfo mBuildInfo = new BuildInfo();

    @SerializedName("icon")
    @SerializedNameCompatible(with = {
            @With(value = "iconPath"),
    })
    private String mIconPath;

    private transient Callable<Bitmap> mIconBitmapGetter;

    @Nullable
    @SerializedName(value = "abis")
    @SerializedNameCompatible(with = {
            @With(value = "abi"),
            @With(value = "abiList"),
    })
    private List<String> mAbis = new ArrayList<>();

    @Nullable
    @SerializedName(value = "libs")
    @SerializedNameCompatible(with = {
            @With(value = "lib"),
            @With(value = "libList"),
    })
    private List<String> mLibs = new ArrayList<>();

    @Nullable
    @SerializedName("node")
    private NodeConfig mNodeConfig;

    @Nullable
    @SerializedName("bun")
    private BunConfig mBunConfig;

    /** Optional Agent registration; execution validates its strict schema separately. */
    @Nullable
    @SerializedName("agent")
    private AgentManifest mAgent;

    @SerializedName("permissions")
    @SerializedNameCompatible(with = {
            @With(value = "permission"),
            @With(value = "permissionList"),
    })
    private List<String> mPermissions = new ArrayList<>(DEFAULT_PERMISSIONS);

    @SerializedName("signatureScheme")
    @SerializedNameCompatible(with = {
            @With(value = "signatureSchemes"),
            @With(value = "signature"),
    })
    @DeserializedMethodName(method = "normalizeSignatureScheme", parameterTypes = {String.class})
    private String mSignatureScheme = "V1 + V2";

    @Nullable
    private transient KeyStore mKeyStore = null;

    @SerializedName(value = "useFeatures")
    @SerializedNameCompatible(with = {
            @With(value = "useFeature"),
            @With(value = "useFeatureList"),
            @With(value = "features"),
            @With(value = "feature"),
            @With(value = "featureList"),
    })
    private List<String> mFeatures = new ArrayList<>();

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

    /**
     * Parses the project fields required to execute a project without requiring APK packaging
     * metadata. User-facing script projects may intentionally omit package and version fields;
     * callers that build an APK must continue using {@link #fromJson(String)}.
     */
    @Nullable
    public static ProjectConfig fromJsonForExecution(String json) {
        if (json == null) {
            return null;
        }
        ProjectConfig config = sGson.fromJson(json, ProjectConfig.class);
        return isValidForExecution(config) ? config : null;
    }

    private static boolean isValid(ProjectConfig config) {
        if (!isValidForExecution(config)) {
            return false;
        }
        if (TextUtils.isEmpty(config.getPackageName())) {
            return false;
        }
        if (TextUtils.isEmpty(config.getVersionName())) {
            return false;
        }
        return config.getVersionCode() != -1;
    }

    private static boolean isValidForExecution(ProjectConfig config) {
        return config != null
                && !TextUtils.isEmpty(config.getName())
                && !TextUtils.isEmpty(config.getMainScriptFileName());
    }

    public static ProjectConfig fromAssets(Context context, String path) {
        try {
            return fromJson(PFiles.read(context.getAssets().open(path)));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static ProjectConfig fromFile(File file) {
        return fromFilePath(file.getPath());
    }

    public static ProjectConfig fromFilePath(String path) {
        String fileContents = null;
        try {
            fileContents = PFiles.read(path);
            return fromJson(fileContents);
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                return null;
            }
            throw e;
        } catch (Exception e1) {
            Log.d(TAG, "Failed to read json from file: " + path + " (" + e1.getMessage() + ")");
            if (fileContents == null) return null;
            try {
                ProjectConfig repaired = fromJson(JsonUtils.repairJson(fileContents));
                Log.d(TAG, "Successfully read repaired json from file: " + path);
                return repaired;
            } catch (Exception e2) {
                Log.d(TAG, "Failed to read repaired json from file: " + path + " (" + e2.getMessage() + ")");
                try {
                    ProjectConfig crucialData = tryReadCrucialData(fileContents, path, false);
                    Log.d(TAG, "Successfully read crucial data from file: " + path);
                    return crucialData;
                } catch (Exception e3) {
                    Log.d(TAG, "Failed to read crucial data from file: " + path + " (" + e3.getMessage() + ")");
                    return null;
                }
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static ProjectConfig tryReadCrucialData(String s, String jsonFilePath, boolean detectConflicts) {
        ProjectConfig projectConfig = new ProjectConfig();
        LaunchConfig launchConfig = new LaunchConfig();
        BuildInfo buildInfo = new BuildInfo();

        Pattern namePattern = Pattern.compile(stringPatternWithKeyCapture("name"), Pattern.CASE_INSENSITIVE);
        Pattern versionNamePattern = Pattern.compile(stringPatternWithKeyCapture("versionName"), Pattern.CASE_INSENSITIVE);
        Pattern versionCodePattern = Pattern.compile(numberPatternWithKeyCapture("versionCode"), Pattern.CASE_INSENSITIVE);
        Pattern packageNamePattern = Pattern.compile(stringPatternWithKeyCapture("packageName"), Pattern.CASE_INSENSITIVE);
        Pattern mainPattern = Pattern.compile(stringPatternWithKeyCapture("main"), Pattern.CASE_INSENSITIVE);
        Pattern projectTypePattern = Pattern.compile(stringPatternWithKeyCapture("type"), Pattern.CASE_INSENSITIVE);
        Pattern projectTypeAliasPattern = Pattern.compile(stringPatternWithKeyCapture("projectType"), Pattern.CASE_INSENSITIVE);
        Pattern iconPattern = Pattern.compile(stringPatternWithKeyCapture("icon"), Pattern.CASE_INSENSITIVE);

        Pattern assetsPattern = Pattern.compile(listPatternWithKeyCapture("asset"), Pattern.CASE_INSENSITIVE);
        Pattern abisPattern = Pattern.compile(listPatternWithKeyCapture("abi"), Pattern.CASE_INSENSITIVE);
        Pattern libsPattern = Pattern.compile(listPatternWithKeyCapture("lib"), Pattern.CASE_INSENSITIVE);
        Pattern useFeaturesPattern = Pattern.compile(listPatternWithKeyCapture("useFeature"), Pattern.CASE_INSENSITIVE);

        Pattern permissionsPattern = Pattern.compile(listPatternWithKeyCapture("permission"), Pattern.CASE_INSENSITIVE);
        Pattern signatureSchemePattern = Pattern.compile(stringPatternWithKeyCapture("signatureScheme"), Pattern.CASE_INSENSITIVE);

        Pattern excludeDirsPattern = Pattern.compile(listPatternWithKeyCapture("excludeDir"), Pattern.CASE_INSENSITIVE);
        Pattern excludedDirsPattern = Pattern.compile(listPatternWithKeyCapture("excludedDir"), Pattern.CASE_INSENSITIVE);

        Pattern buildTimePattern = Pattern.compile(numberPatternWithKeyCapture("buildTime"), Pattern.CASE_INSENSITIVE);
        Pattern buildNumberPattern = Pattern.compile(numberPatternWithKeyCapture("buildNumber"), Pattern.CASE_INSENSITIVE);
        Pattern buildIdPattern = Pattern.compile(stringPatternWithKeyCapture("buildId"), Pattern.CASE_INSENSITIVE);

        Pattern launchConfigHideLogsPattern = Pattern.compile(booleanPatternWithKeyCapture("hideLogs"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigLogsVisiblePattern = Pattern.compile(booleanPatternWithKeyCapture("logsVisible"), Pattern.CASE_INSENSITIVE);

        Pattern launchConfigDisplaySplashPattern = Pattern.compile(booleanPatternWithKeyCapture("displaySplash"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigSplashVisiblePattern = Pattern.compile(booleanPatternWithKeyCapture("splashVisible"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigDisplayLauncherPattern = Pattern.compile(booleanPatternWithKeyCapture("displayLauncher"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigLauncherVisiblePattern = Pattern.compile(booleanPatternWithKeyCapture("launcherVisible"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigHideLogShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("hideLogShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigShowLogShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("showLogShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigDisplayLogShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("displayLogShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigLogShortcutVisiblePattern = Pattern.compile(booleanPatternWithKeyCapture("logShortcutVisible"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigHideSettingsShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("hideSettingsShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigShowSettingsShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("showSettingsShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigDisplaySettingsShortcutPattern = Pattern.compile(booleanPatternWithKeyCapture("displaySettingsShortcut"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigSettingsShortcutVisiblePattern = Pattern.compile(booleanPatternWithKeyCapture("settingsShortcutVisible"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigAutoRunOnBootPattern = Pattern.compile(booleanPatternWithKeyCapture("autoRunOnBoot"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigRunOnBootPattern = Pattern.compile(booleanPatternWithKeyCapture("runOnBoot"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigRequiresSharedStoragePattern = Pattern.compile(booleanPatternWithKeyCapture("requiresSharedStorage"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigSlugTextPattern = Pattern.compile(stringPatternWithKeyCapture("slugText"), Pattern.CASE_INSENSITIVE);
        Pattern launchConfigSlugPattern = Pattern.compile(stringPatternWithKeyCapture("slug"), Pattern.CASE_INSENSITIVE);

        setFieldIfMatchesWithKey(namePattern, s, "name", projectConfig::setName, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(versionNamePattern, s, "versionName", projectConfig::setVersionName, projectConfig, detectConflicts);
        setFieldForDoubleIfMatchesWithKey(versionCodePattern, s, "versionCode", versionCode -> projectConfig.setVersionCode((int) versionCode), projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(packageNamePattern, s, "packageName", projectConfig::setPackageName, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(mainPattern, s, "main", projectConfig::setMainScriptFileName, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(projectTypePattern, s, "type", projectConfig::setProjectType, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(projectTypeAliasPattern, s, "type", projectConfig::setProjectType, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(iconPattern, s, "icon", projectConfig::setIconPath, projectConfig, detectConflicts);

        setListIfMatchesWithKey(assetsPattern, s, "assets", projectConfig::setAssets, projectConfig, detectConflicts);
        setListIfMatchesWithKey(abisPattern, s, "abis", projectConfig::setAbis, projectConfig, detectConflicts);
        setListIfMatchesWithKey(libsPattern, s, "libs", projectConfig::setLibs, projectConfig, detectConflicts);
        setListIfMatchesWithKey(useFeaturesPattern, s, "useFeatures", projectConfig::setFeatures, projectConfig, detectConflicts);

        setListIfMatchesWithKey(permissionsPattern, s, "permissions", projectConfig::setPermissions, projectConfig, detectConflicts);
        setFieldIfMatchesWithKey(signatureSchemePattern, s, "signatureScheme", projectConfig::setSignatureScheme, projectConfig, detectConflicts);

        setListIfMatchesWithKey(excludeDirsPattern, s, "excludeDirs", excludeDirs -> excludeDirs.forEach(projectConfig::excludeDir), projectConfig, detectConflicts);
        setListIfMatchesWithKey(excludedDirsPattern, s, "excludeDirs", excludedDirs -> excludedDirs.forEach(projectConfig::excludeDir), projectConfig, detectConflicts);

        setFieldForDoubleIfMatchesWithKey(buildTimePattern, s, "time", buildTime -> buildInfo.setBuildTime((long) buildTime), buildInfo, detectConflicts);
        setFieldForDoubleIfMatchesWithKey(buildNumberPattern, s, "number", buildNumber -> buildInfo.setBuildNumber((long) buildNumber), buildInfo, detectConflicts);
        setFieldIfMatchesWithKey(buildIdPattern, s, "id", buildInfo::setBuildId, buildInfo, detectConflicts);

        setFieldForBooleanIfMatchesWithKey(launchConfigHideLogsPattern, s, "logsVisible", value -> launchConfig.setLogsVisible(!value), launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigLogsVisiblePattern, s, "logsVisible", launchConfig::setLogsVisible, launchConfig, detectConflicts); /* 优先. */

        setFieldForBooleanIfMatchesWithKey(launchConfigDisplaySplashPattern, s, "splashVisible", launchConfig::setSplashVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigSplashVisiblePattern, s, "splashVisible", launchConfig::setSplashVisible, launchConfig, detectConflicts); /* 优先. */
        setFieldForBooleanIfMatchesWithKey(launchConfigDisplayLauncherPattern, s, "launcherVisible", launchConfig::setLauncherVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigLauncherVisiblePattern, s, "launcherVisible", launchConfig::setLauncherVisible, launchConfig, detectConflicts); /* 优先. */
        setFieldForBooleanIfMatchesWithKey(launchConfigHideLogShortcutPattern, s, "logShortcutVisible", value -> launchConfig.setLogShortcutVisible(!value), launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigShowLogShortcutPattern, s, "logShortcutVisible", launchConfig::setLogShortcutVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigDisplayLogShortcutPattern, s, "logShortcutVisible", launchConfig::setLogShortcutVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigLogShortcutVisiblePattern, s, "logShortcutVisible", launchConfig::setLogShortcutVisible, launchConfig, detectConflicts); /* 优先. */
        setFieldForBooleanIfMatchesWithKey(launchConfigHideSettingsShortcutPattern, s, "settingsShortcutVisible", value -> launchConfig.setSettingsShortcutVisible(!value), launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigShowSettingsShortcutPattern, s, "settingsShortcutVisible", launchConfig::setSettingsShortcutVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigDisplaySettingsShortcutPattern, s, "settingsShortcutVisible", launchConfig::setSettingsShortcutVisible, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigSettingsShortcutVisiblePattern, s, "settingsShortcutVisible", launchConfig::setSettingsShortcutVisible, launchConfig, detectConflicts); /* 优先. */
        setFieldForBooleanIfMatchesWithKey(launchConfigAutoRunOnBootPattern, s, "runOnBoot", launchConfig::setRunOnBoot, launchConfig, detectConflicts);
        setFieldForBooleanIfMatchesWithKey(launchConfigRunOnBootPattern, s, "runOnBoot", launchConfig::setRunOnBoot, launchConfig, detectConflicts); /* 优先. */
        setFieldForBooleanIfMatchesWithKey(launchConfigRequiresSharedStoragePattern, s, "requiresSharedStorage", launchConfig::setRequiresSharedStorage, launchConfig, detectConflicts);
        setFieldIfMatchesWithKey(launchConfigSlugTextPattern, s, "slug", launchConfig::setSlug, launchConfig, detectConflicts);
        setFieldIfMatchesWithKey(launchConfigSlugPattern, s, "slug", launchConfig::setSlug, launchConfig, detectConflicts); /* 优先. */

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
    private static String listPatternWithKeyCapture(String name) {
        // Capture both the matched key name and the list content.
        // zh-CN: 同时捕获命中的 key 名称与数组内容.
        return "\"(" + parseNamePattern(name) + "(?:s|list)?)\"\\s*:\\s*\\[\\s*((?:\"(?:\\\\.|[^\"\\\\])*\"\\s*,\\s*)*\"(?:\\\\.|[^\"\\\\])*\")?\\s*]";
    }

    @NotNull
    @Language("RegExp")
    private static String numberPatternWithKeyCapture(String name) {
        // Capture both the matched key name and the numeric content.
        // zh-CN: 同时捕获命中的 key 名称与数值内容.
        return "\"(" + parseNamePattern(name) + ")\"\\s*:\\s*\"?(\\d+)\"?";
    }

    @NotNull
    @Language("RegExp")
    private static String booleanPatternWithKeyCapture(String name) {
        // Capture both the matched key name and the boolean content.
        // zh-CN: 同时捕获命中的 key 名称与布尔值内容.
        return "\"(" + parseNamePattern(name) + ")\"\\s*:\\s*\"?(true|false)\"?";
    }

    @NotNull
    @Language("RegExp")
    private static String stringPatternWithKeyCapture(String name) {
        // Capture both the matched key name and the string content.
        // zh-CN: 同时捕获命中的 key 名称与字符串内容.
        return "\"(" + parseNamePattern(name) + ")\"\\s*:\\s*\"((?:[^\"]|(?<=\\\\)\")*?)(?<!\\\\)\"";
    }

    @NotNull
    private static String parseNamePattern(String name) {
        return name.replaceAll("(?<=[a-z])([A-Z]+)", "(?:$1|_$1)");
    }

    private static void setFieldIfMatchesWithKey(
            Pattern pattern,
            String s,
            String canonicalKey,
            java.util.function.Consumer<String> setter,
            FuzzyDeserializer.OriginalJsonKeyAware recorder,
            boolean detectConflicts
    ) {
        Matcher matcher = pattern.matcher(s);
        String lastMatchedKey = null;
        String lastValue = null;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            lastMatchedKey = matcher.group(1);
            lastValue = matcher.group(2);
        }
        if (matches == 0) return;
        if (detectConflicts && matches > 1) {
            throw new IllegalArgumentException("Conflicting keys for \"" + canonicalKey + "\" in fallback parsing");
        }
        recorder.recordOriginalJsonKey(canonicalKey, Objects.requireNonNull(lastMatchedKey));
        setter.accept(lastValue);
    }

    private static void setFieldForDoubleIfMatchesWithKey(
            Pattern pattern,
            String s,
            String canonicalKey,
            java.util.function.DoubleConsumer setter,
            FuzzyDeserializer.OriginalJsonKeyAware recorder,
            boolean detectConflicts
    ) {
        Matcher matcher = pattern.matcher(s);
        String lastMatchedKey = null;
        String lastValue = null;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            lastMatchedKey = matcher.group(1);
            lastValue = matcher.group(2);
        }
        if (matches == 0) return;
        if (detectConflicts && matches > 1) {
            throw new IllegalArgumentException("Conflicting keys for \"" + canonicalKey + "\" in fallback parsing");
        }
        recorder.recordOriginalJsonKey(canonicalKey, Objects.requireNonNull(lastMatchedKey));
        setter.accept(Double.parseDouble(Objects.requireNonNull(lastValue)));
    }

    private static void setFieldForBooleanIfMatchesWithKey(
            Pattern pattern,
            String s,
            String canonicalKey,
            java.util.function.Consumer<Boolean> setter,
            FuzzyDeserializer.OriginalJsonKeyAware recorder,
            boolean detectConflicts
    ) {
        Matcher matcher = pattern.matcher(s);
        String lastMatchedKey = null;
        String lastValue = null;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            lastMatchedKey = matcher.group(1);
            lastValue = matcher.group(2);
        }
        if (matches == 0) return;
        if (detectConflicts && matches > 1) {
            throw new IllegalArgumentException("Conflicting keys for \"" + canonicalKey + "\" in fallback parsing");
        }
        recorder.recordOriginalJsonKey(canonicalKey, Objects.requireNonNull(lastMatchedKey));
        setter.accept(Boolean.parseBoolean(Objects.requireNonNull(lastValue)));
    }

    private static void setListIfMatchesWithKey(
            Pattern pattern,
            String s,
            String canonicalKey,
            java.util.function.Consumer<List<String>> setter,
            FuzzyDeserializer.OriginalJsonKeyAware recorder,
            boolean detectConflicts
    ) {
        Matcher matcher = pattern.matcher(s);
        String lastMatchedKey = null;
        String lastContent = null;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            lastMatchedKey = matcher.group(1);
            lastContent = matcher.group(2);
        }
        if (matches == 0) return;
        if (detectConflicts && matches > 1) {
            throw new IllegalArgumentException("Conflicting keys for \"" + canonicalKey + "\" in fallback parsing");
        }

        recorder.recordOriginalJsonKey(canonicalKey, Objects.requireNonNull(lastMatchedKey));

        if (lastContent == null || lastContent.isBlank()) {
            setter.accept(Collections.emptyList());
        } else {
            List<String> list = Arrays.stream(lastContent.split("\\s*,\\s*")).map(str -> {
                if (str.startsWith("\"") && str.endsWith("\"")) {
                    return str.substring(1, str.length() - 1);
                }
                return str;
            }).collect(Collectors.toList());
            setter.accept(list);
        }
    }

    public static boolean isProject(ExplorerPage page) {
        // @Hint by SuperMonster003 on Dec 2, 2024.
        //  ! It is considered a valid project regardless of whether project.json
        //  ! contains the necessary information or can be parsed correctly.
        //  ! zh-CN: 无论 project.json 是否包含必要信息或是否可以正常解析, 都认为是一个有效项目.
        //  !
        //  # return fromProjectDir(page.getPath()) != null;
        return isProject(page.getPath());
    }

    public static boolean isProject(File file) {
        return isProject(file.getPath());
    }

    public static boolean isProject(String path) {
        String pathname = configFileOfDir(path);
        return new File(pathname).exists();
    }

    @Nullable
    public static ProjectConfig fromProjectDir(String path) {
        return fromFilePath(configFileOfDir(path));
    }

    /**
     * Reads a runnable project descriptor while keeping packaging metadata validation separate.
     */
    @Nullable
    public static ProjectConfig fromProjectDirForExecution(String path) {
        String configPath = configFileOfDir(path);
        try {
            return fromJsonForExecution(PFiles.read(configPath));
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            Log.d(TAG, "Failed to read execution json from file: " + configPath
                    + " (" + e.getMessage() + ")");
            return null;
        }
    }

    public static String configFileOfDir(String projectDir) {
        return PFiles.join(projectDir, CONFIG_FILE_NAME);
    }

    public static String normalizeSignatureScheme(String input) {
        Pattern pattern = Pattern.compile("v\\d+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        Set<String> matches = new HashSet<>();
        while (matcher.find()) {
            matches.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        if (matches.isEmpty()) {
            return input.trim();
        }
        return matches.stream().sorted().collect(Collectors.joining(" + "));
    }

    public BuildInfo getBuildInfo() {
        return mBuildInfo;
    }

    public ProjectConfig setBuildInfo(BuildInfo buildInfo) {
        mBuildInfo = buildInfo;
        return this;
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

    /**
     * @deprecated This method is deprecated. Use {@link #getMainScriptFileName()} instead.
     */
    @Deprecated
    public String getMainScriptFile() {
        return getMainScriptFileName();
    }

    @NonNull
    public String getMainScriptFileName() {
        return mMainScriptFileName;
    }

    @Nullable
    public AgentManifest getAgent() {
        return mAgent;
    }

    public ProjectConfig setAgent(@Nullable AgentManifest agent) {
        mAgent = agent;
        return this;
    }

    public ProjectConfig setMainScriptFileName(String mainScriptFileName) {
        mMainScriptFileName = mainScriptFileName;
        return this;
    }

    @Nullable
    public String getProjectType() {
        return mProjectType;
    }

    public ProjectConfig setProjectType(@Nullable String projectType) {
        mProjectType = projectType;
        return this;
    }

    public List<String> getAssets() {
        if (mAssets == null) {
            mAssets = Collections.emptyList();
        }
        return mAssets;
    }

    public void setAssets(List<String> assets) {
        mAssets = assets;
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

    public LaunchConfig getLaunchConfig() {
        return mLaunchConfig;
    }

    public void setLaunchConfig(@NonNull LaunchConfig launchConfig) {
        mLaunchConfig = launchConfig;
    }

    @Override
    public void recordOriginalJsonKey(@NonNull String canonicalKey, @NonNull String originalKey) {
        // Always overwrite to keep the "last key wins" behavior.
        // zh-CN: 始终覆盖, 以保持 "最后一个 key 生效" 的行为.
        mOriginalJsonKeys.put(canonicalKey, originalKey);
    }

    public String toJson(boolean detectConflicts) {
        // Always serialize with canonical keys: legacy keys are upgraded on save.
        // zh-CN: 始终按 canonical key 序列化, 旧字段在保存时自动升级为规范字段.
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
        return mFeatures;
    }

    public void setFeatures(@NonNull List<String> features) {
        mFeatures = features;
    }

    public List<File> getExcludedDirs() {
        return mExcludedDirs.stream().map(dir -> {
            if (dir.endsWith("/")) {
                dir = dir.substring(0, dir.length() - 1);
            }
            if (mSourcePath == null) {
                throw new IllegalStateException("Source path is not set");
            }
            if (dir.startsWith(mSourcePath)) {
                return new File(dir);
            }
            return new File(mSourcePath, dir);
        }).distinct().collect(Collectors.toList());
    }

    public ProjectConfig excludeDir(String dirToExclude) {
        if (!mExcludedDirs.contains(dirToExclude)) {
            mExcludedDirs.add(dirToExclude);
        }
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
        return Objects.requireNonNullElse(mPermissions, DEFAULT_PERMISSIONS);
    }

    public ProjectConfig setPermissions(List<String> permissions) {
        mPermissions = permissions;
        return this;
    }

    @Nullable
    public NodeConfig getNodeConfig() {
        return mNodeConfig;
    }

    public ProjectConfig setNodeConfig(@Nullable NodeConfig nodeConfig) {
        mNodeConfig = nodeConfig;
        return this;
    }

    @Nullable
    public BunConfig getBunConfig() { return mBunConfig; }

    public ProjectConfig setBunConfig(@Nullable BunConfig bunConfig) {
        mBunConfig = bunConfig;
        return this;
    }

    public String getSignatureScheme() {
        return mSignatureScheme;
    }

    public ProjectConfig setSignatureScheme(String signatureScheme) {
        mSignatureScheme = normalizeSignatureScheme(signatureScheme);
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

    public static class BunConfig {
        @Nullable
        @SerializedName("timeoutMs")
        private Long mTimeoutMs;

        @Nullable
        public Long getTimeoutMs() { return mTimeoutMs; }

        public BunConfig setTimeoutMs(@Nullable Long timeoutMs) {
            mTimeoutMs = timeoutMs;
            return this;
        }

        @SerializedName("hostCapabilities")
        private List<String> mHostCapabilities = new ArrayList<>();

        public List<String> getHostCapabilities() {
            return mHostCapabilities == null ? Collections.emptyList() : mHostCapabilities;
        }

        public BunConfig setHostCapabilities(List<String> capabilities) {
            mHostCapabilities = capabilities;
            return this;
        }
    }

    public static class NodeConfig {

        @Nullable
        @SerializedName("backend")
        private String mBackend;

        @Nullable
        @SerializedName("timeoutMs")
        private Long mTimeoutMs;

        @Nullable
        @SerializedName("executionMode")
        private String mExecutionMode;

        @Nullable
        @SerializedName("entryDirective")
        private String mEntryDirective;

        @Nullable
        @SerializedName("permissions")
        private List<String> mPermissions;

        @Nullable
        public String getBackend() {
            return mBackend;
        }

        public NodeConfig setBackend(@Nullable String backend) {
            mBackend = backend;
            return this;
        }

        @Nullable
        public Long getTimeoutMs() {
            return mTimeoutMs;
        }

        public NodeConfig setTimeoutMs(@Nullable Long timeoutMs) {
            mTimeoutMs = timeoutMs;
            return this;
        }

        @Nullable
        public String getExecutionMode() {
            return mExecutionMode;
        }

        public NodeConfig setExecutionMode(@Nullable String executionMode) {
            mExecutionMode = executionMode;
            return this;
        }

        @Nullable
        public String getEntryDirective() {
            return mEntryDirective;
        }

        public NodeConfig setEntryDirective(@Nullable String entryDirective) {
            mEntryDirective = entryDirective;
            return this;
        }

        public List<String> getPermissions() {
            return Objects.requireNonNullElse(mPermissions, Collections.emptyList());
        }

        public NodeConfig setPermissions(@Nullable List<String> permissions) {
            mPermissions = permissions;
            return this;
        }
    }

}
