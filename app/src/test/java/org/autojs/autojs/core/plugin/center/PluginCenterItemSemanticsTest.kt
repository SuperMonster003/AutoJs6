package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCenterItemSemanticsTest {

    private val base = PluginCenterItem(
        title = "Plugin",
        packageName = "org.autojs.plugin.test",
        versionName = "1.0.0",
        isInstalled = false,
    )

    @Test
    fun equivalentFreshItemsHaveTheSameContent() {
        assertTrue(PluginCenterItemSemantics.areContentsTheSame(base, base.copy()))
        assertTrue(PluginCenterItemSemantics.isSameList(listOf(base), listOf(base.copy())))
    }

    @Test
    fun everyVisibleConstructorFieldAffectsContentEquality() {
        val variants = listOf(
            "title" to base.copy(title = "Other"),
            "packageName" to base.copy(packageName = "org.autojs.plugin.other"),
            "versionName" to base.copy(versionName = "2.0.0"),
            "versionCode" to base.copy(versionCode = 2L),
            "versionDate" to base.copy(versionDate = "2026-08-29"),
            "updatableVersionName" to base.copy(updatableVersionName = "2.0.0"),
            "updatableVersionCode" to base.copy(updatableVersionCode = 2L),
            "updatableVersionDate" to base.copy(updatableVersionDate = "2026-08-29"),
            "updatableApkUrl" to base.copy(updatableApkUrl = "https://example.com/update.apk"),
            "updatableApkSha256" to base.copy(updatableApkSha256 = "update-sha"),
            "updatableApkSizeBytes" to base.copy(updatableApkSizeBytes = 2L),
            "updatableChangelogUrl" to base.copy(updatableChangelogUrl = "https://example.com/changelog"),
            "updatableChangelogText" to base.copy(updatableChangelogText = "Changed"),
            "requiresDowngradeReinstall" to base.copy(requiresDowngradeReinstall = true),
            "author" to base.copy(author = "Author"),
            "collaborators" to base.copy(collaborators = listOf("Collaborator")),
            "description" to base.copy(description = "Description"),
            "instructionHardCoded" to base.copy(instructionHardCoded = "Instruction"),
            "instructionMarkdownUrl" to base.copy(instructionMarkdownUrl = "https://example.com/readme"),
            "packageSize" to base.copy(packageSize = 2L),
            "installableApkUrl" to base.copy(installableApkUrl = "https://example.com/install.apk"),
            "installableApkSha256" to base.copy(installableApkSha256 = "install-sha"),
            "installableApkSizeBytes" to base.copy(installableApkSizeBytes = 3L),
            "supportedAbis" to base.copy(supportedAbis = listOf("arm64-v8a")),
            "installedAbis" to base.copy(installedAbis = listOf("arm64-v8a")),
            "remoteIconUrl" to base.copy(remoteIconUrl = "https://example.com/icon.png"),
            "remoteNightIconUrl" to base.copy(remoteNightIconUrl = "https://example.com/icon-night.png"),
            "forceIgnoreLocalIcon" to base.copy(forceIgnoreLocalIcon = true),
            "iconBackgroundColor" to base.copy(iconBackgroundColor = "#336699"),
            "nightIconBackgroundColor" to base.copy(nightIconBackgroundColor = "transparent"),
            "iconSignature" to base.copy(iconSignature = "icon-signature"),
            "isEnabled" to base.copy(isEnabled = false),
            "isInstalled" to base.copy(isInstalled = true),
            "firstInstallTime" to base.copy(firstInstallTime = 1L),
            "lastUpdateTime" to base.copy(lastUpdateTime = 2L),
            "settings" to base.copy(settings = PluginCenterItemSettings(title = "Settings")),
            "source" to base.copy(source = PluginIndexSource.THIRD_PARTY),
            "isFeatured" to base.copy(isFeatured = false),
            "engine" to base.copy(engine = "test-engine"),
            "variant" to base.copy(variant = "test-variant"),
            "engineId" to base.copy(engineId = "test-engine-id"),
            "isOcrService" to base.copy(isOcrService = true),
            "isBarcodeService" to base.copy(isBarcodeService = true),
            "serviceAction" to base.copy(serviceAction = "org.autojs.plugin.TEST"),
            "mechanism" to base.copy(mechanism = PluginMechanism.SDK),
            "authorizedState" to base.copy(authorizedState = PluginAuthorizedState.TRUSTED),
            "activatedState" to base.copy(activatedState = PluginActivatedState.DONE),
            "enabledState" to base.copy(enabledState = PluginEnabledState.DISABLED),
            "lastError" to base.copy(lastError = PluginError(PluginErrorCode.INTERNAL_ERROR)),
            "signingFingerprintSha256" to base.copy(signingFingerprintSha256 = "signing-sha"),
            "isOfficialVerified" to base.copy(isOfficialVerified = true),
            "canActivate" to base.copy(canActivate = true),
            "requiresHostVersion" to base.copy(requiresHostVersion = 2L),
            "hostVersionCode" to base.copy(hostVersionCode = 3L),
        )

        variants.forEach { (field, variant) ->
            assertFalse(
                "$field must affect Plugin Center content equality",
                PluginCenterItemSemantics.areContentsTheSame(base, variant),
            )
        }
    }

    @Test
    fun listEqualityIncludesRowOrder() {
        val other = base.copy(packageName = "org.autojs.plugin.other")

        assertFalse(
            PluginCenterItemSemantics.isSameList(
                current = listOf(base, other),
                next = listOf(other, base),
            )
        )
    }
}
