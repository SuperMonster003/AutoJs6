package org.autojs.autojs.apkbuilder

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildProgress
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class ApkBuilderCompatibilityFacadeTest {

    @Test
    fun historicFluentChainDelegatesToRemoteBuildAndCommitsBuildInfo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(context.cacheDir, "apk-builder-facade-${System.nanoTime()}")
        val sourceDir = File(testRoot, "project").apply { mkdirs() }
        val outputApk = File(testRoot, "output.apk")
        val legacyWorkspace = File(testRoot, "legacy-workspace").apply { mkdirs() }
        val workspaceSentinel = File(legacyWorkspace, "caller-owned.txt").apply { writeText("keep") }
        val sourceConfigFile = File(sourceDir, ProjectConfig.CONFIG_FILE_NAME)

        try {
            val config = ProjectConfig()
                .setName("Facade test")
                .setPackageName("org.example.facade")
                .setVersionName("1.0.0")
                .setVersionCode(7)
                .setSourcePath(sourceDir.path)
                .setBuildInfo(BuildInfo.generate(3L))
            sourceConfigFile.writeText(config.toJson(false))

            val updatedConfig = checkNotNull(ProjectConfig.fromJson(config.toJson(false))).apply {
                buildInfo = BuildInfo.generate(4L)
            }
            val receivedConfig = arrayOfNulls<ProjectConfig>(1)
            val executor = ApkBuilder.RemoteBuildExecutor {
                    receivedContext,
                    projectConfig,
                    receivedOutput,
                    cancelSignal,
                    progressListener,
                ->
                assertSame(context.applicationContext, receivedContext)
                assertSame(config, projectConfig)
                assertSame(outputApk, receivedOutput)
                assertTrue(cancelSignal?.get() == false)
                receivedConfig[0] = projectConfig
                progressListener.onProgress(
                    ApkBuildProgress(
                        step = ApkBuildProgress.STEP_BUILD,
                        title = "Remote build",
                        detail = projectConfig.packageName,
                    )
                )
                receivedOutput.parentFile?.mkdirs()
                receivedOutput.writeBytes(byteArrayOf(0x50, 0x4B))
                org.autojs.autojs.apkbuilder.template.RemoteApkBuildClient.BuildOutcome(
                    status = ApkBuildResult.STATUS_OK,
                    updatedProjectConfigJson = updatedConfig.toJson(false),
                )
            }

            val lifecycleEvents = Collections.synchronizedList(mutableListOf<String>())
            val finished = CountDownLatch(1)
            val callback = object : ApkBuilder.ProgressCallback {
                override fun onPrepare(builder: ApkBuilder) {
                    lifecycleEvents += "prepare"
                }

                override fun onBuild(builder: ApkBuilder) {
                    lifecycleEvents += "build"
                }

                override fun onSign(builder: ApkBuilder) {
                    lifecycleEvents += "sign"
                }

                override fun onClean(builder: ApkBuilder) {
                    lifecycleEvents += "clean"
                }

                override fun onStepProgress(builder: ApkBuilder, title: String, detail: String?) = Unit

                override fun onFinished(builder: ApkBuilder) {
                    lifecycleEvents += "finish"
                    finished.countDown()
                }
            }

            ApkBuilder(null, outputApk, legacyWorkspace.path, executor)
                .setProgressCallback(callback)
                .setCancelSignal(AtomicBoolean(false))
                .prepare(context)
                .withConfig(context, config)
                .build(context)
                .sign(context)
                .commitProjectConfigIfNeeded(context)
                .cleanWorkspace(context)
                .finish()

            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertSame(config, receivedConfig[0])
            assertTrue(outputApk.isFile)
            assertTrue("The compatibility adapter must not delete caller-owned paths", workspaceSentinel.isFile)
            assertEquals(
                4L,
                checkNotNull(ProjectConfig.fromFile(sourceConfigFile)).buildInfo.buildNumber,
            )
            assertEquals(
                listOf("prepare", "build", "sign", "clean", "finish"),
                lifecycleEvents.toList(),
            )
        } finally {
            testRoot.deleteRecursively()
        }
    }
}
