package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBridgePermissionManifestInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun kotlinManifestReadsProjectAndPackageCapabilitiesAndMapsAndroidPermissions() {
        val workDir = File(context.cacheDir, "node-bridge-permission-manifest").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission manifest test directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["toast", "screen_capture", "app.launch", "unknown.future"]
              }
            }
            """.trimIndent(),
        )
        File(workDir, "package.json").writeText(
            """
            {
              "name": "bridge-permission-manifest",
              "autojs6": {
                "node": {
                  "permissions": ["network", "raw_network", "media_projection", "notifications"]
                }
              }
            }
            """.trimIndent(),
        )

        val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)

        assertTrue(manifest.enforced)
        assertEquals(
            listOf("app.launch", "network", "notifications", "raw_network", "screen_capture", "toast", "unknown.future"),
            manifest.permissions,
        )
        assertEquals(listOf("unknown.future"), manifest.unknownPermissions)
        assertTrue(manifest.sources.contains("project.json:node.permissions"))
        assertTrue(manifest.sources.contains("package.json:autojs6.node.permissions"))
        assertEquals(
            listOf(
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            ),
            manifest.androidPermissions,
        )
    }

    @Test
    fun kotlinRuntimeManifestMergesSandboxRootAndBuildNetworkPolicy() {
        val sandboxRoot = File(context.cacheDir, "node-bridge-runtime-manifest").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge runtime manifest test directory: $absolutePath" }
        }
        val workDir = File(sandboxRoot, "utils").apply {
            check(mkdirs()) { "Failed to create bridge runtime manifest work directory: $absolutePath" }
        }
        File(sandboxRoot, "package.json").writeText(
            """
            {
              "name": "bridge-runtime-manifest",
              "autojs6": {
                "node": {
                  "permissions": ["toast"]
                }
              }
            }
            """.trimIndent(),
        )

        val fallbackManifest = NodeBridgePermissionManifest.fromRuntimeWorkingDirectory(
            workingDirectory = workDir.absolutePath,
            sandboxRoot = sandboxRoot.absolutePath,
            includeBuildNetworkPolicy = false,
        )
        assertTrue(fallbackManifest.enforced)
        assertEquals(listOf("toast"), fallbackManifest.permissions)
        assertTrue(fallbackManifest.sources.contains("package.json:autojs6.node.permissions"))

        val buildNetworkManifest = NodeBridgePermissionManifest.fromRuntimeWorkingDirectory(
            workingDirectory = workDir.absolutePath,
            sandboxRoot = sandboxRoot.absolutePath,
            includeBuildNetworkPolicy = true,
        )
        assertTrue(buildNetworkManifest.enforced)
        assertTrue(buildNetworkManifest.permissions.contains(NodeBridgePermissionManifest.NETWORK))
        assertTrue(buildNetworkManifest.permissions.contains(NodeBridgePermissionManifest.TOAST))
        assertTrue(buildNetworkManifest.sources.contains("gradle:autojs.nodejs.network.experimental"))
        assertTrue(buildNetworkManifest.androidPermissions.contains(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET))
    }

    @Test
    fun kotlinRegistryDeclaresCapabilitiesForEverySupportedBridgeMethod() {
        NodeBridgeModules.supportedMethodsByModule.forEach { (module, methods) ->
            methods.forEach { method ->
                val required = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method)
                assertTrue("$module.$method must declare at least one bridge capability", required.isNotEmpty())
                required.forEach { capability ->
                    assertTrue(
                        "$module.$method declares unknown bridge capability: $capability",
                        NodeBridgePermissionManifest.definedCapabilities.contains(capability),
                    )
                }
            }
        }
        assertEquals(
            listOf(NodeBridgePermissionManifest.SHELL),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("shell", "exec", """["id",{"root":false}]"""),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.SHELL, NodeBridgePermissionManifest.SHELL_ROOT),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("shell", "exec", """["id",{"root":true}]"""),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.IMAGE, NodeBridgePermissionManifest.SCREEN_CAPTURE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("image", "captureScreen"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_QUERY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "getPackageName"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_QUERY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "getAppName"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_QUERY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "isInstalled"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_ACTIVITY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "viewFile"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_ACTIVITY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("app", "editFile"),
        )
        assertEquals(NodeBridgePermissionManifest.APP_QUERY, NodeBridgePermissionManifest.normalizeCapability("package-query"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.APP_QUERY),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.APP),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.APP_QUERY),
            ),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("image", "grayscale"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.ACCESSIBILITY),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("accessibility", "setText"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "recognize"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "recognizeText"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ocr", "detectTextBounds"),
        )
        listOf("storage", "storages").forEach { module ->
            assertEquals(
                listOf(NodeBridgePermissionManifest.STORAGE),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(module, "get"),
            )
            assertEquals(
                listOf(NodeBridgePermissionManifest.STORAGE),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(module, "put"),
            )
        }
        listOf("database", "sqlite").forEach { module ->
            assertEquals(
                listOf(NodeBridgePermissionManifest.DATABASE),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(module, "open"),
            )
            assertEquals(
                listOf(NodeBridgePermissionManifest.DATABASE),
                NodeBridgePermissionManifest.requiredCapabilitiesFor(module, "transaction"),
            )
        }
        assertEquals(NodeBridgePermissionManifest.DATABASE, NodeBridgePermissionManifest.normalizeCapability("sqlite"))
        assertEquals(NodeBridgePermissionManifest.PACKAGE_MANAGER, NodeBridgePermissionManifest.normalizeCapability("package-manager"))
        assertEquals(NodeBridgePermissionManifest.PACKAGE_MANAGER, NodeBridgePermissionManifest.normalizeCapability("android_package_manager"))
        assertEquals(NodeBridgePermissionManifest.PACKAGE_MANAGER_MUTATE, NodeBridgePermissionManifest.normalizeCapability("package-manager.mutate"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.PACKAGE_MANAGER),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("package_manager", "verify"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.PACKAGE_MANAGER, NodeBridgePermissionManifest.PACKAGE_MANAGER_MUTATE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("package_manager", "install"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", "notify"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", "getPermissionStatus"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", "openSettings"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("notifications", "cancelAll"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS, NodeBridgePermissionManifest.normalizeCapability("notification.settings"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("device", "openBatteryOptimizationSettings"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.DEVICE_POWER),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.DEVICE),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.DEVICE_POWER, NodeBridgePermissionManifest.normalizeCapability("device-power"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("media", "getAudioStreamInfo"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA_AUDIO),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_AUDIO),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.MEDIA_AUDIO, NodeBridgePermissionManifest.normalizeCapability("media-audio"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_METADATA),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("mediainfo", "read"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA_METADATA),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_METADATA),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.MEDIA_METADATA, NodeBridgePermissionManifest.normalizeCapability("mediainfo"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_RECORDING),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("recorder", "start"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.MEDIA_RECORDING),
            NodeBridgePermissionManifest.missingCapabilities(
                declaredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA),
                requiredCapabilities = listOf(NodeBridgePermissionManifest.MEDIA, NodeBridgePermissionManifest.MEDIA_RECORDING),
            ),
        )
        assertEquals(NodeBridgePermissionManifest.MEDIA_RECORDING, NodeBridgePermissionManifest.normalizeCapability("recorder"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.SENSORS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("sensors", "getAvailableSensors"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.SENSORS),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("sensors", "subscribe"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui", "showLayout"),
        )
        assertEquals(
            listOf(NodeBridgePermissionManifest.UI),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("ui", "drainEvents"),
        )
    }

    @Test
    fun androidProviderRejectsUndeclaredCapabilityBeforeDispatch() {
        val provider = AndroidNodeBridgeCapabilityProvider(context)
        val response = dispatch(
            provider,
            NodeBridgeRequest(
                id = "provider-undeclared-toast",
                module = "toast",
                method = "showToast",
                argsJson = """["blocked"]""",
                timeoutMs = 1000L,
            ),
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, response.error?.code)
        assertEquals("toast", response.error?.module)
        assertEquals("showToast", response.error?.method)
        assertEquals(listOf("toast"), response.error?.missingCapabilities)
    }

    @Test
    fun androidProviderRejectsManifestMissingCapabilityBeforeDispatch() {
        val workDir = File(context.cacheDir, "node-bridge-permission-provider-manifest").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission provider manifest directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "permissions": ["toast"]
              }
            }
            """.trimIndent(),
        )
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "provider-manifest-missing-capability",
                cwd = workDir.absolutePath,
            ),
        )
        val response = dispatch(
            provider,
            NodeBridgeRequest(
                id = "provider-manifest-a11y",
                module = "accessibility",
                method = "isEnabled",
                argsJson = "[]",
                timeoutMs = 1000L,
                permissions = listOf("accessibility"),
            ),
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, response.error?.code)
        assertEquals("accessibility", response.error?.module)
        assertEquals("isEnabled", response.error?.method)
        assertEquals(listOf("accessibility"), response.error?.missingCapabilities)
    }

    @Test
    fun androidProviderRejectsRootShellWithoutRootCapabilityBeforeExecution() {
        val workDir = File(context.cacheDir, "node-bridge-permission-root-shell").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission root shell directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "provider-root-shell",
                cwd = workDir.absolutePath,
            ),
        )
        val response = dispatch(
            provider,
            NodeBridgeRequest(
                id = "provider-root-shell-denied",
                module = "shell",
                method = "exec",
                argsJson = """["id",{"root":true,"timeoutMs":1000}]""",
                timeoutMs = 2000L,
                permissions = listOf("shell"),
            ),
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, response.error?.code)
        assertEquals(listOf("shell.root"), response.error?.missingCapabilities)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.created_count"])
    }

    @Test
    fun androidProviderRejectsPackageMutationWithoutMutationCapabilityBeforeExecution() {
        val workDir = File(context.cacheDir, "node-bridge-permission-package-mutate").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission package mutate directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "provider-package-mutate",
                cwd = workDir.absolutePath,
            ),
        )
        val response = dispatch(
            provider,
            NodeBridgeRequest(
                id = "provider-package-mutate-denied",
                module = "package_manager",
                method = "install",
                argsJson = """[{"name":"@autojs6/mutate","version":"1.0.0","sourceDir":"pkg","expectedIntegrity":"sha512-test","allowMutation":true}]""",
                timeoutMs = 2000L,
                permissions = listOf("package_manager"),
            ),
        )

        assertFalse(response.toString(), response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED, response.error?.code)
        assertEquals(listOf(NodeBridgePermissionManifest.PACKAGE_MANAGER_MUTATE), response.error?.missingCapabilities)
    }

    @Test
    fun androidPermissionMismatchIsDerivedFromCapabilityManifest() {
        val missing = NodeBridgePermissionManifest.missingAndroidPermissionsForCapabilities(
            capabilities = listOf("network", "raw_network", "screen_capture", "notifications", "media.recording"),
            declaredAndroidPermissions = listOf(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET),
        )

        assertEquals(
            listOf(
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_RECORD_AUDIO,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
            ),
            missing,
        )
    }

    @Test
    fun packagedManifestMismatchUsesCapabilityManifestPermissions() {
        val workDir = File(context.cacheDir, "node-bridge-permission-packaged-mismatch").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission packaged mismatch directory: $absolutePath" }
        }
        File(workDir, "package.json").writeText(
            """
            {
              "name": "packaged-capability-mismatch",
              "autojs6": {
                "node": {
                  "permissions": ["network", "media_projection", "notifications"]
                }
              }
            }
            """.trimIndent(),
        )
        val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)

        assertEquals(
            listOf(
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            ),
            manifest.androidPermissions,
        )
        assertEquals(
            listOf(
                NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
                NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
            ),
            NodeBridgePermissionManifest.missingAndroidPermissionsForCapabilities(
                manifest.permissions,
                listOf(
                    NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET,
                    NodeBridgePermissionManifest.ANDROID_PERMISSION_FOREGROUND_SERVICE,
                ),
            ),
        )
    }

    @Test
    fun packagedMetadataWarningsAreMirroredIntoManifestAndMetadata() {
        val workDir = File(context.cacheDir, "node-bridge-permission-packaged-warnings").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge permission packaged warnings directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "profile": "",
                "profiles": ["desktop_compat_opt_in", 7],
                "builtins": "fs",
                "filesystem": {
                  "roots": ["data", ""]
                },
                "network": {
                  "enabled": "yes"
                }
              }
            }
            """.trimIndent(),
        )
        File(workDir, "package.json").writeText(
            """
            {
              "name": "packaged-capability-warnings",
              "autojs6": {
                "node": {
                  "nativeAssets": ["", "native/addon.node"],
                  "filesystemRoots": "cache"
                }
              }
            }
            """.trimIndent(),
        )

        val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)
        val expectedWarnings = listOf(
            "project.json:node.profile must be a non-empty string.",
            "project.json:node.profiles[1] must be a non-empty string.",
            "project.json:node.builtins must be a JSON string array.",
            "project.json:node.filesystem.roots[1] must be a non-empty string.",
            "project.json:node.network.enabled must be a boolean when present.",
            "package.json:autojs6.node.nativeAssets[0] must be a non-empty string.",
            "package.json:autojs6.node.filesystemRoots must be a JSON string array.",
        )

        assertEquals(expectedWarnings, manifest.packagedMetadata.warnings)
        assertEquals(expectedWarnings, manifest.warnings)
        assertEquals(listOf("desktop_compat_opt_in"), manifest.packagedMetadata.requestedProfiles)
        assertEquals(listOf("native/addon.node"), manifest.packagedMetadata.nativeAssets)
        assertEquals(listOf("data"), manifest.packagedMetadata.filesystemRoots)
    }

    @Test
    fun jsManifestAllowsDeclaredToastRejectsUndeclaredAccessibilityAndReportsCapabilities() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node bridge permission manifest focused regression")

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("toast"))
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            """
            "nodejs";
            (async function main() {
              const bridge = require("autojs6:bridge");
              const profile = require("autojs6:profile");
              const requests = [];
              bridge.__test.setTransport({
                postMessage(message) {
                  const request = JSON.parse(message);
                  requests.push(request);
                  setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                    id: request.id,
                    ok: true,
                    result: null
                  })));
                }
              });

              await require("toast").showToast("declared", { timeoutMs: 1000 });

              let deniedCode = "";
              let missing = "";
              try {
                await require("accessibility").isEnabled({ timeoutMs: 1000 });
              } catch (error) {
                deniedCode = String(error && (error.autojs6Code || error.code || ""));
                missing = Array.isArray(error && error.missingCapabilities)
                  ? error.missingCapabilities.join("|")
                  : "";
              }

              const report = bridge.permissions();
              console.log("bridge.perm.toast.requests=" + requests.length + "/" + requests[0].permissions.join("|"));
              console.log("bridge.perm.denied=" + deniedCode + "/" + missing + "/" + requests.length);
              console.log("bridge.perm.report=" + report.enforced + "/" + report.normalizedPermissions.join("|") + "/" + profile.bridgePermissions.enforced);
              console.log("bridge.perm.pending=" + bridge.__test.pendingCount());
            })();
            """.trimIndent(),
            "<embedded-autojs-bridge-permission-manifest.js>",
            runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
        )

        NodeEmbeddedTestHarness.assertSucceeded("bridge permission manifest JS gate", result)
        assertTrue(result.stdout.contains("bridge.perm.toast.requests=1/toast"))
        assertTrue(
            result.stdout.contains(
                "bridge.perm.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}/accessibility/1",
            ),
        )
        assertTrue(result.stdout.contains("bridge.perm.report=true/toast/true"))
        assertTrue(result.stdout.contains("bridge.perm.pending=0"))
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        request: NodeBridgeRequest,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(request) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for ${request.module}.${request.method}", latch.await(2, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing ${request.module}.${request.method} response" }
    }
}
