package org.autojs.autojs.project;

import com.google.gson.annotations.SerializedName;
import org.autojs.autojs.annotation.SerializedNameCompatible;
import org.autojs.autojs.annotation.SerializedNameCompatible.With;

import java.util.zip.CRC32;

public class BuildInfo {

    @SerializedName(value = "id")
    @SerializedNameCompatible(with = {@With(value = "build_id", target = {"AutoJs4", "AutoX"})})
    private String mBuildId;

    @SerializedName(value = "number")
    @SerializedNameCompatible(with = {@With(value = "build_number", target = {"AutoJs4", "AutoX"})})
    private long mBuildNumber;

    @SerializedName(value = "time")
    @SerializedNameCompatible(with = {@With(value = "build_time", target = {"AutoJs4", "AutoX"})})
    private long mBuildTime;

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
}
