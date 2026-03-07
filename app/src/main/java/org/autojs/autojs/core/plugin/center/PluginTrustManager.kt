package org.autojs.autojs.core.plugin.center

import android.content.Context

object PluginTrustManager {

    data class TrustInfo(
        val authorizedState: PluginAuthorizedState,
        val isOfficial: Boolean,
        val isTrusted: Boolean,
        val developer: String? = null,
        val primaryFingerprintSha256: String?,
        val fingerprintsSha256: List<String>,
    )

    fun resolveTrustInfo(context: Context, packageName: String): TrustInfo {
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, packageName)
        val matchingPluginIdentifier = PLUGIN_IDENTIFIERS.firstOrNull { ids ->
            fingerprints.any { ids.fingerprintsSha256.contains(it) }
        }

        val isOfficial = fingerprints.any { it in OFFICIAL_SHA_256 }
        val isTrusted = matchingPluginIdentifier?.state == PluginAuthorizedState.TRUSTED
        val developer = matchingPluginIdentifier?.developer

        val authorizedState = when {
            isOfficial -> PluginAuthorizedState.OFFICIAL
            isTrusted -> PluginAuthorizedState.TRUSTED
            PluginAuthorizationStore.isGranted(context, packageName, fingerprints) -> PluginAuthorizedState.USER_GRANTED
            else -> PluginAuthorizedState.REQUIRED
        }
        return TrustInfo(
            authorizedState = authorizedState,
            isOfficial = isOfficial,
            isTrusted = isTrusted,
            developer = developer,
            primaryFingerprintSha256 = fingerprints.firstOrNull(),
            fingerprintsSha256 = fingerprints,
        )
    }

    fun isAuthorized(context: Context, packageName: String): Boolean {
        val info = resolveTrustInfo(context, packageName)
        // @formatter:off
        return info.authorizedState == PluginAuthorizedState.OFFICIAL
            || info.authorizedState == PluginAuthorizedState.TRUSTED
            || info.authorizedState == PluginAuthorizedState.USER_GRANTED
        // @formatter:on
    }

    val OFFICIAL_SHA_256 = setOf("31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213")

    val PLUGIN_IDENTIFIERS = listOf(
        PluginIdentifier(
            OFFICIAL_SHA_256,
            "SuperMonster003", PluginAuthorizedState.OFFICIAL,
        ),
        PluginIdentifier(
            setOf(
                "9cf34f732e0b93f78fe9f2ef662b4fd153db1dd1426cab9aefb9b9f6f8ace5f0", // Auto.js
                "6840d437e677b627607768aec5f307e314af4f06f179de8bd9aea8b5963c6b3a", // Plugins
            ),
            "hyb1996", PluginAuthorizedState.TRUSTED,
        ),
        PluginIdentifier(
            setOf(
                "517c51b16bead916296eb3cadfd57cd4f871ae8a4f767094ddf635338bad21c1", // Auto.js M
                "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc", // Plugins
            ),
            "TonyJiangWJ", PluginAuthorizedState.TRUSTED,
        ),
        PluginIdentifier(
            setOf(
                "f4595765fb1928aabc3fc231451c5a2ab4c2f896e5cac2cbff08d40b4dcd1b77", // Autox.js v7
            ),
            "aiselp", PluginAuthorizedState.TRUSTED,
        ),
        PluginIdentifier(
            setOf(
                "03c4fd8935c4e330a7553a0dc7c1e88ea5d38b42093422c0a05a9f72eab8bd43", // Plugins
            ),
            "LZX284", PluginAuthorizedState.TRUSTED,
        ),
        PluginIdentifier(
            setOf(
                "6325752bb7c5d6d9f147e53cb1cf743cc16db4f557d947ab0b8a597b81199d0c", // Plugins
            ),
            "HRan2004", PluginAuthorizedState.TRUSTED,
        ),
        PluginIdentifier(
            setOf(
                "8ff046d10b78f8cec3906e240866dcf75f70204acb3f1c8e4baf153cb971085c", // Plugins; Main APK
            ),
            "TomatoOCR",
        )
    )

    data class PluginIdentifier(
        val fingerprintsSha256: Set<String>,
        val developer: String? = null,
        val state: PluginAuthorizedState = PluginAuthorizedState.REQUIRED,
    )
}
