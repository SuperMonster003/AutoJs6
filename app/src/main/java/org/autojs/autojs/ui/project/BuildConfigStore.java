package org.autojs.autojs.ui.project;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.util.StringUtils;
import org.autojs.autojs6.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Persistent profile store for BuildActivity configuration snapshots.
 * BuildActivity 配置快照的持久化配置文件存储.
 * <p>
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 9, 2026.
 */
public final class BuildConfigStore {

    private static final String PREF_NAME = "build_apk_config_profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_PROFILES_JSON = "profiles_json";

    private static final String KEY_ROOT_PROFILES = "profiles";
    private static final String KEY_ENTRY_STATE = "state";
    private static final String KEY_ENTRY_FIELDS = "fields";

    private static final String KEY_EXPORT_NAME = "name";

    public static final String PROFILE_DEFAULT_ID = "__default__";
    public static final String PROFILE_PROJECT_JSON = "project.json";

    public static final String STATE_SOURCE_PATH = "sourcePath";
    public static final String STATE_OUTPUT_PATH = "outputPath";
    public static final String STATE_APP_NAME = "appName";
    public static final String STATE_PACKAGE_NAME = "packageName";
    public static final String STATE_VERSION_NAME = "versionName";
    public static final String STATE_VERSION_CODE = "versionCode";
    public static final String STATE_LAUNCH_LOGS_VISIBLE = "launchLogsVisible";
    public static final String STATE_LAUNCH_SPLASH_VISIBLE = "launchSplashVisible";
    public static final String STATE_LAUNCH_LAUNCHER_VISIBLE = "launchLauncherVisible";
    public static final String STATE_LAUNCH_RUN_ON_BOOT = "launchRunOnBoot";
    public static final String STATE_LAUNCH_SLUG = "launchSlug";
    public static final String STATE_SIGNATURE_SCHEME = "signatureScheme";
    public static final String STATE_KEY_STORE_PATH = "keyStorePath";
    public static final String STATE_ABIS = "abis";
    public static final String STATE_LIBS = "libs";
    public static final String STATE_PERMISSIONS = "permissions";

    public static final String FIELD_FIXED_SOURCE_PATH = "fixedSourcePath";
    public static final String FIELD_FIXED_ICON = "fixedIcon";
    public static final String FIELD_FIXED_OUTPUT_PATH = "fixedOutputPath";
    public static final String FIELD_FIXED_APP_NAME = "fixedAppName";
    public static final String FIELD_FIXED_PACKAGE_NAME = "fixedPackageName";
    public static final String FIELD_FIXED_VERSION_NAME = "fixedVersionName";
    public static final String FIELD_FIXED_VERSION_CODE = "fixedVersionCode";
    public static final String FIELD_FIXED_LAUNCH_LOGS_VISIBLE = "fixedLaunchLogsVisible";
    public static final String FIELD_FIXED_LAUNCH_SPLASH_VISIBLE = "fixedLaunchSplashVisible";
    public static final String FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE = "fixedLaunchLauncherVisible";
    public static final String FIELD_FIXED_LAUNCH_RUN_ON_BOOT = "fixedLaunchRunOnBoot";
    public static final String FIELD_FIXED_LAUNCH_SLUG = "fixedLaunchSlug";
    public static final String FIELD_FIXED_ABIS = "fixedAbis";
    public static final String FIELD_FIXED_LIBS = "fixedLibs";
    public static final String FIELD_FIXED_SIGNATURE_SCHEME = "fixedSignatureScheme";
    public static final String FIELD_FIXED_KEY_STORE = "fixedKeyStore";
    public static final String FIELD_FIXED_PERMISSIONS = "fixedPermissions";

    private BuildConfigStore() {
    }

    public static final class ProfilePayload {
        @NonNull
        public final String name;
        @NonNull
        public final JSONObject state;
        @NonNull
        public final JSONObject fields;

        public ProfilePayload(@NonNull String name, @NonNull JSONObject state, @NonNull JSONObject fields) {
            this.name = name;
            this.state = state;
            this.fields = fields;
        }
    }

    @NonNull
    public static String getActiveProfileName(@NonNull Context context) {
        SharedPreferences sp = prefs(context);
        String name = sp.getString(KEY_ACTIVE_PROFILE, PROFILE_DEFAULT_ID);
        if (name == null || name.trim().isEmpty()) {
            return PROFILE_DEFAULT_ID;
        }
        return name;
    }

    public static void setActiveProfileName(@NonNull Context context, @NonNull String name) {
        prefs(context).edit().putString(KEY_ACTIVE_PROFILE, name).apply();
    }

    @NonNull
    public static String getDefaultProfileDisplayName(@NonNull Context context) {
        return context.getString(R.string.text_default);
    }

    public static boolean isReservedDefaultName(@NonNull Context context, @Nullable String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        var computed = new ArrayList<>() {{
            add(PROFILE_DEFAULT_ID);
        }};
        for (Language language : Language.values()) {
            if (language == Language.AUTO) continue;
            String s = StringUtils.getStringForLocale(context, language.getLocale(), R.string.text_default);
            if (s.isBlank()) continue;
            computed.add(s);
        }
        return computed.contains(n)
               || Objects.equals(n, getDefaultProfileDisplayName(context));
    }

    public static boolean isReservedProfileName(@NonNull Context context, @Nullable String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        return isReservedDefaultName(context, n)
               || Objects.equals(n, PROFILE_PROJECT_JSON);
    }

    @NonNull
    public static String getPrefillProfileName(@NonNull Context context) {
        String prefix = "config-";
        List<String> profiles = listProfiles(context);
        int candidate = 1;
        while (true) {
            String name = prefix + candidate;
            if (!profiles.contains(name)) {
                return name;
            }
            candidate++;
        }
    }

    @NonNull
    public static List<String> listProfiles(@NonNull Context context) {
        JSONObject root = readRootOrCreate(context);
        JSONObject profiles = root.optJSONObject(KEY_ROOT_PROFILES);
        ArrayList<String> list = new ArrayList<>();
        if (profiles != null) {
            profiles.keys().forEachRemaining(list::add);
        }
        list.sort(String::compareToIgnoreCase);
        return list;
    }

    public static void ensureDefaultProfileExists(@NonNull Context context, @Nullable JSONObject defaultState) {
        ensureDefaultProfileExists(context, defaultState, null);
    }

    public static void ensureDefaultProfileExists(@NonNull Context context, @Nullable JSONObject defaultState, @Nullable JSONObject defaultFields) {
        JSONObject root = readRootOrCreate(context);
        JSONObject profiles = root.optJSONObject(KEY_ROOT_PROFILES);
        if (profiles == null) {
            profiles = new JSONObject();
            putSafely(root, KEY_ROOT_PROFILES, profiles);
        }

        boolean changed = false;
        JSONObject expectedDefault = normalizeProfile(null, defaultState, defaultFields);

        JSONObject currentDefault = profiles.optJSONObject(PROFILE_DEFAULT_ID);
        if (currentDefault == null || !Objects.equals(currentDefault.toString(), expectedDefault.toString())) {
            putSafely(profiles, PROFILE_DEFAULT_ID, expectedDefault);
            changed = true;
        }

        String active = getActiveProfileName(context);
        if (!profiles.has(active)) {
            setActiveProfileName(context, PROFILE_DEFAULT_ID);
        }

        if (changed) {
            persistRoot(context, root);
        }
    }

    @Nullable
    public static ProfilePayload loadProfilePayload(@NonNull Context context, @NonNull String name) {
        JSONObject root = readRootOrCreate(context);
        JSONObject profiles = root.optJSONObject(KEY_ROOT_PROFILES);
        if (profiles == null) return null;

        JSONObject raw = profiles.optJSONObject(name);
        if (raw == null) return null;

        JSONObject normalized = normalizeProfile(raw, null, null);
        return new ProfilePayload(
                name,
                normalized.optJSONObject(KEY_ENTRY_STATE) == null
                        ? normalizeState(null)
                        : normalized.optJSONObject(KEY_ENTRY_STATE),
                normalized.optJSONObject(KEY_ENTRY_FIELDS) == null
                        ? defaultFields()
                        : normalized.optJSONObject(KEY_ENTRY_FIELDS)
        );
    }

    @Nullable
    public static JSONObject loadProfile(@NonNull Context context, @NonNull String name) {
        ProfilePayload payload = loadProfilePayload(context, name);
        return payload == null ? null : payload.state;
    }

    @NonNull
    public static JSONObject loadProfileFields(@NonNull Context context, @NonNull String name) {
        ProfilePayload payload = loadProfilePayload(context, name);
        return payload == null ? defaultFields() : payload.fields;
    }

    public static void saveProfile(@NonNull Context context, @NonNull String name, @Nullable JSONObject state) {
        saveProfile(context, name, state, null);
    }

    public static void saveProfile(@NonNull Context context, @NonNull String name, @Nullable JSONObject state, @Nullable JSONObject fields) {
        JSONObject root = readRootOrCreate(context);
        JSONObject profiles = root.optJSONObject(KEY_ROOT_PROFILES);
        if (profiles == null) {
            profiles = new JSONObject();
            putSafely(root, KEY_ROOT_PROFILES, profiles);
        }

        JSONObject normalizedEntry = normalizeProfile(null, state, fields);
        putSafely(profiles, name, normalizedEntry);
        persistRoot(context, root);
    }

    public static void deleteProfile(@NonNull Context context, @NonNull String name) {
        if (PROFILE_DEFAULT_ID.equals(name) || PROFILE_PROJECT_JSON.equals(name)) {
            return;
        }
        JSONObject root = readRootOrCreate(context);
        JSONObject profiles = root.optJSONObject(KEY_ROOT_PROFILES);
        if (profiles == null) return;
        profiles.remove(name);
        persistRoot(context, root);
        if (Objects.equals(getActiveProfileName(context), name)) {
            setActiveProfileName(context, PROFILE_DEFAULT_ID);
        }
    }

    @NonNull
    public static JSONObject exportProfileToJson(@NonNull Context context, @NonNull String name) {
        ProfilePayload payload = loadProfilePayload(context, name);

        JSONObject out = new JSONObject();
        putSafely(out, KEY_EXPORT_NAME, name);
        putSafely(out, KEY_ENTRY_STATE, payload == null ? normalizeState(null) : normalizeState(payload.state));
        putSafely(out, KEY_ENTRY_FIELDS, payload == null ? defaultFields() : normalizeFields(payload.fields));
        return out;
    }

    @NonNull
    public static ProfilePayload importProfileFromJson(@NonNull JSONObject json) {
        String name = json.optString(KEY_EXPORT_NAME, "").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Invalid profile name");
        }

        JSONObject state = json.optJSONObject(KEY_ENTRY_STATE);
        if (state == null && looksLikeLegacyState(json)) {
            state = json;
        }

        JSONObject fields = json.optJSONObject(KEY_ENTRY_FIELDS);

        return new ProfilePayload(name, normalizeState(state), normalizeFields(fields));
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static JSONObject readRootOrCreate(@NonNull Context context) {
        String raw = prefs(context).getString(KEY_PROFILES_JSON, null);
        JSONObject root;
        try {
            root = raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (Throwable ignored) {
            root = new JSONObject();
        }
        if (!root.has(KEY_ROOT_PROFILES) || !(root.opt(KEY_ROOT_PROFILES) instanceof JSONObject)) {
            putSafely(root, KEY_ROOT_PROFILES, new JSONObject());
        }
        return root;
    }

    private static void persistRoot(@NonNull Context context, @NonNull JSONObject root) {
        prefs(context).edit().putString(KEY_PROFILES_JSON, root.toString()).apply();
    }

    @NonNull
    private static JSONObject normalizeProfile(@Nullable JSONObject rawProfile, @Nullable JSONObject fallbackState, @Nullable JSONObject fallbackFields) {
        JSONObject stateSrc = null;
        JSONObject fieldsSrc = null;

        if (rawProfile != null) {
            if (rawProfile.has(KEY_ENTRY_STATE) || rawProfile.has(KEY_ENTRY_FIELDS)) {
                stateSrc = rawProfile.optJSONObject(KEY_ENTRY_STATE);
                fieldsSrc = rawProfile.optJSONObject(KEY_ENTRY_FIELDS);
            } else if (looksLikeLegacyState(rawProfile)) {
                stateSrc = rawProfile;
            }
        }

        JSONObject out = new JSONObject();
        putSafely(out, KEY_ENTRY_STATE, normalizeState(stateSrc != null ? stateSrc : fallbackState));
        putSafely(out, KEY_ENTRY_FIELDS, normalizeFields(fieldsSrc != null ? fieldsSrc : fallbackFields));
        return out;
    }

    private static boolean looksLikeLegacyState(@NonNull JSONObject object) {
        return object.has(STATE_SOURCE_PATH)
               || object.has(STATE_OUTPUT_PATH)
               || object.has(STATE_APP_NAME)
               || object.has(STATE_PACKAGE_NAME)
               || object.has(STATE_VERSION_NAME)
               || object.has(STATE_VERSION_CODE)
               || object.has(STATE_LAUNCH_LOGS_VISIBLE)
               || object.has(STATE_LAUNCH_SPLASH_VISIBLE)
               || object.has(STATE_LAUNCH_LAUNCHER_VISIBLE)
               || object.has(STATE_LAUNCH_RUN_ON_BOOT)
               || object.has(STATE_LAUNCH_SLUG)
               || object.has(STATE_SIGNATURE_SCHEME)
               || object.has(STATE_KEY_STORE_PATH)
               || object.has(STATE_ABIS)
               || object.has(STATE_LIBS)
               || object.has(STATE_PERMISSIONS);
    }

    @NonNull
    private static JSONObject normalizeState(@Nullable JSONObject state) {
        JSONObject out = new JSONObject();
        JSONObject src = state == null ? new JSONObject() : state;

        putSafely(out, STATE_SOURCE_PATH, src.optString(STATE_SOURCE_PATH, ""));
        putSafely(out, STATE_OUTPUT_PATH, src.optString(STATE_OUTPUT_PATH, ""));
        putSafely(out, STATE_APP_NAME, src.optString(STATE_APP_NAME, ""));
        putSafely(out, STATE_PACKAGE_NAME, src.optString(STATE_PACKAGE_NAME, ""));
        putSafely(out, STATE_VERSION_NAME, src.optString(STATE_VERSION_NAME, ""));
        putSafely(out, STATE_VERSION_CODE, src.optString(STATE_VERSION_CODE, ""));
        putSafely(out, STATE_LAUNCH_LOGS_VISIBLE, src.optBoolean(STATE_LAUNCH_LOGS_VISIBLE, true));
        putSafely(out, STATE_LAUNCH_SPLASH_VISIBLE, src.optBoolean(STATE_LAUNCH_SPLASH_VISIBLE, true));
        putSafely(out, STATE_LAUNCH_LAUNCHER_VISIBLE, src.optBoolean(STATE_LAUNCH_LAUNCHER_VISIBLE, true));
        putSafely(out, STATE_LAUNCH_RUN_ON_BOOT, src.optBoolean(STATE_LAUNCH_RUN_ON_BOOT, false));
        putSafely(out, STATE_LAUNCH_SLUG, src.optString(STATE_LAUNCH_SLUG, ""));
        putSafely(out, STATE_SIGNATURE_SCHEME, src.optString(STATE_SIGNATURE_SCHEME, ""));
        putSafely(out, STATE_KEY_STORE_PATH, src.optString(STATE_KEY_STORE_PATH, ""));

        putSafely(out, STATE_ABIS, normalizeStringArray(src.optJSONArray(STATE_ABIS)));
        putSafely(out, STATE_LIBS, normalizeStringArray(src.optJSONArray(STATE_LIBS)));
        putSafely(out, STATE_PERMISSIONS, normalizeStringArray(src.optJSONArray(STATE_PERMISSIONS)));

        return out;
    }

    @NonNull
    private static JSONObject defaultFields() {
        JSONObject out = new JSONObject();
        putSafely(out, FIELD_FIXED_SOURCE_PATH, false);
        putSafely(out, FIELD_FIXED_ICON, false);
        putSafely(out, FIELD_FIXED_OUTPUT_PATH, false);
        putSafely(out, FIELD_FIXED_APP_NAME, false);
        putSafely(out, FIELD_FIXED_PACKAGE_NAME, false);
        putSafely(out, FIELD_FIXED_VERSION_NAME, false);
        putSafely(out, FIELD_FIXED_VERSION_CODE, false);
        putSafely(out, FIELD_FIXED_LAUNCH_LOGS_VISIBLE, false);
        putSafely(out, FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, false);
        putSafely(out, FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, false);
        putSafely(out, FIELD_FIXED_LAUNCH_RUN_ON_BOOT, false);
        putSafely(out, FIELD_FIXED_LAUNCH_SLUG, false);
        putSafely(out, FIELD_FIXED_ABIS, false);
        putSafely(out, FIELD_FIXED_LIBS, true);
        putSafely(out, FIELD_FIXED_SIGNATURE_SCHEME, true);
        putSafely(out, FIELD_FIXED_KEY_STORE, true);
        putSafely(out, FIELD_FIXED_PERMISSIONS, true);
        return out;
    }

    @NonNull
    private static JSONObject normalizeFields(@Nullable JSONObject fields) {
        JSONObject defaults = defaultFields();
        JSONObject src = fields == null ? new JSONObject() : fields;

        JSONObject out = new JSONObject();
        putSafely(out, FIELD_FIXED_SOURCE_PATH, src.optBoolean(FIELD_FIXED_SOURCE_PATH, defaults.optBoolean(FIELD_FIXED_SOURCE_PATH, false)));
        putSafely(out, FIELD_FIXED_ICON, src.optBoolean(FIELD_FIXED_ICON, defaults.optBoolean(FIELD_FIXED_ICON, false)));
        putSafely(out, FIELD_FIXED_OUTPUT_PATH, src.optBoolean(FIELD_FIXED_OUTPUT_PATH, defaults.optBoolean(FIELD_FIXED_OUTPUT_PATH, false)));
        putSafely(out, FIELD_FIXED_APP_NAME, src.optBoolean(FIELD_FIXED_APP_NAME, defaults.optBoolean(FIELD_FIXED_APP_NAME, false)));
        putSafely(out, FIELD_FIXED_PACKAGE_NAME, src.optBoolean(FIELD_FIXED_PACKAGE_NAME, defaults.optBoolean(FIELD_FIXED_PACKAGE_NAME, false)));
        putSafely(out, FIELD_FIXED_VERSION_NAME, src.optBoolean(FIELD_FIXED_VERSION_NAME, defaults.optBoolean(FIELD_FIXED_VERSION_NAME, false)));
        putSafely(out, FIELD_FIXED_VERSION_CODE, src.optBoolean(FIELD_FIXED_VERSION_CODE, defaults.optBoolean(FIELD_FIXED_VERSION_CODE, false)));
        putSafely(out, FIELD_FIXED_LAUNCH_LOGS_VISIBLE, src.optBoolean(FIELD_FIXED_LAUNCH_LOGS_VISIBLE, defaults.optBoolean(FIELD_FIXED_LAUNCH_LOGS_VISIBLE, false)));
        putSafely(out, FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, src.optBoolean(FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, defaults.optBoolean(FIELD_FIXED_LAUNCH_SPLASH_VISIBLE, false)));
        putSafely(out, FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, src.optBoolean(FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, defaults.optBoolean(FIELD_FIXED_LAUNCH_LAUNCHER_VISIBLE, false)));
        putSafely(out, FIELD_FIXED_LAUNCH_RUN_ON_BOOT, src.optBoolean(FIELD_FIXED_LAUNCH_RUN_ON_BOOT, defaults.optBoolean(FIELD_FIXED_LAUNCH_RUN_ON_BOOT, false)));
        putSafely(out, FIELD_FIXED_LAUNCH_SLUG, src.optBoolean(FIELD_FIXED_LAUNCH_SLUG, defaults.optBoolean(FIELD_FIXED_LAUNCH_SLUG, false)));
        putSafely(out, FIELD_FIXED_ABIS, src.optBoolean(FIELD_FIXED_ABIS, defaults.optBoolean(FIELD_FIXED_ABIS, false)));
        putSafely(out, FIELD_FIXED_LIBS, src.optBoolean(FIELD_FIXED_LIBS, defaults.optBoolean(FIELD_FIXED_LIBS, true)));
        putSafely(out, FIELD_FIXED_SIGNATURE_SCHEME, src.optBoolean(FIELD_FIXED_SIGNATURE_SCHEME, defaults.optBoolean(FIELD_FIXED_SIGNATURE_SCHEME, true)));
        putSafely(out, FIELD_FIXED_KEY_STORE, src.optBoolean(FIELD_FIXED_KEY_STORE, defaults.optBoolean(FIELD_FIXED_KEY_STORE, true)));
        putSafely(out, FIELD_FIXED_PERMISSIONS, src.optBoolean(FIELD_FIXED_PERMISSIONS, defaults.optBoolean(FIELD_FIXED_PERMISSIONS, true)));

        return out;
    }

    @NonNull
    private static JSONArray normalizeStringArray(@Nullable JSONArray source) {
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                String s = source.optString(i, "").trim();
                if (!s.isEmpty()) {
                    dedup.add(s);
                }
            }
        }
        JSONArray out = new JSONArray();
        for (String s : dedup) {
            out.put(s);
        }
        return out;
    }

    private static void putSafely(@NonNull JSONObject target, @NonNull String key, @Nullable Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
            // Ignore malformed value to keep profile storage resilient.
        }
    }
}
