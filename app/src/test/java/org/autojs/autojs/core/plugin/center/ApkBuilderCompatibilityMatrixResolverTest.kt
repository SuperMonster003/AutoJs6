package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkBuilderCompatibilityMatrixResolverTest {

    private val sha256 = "a".repeat(64)

    @Test
    fun selectsHighestCompatibleEntryThenExactDeviceAbi() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                        artifact("inrt-arm64-v8a", listOf("arm64-v8a")),
                    ),
                ),
                entry(
                    pluginVersionCode = 680102,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                        artifact("inrt-arm64-v8a", listOf("arm64-v8a")),
                    ),
                ),
            ),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        requireNotNull(result)
        assertEquals(680102L, result.release.versionCode)
        assertEquals("1.0.0+autojs6-6.8.0-alpha5", result.release.versionName)
        assertEquals("inrt-arm64-v8a", result.artifactVariant)
        assertEquals(listOf("arm64-v8a"), result.supportedAbis)
        assertTrue(result.release.compatibilityMatrixMatched)
        assertTrue(result.release.apkUrl!!.endsWith("inrt-arm64-v8a.apk"))
    }

    @Test
    fun fallsBackToUniversalArtifactWhenExactAbiIsAbsent() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                        artifact("inrt-x86", listOf("x86")),
                    ),
                ),
            ),
            hostVersionCode = 680,
            deviceAbis = listOf("x86_64"),
        )

        requireNotNull(result)
        assertEquals("inrt-universal", result.artifactVariant)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"), result.supportedAbis)
    }

    @Test
    fun doesNotUseLowerEntryWhenHighestEntryHasNoUsableArtifact() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                    ),
                ),
                entry(
                    pluginVersionCode = 680102,
                    artifacts = listOf(artifact("inrt-x86", listOf("x86"))),
                ),
            ),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun returnsNullWhenHostRangeDoesNotMatch() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    minHostVersionCode = 681,
                    maxHostVersionCode = 682,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                    ),
                ),
            ),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun acceptsExplicitPatchCompatibilityRange() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    minHostVersionCode = 680,
                    maxHostVersionCode = 682,
                    allowPatchVersionMismatch = true,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                    ),
                ),
            ),
            hostVersionCode = 681,
            deviceAbis = listOf("arm64-v8a"),
        )

        requireNotNull(result)
        assertEquals(680101L, result.release.versionCode)
    }

    @Test
    fun rejectsWidenedRangeWithoutExplicitPermission() {
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(
                entry(
                    pluginVersionCode = 680101,
                    minHostVersionCode = 680,
                    maxHostVersionCode = 682,
                    allowPatchVersionMismatch = false,
                    artifacts = listOf(
                        artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
                    ),
                ),
            ),
            hostVersionCode = 681,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun rejectsWidenedRangeWhoseBuiltForHostIsOutsideTheInterval() {
        val malformedEntry = entry(
            pluginVersionCode = 680101,
            minHostVersionCode = 680,
            maxHostVersionCode = 682,
            allowPatchVersionMismatch = true,
            artifacts = listOf(
                artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
            ),
        ).replace("\"hostVersionCode\": 680", "\"hostVersionCode\": 679")
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(malformedEntry),
            hostVersionCode = 681,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun rejectsNonBooleanPatchPermissionEvenForExactRange() {
        val malformedEntry = entry(
            pluginVersionCode = 680101,
            artifacts = listOf(
                artifact("inrt-universal", listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")),
            ),
        ).replace("\"allowPatchVersionMismatch\": false", "\"allowPatchVersionMismatch\": \"false\"")
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(malformedEntry),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun rejectsArtifactOutsideOfficialReleaseRepository() {
        val untrustedArtifact = artifact("inrt-arm64-v8a", listOf("arm64-v8a"))
            .replace("https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Builder-Template", "https://example.com")
        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(entry(pluginVersionCode = 680101, artifacts = listOf(untrustedArtifact))),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a"),
        )

        assertNull(result)
    }

    @Test
    fun acceptsLegacyTopLevelUniversalProjection() {
        val apkName = "legacy-universal.apk"
        val legacyEntry = """
            {
              "pluginVersionName": "1.0.0+autojs6-6.8.0-alpha5",
              "pluginVersionCode": 680101,
              "hostVersionName": "6.8.0 Alpha5",
              "minHostVersionCode": 680,
              "maxHostVersionCode": 680,
              "apkName": "$apkName",
              "apkUrl": "https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Builder-Template/releases/download/v6.8.0/$apkName",
              "apkSha256": "$sha256",
              "apkSizeBytes": 42,
              "releasedAt": "2026-08-30T12:34:56Z"
            }
        """.trimIndent()

        val result = ApkBuilderCompatibilityMatrixResolver.resolve(
            json = matrix(legacyEntry),
            hostVersionCode = 680,
            deviceAbis = listOf("arm64-v8a"),
        )

        requireNotNull(result)
        assertEquals("inrt-universal", result.artifactVariant)
        assertTrue(result.supportedAbis.isEmpty())
        assertEquals("2026-08-30", result.release.versionDate)
        assertTrue(result.release.versionName.contains("+autojs6-", ignoreCase = true))
    }

    private fun matrix(vararg entries: String): String = """
        {
          "schemaVersion": 1,
          "entries": [${entries.joinToString(",")}]
        }
    """.trimIndent()

    private fun entry(
        pluginVersionCode: Long,
        minHostVersionCode: Long = 680,
        maxHostVersionCode: Long = 680,
        allowPatchVersionMismatch: Boolean = false,
        artifacts: List<String>,
    ): String = """
        {
          "pluginVersionName": "1.0.0",
          "pluginVersionCode": $pluginVersionCode,
          "hostVersionName": "6.8.0 Alpha5",
          "hostVersionCode": 680,
          "minHostVersionCode": $minHostVersionCode,
          "maxHostVersionCode": $maxHostVersionCode,
          "allowPatchVersionMismatch": $allowPatchVersionMismatch,
          "releasedAt": "2026-08-30T12:34:56Z",
          "artifacts": [${artifacts.joinToString(",")}]
        }
    """.trimIndent()

    private fun artifact(variant: String, abis: List<String>): String {
        val apkName = "$variant.apk"
        return """
            {
              "variant": "$variant",
              "supportedAbis": [${abis.joinToString(",") { "\"$it\"" }}],
              "apkName": "$apkName",
              "apkUrl": "https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Builder-Template/releases/download/v6.8.0/$apkName",
              "apkSha256": "$sha256",
              "apkSizeBytes": 42
            }
        """.trimIndent()
    }
}
