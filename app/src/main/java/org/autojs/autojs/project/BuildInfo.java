package org.autojs.autojs.project;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.autojs.autojs.annotation.SerializedNameCompatible;
import org.autojs.autojs.annotation.SerializedNameCompatible.With;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
public class BuildInfo implements FuzzyDeserializer.OriginalJsonKeyAware {

    @SerializedName(value = "id")
    @SerializedNameCompatible(with = {@With(value = "build_id", target = {"AutoJs4", "AutoX"})})
    private String mBuildId;

    @SerializedName(value = "number")
    @SerializedNameCompatible(with = {@With(value = "build_number", target = {"AutoJs4", "AutoX"})})
    private long mBuildNumber;

    @SerializedName(value = "time")
    @SerializedNameCompatible(with = {@With(value = "build_time", target = {"AutoJs4", "AutoX"})})
    private long mBuildTime;

    private final transient Map<String, String> mOriginalJsonKeys = new LinkedHashMap<>();

    public String getBuildId() {
        return mBuildId;
    }

    public void setBuildId(String buildId) {
        mBuildId = buildId;
    }

    public long getBuildNumber() {
        return mBuildNumber;
    }

    public void setBuildNumber(long buildNumber) {
        mBuildNumber = buildNumber;
    }

    public long getBuildTime() {
        return mBuildTime;
    }

    public void setBuildTime(long buildTime) {
        mBuildTime = buildTime;
    }

    public static BuildInfo generate(long buildNumber) {
        BuildInfo info = new BuildInfo();
        info.setBuildNumber(buildNumber);
        info.setBuildTime(System.currentTimeMillis());
        info.setBuildId(generateBuildId(buildNumber, info.getBuildTime()));
        return info;
    }

    private static String generateBuildId(long buildNumber, long buildTime) {
        CRC32 crc32 = new CRC32();
        crc32.update((buildNumber + "" + buildTime).getBytes());
        return String.format("%08X", crc32.getValue()) + "-" + buildNumber;
    }

    @Override
    public void recordOriginalJsonKey(String canonicalKey, String originalKey) {
        mOriginalJsonKeys.put(canonicalKey, originalKey);
    }

    public void applyOriginalJsonKeys(JsonObject obj, boolean detectConflicts) {
        for (Map.Entry<String, String> entry : mOriginalJsonKeys.entrySet()) {
            String canonicalKey = entry.getKey();
            String originalKey = entry.getValue();
            if (canonicalKey == null || originalKey == null || Objects.equals(canonicalKey, originalKey)) {
                continue;
            }
            if (!obj.has(canonicalKey)) {
                continue;
            }
            if (obj.has(originalKey)) {
                if (detectConflicts) {
                    throw new IllegalStateException("Conflicting keys when serializing build: \"" + canonicalKey + "\" and \"" + originalKey + "\"");
                }
                obj.remove(canonicalKey);
                continue;
            }
            JsonElement value = obj.remove(canonicalKey);
            obj.add(originalKey, value);
        }
    }
}
