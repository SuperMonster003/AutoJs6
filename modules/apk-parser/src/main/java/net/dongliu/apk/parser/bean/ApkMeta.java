package net.dongliu.apk.parser.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Apk meta info
 *
 * @author dongliu
 */
public class ApkMeta {

    public final String packageName;
    /**
     * get the apk's title(name)
     */
    public final String label;
    private final String icon;
    public final String versionName;
    public final long versionCode;
    public final Long revisionCode;
    public final String sharedUserId;
    public final String sharedUserLabel;
    public final String split;
    public final String configForSplit;
    public final boolean isFeatureSplit;
    public final boolean isSplitRequired;
    public final boolean isolatedSplits;
    public final String installLocation;
    public final String minSdkVersion;
    public final String targetSdkVersion;
    @Nullable
    public final String maxSdkVersion;
    @Nullable
    public final String compileSdkVersion;
    @Nullable
    public final String compileSdkVersionCodename;
    @Nullable
    public final String platformBuildVersionCode;
    @Nullable
    public final String platformBuildVersionName;
    public final GlEsVersion glEsVersion;
    public final boolean isAnyDensity;
    public final boolean isSmallScreens;
    public final boolean isNormalScreens;
    public final boolean isLargeScreens;
    public final boolean isDebuggable;
    @NonNull
    public final List<String> usesPermissions;
    public final List<UseFeature> usesFeatures;
    public final List<Permission> permissions;

    private ApkMeta(final Builder builder) {
        this.packageName = builder.packageName;
        this.label = builder.label;
        this.icon = builder.icon;
        this.versionName = builder.versionName;
        this.versionCode = builder.versionCode;
        this.revisionCode = builder.revisionCode;
        this.sharedUserId = builder.sharedUserId;
        this.sharedUserLabel = builder.sharedUserLabel;
        this.split = builder.split;
        this.configForSplit = builder.configForSplit;
        this.isFeatureSplit = builder.isFeatureSplit;
        this.isSplitRequired = builder.isSplitRequired;
        this.isolatedSplits = builder.isolatedSplits;
        this.installLocation = builder.installLocation;
        this.minSdkVersion = builder.minSdkVersion;
        this.targetSdkVersion = builder.targetSdkVersion;
        this.maxSdkVersion = builder.maxSdkVersion;
        this.compileSdkVersion = builder.compileSdkVersion;
        this.compileSdkVersionCodename = builder.compileSdkVersionCodename;
        this.platformBuildVersionCode = builder.platformBuildVersionCode;
        this.platformBuildVersionName = builder.platformBuildVersionName;
        this.glEsVersion = builder.glEsVersion;
        this.isAnyDensity = builder.anyDensity;
        this.isSmallScreens = builder.smallScreens;
        this.isNormalScreens = builder.normalScreens;
        this.isLargeScreens = builder.largeScreens;
        this.isDebuggable = builder.debuggable;
        this.usesPermissions = builder.usesPermissions;
        this.usesFeatures = builder.usesFeatures;
        this.permissions = builder.permissions;
    }

    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @NonNull
    @Override
    public String toString() {
        return "packageName: \t" + this.packageName + "\n"
                + "label: \t" + this.label + "\n"
                + "icon: \t" + this.icon + "\n"
                + "versionName: \t" + this.versionName + "\n"
                + "versionCode: \t" + this.versionCode + "\n"
                + "minSdkVersion: \t" + this.minSdkVersion + "\n"
                + "targetSdkVersion: \t" + this.targetSdkVersion + "\n"
                + "maxSdkVersion: \t" + this.maxSdkVersion;
    }

    public static final class Builder {
        public String applicationClassRelativePath;
        private String packageName;
        private String label;
        private String icon;
        private String versionName;
        private long versionCode = 0L;
        private Long revisionCode;
        private String sharedUserId;
        private String sharedUserLabel;
        public String split;
        public String configForSplit;
        public boolean isFeatureSplit;
        public boolean isSplitRequired;
        public boolean isolatedSplits;
        private String installLocation;
        private String minSdkVersion;
        private String targetSdkVersion;
        private String maxSdkVersion;
        private String compileSdkVersion;
        private String compileSdkVersionCodename;
        private String platformBuildVersionCode;
        private String platformBuildVersionName;
        private GlEsVersion glEsVersion;
        private boolean anyDensity;
        private boolean smallScreens;
        private boolean normalScreens;
        private boolean largeScreens;
        private boolean debuggable;
        private final List<String> usesPermissions = new ArrayList<>();
        private final List<UseFeature> usesFeatures = new ArrayList<>();
        private final List<Permission> permissions = new ArrayList<>();

        private Builder() {
        }

        @NonNull
        public Builder setPackageName(final @Nullable String packageName) {
            this.packageName = packageName;
            return this;
        }

        public String getPackageName() {
            return packageName;
        }

        @NonNull
        public Builder setLabel(final @NonNull String label) {
            this.label = label;
            return this;
        }

        public String getLabel() {
            return label;
        }

        @NonNull
        public Builder setIcon(final @Nullable String icon) {
            this.icon = icon;
            return this;
        }

        @NonNull
        public Builder setVersionName(final @Nullable String versionName) {
            this.versionName = versionName;
            return this;
        }

        @NonNull
        public Builder setVersionCode(final long versionCode) {
            this.versionCode = versionCode;
            return this;
        }

        @NonNull
        public Builder setRevisionCode(final @Nullable Long revisionCode) {
            this.revisionCode = revisionCode;
            return this;
        }

        @NonNull
        public Builder setSharedUserId(final @Nullable String sharedUserId) {
            this.sharedUserId = sharedUserId;
            return this;
        }

        @NonNull
        public Builder setSharedUserLabel(final @Nullable String sharedUserLabel) {
            this.sharedUserLabel = sharedUserLabel;
            return this;
        }

        @NonNull
        public Builder setSplit(final @Nullable String split) {
            this.split = split;
            return this;
        }

        @NonNull
        public Builder setConfigForSplit(final @Nullable String configForSplit) {
            this.configForSplit = configForSplit;
            return this;
        }

        @NonNull
        public Builder setIsFeatureSplit(final boolean isFeatureSplit) {
            this.isFeatureSplit = isFeatureSplit;
            return this;
        }

        @NonNull
        public Builder setIsSplitRequired(final boolean isSplitRequired) {
            this.isSplitRequired = isSplitRequired;
            return this;
        }

        @NonNull
        public Builder setIsolatedSplits(final boolean isolatedSplits) {
            this.isolatedSplits = isolatedSplits;
            return this;
        }

        @NonNull
        public Builder setInstallLocation(final @NonNull String installLocation) {
            this.installLocation = installLocation;
            return this;
        }

        @NonNull
        public Builder setMinSdkVersion(final @NonNull String minSdkVersion) {
            this.minSdkVersion = minSdkVersion;
            return this;
        }

        @NonNull
        public Builder setTargetSdkVersion(final @NonNull String targetSdkVersion) {
            this.targetSdkVersion = targetSdkVersion;
            return this;
        }

        @NonNull
        public Builder setMaxSdkVersion(final @NonNull String maxSdkVersion) {
            this.maxSdkVersion = maxSdkVersion;
            return this;
        }

        @NonNull
        public Builder setCompileSdkVersion(final @Nullable String compileSdkVersion) {
            this.compileSdkVersion = compileSdkVersion;
            return this;
        }

        @NonNull
        public Builder setCompileSdkVersionCodename(final @Nullable String compileSdkVersionCodename) {
            this.compileSdkVersionCodename = compileSdkVersionCodename;
            return this;
        }

        @NonNull
        public Builder setPlatformBuildVersionCode(final @Nullable String platformBuildVersionCode) {
            this.platformBuildVersionCode = platformBuildVersionCode;
            return this;
        }

        @NonNull
        public Builder setPlatformBuildVersionName(final @Nullable String platformBuildVersionName) {
            this.platformBuildVersionName = platformBuildVersionName;
            return this;
        }

        @NonNull
        public Builder setGlEsVersion(final @NonNull GlEsVersion glEsVersion) {
            this.glEsVersion = glEsVersion;
            return this;
        }

        @NonNull
        public Builder setAnyDensity(final boolean anyDensity) {
            this.anyDensity = anyDensity;
            return this;
        }

        @NonNull
        public Builder setSmallScreens(final boolean smallScreens) {
            this.smallScreens = smallScreens;
            return this;
        }

        @NonNull
        public Builder setNormalScreens(final boolean normalScreens) {
            this.normalScreens = normalScreens;
            return this;
        }

        @NonNull
        public Builder setLargeScreens(final boolean largeScreens) {
            this.largeScreens = largeScreens;
            return this;
        }

        @NonNull
        public Builder setDebuggable(final boolean debuggable) {
            this.debuggable = debuggable;
            return this;
        }

        @NonNull
        public Builder addUsesPermission(final @Nullable String usesPermission) {
            this.usesPermissions.add(usesPermission);
            return this;
        }

        @NonNull
        public Builder addUsesFeature(final @NonNull UseFeature usesFeature) {
            this.usesFeatures.add(usesFeature);
            return this;
        }

        @NonNull
        public Builder addPermissions(final @NonNull Permission permission) {
            this.permissions.add(permission);
            return this;
        }

        @NonNull
        public ApkMeta build() {
            return new ApkMeta(this);
        }
    }
}
