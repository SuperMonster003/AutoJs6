package org.autojs.autojs.apkbuilder.template

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.core.plugin.typescript.TypeScriptApkBuildCompiler
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import org.autojs.plugin.apkbuilder.template.TypeScriptBuildStagingCipher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeScriptApkBuildDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compilesEncryptedRhinoProjectBeforeRemotePackaging() {
        val root = File(context.cacheDir, "s5-typescript-apk-project").apply {
            deleteRecursively()
            check(mkdirs())
        }
        val outputDirectory = requireNotNull(context.getExternalFilesDir(null))
        val outputApk = File(outputDirectory, OUTPUT_APK_NAME).apply { delete() }
        val config = ProjectConfig()
            .setName("S5 TypeScript APK")
            .setPackageName(OUTPUT_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setMainScriptFileName("main.ts")
            .setProjectType("rhino")
            .setSourcePath(root.absolutePath)
            .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
            .setLibs(emptyList())
            .setPermissions(emptyList())
        config.buildInfo.apply {
            buildId = INPUT_BUILD_ID
            buildNumber = 1L
            buildTime = 1L
        }

        val library = File(root, "lib").apply { check(mkdirs()) }
        val mainSource = File(root, "main.ts")
        val moduleSource = File(library, "value.ts")
        val compilerCacheBefore = compilerCacheSnapshot()
        try {
            File(root, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
            File(root, "tsconfig.json").writeText(
                """{"compilerOptions":{"strict":true}}""",
                Charsets.UTF_8,
            )
            File(root, "types.d.ts").writeText(
                "declare const buildTimeOnlyDeclaration: unique symbol;\n",
                Charsets.UTF_8,
            )
            File(root, "payload.json").writeText("""{"retained":true}""", Charsets.UTF_8)
            writeEncryptedSource(
                mainSource,
                """
                    import { answer } from "./lib/value"
                    const marker: string = "${RUNTIME_MARKER_PREFIX}" + answer
                    console.log(marker)
                    toast(marker)
                """.trimIndent() + "\n",
                config,
            )
            writeEncryptedSource(
                moduleSource,
                "export const answer: number = 42;\n",
                config,
            )

            assertPreparedStagingIsEncrypted(config)

            val outcome = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = config,
                outApk = outputApk,
                cancelSignal = AtomicBoolean(false),
                progressListener = RemoteApkBuildClient.ProgressListener { progress ->
                    Log.i(TAG, "step=${progress.step} title=${progress.title} detail=${progress.detail}")
                },
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
            assertTrue(
                "Remote APK build failed: status=${outcome.status}, " +
                    "warnings=${outcome.warnings}, errors=${outcome.errors}",
                outcome.isSuccess,
            )
            assertTrue("Remote APK output is absent", outputApk.isFile && outputApk.length() > 0L)
            assertPackagedOutput(outputApk)
            assertEncryptedSourceWasNotMutated(mainSource, RUNTIME_MARKER_PREFIX)
            assertEncryptedSourceWasNotMutated(moduleSource, "answer: number")
            assertFalse(
                "Private APK Builder transport staging was not cleaned",
                File(context.cacheDir, "apk-remote-build-input").listFiles().orEmpty().any(),
            )
            assertFalse(
                "TypeScript compiler input archive was not cleaned",
                context.cacheDir.listFiles().orEmpty().any { file ->
                    file.name.startsWith("typescript-input-") ||
                        file.name.startsWith("typescript-output-")
                },
            )
            assertEquals(
                "Encrypted APK build changed the persistent TypeScript compiler cache",
                compilerCacheBefore,
                compilerCacheSnapshot(),
            )
            Log.i(
                TAG,
                "S5 TypeScript APK ready: ${outputApk.absolutePath} (${outputApk.length()} bytes)",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun packagesPlainJavaScriptWithoutTypeScriptStaging() {
        val root = File(context.cacheDir, "s5-javascript-apk-project").apply {
            deleteRecursively()
            check(mkdirs())
        }
        val outputDirectory = requireNotNull(context.getExternalFilesDir(null))
        val outputApk = File(outputDirectory, JAVASCRIPT_OUTPUT_APK_NAME).apply { delete() }
        val config = ProjectConfig()
            .setName("S5 JavaScript APK")
            .setPackageName(JAVASCRIPT_OUTPUT_PACKAGE_NAME)
            .setVersionName("1.0.0")
            .setVersionCode(1)
            .setMainScriptFileName("main.js")
            .setProjectType("rhino")
            .setSourcePath(root.absolutePath)
            .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
            .setLibs(emptyList())
            .setPermissions(emptyList())
        config.buildInfo.apply {
            buildId = INPUT_BUILD_ID
            buildNumber = 1L
            buildTime = 1L
        }

        try {
            File(root, "project.json").writeText(config.toJson(false), Charsets.UTF_8)
            File(root, "main.js").writeText(
                "console.log(\"$JAVASCRIPT_RUNTIME_MARKER\");\n",
                Charsets.UTF_8,
            )
            val prepared = RemoteApkBuildRequestPreparer.prepare(
                context = context,
                projectConfig = config,
                outputFileName = JAVASCRIPT_OUTPUT_APK_NAME,
                cancelSignal = AtomicBoolean(false),
            )
            try {
                val extras = requireNotNull(prepared.request.extras)
                assertFalse(
                    extras.containsKey(
                        ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_VERSION,
                    ),
                )
                assertFalse(
                    extras.containsKey(
                        ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_KEY,
                    ),
                )
                assertFalse(
                    extras.containsKey(
                        ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTED_PATHS,
                    ),
                )
                ZipFile(prepared.archiveFile).use { zip ->
                    assertTrue(
                        zip.readRequired("project/main.js")
                            .toString(StandardCharsets.UTF_8)
                            .contains(JAVASCRIPT_RUNTIME_MARKER),
                    )
                }
            } finally {
                prepared.cleanup()
            }

            val outcome = RemoteApkBuildClient.buildBlocking(
                context = context,
                projectConfig = config,
                outApk = outputApk,
                cancelSignal = AtomicBoolean(false),
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
            assertTrue(
                "Plain JavaScript remote APK build failed: ${outcome.errors}",
                outcome.isSuccess,
            )
            ZipFile(outputApk).use { zip ->
                val mainJavaScript = zip.readRequired("$PROJECT_ASSET_ROOT/main.js")
                try {
                    assertTrue(EncryptedScriptFileHeader.isValidFile(mainJavaScript))
                    assertFalse(
                        mainJavaScript.toString(StandardCharsets.UTF_8)
                            .contains(JAVASCRIPT_RUNTIME_MARKER),
                    )
                } finally {
                    mainJavaScript.fill(0)
                }
                val packagedConfig = JSONObject(
                    zip.readRequired("$PROJECT_ASSET_ROOT/project.json")
                        .toString(StandardCharsets.UTF_8),
                )
                assertEquals("main.js", packagedConfig.getString("main"))
            }
            Log.i(
                TAG,
                "S5 JavaScript APK ready: ${outputApk.absolutePath} (${outputApk.length()} bytes)",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertPreparedStagingIsEncrypted(config: ProjectConfig) {
        val prepared = RemoteApkBuildRequestPreparer.prepare(
            context = context,
            projectConfig = config,
            outputFileName = OUTPUT_APK_NAME,
            cancelSignal = AtomicBoolean(false),
        )
        try {
            val extras = requireNotNull(prepared.request.extras)
            assertEquals(
                TypeScriptBuildStagingCipher.VERSION,
                extras.getInt(
                    ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_VERSION,
                    0,
                ),
            )
            val encryptedPaths = requireNotNull(
                extras.getStringArrayList(
                    ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTED_PATHS,
                ),
            ).toSet()
            assertEquals(
                setOf(
                    TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH,
                    "lib/value.js",
                    "main.js",
                ),
                encryptedPaths,
            )
            val key = requireNotNull(
                extras.getByteArray(
                    ApkBuildRequestExtraKeys.TYPESCRIPT_STAGING_ENCRYPTION_KEY,
                ),
            ).copyOf()
            try {
                ZipFile(prepared.archiveFile).use { zip ->
                    val entries = Collections.list(zip.entries()).map { entry -> entry.name }
                    assertFalse(entries.any(::isTypeScriptSourceEntry))
                    encryptedPaths.forEach { relativePath ->
                        val envelope = zip.readRequired("project/$relativePath")
                        try {
                            assertTrue(
                                "Generated JavaScript was clear in Builder staging: $relativePath",
                                TypeScriptBuildStagingCipher.isEncrypted(envelope),
                            )
                            assertFalse(
                                envelope.toString(StandardCharsets.UTF_8)
                                    .contains(RUNTIME_MARKER_PREFIX),
                            )
                            val cleartext = TypeScriptBuildStagingCipher.decrypt(envelope, key)
                            try {
                                if (relativePath == "main.js") {
                                    assertTrue(
                                        cleartext.toString(StandardCharsets.UTF_8)
                                            .contains(RUNTIME_MARKER_PREFIX),
                                    )
                                }
                            } finally {
                                cleartext.fill(0)
                            }
                        } finally {
                            envelope.fill(0)
                        }
                    }
                    listOf("main.js.map", "lib/value.js.map").forEach { relativePath ->
                        val sourceMap = zip.readRequired("project/$relativePath")
                        try {
                            assertFalse(
                                sourceMap.toString(StandardCharsets.UTF_8)
                                    .contains("sourcesContent"),
                            )
                        } finally {
                            sourceMap.fill(0)
                        }
                    }
                }
            } finally {
                key.fill(0)
            }
        } finally {
            prepared.cleanup()
        }
        assertFalse(
            "Encrypted APK Builder staging was not cleaned",
            File(context.cacheDir, "apk-remote-build-input").listFiles().orEmpty().any(),
        )
    }

    private fun assertPackagedOutput(apk: File) {
        ZipFile(apk).use { zip ->
            val names = Collections.list(zip.entries()).map { entry -> entry.name }
            val projectEntries = names.filter { name -> name.startsWith(PROJECT_ASSET_ROOT) }
            assertTrue("Packaged project assets are absent", projectEntries.isNotEmpty())
            assertFalse(
                "Raw TypeScript source entered the packaged APK: $projectEntries",
                projectEntries.any(::isTypeScriptSourceEntry),
            )
            assertFalse("tsconfig.json entered the packaged APK", "$PROJECT_ASSET_ROOT/tsconfig.json" in names)
            assertFalse(
                "TypeScript compiler assets entered the packaged APK",
                names.any(::isCompilerAsset),
            )

            val mainJavaScript = zip.readRequired("$PROJECT_ASSET_ROOT/main.js")
            val moduleJavaScript = zip.readRequired("$PROJECT_ASSET_ROOT/lib/value.js")
            val launcherJavaScript = zip.readRequired(
                "$PROJECT_ASSET_ROOT/${TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH}",
            )
            assertTrue(EncryptedScriptFileHeader.isValidFile(mainJavaScript))
            assertTrue(EncryptedScriptFileHeader.isValidFile(moduleJavaScript))
            assertTrue(EncryptedScriptFileHeader.isValidFile(launcherJavaScript))
            assertFalse(mainJavaScript.toString(StandardCharsets.UTF_8).contains(RUNTIME_MARKER_PREFIX))

            listOf("main.js.map", "lib/value.js.map").forEach { relativePath ->
                val sourceMap = JSONObject(
                    zip.readRequired("$PROJECT_ASSET_ROOT/$relativePath")
                        .toString(StandardCharsets.UTF_8),
                )
                assertEquals(3, sourceMap.getInt("version"))
                assertFalse("Release Source Map contains sourcesContent", sourceMap.has("sourcesContent"))
            }

            val compilationManifest = JSONObject(
                zip.readRequired("$PROJECT_ASSET_ROOT/.autojs6/typescript-compilation.json")
                    .toString(StandardCharsets.UTF_8),
            )
            assertEquals("autojs6.typescript.apk-compilation", compilationManifest.getString("schema"))
            assertEquals("emitted", compilationManifest.getString("outcome"))
            assertEquals("main.js", compilationManifest.getString("entryJavaScriptPath"))
            assertEquals(
                TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH,
                compilationManifest.getString("launchScriptPath"),
            )
            assertEquals("external_without_sources_content", compilationManifest.getString("sourceMapPolicy"))
            assertEquals(2, compilationManifest.getJSONArray("modules").length())
            assertFalse(compilationManifest.toString().contains("main.ts"))
            assertFalse(compilationManifest.toString().contains("value.ts"))
            assertFalse(compilationManifest.toString().contains("autojs://"))

            val packagedConfig = JSONObject(
                zip.readRequired("$PROJECT_ASSET_ROOT/project.json").toString(StandardCharsets.UTF_8),
            )
            assertEquals(
                TypeScriptApkBuildCompiler.RHINO_LAUNCHER_PATH,
                packagedConfig.getString("main"),
            )
            assertEquals(OUTPUT_PACKAGE_NAME, packagedConfig.getString("packageName"))
            assertNotNull(packagedConfig.getJSONObject("build").getString("id"))
            assertEquals(
                true,
                JSONObject(
                    zip.readRequired("$PROJECT_ASSET_ROOT/payload.json")
                        .toString(StandardCharsets.UTF_8),
                ).getBoolean("retained"),
            )
        }
    }

    private fun writeEncryptedSource(file: File, source: String, config: ProjectConfig) {
        val cleartext = source.toByteArray(StandardCharsets.UTF_8)
        val key = md5(config.packageName + config.versionName + config.mainScriptFileName)
            .toByteArray(StandardCharsets.UTF_8)
        val vector = md5(config.buildInfo.buildId + config.name).take(16)
        val encrypted = AdvancedEncryptionStandard(key, vector).encrypt(cleartext)
        try {
            FileOutputStream(file).use { output ->
                EncryptedScriptFileHeader.writeHeader(output)
                output.write(encrypted)
            }
        } finally {
            cleartext.fill(0)
            key.fill(0)
            encrypted.fill(0)
        }
    }

    private fun assertEncryptedSourceWasNotMutated(file: File, cleartextNeedle: String) {
        val bytes = file.readBytes()
        try {
            assertTrue("Encrypted source header was removed: ${file.path}", EncryptedScriptFileHeader.isValidFile(bytes))
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(cleartextNeedle))
        } finally {
            bytes.fill(0)
        }
    }

    private fun compilerCacheSnapshot(): Map<String, Pair<Long, Long>> {
        val root = File(context.cacheDir, "typescript-compiler-cache-v1")
        return root.listFiles().orEmpty()
            .associate { file -> file.name to (file.length() to file.lastModified()) }
    }

    private fun ZipFile.readRequired(name: String): ByteArray {
        val entry = requireNotNull(getEntry(name)) { "APK entry is absent: $name" }
        require(entry.size in 1..MAX_TEST_ENTRY_BYTES) { "APK entry has invalid size: $name" }
        return getInputStream(entry).use { input -> input.readBytes() }
    }

    private fun isTypeScriptSourceEntry(name: String): Boolean {
        val lower = name.lowercase()
        return TYPE_SCRIPT_SUFFIXES.any(lower::endsWith)
    }

    private fun isCompilerAsset(name: String): Boolean {
        val lower = name.lowercase()
        return "typescript-compiler" in lower ||
            lower.endsWith("/typescript.js") ||
            lower.endsWith("/compiler-worker.js") ||
            lower.endsWith("/typescriptservices.js")
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val TAG = "TypeScriptApkBuildTest"
        const val PROJECT_ASSET_ROOT = "assets/project"
        const val OUTPUT_APK_NAME = "s5-typescript-packaged.apk"
        const val OUTPUT_PACKAGE_NAME = "org.autojs.autojs6.s5typescriptapk"
        const val JAVASCRIPT_OUTPUT_APK_NAME = "s5-javascript-packaged.apk"
        const val JAVASCRIPT_OUTPUT_PACKAGE_NAME = "org.autojs.autojs6.s5javascriptapk"
        const val INPUT_BUILD_ID = "5A5A5A5A-1"
        const val RUNTIME_MARKER_PREFIX = "AUTOJS6_S5_TYPESCRIPT_APK_OK_"
        const val JAVASCRIPT_RUNTIME_MARKER = "AUTOJS6_S5_JAVASCRIPT_APK_OK"
        const val MAX_TEST_ENTRY_BYTES = 8L * 1024L * 1024L
        val TYPE_SCRIPT_SUFFIXES = listOf(".d.mts", ".d.cts", ".d.ts", ".mts", ".cts", ".tsx", ".ts")
    }
}
