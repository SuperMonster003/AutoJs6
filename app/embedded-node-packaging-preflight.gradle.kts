import groovy.json.JsonSlurper
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

private val embeddedNodePackagingReportVariant = "appDebug"
private val embeddedNodePackagingReportVariantTaskSuffix = "AppDebug"
private val embeddedNodePackagingRequiredNodeAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
private val embeddedNodePackagingKnownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
private val embeddedNodePackagingNativeLibs = listOf("libnode.so", "libc++_shared.so", "libautojs6-node.so")
private val embeddedNodePackagingReleaseFlavorProperty = "autojs.nodejs.releaseFlavor"
private val embeddedNodePackagingReleaseAbisProperty = "autojs.nodejs.releaseAbis"
private val embeddedNodePackagingTestDeviceProfileProperty = "autojs.nodejs.test.deviceProfile"
private val embeddedNodePackagingDefaultReleaseFlavor = "nodeFull"
private val embeddedNodePackagingScriptServiceClassName = "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService"
private val embeddedNodePackagingScriptServiceDexDescriptor =
    "L${embeddedNodePackagingScriptServiceClassName.replace('.', '/')};"
private val embeddedNodePackagingRuntimeBridgeClassName = "org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge"
private val embeddedNodePackagingRuntimeBridgeDexDescriptor =
    "L${embeddedNodePackagingRuntimeBridgeClassName.replace('.', '/')};"
private val embeddedNodePackagingLegacyLifecycleMethodName = "nativeRunEmbeddedScriptLifecycle"
private val embeddedNodePackagingLifecycleWithModulesMethodName = "nativeRunEmbeddedScriptLifecycleWithModules"
private val embeddedNodePackagingCurrentLifecycleWithModulesMethodName =
    "${embeddedNodePackagingLifecycleWithModulesMethodName}V2"
private val embeddedNodePackagingInrtNodeProjectRunnerMarker = "autojs6.inrt.node_project_runner.v1"
private val embeddedNodePackagingLegacyLifecycleJniSymbol =
    "Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycle"
private val embeddedNodePackagingLifecycleWithModulesJniSymbol =
    "Java_org_autojs_autojs_engine_NativeNodeEmbeddedRuntimeBridge_nativeRunEmbeddedScriptLifecycleWithModules"
private val embeddedNodePackagingCurrentLifecycleWithModulesJniSymbol =
    "${embeddedNodePackagingLifecycleWithModulesJniSymbol}V2"
private val embeddedNodePackagingProjectConfigEntry = "assets/project/project.json"
private val embeddedNodePackagingPackageJsonEntry = "assets/project/package.json"
private val embeddedNodePackagingRuntimeMatrixEntry = "assets/project/runtime-matrix.expected.txt"
private val embeddedNodePackagingPreloadBudgetEntry = "assets/project/preload-budget.expected.txt"
private val embeddedNodePackagingModuleSourcesLimit = 64
private val embeddedNodePackagingModuleSourceMaxBytes = 262_144
private val embeddedNodePackagingModuleSourcesMaxBytes = 524_288
private val embeddedNodePackagingModuleSourceBudgetExceededCode = "ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED"
private val embeddedNodePackagingInrtNodeProjectLaunchSourceMarker =
    "autojs6.inrt.node_project_launch_source.v2"
private val embeddedNodePackagingInrtNodeProjectStableRequestMarkers = listOf(
    "requestReady=",
    "requestLaunchKind=",
    "packageDirProjectRoot=",
    "workingDirectoryProjectRoot=",
    "argvCount=",
    "envKeys=",
)
private val embeddedNodePackagingInrtNodeProjectStableFailureMarkers = listOf(
    "autojs6.node_project_launch.failure.v1",
    "autojs6.node_project_runner.request_failure.v1",
)
private val embeddedNodePackagingFullSmokeMarkers = listOf(
    "packaged.node.version=v24.5.0",
    "packaged.text=packaged",
    "packaged.hash=a3453242",
    "packaged.stream=stream",
    "packaged.promise=drained",
    "packaged.cwd=project",
    "packaged.project_runner.ready=true",
    "packaged.project_runner.entry=src/main.js:true",
    "packaged.project_runner.cwd=project:true",
    "packaged.project_runner.require_main=true",
    "packaged.project_runner.root_fs=true",
    "packaged.project_runner.root_require=true",
    "packaged.project_runner.script_argv=true",
    "packaged.project_runner.lifecycle_env=true",
    "packaged.fs.mkdir=true",
    "packaged.fs.readdir=true",
    "packaged.fs.stat=true",
    "packaged.fs.lstat=true",
    "packaged.fs.append=true",
    "packaged.fs.copy=true",
    "packaged.fs.realpath=true",
    "packaged.fs.rm=true",
    "packaged.fs.promisify.extended=true",
    "packaged.fs.promises.ready=true",
    "packaged.fs.promises.alias=true",
    "packaged.buffer.builtin=true",
    "packaged.process.builtin=true",
    "packaged.process.alias=true",
    "packaged.constants.builtin=true",
    "packaged.constants.fs=true",
    "packaged.dynamic_require.ready=true",
    "packaged.dynamic_require.cache=true",
    "packaged.dynamic_require.json=true",
    "packaged.module.create_require.ready=true",
    "packaged.module.create_require.resolve=true",
    "packaged.module.create_require.cache=true",
    "packaged.cli.ready=true",
    "packaged.cli.argv=true",
    "packaged.cli.require_main=true",
    "packaged.cli.combo=true",
    "packaged.cli.process_constants=true",
    "packaged.glob.ready=true",
    "packaged.glob.matches=true",
    "packaged.glob.ignore=true",
    "packaged.glob.fs_promises=true",
    "packaged.dirent.ready=true",
    "packaged.dirent.readdir=true",
    "packaged.dirent.opendir=true",
    "packaged.dirent.async_iterator=true",
    "packaged.readlink.surface=true",
    "packaged.readlink.symlink_disabled=true",
    "packaged.config.ready=true",
    "packaged.config.plugin=true",
    "packaged.config.scoped=true",
    "packaged.config.output=true",
    "packaged.discovery.ready=true",
    "packaged.discovery.package_json=true",
    "packaged.discovery.plugin=true",
    "packaged.discovery.output=true",
    "packaged.url.file_url=true",
    "packaged.url.fs_url=true",
    "packaged.url.opendir_url=true",
    "packaged.url.sandbox=true",
    "packaged.cp.ready=true",
    "packaged.cp.recursive=true",
    "packaged.cp.url=true",
    "packaged.cp.callback=true",
    "packaged.utimes.ready=true",
    "packaged.cp.preserve_timestamps=true",
    "packaged.mkdtemp.ready=true",
    "packaged.rename.ready=true",
    "packaged.atomic.ready=true",
    "packaged.atomic.url=true",
    "packaged.cache_copy.ready=true",
    "packaged.chdir.ready=true",
    "packaged.chdir.fs=true",
    "packaged.chdir.require=true",
    "packaged.node_modules_scope.ready=true",
    "packaged.node_modules_scope.nearest=true",
    "packaged.node_modules_scope.imports=true",
)
private val embeddedNodePackagingRealNpmCorpusReadyMarker = "packaged.real_npm_corpus.ready=true"
private val embeddedNodePackagingRealNpmCorpusMarkers = listOf(
    embeddedNodePackagingRealNpmCorpusReadyMarker,
    "packaged.real.npm.ok=true",
    "packaged.real.npm.debug=pkg:test ready +2ms optional=true",
    "packaged.real.npm.buffer=ok",
    "packaged.real.npm.cache=true",
    "packaged.real.npm.batch2.ready=true",
    "packaged.real.npm.batch2.ok=true",
    "packaged.real.npm.string_width=true",
    "packaged.real.npm.strip_ansi=true",
    "packaged.real.npm.camelcase=true",
    "packaged.real.npm.brace_expansion=true",
)
private val embeddedNodePackagingRealNpmPackageJsonEntries = listOf(
    "assets/project/node_modules/is-number/package.json",
    "assets/project/node_modules/is-odd/package.json",
    "assets/project/node_modules/kind-of/package.json",
    "assets/project/node_modules/once/package.json",
    "assets/project/node_modules/wrappy/package.json",
    "assets/project/node_modules/ms/package.json",
    "assets/project/node_modules/debug/package.json",
    "assets/project/node_modules/debug/node_modules/ms/package.json",
    "assets/project/node_modules/picocolors/package.json",
    "assets/project/node_modules/safe-buffer/package.json",
    "assets/project/node_modules/camelcase/package.json",
    "assets/project/node_modules/decamelize/package.json",
    "assets/project/node_modules/ansi-regex/package.json",
    "assets/project/node_modules/strip-ansi/package.json",
    "assets/project/node_modules/is-fullwidth-code-point/package.json",
    "assets/project/node_modules/east-asian-width-lite/package.json",
    "assets/project/node_modules/emoji-regex-lite/package.json",
    "assets/project/node_modules/string-width/package.json",
    "assets/project/node_modules/has-flag/package.json",
    "assets/project/node_modules/supports-color-lite/package.json",
    "assets/project/node_modules/yargs-parser-lite/package.json",
    "assets/project/node_modules/escape-string-regexp/package.json",
    "assets/project/node_modules/balanced-match/package.json",
    "assets/project/node_modules/concat-map/package.json",
    "assets/project/node_modules/brace-expansion-lite/package.json",
)
private val embeddedNodePackagingRealNpmJavaScriptEntries = listOf(
    "assets/project/node_modules/is-number/index.js",
    "assets/project/node_modules/is-odd/index.js",
    "assets/project/node_modules/kind-of/index.js",
    "assets/project/node_modules/once/once.js",
    "assets/project/node_modules/wrappy/wrappy.js",
    "assets/project/node_modules/ms/index.js",
    "assets/project/node_modules/debug/src/index.js",
    "assets/project/node_modules/debug/src/common.js",
    "assets/project/node_modules/debug/node_modules/ms/index.js",
    "assets/project/node_modules/picocolors/picocolors.js",
    "assets/project/node_modules/safe-buffer/index.js",
    "assets/project/node_modules/camelcase/index.js",
    "assets/project/node_modules/decamelize/index.js",
    "assets/project/node_modules/ansi-regex/index.js",
    "assets/project/node_modules/strip-ansi/index.js",
    "assets/project/node_modules/is-fullwidth-code-point/index.js",
    "assets/project/node_modules/east-asian-width-lite/source/index.js",
    "assets/project/node_modules/emoji-regex-lite/index.js",
    "assets/project/node_modules/string-width/index.js",
    "assets/project/node_modules/has-flag/index.js",
    "assets/project/node_modules/supports-color-lite/index.js",
    "assets/project/node_modules/supports-color-lite/node.js",
    "assets/project/node_modules/yargs-parser-lite/build/index.cjs",
    "assets/project/node_modules/escape-string-regexp/index.js",
    "assets/project/node_modules/balanced-match/index.js",
    "assets/project/node_modules/concat-map/index.js",
    "assets/project/node_modules/brace-expansion-lite/index.js",
)
private val embeddedNodePackagingCjsResolverReadyMarker = "packaged.cjs.resolver.ready=true"
private val embeddedNodePackagingCjsResolverMarkers = listOf(
    embeddedNodePackagingCjsResolverReadyMarker,
    "packaged.cjs.resolver.ok=true",
    "packaged.cjs.exports=true",
    "packaged.cjs.imports=true",
    "packaged.cjs.self_reference=true",
    "packaged.cjs.scoped_self_reference=true",
    "packaged.cjs.require_resolve=true",
    "packaged.cjs.blocked=true",
    "packaged.cjs.esm_rejected=true",
)
private val embeddedNodePackagingCjsResolverPackageJsonEntries = listOf(
    "assets/project/node_modules/resolver-edge-pkg/package.json",
    "assets/project/node_modules/imports-edge-pkg/package.json",
    "assets/project/node_modules/@scope/resolver-self-pkg/package.json",
    "assets/project/node_modules/fallback-main-pkg/package.json",
)
private val embeddedNodePackagingCjsResolverJavaScriptEntries = listOf(
    "assets/project/node_modules/resolver-edge-pkg/index.js",
    "assets/project/node_modules/resolver-edge-pkg/features/a.js",
    "assets/project/node_modules/resolver-edge-pkg/condition/require.js",
    "assets/project/node_modules/resolver-edge-pkg/condition/node.js",
    "assets/project/node_modules/resolver-edge-pkg/condition/default.js",
    "assets/project/node_modules/resolver-edge-pkg/private/secret.js",
    "assets/project/node_modules/resolver-edge-pkg/esm.mjs",
    "assets/project/node_modules/imports-edge-pkg/index.js",
    "assets/project/node_modules/imports-edge-pkg/lib/alias.js",
    "assets/project/node_modules/imports-edge-pkg/lib/wild/a.js",
    "assets/project/node_modules/imports-edge-pkg/lib/condition/require.js",
    "assets/project/node_modules/imports-edge-pkg/lib/condition/node.js",
    "assets/project/node_modules/imports-edge-pkg/lib/condition/default.js",
    "assets/project/node_modules/imports-edge-pkg/lib/private.js",
    "assets/project/node_modules/imports-edge-pkg/esm.mjs",
    "assets/project/node_modules/@scope/resolver-self-pkg/index.js",
    "assets/project/node_modules/@scope/resolver-self-pkg/feature.js",
    "assets/project/node_modules/fallback-main-pkg/index.js",
)
private val embeddedNodePackagingDependencyResolutionReadyMarker = "packaged.cjs.dependency_resolution.ready=true"
private val embeddedNodePackagingDependencyResolutionMarkers = listOf(
    embeddedNodePackagingDependencyResolutionReadyMarker,
    "packaged.cjs.dependency_resolution.ok=true",
    "packaged.cjs.builtin_precedence=true",
    "packaged.cjs.nested_dependency=true",
    "packaged.cjs.subpath_resolution=true",
    "packaged.cjs.cache_identity=true",
    "packaged.node_modules_scope.ready=true",
    "packaged.node_modules_scope.nearest=true",
    "packaged.node_modules_scope.imports=true",
)
private val embeddedNodePackagingDependencyResolutionPackageJsonEntries = listOf(
    "assets/project/node_modules/path/package.json",
    "assets/project/node_modules/fs/package.json",
    "assets/project/node_modules/util/package.json",
    "assets/project/node_modules/resolution-shared/package.json",
    "assets/project/node_modules/resolution-parent/package.json",
    "assets/project/node_modules/resolution-parent/node_modules/resolution-shared/package.json",
)
private val embeddedNodePackagingDependencyResolutionJavaScriptEntries = listOf(
    "assets/project/node_modules/path/index.js",
    "assets/project/node_modules/fs/index.js",
    "assets/project/node_modules/util/index.js",
    "assets/project/node_modules/resolution-shared/index.js",
    "assets/project/node_modules/resolution-shared/subpath.js",
    "assets/project/node_modules/resolution-parent/index.js",
    "assets/project/node_modules/resolution-parent/node_modules/resolution-shared/index.js",
    "assets/project/node_modules/resolution-parent/node_modules/resolution-shared/subpath.js",
)
private val embeddedNodePackagingDiagnosticsReadyMarker = "packaged.diagnostics.ready=true"
private val embeddedNodePackagingDiagnosticsMarkers = listOf(
    embeddedNodePackagingDiagnosticsReadyMarker,
    "packaged.diagnostics.disabled_builtin.code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED",
    "packaged.diagnostics.disabled_builtin.message=true",
    "packaged.diagnostics.fs_escape.message=true",
    "packaged.diagnostics.fs_absolute.message=true",
    "packaged.diagnostics.cjs_stack.message=true",
    "packaged.diagnostics.cjs_stack.source=true",
    "packaged.diagnostics.esm.type_module.message=true",
    "packaged.diagnostics.esm.exports_context=true",
    "packaged.diagnostics.after_failure.ok=true",
    "packaged.diagnostics.esm.code=ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED",
    "packaged.diagnostics.timeout.code=ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT",
    "packaged.diagnostics.timeout.timedOut=true",
    "packaged.diagnostics.timeout.pendingTimers=true",
)
private val embeddedNodePackagingDiagnosticsPackageJsonEntries = listOf(
    "assets/project/node_modules/diagnostic-boom/package.json",
    "assets/project/node_modules/diagnostic-type-module/package.json",
    "assets/project/node_modules/diagnostic-esm-export-context/package.json",
)
private val embeddedNodePackagingDiagnosticsJavaScriptEntries = listOf(
    "assets/project/main.js",
    "assets/project/esm-entry.mjs",
    "assets/project/dynamic-import.js",
    "assets/project/dynamic-target.mjs",
    "assets/project/dynamic-legacy.cjs",
    "assets/project/timeout.js",
    "assets/project/node_modules/diagnostic-boom/index.js",
    "assets/project/node_modules/diagnostic-type-module/index.mjs",
    "assets/project/node_modules/diagnostic-esm-export-context/esm.mjs",
)
private val embeddedNodePackagingForegroundServicePermission = "android.permission.FOREGROUND_SERVICE"
private val embeddedNodePackagingForegroundServiceMediaProjectionPermission =
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
private val embeddedNodePackagingForegroundServiceSpecialUsePermission = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
private val embeddedNodePackagingInternetPermission = "android.permission.INTERNET"
private val embeddedNodePackagingKnownBridgeCapabilities = listOf(
    "toast",
    "app",
    "app.launch",
    "app.settings",
    "app.activity",
    "dialogs",
    "engines",
    "engines.exec",
    "accessibility",
    "screen_capture",
    "image",
    "ocr",
    "storage",
    "ui",
    "clipboard",
    "device",
    "shell",
    "shell.root",
    "network",
    "work_manager",
    "java_interop",
)
private val embeddedNodePackagingRelevantAndroidPermissions = listOf(
    embeddedNodePackagingInternetPermission,
    embeddedNodePackagingForegroundServicePermission,
    embeddedNodePackagingForegroundServiceMediaProjectionPermission,
    embeddedNodePackagingForegroundServiceSpecialUsePermission,
)
private val embeddedNodePackagingAndroidPermissionsByBridgeCapability = mapOf(
    "network" to listOf(embeddedNodePackagingInternetPermission),
    "screen_capture" to listOf(
        embeddedNodePackagingForegroundServicePermission,
        embeddedNodePackagingForegroundServiceMediaProjectionPermission,
        embeddedNodePackagingForegroundServiceSpecialUsePermission,
    ),
)
private val embeddedNodePackagingSpecialUseFgsSubtypeProperty = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
private val embeddedNodePackagingRefreshTaskName = "embeddedNodeRefreshInrtTemplateApk"
private val embeddedNodePackagedSmokePullTaskName = "embeddedNodePullPackagedSmokeApks"
private val embeddedNodePackagedSmokeHostVerifyTaskName = "embeddedNodePackagedSmokeHostVerify"
private val embeddedNodePackagingSmokePublicExportDir = "/sdcard/Download/AutoJs6/embedded-node-packaged-smoke"
private val embeddedNodePackagingSmokeApkNames = listOf(
    "PackagedNodeSmoke_v1.0.0.apk",
    "PackagedNodeScriptLaunchSmoke_v1.0.0.apk",
    "PackagedNodeBinLaunchSmoke_v1.0.0.apk",
    "PackagedNodeBinStringScriptSmoke_v1.0.0.apk",
    "PackagedNodeDiagnosticsSmoke_v1.0.0.apk",
    "PackagedNodeEsmDiagnosticsSmoke_v1.0.0.apk",
    "PackagedNodeDynamicImportDiagnosticsSmoke_v1.0.0.apk",
    "PackagedNodeTimeoutDiagnosticsSmoke_v1.0.0.apk",
    "PackagedNodeV11BridgeSmoke_v1.0.0.apk",
)
private val embeddedNodePackagingSmokeVerifierReadyApkNames = setOf(
    "PackagedNodeSmoke_v1.0.0.apk",
    "PackagedNodeDiagnosticsSmoke_v1.0.0.apk",
)
private val embeddedNodePackagingSmokeHostDirName = "embedded-node-packaged-smoke"

private fun embeddedNodePackagingTaskRequested(taskName: String): Boolean =
    gradle.startParameter.taskNames.any { requested ->
        requested == taskName || requested == ":app:$taskName" || requested.endsWith(":$taskName")
    }

if (embeddedNodePackagingTaskRequested(embeddedNodePackagingRefreshTaskName)) {
    tasks.matching { it.name == "lintVitalAnalyzeInrtRelease" }.configureEach {
        enabled = false
    }
}

private data class EmbeddedNodePackagingTools(
    val sdkDir: File,
    val aapt2: File,
)

private data class EmbeddedNodePackagingManifestInfo(
    val inspected: Boolean,
    val tool: File?,
    val error: String?,
    val packageName: String?,
    val minSdkVersion: String?,
    val targetSdkVersion: String?,
    val declaresInternetPermission: Boolean,
    val declaresForegroundServicePermission: Boolean,
    val declaresForegroundServiceMediaProjectionPermission: Boolean,
    val declaresForegroundServiceSpecialUsePermission: Boolean,
    val declaresEmbeddedProbeService: Boolean,
    val embeddedProbeServiceUsesNodeProcess: Boolean,
    val embeddedProbeServiceUsesSpecialUseFgs: Boolean,
    val declaresEmbeddedScriptService: Boolean,
    val embeddedScriptServiceUsesNodeProcess: Boolean,
    val embeddedScriptServiceUsesSpecialUseFgs: Boolean,
    val embeddedScriptServiceDeclaresSpecialUseSubtypeProperty: Boolean,
)

private data class EmbeddedNodePackagingTemplateFreshness(
    val templateApk: File,
    val manifest: EmbeddedNodePackagingManifestInfo,
    val dexEntries: List<String>,
    val dexDefinesScriptService: Boolean,
    val dexDefinesRuntimeBridge: Boolean,
    val dexContainsLegacyLifecycleMethod: Boolean,
    val dexContainsLifecycleWithModulesMethod: Boolean,
    val dexContainsCurrentLifecycleWithModulesMethod: Boolean,
    val dexContainsInrtNodeProjectRunnerMarker: Boolean,
    val dexContainsInrtNodeProjectLaunchSourceMarker: Boolean,
    val dexContainsInrtNodeProjectStableRequestMarkers: Map<String, Boolean>,
    val dexContainsInrtNodeProjectStableFailureMarkers: Map<String, Boolean>,
    val hasLibnode: Boolean,
    val hasBridge: Boolean,
    val hasLibcxx: Boolean,
    val arm64HasLibnode: Boolean,
    val arm64HasBridge: Boolean,
    val arm64HasLibcxx: Boolean,
    val x86LibnodePresent: Boolean,
    val x86BridgePresent: Boolean,
    val x86NodeRuntimePresent: Boolean,
    val bridgeLibContainsLegacyLifecycleSymbol: Boolean,
    val bridgeLibContainsLifecycleWithModulesSymbol: Boolean,
    val bridgeLibContainsCurrentLifecycleWithModulesSymbol: Boolean,
    val manifestServiceReady: Boolean,
    val manifestPermissionsReady: Boolean,
    val dexBridgeReady: Boolean,
    val inrtNodeProjectRunnerReady: Boolean,
    val inrtNodeProjectLaunchSourceReady: Boolean,
    val inrtNodeProjectStableRequestDiagnosticsReady: Boolean,
    val inrtNodeProjectStableFailureDiagnosticsReady: Boolean,
    val nativeLibsReady: Boolean,
    val ready: Boolean,
    val missing: List<String>,
)

private data class EmbeddedNodePackagedOutputVerification(
    val report: String,
    val ready: Boolean,
    val moduleSourcesReady: Boolean,
)

private data class EmbeddedNodePackagingModuleSourceBudgetEntry(
    val index: Int,
    val sourceName: String,
    val bytes: Int?,
)

private data class EmbeddedNodePackagingBridgeCapabilityReport(
    val enforced: Boolean,
    val sources: List<String>,
    val capabilities: List<String>,
    val unknownCapabilities: List<String>,
    val requiredAndroidPermissions: List<String>,
    val declaredRelevantAndroidPermissions: List<String>,
    val missingAndroidPermissions: List<String>,
    val unusedRelevantAndroidPermissions: List<String>,
) {
    val requiredManifestReady: Boolean
        get() = missingAndroidPermissions.isEmpty()

    val requiredOnlyReady: Boolean
        get() = unusedRelevantAndroidPermissions.isEmpty()
}

private data class EmbeddedNodeReleaseFlavorPolicy(
    val flavor: String,
    val expectedAbis: List<String>,
    val expectedAbisSource: String,
)

private fun String.toEmbeddedNodePackagingBoolean(): Boolean =
    trim().let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }

private fun embeddedNodePackagingNormalizeReleaseFlavor(rawFlavor: String): String =
    when (rawFlavor.trim().lowercase()) {
        "nodefull", "node-full", "full" -> "nodeFull"
        "nodemini", "node-mini", "mini" -> "nodeMini"
        "rhinoonly", "rhino-only", "rhino" -> "rhinoOnly"
        else -> throw GradleException(
            "Unsupported Embedded Node release flavor '$rawFlavor'. Use nodeFull, nodeMini, or rhinoOnly."
        )
    }

private fun embeddedNodePackagingReleaseAbiList(rawAbis: String?): List<String> =
    rawAbis
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

private fun embeddedNodePackagingReleaseFlavorPolicy(
    rawFlavor: String,
    rawReleaseAbis: String?,
    explicitExpectedAbis: List<String> = emptyList(),
): EmbeddedNodeReleaseFlavorPolicy {
    val flavor = embeddedNodePackagingNormalizeReleaseFlavor(rawFlavor)
    if (explicitExpectedAbis.isNotEmpty()) {
        return EmbeddedNodeReleaseFlavorPolicy(
            flavor = flavor,
            expectedAbis = explicitExpectedAbis,
            expectedAbisSource = "explicit autojs.embeddedNode.expectedAbis",
        )
    }

    val releaseAbis = embeddedNodePackagingReleaseAbiList(rawReleaseAbis)
    if (flavor == "nodeFull" &&
        releaseAbis.isNotEmpty() &&
        releaseAbis.toSet() != embeddedNodePackagingRequiredNodeAbis.toSet()
    ) {
        throw GradleException(
            "nodeFull must package the full Embedded Node ABI set: ${embeddedNodePackagingRequiredNodeAbis.joinToString()}. " +
                    "Use -P$embeddedNodePackagingReleaseFlavorProperty=nodeMini to publish a narrower ABI set."
        )
    }

    return EmbeddedNodeReleaseFlavorPolicy(
        flavor = flavor,
        expectedAbis = when (flavor) {
            "nodeFull" -> embeddedNodePackagingRequiredNodeAbis
            "nodeMini" -> releaseAbis.ifEmpty { listOf("arm64-v8a") }
            else -> emptyList()
        },
        expectedAbisSource = if (releaseAbis.isEmpty()) {
            "$embeddedNodePackagingReleaseFlavorProperty default"
        } else {
            embeddedNodePackagingReleaseAbisProperty
        },
    )
}

private fun embeddedNodePackagingApkNativeEntries(apk: File, libName: String): List<String> =
    if (!apk.isFile) {
        emptyList()
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith("/$libName") }
                .sorted()
                .toList()
        }
    }

private fun embeddedNodePackagingApkNativeAbis(apk: File, libName: String): List<String> =
    embeddedNodePackagingApkNativeEntries(apk, libName)
        .mapNotNull { entry ->
            val parts = entry.split('/')
            parts.getOrNull(1)
        }
        .distinct()
        .sorted()

private fun embeddedNodePackagingApkEntryNames(apk: File): Set<String> =
    if (!apk.isFile) {
        emptySet()
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .toSet()
        }
    }

private fun embeddedNodePackagingApkEntryBytes(apk: File, entryName: String): ByteArray? =
    if (!apk.isFile) {
        null
    } else {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }

private fun embeddedNodePackagingApkEntryText(apk: File, entryName: String): String? =
    embeddedNodePackagingApkEntryBytes(apk, entryName)?.toString(Charsets.UTF_8)

private fun embeddedNodePackagingPropertyMap(text: String?): Map<String, String> =
    text?.lineSequence()
        ?.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) {
                null
            } else {
                line.substring(0, index) to line.substring(index + 1)
            }
        }
        ?.toMap()
        .orEmpty()

private fun embeddedNodePackagingJsonMap(text: String?): Map<*, *>? =
    text
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { JsonSlurper().parseText(it) as? Map<*, *> }.getOrNull() }

private fun embeddedNodePackagingBridgeCapabilityReport(
    projectConfigText: String?,
    packageJsonText: String?,
    manifestInfo: EmbeddedNodePackagingManifestInfo,
): EmbeddedNodePackagingBridgeCapabilityReport {
    val sources = mutableListOf<String>()
    val capabilities = linkedSetOf<String>()
    fun collect(sourceName: String, value: Any?) {
        if (value !is Iterable<*>) {
            return
        }
        sources += sourceName
        value.forEach { item ->
            if (item is String && item.isNotBlank()) {
                capabilities += embeddedNodePackagingNormalizeBridgeCapability(item)
            }
        }
    }

    val projectJson = embeddedNodePackagingJsonMap(projectConfigText)
    val projectNode = projectJson?.get("node") as? Map<*, *>
    collect("project.json:node.permissions", projectNode?.get("permissions"))
    collect("project.json:node.bridgePermissions", projectNode?.get("bridgePermissions"))

    val packageJson = embeddedNodePackagingJsonMap(packageJsonText)
    val autojs6 = packageJson?.get("autojs6") as? Map<*, *>
    collect("package.json:autojs6.permissions", autojs6?.get("permissions"))
    collect("package.json:autojs6.bridgePermissions", autojs6?.get("bridgePermissions"))
    val autojs6Node = autojs6?.get("node") as? Map<*, *>
    collect("package.json:autojs6.node.permissions", autojs6Node?.get("permissions"))
    collect("package.json:autojs6.node.bridgePermissions", autojs6Node?.get("bridgePermissions"))

    val normalizedCapabilities = capabilities.filter { it.isNotEmpty() }.sorted()
    val unknownCapabilities = normalizedCapabilities.filterNot { it in embeddedNodePackagingKnownBridgeCapabilities }
    val requiredAndroidPermissions = normalizedCapabilities
        .flatMap { embeddedNodePackagingAndroidPermissionsByBridgeCapability[it].orEmpty() }
        .distinct()
        .sortedWith(compareBy({ embeddedNodePackagingRelevantAndroidPermissions.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }, { it }))
    val declaredRelevantAndroidPermissions = embeddedNodePackagingRelevantAndroidPermissions.filter { permission ->
        manifestInfo.declaresAndroidPermission(permission)
    }
    return EmbeddedNodePackagingBridgeCapabilityReport(
        enforced = sources.isNotEmpty(),
        sources = sources.distinct(),
        capabilities = normalizedCapabilities,
        unknownCapabilities = unknownCapabilities,
        requiredAndroidPermissions = requiredAndroidPermissions,
        declaredRelevantAndroidPermissions = declaredRelevantAndroidPermissions,
        missingAndroidPermissions = requiredAndroidPermissions.filterNot { it in declaredRelevantAndroidPermissions },
        unusedRelevantAndroidPermissions = declaredRelevantAndroidPermissions.filterNot { it in requiredAndroidPermissions },
    )
}

private fun embeddedNodePackagingNormalizeBridgeCapability(value: String): String =
    value.trim().lowercase().replace(':', '.').let { normalized ->
        when (normalized) {
            "media_projection", "media.projection", "screen.capture", "screencapture" -> "screen_capture"
            "engine.exec" -> "engines.exec"
            "storages" -> "storage"
            "java" -> "java_interop"
            else -> normalized
        }
    }

private fun EmbeddedNodePackagingManifestInfo.declaresAndroidPermission(permission: String): Boolean =
    when (permission) {
        embeddedNodePackagingInternetPermission -> declaresInternetPermission
        embeddedNodePackagingForegroundServicePermission -> declaresForegroundServicePermission
        embeddedNodePackagingForegroundServiceMediaProjectionPermission -> declaresForegroundServiceMediaProjectionPermission
        embeddedNodePackagingForegroundServiceSpecialUsePermission -> declaresForegroundServiceSpecialUsePermission
        else -> false
    }

private fun Map<String, String>.embeddedNodePackagingIntValue(key: String): Int? =
    this[key]?.toIntOrNull()

private fun embeddedNodePackagingModuleSourceBudgetEntries(
    values: Map<String, String>,
    prefix: String = "packaged.preload.module_sources.",
): List<EmbeddedNodePackagingModuleSourceBudgetEntry> =
    values.entries
        .asSequence()
        .mapNotNull { entry ->
            entry.key
                .removePrefix(prefix)
                .takeIf { it != entry.key }
                ?.toIntOrNull()
                ?.let { index ->
                    EmbeddedNodePackagingModuleSourceBudgetEntry(
                        index = index,
                        sourceName = entry.value,
                        bytes = values["$prefix$index.bytes"]?.toIntOrNull(),
                    )
                }
        }
        .sortedBy { it.index }
        .toList()

private fun ByteArray.embeddedNodePackagingContainsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty()) {
        return true
    }
    if (needle.size > size) {
        return false
    }
    for (start in 0..(size - needle.size)) {
        var matched = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matched = false
                break
            }
        }
        if (matched) {
            return true
        }
    }
    return false
}

private fun embeddedNodePackagingApkEntryContainsUtf8(apk: File, entryName: String, value: String): Boolean =
    embeddedNodePackagingApkEntryBytes(apk, entryName)
        ?.embeddedNodePackagingContainsBytes(value.toByteArray(Charsets.UTF_8)) == true

private fun embeddedNodePackagingJsonStringValue(json: String?, key: String): String? {
    if (json == null) {
        return null
    }
    val escapedKey = Regex.escape(key)
    return Regex(""""$escapedKey"\s*:\s*"([^"]*)"""")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
}

private fun embeddedNodePackagingProjectScriptEntry(projectConfigText: String?): String =
    embeddedNodePackagingJsonStringValue(projectConfigText, "main")
        ?.takeIf(String::isNotBlank)
        ?.replace('\\', '/')
        ?.let { "assets/project/$it" }
        ?: "assets/project/main.js"

private fun embeddedNodePackagingFormatFile(file: File): String =
    if (file.isFile) {
        "${file.absolutePath} (${file.length()} bytes)"
    } else {
        "missing: ${file.absolutePath}"
    }

private fun embeddedNodePackagingSha256(file: File): String? {
    if (!file.isFile) {
        return null
    }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun embeddedNodePackagingSha256Text(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(text.toByteArray(StandardCharsets.UTF_8))
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun embeddedNodePackagingApkEntrySha256(apk: File, entryName: String): String? {
    if (!apk.isFile) {
        return null
    }
    return ZipFile(apk).use { zip ->
        val entry = zip.getEntry(entryName) ?: return@use null
        val digest = MessageDigest.getInstance("SHA-256")
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private fun embeddedNodePackagingApkEntrySize(apk: File, entryName: String): Long? {
    if (!apk.isFile) {
        return null
    }
    return ZipFile(apk).use { zip ->
        zip.getEntry(entryName)?.size?.takeIf { it >= 0L }
    }
}

private fun embeddedNodePackagingInrtUniversalApkName(rootDir: File): String {
    val properties = Properties()
    val versionProperties = rootDir.resolve("version.properties")
    if (versionProperties.isFile) {
        versionProperties.inputStream().use(properties::load)
    }
    val versionName = properties.getProperty("VERSION_NAME", "unknown")
        .trim()
        .replace(Regex("\\s+"), "-")
        .lowercase()
    return "inrt-v$versionName-universal.apk"
}

private fun embeddedNodePackagingInrtReleaseUniversalApk(projectDir: File, rootDir: File): File {
    val apkDir = projectDir.resolve("build/outputs/apk/inrt/release")
    val expected = apkDir.resolve(embeddedNodePackagingInrtUniversalApkName(rootDir))
    if (expected.isFile) {
        return expected
    }
    return apkDir.listFiles { file ->
        file.isFile &&
                file.extension.equals("apk", ignoreCase = true) &&
                file.name.contains("universal", ignoreCase = true)
    }
        ?.maxByOrNull(File::lastModified)
        ?: expected
}

private fun embeddedNodePackagingMissingManifestInfo(
    tools: EmbeddedNodePackagingTools?,
    error: String,
): EmbeddedNodePackagingManifestInfo =
    EmbeddedNodePackagingManifestInfo(
        inspected = false,
        tool = tools?.aapt2,
        error = error,
        packageName = null,
        minSdkVersion = null,
        targetSdkVersion = null,
        declaresInternetPermission = false,
        declaresForegroundServicePermission = false,
        declaresForegroundServiceMediaProjectionPermission = false,
        declaresForegroundServiceSpecialUsePermission = false,
        declaresEmbeddedProbeService = false,
        embeddedProbeServiceUsesNodeProcess = false,
        embeddedProbeServiceUsesSpecialUseFgs = false,
        declaresEmbeddedScriptService = false,
        embeddedScriptServiceUsesNodeProcess = false,
        embeddedScriptServiceUsesSpecialUseFgs = false,
        embeddedScriptServiceDeclaresSpecialUseSubtypeProperty = false,
    )

private fun embeddedNodePackagingApkDexEntries(apk: File): List<String> =
    if (!apk.isFile) {
        emptyList()
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter { it == "classes.dex" || Regex("""classes\d+\.dex""").matches(it) }
                .sorted()
                .toList()
        }
    }

private fun ByteArray.embeddedNodePackagingU4(offset: Int): Int {
    require(offset >= 0 && offset + 4 <= size)
    return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
}

private fun embeddedNodePackagingDexString(data: ByteArray, offset: Int): String? {
    if (offset !in data.indices) {
        return null
    }
    var cursor = offset
    var shift = 0
    while (cursor < data.size) {
        val value = data[cursor++].toInt() and 0xff
        if ((value and 0x80) == 0) {
            break
        }
        shift += 7
        if (shift > 28) {
            return null
        }
    }
    val start = cursor
    while (cursor < data.size && data[cursor].toInt() != 0) {
        cursor += 1
    }
    if (cursor >= data.size) {
        return null
    }
    return String(data, start, cursor - start, Charsets.UTF_8)
}

private fun embeddedNodePackagingDexDefinesClass(data: ByteArray, descriptor: String): Boolean =
    runCatching {
        if (data.size < 112 || data[0].toInt().toChar() != 'd' || data[1].toInt().toChar() != 'e' || data[2].toInt().toChar() != 'x') {
            return@runCatching false
        }
        val stringIdsSize = data.embeddedNodePackagingU4(56)
        val stringIdsOff = data.embeddedNodePackagingU4(60)
        val typeIdsSize = data.embeddedNodePackagingU4(64)
        val typeIdsOff = data.embeddedNodePackagingU4(68)
        val classDefsSize = data.embeddedNodePackagingU4(96)
        val classDefsOff = data.embeddedNodePackagingU4(100)
        if (
            stringIdsSize < 0 ||
            typeIdsSize < 0 ||
            classDefsSize < 0 ||
            stringIdsOff < 0 ||
            typeIdsOff < 0 ||
            classDefsOff < 0 ||
            stringIdsOff + stringIdsSize * 4 > data.size ||
            typeIdsOff + typeIdsSize * 4 > data.size ||
            classDefsOff + classDefsSize * 32 > data.size
        ) {
            return@runCatching false
        }
        for (index in 0 until classDefsSize) {
            val classIdx = data.embeddedNodePackagingU4(classDefsOff + index * 32)
            if (classIdx !in 0 until typeIdsSize) {
                continue
            }
            val descriptorIdx = data.embeddedNodePackagingU4(typeIdsOff + classIdx * 4)
            if (descriptorIdx !in 0 until stringIdsSize) {
                continue
            }
            val stringDataOff = data.embeddedNodePackagingU4(stringIdsOff + descriptorIdx * 4)
            if (embeddedNodePackagingDexString(data, stringDataOff) == descriptor) {
                return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

private fun embeddedNodePackagingDexContainsString(data: ByteArray, value: String): Boolean =
    runCatching {
        if (data.size < 112 || data[0].toInt().toChar() != 'd' || data[1].toInt().toChar() != 'e' || data[2].toInt().toChar() != 'x') {
            return@runCatching false
        }
        val stringIdsSize = data.embeddedNodePackagingU4(56)
        val stringIdsOff = data.embeddedNodePackagingU4(60)
        if (stringIdsSize < 0 || stringIdsOff < 0 || stringIdsOff + stringIdsSize * 4 > data.size) {
            return@runCatching false
        }
        for (index in 0 until stringIdsSize) {
            val stringDataOff = data.embeddedNodePackagingU4(stringIdsOff + index * 4)
            if (embeddedNodePackagingDexString(data, stringDataOff) == value) {
                return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

private fun embeddedNodePackagingDexContainsStringFragment(data: ByteArray, value: String): Boolean =
    runCatching {
        if (data.size < 112 || data[0].toInt().toChar() != 'd' || data[1].toInt().toChar() != 'e' || data[2].toInt().toChar() != 'x') {
            return@runCatching false
        }
        val stringIdsSize = data.embeddedNodePackagingU4(56)
        val stringIdsOff = data.embeddedNodePackagingU4(60)
        if (stringIdsSize < 0 || stringIdsOff < 0 || stringIdsOff + stringIdsSize * 4 > data.size) {
            return@runCatching false
        }
        for (index in 0 until stringIdsSize) {
            val stringDataOff = data.embeddedNodePackagingU4(stringIdsOff + index * 4)
            if (embeddedNodePackagingDexString(data, stringDataOff)?.contains(value) == true) {
                return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

private fun embeddedNodePackagingApkDexDefinesClass(apk: File, descriptor: String): Boolean =
    if (!apk.isFile) {
        false
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name == "classes.dex" || Regex("""classes\d+\.dex""").matches(it.name) }
                .any { entry ->
                    zip.getInputStream(entry).use { input ->
                        embeddedNodePackagingDexDefinesClass(input.readBytes(), descriptor)
                    }
                }
        }
    }

private fun embeddedNodePackagingApkDexContainsString(apk: File, value: String): Boolean =
    if (!apk.isFile) {
        false
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name == "classes.dex" || Regex("""classes\d+\.dex""").matches(it.name) }
                .any { entry ->
                    zip.getInputStream(entry).use { input ->
                        embeddedNodePackagingDexContainsString(input.readBytes(), value)
                    }
                }
        }
    }

private fun embeddedNodePackagingApkDexContainsStringFragment(apk: File, value: String): Boolean =
    if (!apk.isFile) {
        false
    } else {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filter { it.name == "classes.dex" || Regex("""classes\d+\.dex""").matches(it.name) }
                .any { entry ->
                    zip.getInputStream(entry).use { input ->
                        embeddedNodePackagingDexContainsStringFragment(input.readBytes(), value)
                    }
                }
        }
    }

private fun embeddedNodePackagingCsvList(value: String?): List<String> =
    value.orEmpty()
        .split(',', ';', ' ', '\n', '\r', '\t')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

private fun embeddedNodePackagingTestDeviceProfile(): String? =
    providers.gradleProperty(embeddedNodePackagingTestDeviceProfileProperty).orNull
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?.also { profile ->
            if (profile !in setOf("sony", "emulator", "smoke", "full")) {
                throw GradleException(
                    "Unsupported Embedded Node test device profile '$profile'. " +
                            "Use sony, emulator, smoke, or full."
                )
            }
        }

private fun embeddedNodePackagingRequiresHeavyDeviceProfile(taskName: String, profile: String?) {
    if (profile !in setOf("sony", "full")) {
        throw GradleException(
            "$taskName requires -P$embeddedNodePackagingTestDeviceProfileProperty=sony or full " +
                    "so heavy connected-device stress gates are selected explicitly. Current profile: ${profile ?: "unset"}."
        )
    }
}

private fun embeddedNodePackagingResolveFile(rootDir: File, path: String?): File? =
    path?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { File(it) }
        ?.let { file -> if (file.isAbsolute) file else rootDir.resolve(file.path) }

private fun String.containsAllFragments(vararg fragments: String): Boolean =
    fragments.all { contains(it) }

private fun findEmbeddedNodePackagingSdkDir(rootDir: File): File? {
    val localProperties = rootDir.resolve("local.properties")
    if (localProperties.isFile) {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let {
            return File(it)
        }
    }
    return listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .asSequence()
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .map(::File)
        .firstOrNull(File::isDirectory)
}

private fun findEmbeddedNodePackagingTools(rootDir: File): EmbeddedNodePackagingTools? {
    val sdkDir = findEmbeddedNodePackagingSdkDir(rootDir) ?: return null
    val exe = if (System.getProperty("os.name").contains("win", ignoreCase = true)) ".exe" else ""
    val aapt2 = sdkDir.resolve("build-tools")
        .listFiles()
        ?.filter(File::isDirectory)
        ?.sortedBy { it.name }
        ?.map { it.resolve("aapt2$exe") }
        ?.lastOrNull(File::isFile)
        ?: return null
    return EmbeddedNodePackagingTools(sdkDir, aapt2)
}

private fun findEmbeddedNodePackagingAdb(rootDir: File): File? {
    val sdkDir = findEmbeddedNodePackagingSdkDir(rootDir) ?: return null
    val exe = if (System.getProperty("os.name").contains("win", ignoreCase = true)) ".exe" else ""
    return sdkDir.resolve("platform-tools/adb$exe")
        .takeIf(File::isFile)
}

private fun embeddedNodePackagingAdbCommand(adb: File, serial: String?, vararg args: String): Array<String> =
    buildList {
        add(adb.absolutePath)
        serial?.takeIf(String::isNotBlank)?.let {
            add("-s")
            add(it)
        }
        addAll(args)
    }.toTypedArray()

private fun runEmbeddedNodePackagingCommand(vararg command: String): Pair<Int, String> {
    val process = ProcessBuilder(command.toList())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}

private fun dumpEmbeddedNodePackagingManifestXmlTree(tools: EmbeddedNodePackagingTools?, apk: File): Pair<File?, String?> {
    if (tools == null || !tools.aapt2.isFile) {
        return null to "aapt2 was not found"
    }
    if (!apk.isFile) {
        return tools.aapt2 to "APK is missing: ${apk.absolutePath}"
    }
    val (exitCode, output) = runEmbeddedNodePackagingCommand(
        tools.aapt2.absolutePath,
        "dump",
        "xmltree",
        "--file",
        "AndroidManifest.xml",
        apk.absolutePath,
    )
    return tools.aapt2 to (output.takeIf { exitCode == 0 } ?: "aapt2 dump xmltree failed with exit=$exitCode: $output")
}

private fun embeddedNodePackagingRegexValue(text: String, pattern: Regex): String? =
    pattern.find(text)?.groupValues?.getOrNull(1)

private fun embeddedNodePackagingXmlTreeElementBlocks(xmlTree: String, elementName: String): List<String> {
    val lines = xmlTree.lines()
    val blocks = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val elementMarker = "E: $elementName"
        if (!line.contains(elementMarker)) {
            index += 1
            continue
        }
        val indent = line.indexOf('E').takeIf { it >= 0 } ?: 0
        val blockLines = mutableListOf(line)
        index += 1
        while (index < lines.size) {
            val nextLine = lines[index]
            val nextElementIndex = nextLine.indexOf("E: ")
            if (nextElementIndex >= 0 && nextElementIndex <= indent) {
                break
            }
            blockLines += nextLine
            index += 1
        }
        blocks += blockLines.joinToString("\n")
    }
    return blocks
}

private fun embeddedNodePackagingServiceBlock(xmlTree: String, className: String): String? =
    embeddedNodePackagingXmlTreeElementBlocks(xmlTree, "service")
        .firstOrNull { it.contains(className) }

private fun embeddedNodePackagingManifestInfo(tools: EmbeddedNodePackagingTools?, apk: File): EmbeddedNodePackagingManifestInfo {
    val (tool, dumpOrError) = dumpEmbeddedNodePackagingManifestXmlTree(tools, apk)
    val xmlTree = dumpOrError?.takeIf { !it.startsWith("aapt2 was not found") && !it.startsWith("APK is missing") && !it.startsWith("aapt2 dump xmltree failed") }
    if (xmlTree == null) {
        return embeddedNodePackagingMissingManifestInfo(tools, dumpOrError ?: "manifest xmltree dump returned no output")
    }

    val probeService = "org.autojs.autojs.engine.NativeNodeEmbeddedProbeService"
    val probeBlock = embeddedNodePackagingServiceBlock(xmlTree, probeService)
    val scriptBlock = embeddedNodePackagingServiceBlock(xmlTree, embeddedNodePackagingScriptServiceClassName)
    return EmbeddedNodePackagingManifestInfo(
        inspected = true,
        tool = tool,
        error = null,
        packageName = embeddedNodePackagingRegexValue(xmlTree, Regex("""A: package="([^"]+)"""")),
        minSdkVersion = embeddedNodePackagingRegexValue(xmlTree, Regex("""minSdkVersion[^\n=]*=(\d+)""")),
        targetSdkVersion = embeddedNodePackagingRegexValue(xmlTree, Regex("""targetSdkVersion[^\n=]*=(\d+)""")),
        declaresInternetPermission = xmlTree.contains("$embeddedNodePackagingInternetPermission\""),
        declaresForegroundServicePermission = xmlTree.contains("$embeddedNodePackagingForegroundServicePermission\""),
        declaresForegroundServiceMediaProjectionPermission = xmlTree.contains("$embeddedNodePackagingForegroundServiceMediaProjectionPermission\""),
        declaresForegroundServiceSpecialUsePermission = xmlTree.contains("$embeddedNodePackagingForegroundServiceSpecialUsePermission\""),
        declaresEmbeddedProbeService = probeBlock != null,
        embeddedProbeServiceUsesNodeProcess = probeBlock?.contains("\":nodejs_embedded\"") == true,
        embeddedProbeServiceUsesSpecialUseFgs = probeBlock?.contains("foregroundServiceType") == true && probeBlock.contains("0x40000000"),
        declaresEmbeddedScriptService = scriptBlock != null,
        embeddedScriptServiceUsesNodeProcess = scriptBlock?.contains("\":nodejs_embedded\"") == true,
        embeddedScriptServiceUsesSpecialUseFgs = scriptBlock?.contains("foregroundServiceType") == true && scriptBlock.contains("0x40000000"),
        embeddedScriptServiceDeclaresSpecialUseSubtypeProperty = scriptBlock?.contains(embeddedNodePackagingSpecialUseFgsSubtypeProperty) == true,
    )
}

private fun embeddedNodePackagingNormalizedManifestText(info: EmbeddedNodePackagingManifestInfo): String =
    buildString {
        appendLine("inspected=${info.inspected}")
        appendLine("packageName=${info.packageName.orEmpty()}")
        appendLine("minSdkVersion=${info.minSdkVersion.orEmpty()}")
        appendLine("targetSdkVersion=${info.targetSdkVersion.orEmpty()}")
        appendLine("declaresInternetPermission=${info.declaresInternetPermission}")
        appendLine("declaresForegroundServicePermission=${info.declaresForegroundServicePermission}")
        appendLine("declaresForegroundServiceMediaProjectionPermission=${info.declaresForegroundServiceMediaProjectionPermission}")
        appendLine("declaresForegroundServiceSpecialUsePermission=${info.declaresForegroundServiceSpecialUsePermission}")
        appendLine("declaresEmbeddedProbeService=${info.declaresEmbeddedProbeService}")
        appendLine("embeddedProbeServiceUsesNodeProcess=${info.embeddedProbeServiceUsesNodeProcess}")
        appendLine("embeddedProbeServiceUsesSpecialUseFgs=${info.embeddedProbeServiceUsesSpecialUseFgs}")
        appendLine("declaresEmbeddedScriptService=${info.declaresEmbeddedScriptService}")
        appendLine("embeddedScriptServiceUsesNodeProcess=${info.embeddedScriptServiceUsesNodeProcess}")
        appendLine("embeddedScriptServiceUsesSpecialUseFgs=${info.embeddedScriptServiceUsesSpecialUseFgs}")
        appendLine("embeddedScriptServiceDeclaresSpecialUseSubtypeProperty=${info.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty}")
    }

private fun StringBuilder.appendEmbeddedNodePackagingManifestInfo(label: String, info: EmbeddedNodePackagingManifestInfo) {
    appendLine("  $label manifest inspected: ${info.inspected}")
    appendLine("  $label manifest parser: ${info.tool?.absolutePath ?: "unavailable"}")
    info.error?.let { appendLine("  $label manifest parser error: $it") }
    appendLine("  $label package: ${info.packageName ?: "unknown"}")
    appendLine("  $label minSdkVersion: ${info.minSdkVersion ?: "unknown"}")
    appendLine("  $label targetSdkVersion: ${info.targetSdkVersion ?: "unknown"}")
    appendLine("  $label declares INTERNET permission: ${info.declaresInternetPermission}")
    appendLine("  $label declares FOREGROUND_SERVICE permission: ${info.declaresForegroundServicePermission}")
    appendLine("  $label declares FOREGROUND_SERVICE_MEDIA_PROJECTION permission: ${info.declaresForegroundServiceMediaProjectionPermission}")
    appendLine("  $label declares FOREGROUND_SERVICE_SPECIAL_USE permission: ${info.declaresForegroundServiceSpecialUsePermission}")
    appendLine("  $label declares NativeNodeEmbeddedProbeService: ${info.declaresEmbeddedProbeService}")
    appendLine("  $label probe service process is :nodejs_embedded: ${info.embeddedProbeServiceUsesNodeProcess}")
    appendLine("  $label probe service specialUse FGS: ${info.embeddedProbeServiceUsesSpecialUseFgs}")
    appendLine("  $label declares NativeNodeEmbeddedScriptService: ${info.declaresEmbeddedScriptService}")
    appendLine("  $label script service process is :nodejs_embedded: ${info.embeddedScriptServiceUsesNodeProcess}")
    appendLine("  $label script service specialUse FGS: ${info.embeddedScriptServiceUsesSpecialUseFgs}")
    appendLine("  $label script service declares specialUse subtype property: ${info.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty}")
}

private fun StringBuilder.appendEmbeddedNodePackagingAbiMatrix(label: String, apk: File, abis: List<String>) {
    appendLine("  $label ABI matrix:")
    if (!apk.isFile) {
        appendLine("    APK missing: ${apk.absolutePath}")
        return
    }
    val entriesByLib = embeddedNodePackagingNativeLibs.associateWith { embeddedNodePackagingApkNativeAbis(apk, it).toSet() }
    abis.forEach { abi ->
        appendLine("    ABI: $abi")
        embeddedNodePackagingNativeLibs.forEach { libName ->
            appendLine("      $libName: ${abi in entriesByLib.getValue(libName)}")
        }
    }
}

private fun embeddedNodePackagingTemplateFreshness(
    tools: EmbeddedNodePackagingTools?,
    templateApk: File,
): EmbeddedNodePackagingTemplateFreshness {
    val manifest = embeddedNodePackagingManifestInfo(tools, templateApk)
    val dexEntries = embeddedNodePackagingApkDexEntries(templateApk)
    val dexDefinesScriptService =
        embeddedNodePackagingApkDexDefinesClass(templateApk, embeddedNodePackagingScriptServiceDexDescriptor)
    val dexDefinesRuntimeBridge =
        embeddedNodePackagingApkDexDefinesClass(templateApk, embeddedNodePackagingRuntimeBridgeDexDescriptor)
    val dexContainsLegacyLifecycleMethod =
        embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingLegacyLifecycleMethodName)
    val dexContainsLifecycleWithModulesMethod =
        embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingLifecycleWithModulesMethodName)
    val dexContainsCurrentLifecycleWithModulesMethod =
        embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingCurrentLifecycleWithModulesMethodName)
    val dexContainsInrtNodeProjectRunnerMarker =
        embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingInrtNodeProjectRunnerMarker)
    val dexContainsInrtNodeProjectLaunchSourceMarker =
        embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingInrtNodeProjectLaunchSourceMarker)
    val dexContainsInrtNodeProjectStableRequestMarkers =
        embeddedNodePackagingInrtNodeProjectStableRequestMarkers.associateWith { marker ->
            embeddedNodePackagingApkDexContainsStringFragment(templateApk, marker)
        }
    val dexContainsInrtNodeProjectStableFailureMarkers =
        embeddedNodePackagingInrtNodeProjectStableFailureMarkers.associateWith { marker ->
            embeddedNodePackagingApkDexContainsString(templateApk, marker)
        }
    val hasLibnode = templateApk.isFile && embeddedNodePackagingApkNativeEntries(templateApk, "libnode.so").isNotEmpty()
    val hasBridge = templateApk.isFile && embeddedNodePackagingApkNativeEntries(templateApk, "libautojs6-node.so").isNotEmpty()
    val hasLibcxx = templateApk.isFile && embeddedNodePackagingApkNativeEntries(templateApk, "libc++_shared.so").isNotEmpty()
    val arm64HasLibnode = "lib/arm64-v8a/libnode.so" in embeddedNodePackagingApkNativeEntries(templateApk, "libnode.so")
    val arm64HasBridge = "lib/arm64-v8a/libautojs6-node.so" in embeddedNodePackagingApkNativeEntries(templateApk, "libautojs6-node.so")
    val arm64HasLibcxx = "lib/arm64-v8a/libc++_shared.so" in embeddedNodePackagingApkNativeEntries(templateApk, "libc++_shared.so")
    val x86LibnodePresent = "lib/x86/libnode.so" in embeddedNodePackagingApkNativeEntries(templateApk, "libnode.so")
    val x86BridgePresent = "lib/x86/libautojs6-node.so" in embeddedNodePackagingApkNativeEntries(templateApk, "libautojs6-node.so")
    val x86NodeRuntimePresent = x86LibnodePresent || x86BridgePresent
    val bridgeLibEntry = "lib/arm64-v8a/libautojs6-node.so"
    val bridgeLibContainsLegacyLifecycleSymbol =
        embeddedNodePackagingApkEntryContainsUtf8(templateApk, bridgeLibEntry, embeddedNodePackagingLegacyLifecycleJniSymbol)
    val bridgeLibContainsLifecycleWithModulesSymbol =
        embeddedNodePackagingApkEntryContainsUtf8(templateApk, bridgeLibEntry, embeddedNodePackagingLifecycleWithModulesJniSymbol)
    val bridgeLibContainsCurrentLifecycleWithModulesSymbol =
        embeddedNodePackagingApkEntryContainsUtf8(templateApk, bridgeLibEntry, embeddedNodePackagingCurrentLifecycleWithModulesJniSymbol)
    val manifestServiceReady = manifest.declaresEmbeddedScriptService &&
            manifest.embeddedScriptServiceUsesNodeProcess &&
            manifest.embeddedScriptServiceUsesSpecialUseFgs &&
            manifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty
    val manifestPermissionsReady = manifest.declaresForegroundServicePermission &&
            manifest.declaresForegroundServiceSpecialUsePermission
    val dexBridgeReady = dexDefinesRuntimeBridge && dexContainsCurrentLifecycleWithModulesMethod
    val inrtNodeProjectRunnerReady = dexContainsInrtNodeProjectRunnerMarker
    val inrtNodeProjectLaunchSourceReady = dexContainsInrtNodeProjectLaunchSourceMarker
    val inrtNodeProjectStableRequestDiagnosticsReady =
        dexContainsInrtNodeProjectStableRequestMarkers.values.all { it }
    val inrtNodeProjectStableFailureDiagnosticsReady =
        dexContainsInrtNodeProjectStableFailureMarkers.values.all { it }
    val nativeLibsReady = arm64HasLibnode &&
            arm64HasBridge &&
            arm64HasLibcxx &&
            !x86NodeRuntimePresent
    val ready = hasLibnode &&
            hasBridge &&
            hasLibcxx &&
            manifestServiceReady &&
            manifestPermissionsReady &&
            dexDefinesScriptService &&
            dexBridgeReady &&
            inrtNodeProjectRunnerReady &&
            inrtNodeProjectLaunchSourceReady &&
            inrtNodeProjectStableRequestDiagnosticsReady &&
            inrtNodeProjectStableFailureDiagnosticsReady &&
            nativeLibsReady
    val missing = buildList {
        if (!hasLibnode) add("libnode.so")
        if (!hasBridge) add("libautojs6-node.so")
        if (!hasLibcxx) add("libc++_shared.so")
        if (!arm64HasLibnode) add("arm64-v8a/libnode.so")
        if (!arm64HasBridge) add("arm64-v8a/libautojs6-node.so")
        if (!arm64HasLibcxx) add("arm64-v8a/libc++_shared.so")
        if (x86NodeRuntimePresent) add("x86 Node runtime should be absent")
        if (!manifestPermissionsReady) add("foreground service permissions")
        if (!manifestServiceReady) add("NativeNodeEmbeddedScriptService manifest declaration")
        if (!dexDefinesScriptService) add("dex class_def for NativeNodeEmbeddedScriptService")
        if (!dexDefinesRuntimeBridge) add("dex class_def for NativeNodeEmbeddedRuntimeBridge")
        if (!dexContainsCurrentLifecycleWithModulesMethod) {
            add("dex string $embeddedNodePackagingCurrentLifecycleWithModulesMethodName")
        }
        if (!inrtNodeProjectRunnerReady) add("dex string $embeddedNodePackagingInrtNodeProjectRunnerMarker")
        if (!inrtNodeProjectLaunchSourceReady) {
            add("dex string $embeddedNodePackagingInrtNodeProjectLaunchSourceMarker")
        }
        dexContainsInrtNodeProjectStableRequestMarkers
            .filterValues { present -> !present }
            .keys
            .forEach { marker -> add("dex string $marker") }
        dexContainsInrtNodeProjectStableFailureMarkers
            .filterValues { present -> !present }
            .keys
            .forEach { marker -> add("dex string $marker") }
    }
    return EmbeddedNodePackagingTemplateFreshness(
        templateApk = templateApk,
        manifest = manifest,
        dexEntries = dexEntries,
        dexDefinesScriptService = dexDefinesScriptService,
        dexDefinesRuntimeBridge = dexDefinesRuntimeBridge,
        dexContainsLegacyLifecycleMethod = dexContainsLegacyLifecycleMethod,
        dexContainsLifecycleWithModulesMethod = dexContainsLifecycleWithModulesMethod,
        dexContainsCurrentLifecycleWithModulesMethod = dexContainsCurrentLifecycleWithModulesMethod,
        dexContainsInrtNodeProjectRunnerMarker = dexContainsInrtNodeProjectRunnerMarker,
        dexContainsInrtNodeProjectLaunchSourceMarker = dexContainsInrtNodeProjectLaunchSourceMarker,
        dexContainsInrtNodeProjectStableRequestMarkers = dexContainsInrtNodeProjectStableRequestMarkers,
        dexContainsInrtNodeProjectStableFailureMarkers = dexContainsInrtNodeProjectStableFailureMarkers,
        hasLibnode = hasLibnode,
        hasBridge = hasBridge,
        hasLibcxx = hasLibcxx,
        arm64HasLibnode = arm64HasLibnode,
        arm64HasBridge = arm64HasBridge,
        arm64HasLibcxx = arm64HasLibcxx,
        x86LibnodePresent = x86LibnodePresent,
        x86BridgePresent = x86BridgePresent,
        x86NodeRuntimePresent = x86NodeRuntimePresent,
        bridgeLibContainsLegacyLifecycleSymbol = bridgeLibContainsLegacyLifecycleSymbol,
        bridgeLibContainsLifecycleWithModulesSymbol = bridgeLibContainsLifecycleWithModulesSymbol,
        bridgeLibContainsCurrentLifecycleWithModulesSymbol = bridgeLibContainsCurrentLifecycleWithModulesSymbol,
        manifestServiceReady = manifestServiceReady,
        manifestPermissionsReady = manifestPermissionsReady,
        dexBridgeReady = dexBridgeReady,
        inrtNodeProjectRunnerReady = inrtNodeProjectRunnerReady,
        inrtNodeProjectLaunchSourceReady = inrtNodeProjectLaunchSourceReady,
        inrtNodeProjectStableRequestDiagnosticsReady = inrtNodeProjectStableRequestDiagnosticsReady,
        inrtNodeProjectStableFailureDiagnosticsReady = inrtNodeProjectStableFailureDiagnosticsReady,
        nativeLibsReady = nativeLibsReady,
        ready = ready,
        missing = missing,
    )
}

private fun StringBuilder.appendEmbeddedNodePackagingTemplateFreshness(
    label: String,
    freshness: EmbeddedNodePackagingTemplateFreshness,
) {
    appendLine("  $label APK exists: ${freshness.templateApk.isFile}")
    appendLine("  $label DEX entries: ${if (freshness.dexEntries.isEmpty()) "none" else freshness.dexEntries.joinToString()}")
    appendLine("  $label dex contains NativeNodeEmbeddedScriptService: ${freshness.dexDefinesScriptService}")
    appendLine("  $label dex contains NativeNodeEmbeddedRuntimeBridge: ${freshness.dexDefinesRuntimeBridge}")
    appendLine("  $label dex contains $embeddedNodePackagingLegacyLifecycleMethodName: ${freshness.dexContainsLegacyLifecycleMethod}")
    appendLine("  $label dex contains legacy $embeddedNodePackagingLifecycleWithModulesMethodName: ${freshness.dexContainsLifecycleWithModulesMethod}")
    appendLine("  $label dex contains current $embeddedNodePackagingCurrentLifecycleWithModulesMethodName: ${freshness.dexContainsCurrentLifecycleWithModulesMethod}")
    appendLine("  $label dex contains inrt node project runner marker: ${freshness.dexContainsInrtNodeProjectRunnerMarker}")
    appendLine("  $label dex contains inrt node project launch source marker: ${freshness.dexContainsInrtNodeProjectLaunchSourceMarker}")
    freshness.dexContainsInrtNodeProjectStableRequestMarkers.forEach { (marker, present) ->
        appendLine("  $label dex contains inrt node project request marker $marker: $present")
    }
    freshness.dexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
        appendLine("  $label dex contains inrt node project failure marker $marker: $present")
    }
    appendLine("  $label manifest service ready: ${freshness.manifestServiceReady}")
    appendLine("  $label manifest foreground-service permissions ready: ${freshness.manifestPermissionsReady}")
    appendLine("  $label inrt node project runner ready: ${freshness.inrtNodeProjectRunnerReady}")
    appendLine("  $label inrt node project launch source ready: ${freshness.inrtNodeProjectLaunchSourceReady}")
    appendLine("  $label inrt node project stable request diagnostics ready: ${freshness.inrtNodeProjectStableRequestDiagnosticsReady}")
    appendLine("  $label inrt node project stable failure diagnostics ready: ${freshness.inrtNodeProjectStableFailureDiagnosticsReady}")
    appendLine("  $label native libs ready: ${freshness.nativeLibsReady}")
    appendLine("  $label bridge JNI symbols report-only:")
    appendLine("    legacy $embeddedNodePackagingLegacyLifecycleMethodName: ${freshness.bridgeLibContainsLegacyLifecycleSymbol}")
    appendLine("    legacy $embeddedNodePackagingLifecycleWithModulesMethodName: ${freshness.bridgeLibContainsLifecycleWithModulesSymbol}")
    appendLine("    current $embeddedNodePackagingCurrentLifecycleWithModulesMethodName: ${freshness.bridgeLibContainsCurrentLifecycleWithModulesSymbol}")
    appendLine("  $label freshness ready: ${freshness.ready}")
    appendLine("  $label freshness missing: ${if (freshness.missing.isEmpty()) "none" else freshness.missing.joinToString()}")
}

private fun embeddedNodePackagingAllNativeLibsInSource(sourceJniLibsRoot: File, abi: String): Boolean =
    embeddedNodePackagingNativeLibs.all { libName -> sourceJniLibsRoot.resolve("$abi/$libName").isFile }

private fun embeddedNodePackagingAllNativeLibsInApk(apk: File, abi: String): Boolean =
    apk.isFile && embeddedNodePackagingNativeLibs.all { libName ->
        "lib/$abi/$libName" in embeddedNodePackagingApkNativeEntries(apk, libName)
    }

private fun embeddedNodePackagingAllNativeLibsInDirectory(nativeLibsRoot: File, abi: String): Boolean =
    embeddedNodePackagingNativeLibs.all { libName -> nativeLibsRoot.resolve("$abi/$libName").isFile }

private fun embeddedNodePackagingCompleteReadyAbisInApk(apk: File): List<String> =
    embeddedNodePackagingKnownAbis.filter { abi -> embeddedNodePackagingAllNativeLibsInApk(apk, abi) }

private fun embeddedNodePackagingSourceContains(file: File, vararg fragments: String): Boolean =
    file.isFile && file.readText().let { text -> fragments.all(text::contains) }

private fun embeddedNodePackagingSourceContainsAny(file: File, vararg fragments: String): Boolean =
    file.isFile && file.readText().let { text -> fragments.any(text::contains) }

val embeddedNodeRefreshInrtTemplateApk = tasks.register(embeddedNodePackagingRefreshTaskName) {
    group = "verification"
    description = "Refreshes assets-app/template.apk from the inrtRelease universal APK and verifies Embedded Node freshness."

    dependsOn("packageInrtRelease")
    outputs.file(projectDir.resolve("src/main/assets-app/template.apk"))
    outputs.file(layout.buildDirectory.file("reports/embedded-node/inrt-template-refresh.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val tools = findEmbeddedNodePackagingTools(rootProject.projectDir)
        val sourceApk = embeddedNodePackagingInrtReleaseUniversalApk(projectDir, rootProject.projectDir)
        val targetApk = projectDir.resolve("src/main/assets-app/template.apk")
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/inrt-template-refresh.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        if (!sourceApk.isFile) {
            val message = "inrtRelease universal APK is missing: ${sourceApk.absolutePath}"
            reportFile.writeText(
                buildString {
                    appendLine("Embedded Node.js inrt template refresh report")
                    appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
                    appendLine()
                    appendLine("Source:")
                    appendLine("  APK: ${embeddedNodePackagingFormatFile(sourceApk)}")
                    appendLine("  expected task dependency: packageInrtRelease")
                    appendLine()
                    appendLine("Status:")
                    appendLine("  template.refresh.ready=false")
                    appendLine("  template.refresh.error=$message")
                    appendLine()
                    appendLine("Fallback command if Gradle task graph changes:")
                    appendLine("  .\\gradlew.bat --console=plain :app:assembleInrtRelease -x :app:lintVitalAnalyzeInrtRelease")
                }
            )
            throw GradleException("$message. See ${reportFile.absolutePath}")
        }

        val sourceFreshness = embeddedNodePackagingTemplateFreshness(tools, sourceApk)
        if (!sourceFreshness.ready) {
            reportFile.writeText(
                buildString {
                    appendLine("Embedded Node.js inrt template refresh report")
                    appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
                    appendLine()
                    appendLine("Source:")
                    appendLine("  APK: ${embeddedNodePackagingFormatFile(sourceApk)}")
                    appendLine("  SHA-256: ${embeddedNodePackagingSha256(sourceApk) ?: "unavailable"}")
                    appendEmbeddedNodePackagingTemplateFreshness("source", sourceFreshness)
                    appendLine()
                    appendLine("Status:")
                    appendLine("  template.refresh.ready=false")
                    appendLine("  template.refresh.error=source inrtRelease APK is stale or incomplete")
                    appendLine("  template.refresh.missing=${if (sourceFreshness.missing.isEmpty()) "unknown" else sourceFreshness.missing.joinToString()}")
                }
            )
            throw GradleException(
                "Refusing to refresh template.apk from a stale inrtRelease APK. " +
                        "Missing: ${if (sourceFreshness.missing.isEmpty()) "unknown" else sourceFreshness.missing.joinToString()}. " +
                        "See ${reportFile.absolutePath}"
            )
        }

        targetApk.parentFile.mkdirs()
        sourceApk.copyTo(targetApk, overwrite = true)

        val targetFreshness = embeddedNodePackagingTemplateFreshness(tools, targetApk)
        val sourceSha256 = embeddedNodePackagingSha256(sourceApk)
        val targetSha256 = embeddedNodePackagingSha256(targetApk)
        val report = buildString {
            appendLine("Embedded Node.js inrt template refresh report")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine()
            appendLine("Refresh task:")
            appendLine("  task: :app:$embeddedNodePackagingRefreshTaskName")
            appendLine("  release package task dependency: packageInrtRelease")
            appendLine("  avoids known lint task: lintVitalAnalyzeInrtRelease")
            appendLine()
            appendLine("Source:")
            appendLine("  APK: ${embeddedNodePackagingFormatFile(sourceApk)}")
            appendLine("  SHA-256: ${sourceSha256 ?: "unavailable"}")
            appendEmbeddedNodePackagingTemplateFreshness("source", sourceFreshness)
            appendLine()
            appendLine("Target:")
            appendLine("  template.apk: ${embeddedNodePackagingFormatFile(targetApk)}")
            appendLine("  SHA-256: ${targetSha256 ?: "unavailable"}")
            appendEmbeddedNodePackagingTemplateFreshness("target", targetFreshness)
            appendLine()
            appendLine("Summary:")
            appendLine("  template.refresh.source=${sourceApk.absolutePath}")
            appendLine("  template.refresh.target=${targetApk.absolutePath}")
            appendLine("  template.refresh.sha256.source=${sourceSha256 ?: "unavailable"}")
            appendLine("  template.refresh.sha256.target=${targetSha256 ?: "unavailable"}")
            appendLine("  template.refresh.dex.legacy_with_modules=${targetFreshness.dexContainsLifecycleWithModulesMethod}")
            appendLine("  template.refresh.dex.with_modules=${targetFreshness.dexContainsCurrentLifecycleWithModulesMethod}")
            appendLine("  template.refresh.native.legacy_with_modules=${targetFreshness.bridgeLibContainsLifecycleWithModulesSymbol}")
            appendLine("  template.refresh.native.with_modules=${targetFreshness.bridgeLibContainsCurrentLifecycleWithModulesSymbol}")
            appendLine("  template.refresh.ready=${targetFreshness.ready}")
            appendLine("  template.refresh.missing=${if (targetFreshness.missing.isEmpty()) "none" else targetFreshness.missing.joinToString()}")
            appendLine()
            appendLine("Fallback command if this task cannot be used:")
            appendLine("  .\\gradlew.bat --console=plain :app:assembleInrtRelease -x :app:lintVitalAnalyzeInrtRelease")
        }
        reportFile.writeText(report)
        logger.lifecycle("Embedded Node.js inrt template refreshed: ${targetApk.absolutePath}")
        logger.lifecycle("Embedded Node.js inrt template SHA-256: ${targetSha256 ?: "unavailable"}")
        logger.lifecycle("Embedded Node.js inrt template freshness ready: ${targetFreshness.ready}")
        if (!targetFreshness.ready) {
            throw GradleException(
                "Refreshed template.apk is stale or incomplete. " +
                        "Missing: ${if (targetFreshness.missing.isEmpty()) "unknown" else targetFreshness.missing.joinToString()}. " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}

val verifyNodeTemplateReproducibility = tasks.register("verifyNodeTemplateReproducibility") {
    group = "verification"
    description = "Verifies current inrt template freshness, deterministic manifest summary, and native lib injection policy."

    val templateApk = projectDir.resolve("src/main/assets-app/template.apk")
    val reportFile = layout.buildDirectory
        .file("reports/embedded-node/template-reproducibility.txt")
        .get()
        .asFile

    outputs.file(reportFile)
    outputs.upToDateWhen { false }

    doLast {
        val tools = findEmbeddedNodePackagingTools(rootProject.projectDir)
        val freshness = embeddedNodePackagingTemplateFreshness(tools, templateApk)
        val templateSha256 = embeddedNodePackagingSha256(templateApk)
        val normalizedManifest = embeddedNodePackagingNormalizedManifestText(freshness.manifest)
        val normalizedManifestSha256 = embeddedNodePackagingSha256Text(normalizedManifest)
        val manifestHashReady = freshness.manifest.inspected && normalizedManifestSha256.isNotBlank()
        val manifestPatchDeterministic = manifestHashReady &&
                freshness.manifestServiceReady &&
                freshness.manifestPermissionsReady
        val nativeLibInjectionPolicy = "template-arm64-runtime-seed; packaged-output-injects-selected-abis-from-host-provider; x86-denied"
        val nativeLibInjectionPolicyReady = freshness.nativeLibsReady && !freshness.x86NodeRuntimePresent
        val missing = buildList {
            addAll(freshness.missing)
            if (templateSha256.isNullOrBlank()) {
                add("template APK SHA-256")
            }
            if (!manifestHashReady) {
                add("normalized manifest SHA-256")
            }
            if (!manifestPatchDeterministic) {
                add("deterministic manifest patch summary")
            }
            if (!nativeLibInjectionPolicyReady) {
                add("native lib injection policy")
            }
        }.distinct()
        val ready = freshness.ready &&
                !templateSha256.isNullOrBlank() &&
                manifestPatchDeterministic &&
                nativeLibInjectionPolicyReady

        reportFile.parentFile.mkdirs()
        reportFile.writeText(
            buildString {
                appendLine("Embedded Node.js template reproducibility report")
                appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
                appendLine()
                appendLine("Template:")
                appendLine("  APK: ${embeddedNodePackagingFormatFile(templateApk)}")
                appendLine("  SHA-256: ${templateSha256 ?: "unavailable"}")
                appendEmbeddedNodePackagingTemplateFreshness("template", freshness)
                appendLine()
                appendLine("Manifest reproducibility:")
                appendLine("  normalized manifest SHA-256: $normalizedManifestSha256")
                appendLine("  normalized manifest hash ready: $manifestHashReady")
                appendLine("  manifest patch deterministic: $manifestPatchDeterministic")
                appendLine("  normalized manifest fields:")
                normalizedManifest.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { line -> appendLine("    $line") }
                appendLine()
                appendLine("Native lib injection policy:")
                appendLine("  policy: $nativeLibInjectionPolicy")
                appendLine("  policy ready: $nativeLibInjectionPolicyReady")
                appendLine("  arm64 template seed ready: ${freshness.arm64HasLibnode && freshness.arm64HasBridge && freshness.arm64HasLibcxx}")
                appendLine("  x86 runtime absent: ${!freshness.x86NodeRuntimePresent}")
                appendLine()
                appendLine("Summary:")
                appendLine("  template.reproducibility.ready=$ready")
                appendLine("  template.reproducibility.missing=${if (missing.isEmpty()) "none" else missing.joinToString()}")
                appendLine("  template.refresh.ready=${freshness.ready}")
                appendLine("  template.refresh.missing=${if (freshness.missing.isEmpty()) "none" else freshness.missing.joinToString()}")
                appendLine("  template.apk.sha256=${templateSha256 ?: "unavailable"}")
                appendLine("  template.manifest.normalized.sha256=$normalizedManifestSha256")
                appendLine("  template.manifest.patch.deterministic=$manifestPatchDeterministic")
                appendLine("  template.native_lib_injection.policy=$nativeLibInjectionPolicy")
            }
        )
        logger.lifecycle("Embedded Node.js template reproducibility ready: $ready")
        logger.lifecycle("Embedded Node.js template reproducibility report: ${reportFile.absolutePath}")
        if (!ready) {
            throw GradleException(
                "Embedded Node.js template reproducibility failed. " +
                        "Missing: ${if (missing.isEmpty()) "unknown" else missing.joinToString()}. " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}

val embeddedNodePullPackagedSmokeApks = tasks.register(embeddedNodePackagedSmokePullTaskName) {
    group = "verification"
    description = "Pulls Embedded Node packaged smoke APK artifacts from a connected device into app/build."

    outputs.dir(layout.buildDirectory.dir(embeddedNodePackagingSmokeHostDirName))
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-smoke-apk-pull.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/packaged-smoke-apk-pull.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        val adb = findEmbeddedNodePackagingAdb(rootProject.projectDir)
        var adbSerialProperty: String? = null
        for (propertyName in listOf("autojs.embeddedNode.adbSerial", "embeddedNodeAdbSerial", "adbSerial")) {
            val value = providers.gradleProperty(propertyName).orNull?.trim()
            if (!value.isNullOrEmpty()) {
                adbSerialProperty = value
                break
            }
        }

        val hostDir = layout.buildDirectory
            .dir(embeddedNodePackagingSmokeHostDirName)
            .get()
            .asFile
        hostDir.deleteRecursively()
        hostDir.mkdirs()

        val report = StringBuilder()
        report.appendLine("Embedded Node.js packaged smoke APK pull report")
        report.appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
        report.appendLine()
        report.appendLine("Inputs:")
        report.appendLine("  adb: ${adb?.absolutePath ?: "unavailable"}")
        report.appendLine("  adb serial property: ${adbSerialProperty ?: "not set"}")
        report.appendLine("  device export dir: $embeddedNodePackagingSmokePublicExportDir")
        report.appendLine("  host output dir: ${hostDir.absolutePath}")
        report.appendLine()

        if (adb == null) {
            report.appendLine("Status:")
            report.appendLine("  embedded.node.packaged.smoke.pull.ready=false")
            report.appendLine("  embedded.node.packaged.smoke.pull.error=adb not found in Android SDK platform-tools")
            reportFile.writeText(report.toString())
            throw GradleException("adb not found in Android SDK platform-tools. See ${reportFile.absolutePath}")
        }

        val (devicesExit, devicesOutput) = runEmbeddedNodePackagingCommand(adb.absolutePath, "devices")
        report.appendLine("ADB devices:")
        report.appendLine(devicesOutput.trim().prependIndent("  "))
        report.appendLine()

        val (stateExit, stateOutput) = runEmbeddedNodePackagingCommand(
            *embeddedNodePackagingAdbCommand(adb, adbSerialProperty, "get-state")
        )
        report.appendLine("ADB state:")
        report.appendLine("  exit=$stateExit")
        report.appendLine(stateOutput.trim().prependIndent("  "))
        report.appendLine()
        if (devicesExit != 0 || stateExit != 0 || !stateOutput.contains("device")) {
            report.appendLine("Status:")
            report.appendLine("  embedded.node.packaged.smoke.pull.ready=false")
            report.appendLine("  embedded.node.packaged.smoke.pull.error=connected device unavailable")
            report.appendLine()
            report.appendLine("Hints:")
            report.appendLine("  Connect exactly one device, or pass -Pautojs.embeddedNode.adbSerial=<serial>.")
            report.appendLine("  Run EmbeddedNodePackagedApkSmokeTest before pulling artifacts.")
            reportFile.writeText(report.toString())
            throw GradleException("Connected Android device unavailable. See ${reportFile.absolutePath}")
        }

        val pulledFiles = mutableListOf<File>()
        val missingItems = mutableListOf<String>()
        embeddedNodePackagingSmokeApkNames.forEach { apkName ->
            val remotePath = "$embeddedNodePackagingSmokePublicExportDir/$apkName"
            val targetFile = hostDir.resolve(apkName)
            report.appendLine("Artifact: $apkName")
            report.appendLine("  remote: $remotePath")
            report.appendLine("  target: ${targetFile.absolutePath}")

            val (lsExit, lsOutput) = runEmbeddedNodePackagingCommand(
                *embeddedNodePackagingAdbCommand(adb, adbSerialProperty, "shell", "ls", "-l", remotePath)
            )
            report.appendLine("  remote ls exit: $lsExit")
            report.appendLine(lsOutput.trim().prependIndent("  remote ls: "))
            if (lsExit != 0 || lsOutput.contains("No such file", ignoreCase = true)) {
                missingItems += "$apkName missing on device"
                report.appendLine("  pulled: false")
                report.appendLine()
                return@forEach
            }

            val (pullExit, pullOutput) = runEmbeddedNodePackagingCommand(
                *embeddedNodePackagingAdbCommand(adb, adbSerialProperty, "pull", remotePath, targetFile.absolutePath)
            )
            report.appendLine("  adb pull exit: $pullExit")
            report.appendLine(pullOutput.trim().prependIndent("  adb pull: "))
            val pulled = pullExit == 0 && targetFile.isFile && targetFile.length() > 0L
            if (pulled) {
                pulledFiles += targetFile
            } else {
                missingItems += "$apkName pull failed or produced empty file"
            }
            report.appendLine("  pulled: $pulled")
            report.appendLine("  size: ${if (targetFile.isFile) targetFile.length().toString() else "missing"}")
            report.appendLine("  sha256: ${embeddedNodePackagingSha256(targetFile) ?: "unavailable"}")
            report.appendLine()
        }

        val ready = missingItems.isEmpty() && pulledFiles.size == embeddedNodePackagingSmokeApkNames.size
        report.appendLine("Verifier commands:")
        val verifierReadyFiles = pulledFiles.filter { it.name in embeddedNodePackagingSmokeVerifierReadyApkNames }
        verifierReadyFiles.forEach { file ->
            val expectedAbis = "arm64-v8a"
            report.appendLine(
                "  .\\gradlew.bat --console=plain :app:embeddedNodePackagedApkOutputVerify " +
                        "\"-Pautojs.embeddedNode.packagedApk=${file.absolutePath}\" " +
                        "\"-Pautojs.embeddedNode.expectedAbis=$expectedAbis\""
            )
        }
        if (verifierReadyFiles.isEmpty()) {
            report.appendLine("  none; no verifier-ready success/diagnostics APKs were pulled")
        }
        val installRunOnlyFiles = pulledFiles.filterNot { it.name in embeddedNodePackagingSmokeVerifierReadyApkNames }
        if (installRunOnlyFiles.isNotEmpty()) {
            report.appendLine("  install-and-run diagnostics only:")
            installRunOnlyFiles.forEach { file ->
                report.appendLine("    ${file.absolutePath}")
            }
        }
        report.appendLine()
        report.appendLine("Summary:")
        report.appendLine("  embedded.node.packaged.smoke.pull.host_dir=${hostDir.absolutePath}")
        embeddedNodePackagingSmokeApkNames.forEach { apkName ->
            val file = hostDir.resolve(apkName)
            report.appendLine("  embedded.node.packaged.smoke.pull.${apkName.removeSuffix(".apk")}.exists=${file.isFile}")
            report.appendLine("  embedded.node.packaged.smoke.pull.${apkName.removeSuffix(".apk")}.sha256=${embeddedNodePackagingSha256(file) ?: "unavailable"}")
        }
        report.appendLine("  embedded.node.packaged.smoke.pull.ready=$ready")
        report.appendLine("  embedded.node.packaged.smoke.pull.missing=${if (missingItems.isEmpty()) "none" else missingItems.joinToString()}")
        report.appendLine()
        report.appendLine("Hints:")
        report.appendLine("  Run this after EmbeddedNodePackagedApkSmokeTest has exported all current smoke APKs.")
        report.appendLine("  Device export dir must contain exactly the current smoke APK names; this task cleans the host target before pulling.")

        reportFile.writeText(report.toString())
        logger.lifecycle("Embedded Node.js packaged smoke APK pull report written to ${reportFile.absolutePath}")
        logger.lifecycle("Embedded Node.js packaged smoke APK pull ready: $ready")
        if (!ready) {
            throw GradleException(
                "Embedded Node.js packaged smoke APK pull failed. " +
                        "Missing: ${if (missingItems.isEmpty()) "unknown" else missingItems.joinToString()}. " +
                        "Run EmbeddedNodePackagedApkSmokeTest first, then rerun :app:$embeddedNodePackagedSmokePullTaskName. " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}

val embeddedNodePackagingPreflightAppDebug = tasks.register("embeddedNodePackagingPreflightAppDebug") {
    group = "verification"
    description = "Writes an Embedded Node.js appDebug packaging/distribution preflight report."

    dependsOn("assemble$embeddedNodePackagingReportVariantTaskSuffix")
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaging-preflight-$embeddedNodePackagingReportVariant.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/packaging-preflight-$embeddedNodePackagingReportVariant.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend").orElse("rhino").get()
            .trim()
            .lowercase()
            .let { if (it in setOf("rhino", "native", "embedded", "auto")) it else "rhino" }
        val scriptExecutionEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled")
            .orElse("false")
            .get()
            .toEmbeddedNodePackagingBoolean()
        val releaseFlavorPolicy = embeddedNodePackagingReleaseFlavorPolicy(
            rawFlavor = providers.gradleProperty(embeddedNodePackagingReleaseFlavorProperty)
                .orElse(embeddedNodePackagingDefaultReleaseFlavor)
                .get(),
            rawReleaseAbis = providers.gradleProperty(embeddedNodePackagingReleaseAbisProperty).orNull,
        )
        val releaseFlavor = releaseFlavorPolicy.flavor
        val releaseExpectedAbis = releaseFlavorPolicy.expectedAbis
        val releaseUnsupportedAbis = releaseExpectedAbis.filterNot { it in embeddedNodePackagingRequiredNodeAbis }
        val nativeRuntimeRequested = releaseFlavor != "rhinoOnly" && runtimeBackend in setOf("native", "embedded", "auto")
        val sourceJniLibsRoot = projectDir.resolve("src/main/jniLibs")
        val mergedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/$embeddedNodePackagingReportVariant/merge${embeddedNodePackagingReportVariantTaskSuffix}NativeLibs/out/lib")
            .get()
            .asFile
        val appDebugApkDir = layout.buildDirectory.dir("outputs/apk/app/debug").get().asFile
        val appDebugApks = appDebugApkDir
            .listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.sortedBy(File::getName)
            .orEmpty()
        val appDebugUniversalApk = appDebugApks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        val templateApk = projectDir.resolve("src/main/assets-app/template.apk")
        val manifest = projectDir.resolve("src/main/AndroidManifest.xml")
        val manifestText = manifest.takeIf(File::isFile)?.readText().orEmpty()
        val tools = findEmbeddedNodePackagingTools(rootProject.projectDir)
        val appDebugUniversalManifest = appDebugUniversalApk
            ?.let { embeddedNodePackagingManifestInfo(tools, it) }
            ?: embeddedNodePackagingMissingManifestInfo(tools, "appDebug universal APK is missing")
        val templateFreshness = embeddedNodePackagingTemplateFreshness(tools, templateApk)
        val templateManifest = templateFreshness.manifest
        val appDebugUniversalDexEntries = appDebugUniversalApk?.let { embeddedNodePackagingApkDexEntries(it) }.orEmpty()
        val appDebugUniversalDexDefinesScriptService = appDebugUniversalApk
            ?.let { embeddedNodePackagingApkDexDefinesClass(it, embeddedNodePackagingScriptServiceDexDescriptor) } == true
        val templateDexEntries = templateFreshness.dexEntries
        val templateDexDefinesScriptService = templateFreshness.dexDefinesScriptService
        val templateDexDefinesRuntimeBridge = templateFreshness.dexDefinesRuntimeBridge
        val templateDexContainsLegacyLifecycleMethod = templateFreshness.dexContainsLegacyLifecycleMethod
        val templateDexContainsLifecycleWithModulesMethod = templateFreshness.dexContainsLifecycleWithModulesMethod
        val templateDexContainsCurrentLifecycleWithModulesMethod =
            templateFreshness.dexContainsCurrentLifecycleWithModulesMethod
        val templateDexContainsInrtNodeProjectRunnerMarker = templateFreshness.dexContainsInrtNodeProjectRunnerMarker
        val templateDexContainsInrtNodeProjectLaunchSourceMarker =
            templateFreshness.dexContainsInrtNodeProjectLaunchSourceMarker
        val templateDexContainsInrtNodeProjectStableRequestMarkers =
            templateFreshness.dexContainsInrtNodeProjectStableRequestMarkers
        val templateDexContainsInrtNodeProjectStableFailureMarkers =
            templateFreshness.dexContainsInrtNodeProjectStableFailureMarkers
        val appDebugUniversalHasLibnode = appDebugUniversalApk?.let { embeddedNodePackagingApkNativeEntries(it, "libnode.so").isNotEmpty() } == true
        val appDebugUniversalHasBridge = appDebugUniversalApk?.let { embeddedNodePackagingApkNativeEntries(it, "libautojs6-node.so").isNotEmpty() } == true
        val appDebugUniversalHasLibcxx = appDebugUniversalApk?.let { embeddedNodePackagingApkNativeEntries(it, "libc++_shared.so").isNotEmpty() } == true
        val sourceLibnodeReadyAbis = embeddedNodePackagingKnownAbis
            .filter { abi -> sourceJniLibsRoot.resolve("$abi/libnode.so").isFile }
        val mergedCompleteReadyAbis = embeddedNodePackagingKnownAbis
            .filter { abi -> embeddedNodePackagingAllNativeLibsInDirectory(mergedNativeLibsRoot, abi) }
        val appDebugUniversalCompleteReadyAbis = appDebugUniversalApk
            ?.let { embeddedNodePackagingCompleteReadyAbisInApk(it) }
            .orEmpty()
        val sourceX86NodeRuntimePresent =
            sourceJniLibsRoot.resolve("x86/libnode.so").isFile ||
                    sourceJniLibsRoot.resolve("x86/libautojs6-node.so").isFile
        val mergedX86NodeRuntimePresent =
            mergedNativeLibsRoot.resolve("x86/libnode.so").isFile ||
                    mergedNativeLibsRoot.resolve("x86/libautojs6-node.so").isFile
        val appDebugUniversalX86NodeRuntimePresent = appDebugUniversalApk?.let { apk ->
            "lib/x86/libnode.so" in embeddedNodePackagingApkNativeEntries(apk, "libnode.so") ||
                    "lib/x86/libautojs6-node.so" in embeddedNodePackagingApkNativeEntries(apk, "libautojs6-node.so")
        } == true
        val releaseFlavorPolicyMissing = buildList {
            if (releaseUnsupportedAbis.isNotEmpty()) {
                add("unsupported Embedded Node release ABI(s): ${releaseUnsupportedAbis.joinToString()}")
            }
            when (releaseFlavor) {
                "rhinoOnly" -> {
                    if (appDebugUniversalHasLibnode || appDebugUniversalHasBridge) {
                        add("rhinoOnly appDebug APK must not package libnode.so or libautojs6-node.so")
                    }
                }
                else -> {
                    releaseExpectedAbis.filterNot { it in sourceLibnodeReadyAbis }.takeIf { it.isNotEmpty() }?.let {
                        add("src/main/jniLibs missing libnode.so for ABI(s): ${it.joinToString()}")
                    }
                    releaseExpectedAbis.filterNot { it in mergedCompleteReadyAbis }.takeIf { it.isNotEmpty() }?.let {
                        add("merged appDebug native libs missing complete Embedded Node libs for ABI(s): ${it.joinToString()}")
                    }
                    releaseExpectedAbis.filterNot { it in appDebugUniversalCompleteReadyAbis }.takeIf { it.isNotEmpty() }?.let {
                        add("appDebug universal APK missing complete Embedded Node libs for ABI(s): ${it.joinToString()}")
                    }
                    if (sourceX86NodeRuntimePresent || mergedX86NodeRuntimePresent || appDebugUniversalX86NodeRuntimePresent) {
                        add("x86 Embedded Node runtime must remain absent and fail-fast")
                    }
                }
            }
        }
        val releaseFlavorPolicyReady = releaseFlavorPolicyMissing.isEmpty()
        val templateHasLibnode = templateFreshness.hasLibnode
        val templateHasBridge = templateFreshness.hasBridge
        val templateHasLibcxx = templateFreshness.hasLibcxx
        val templateArm64HasLibnode = templateFreshness.arm64HasLibnode
        val templateArm64HasBridge = templateFreshness.arm64HasBridge
        val templateArm64HasLibcxx = templateFreshness.arm64HasLibcxx
        val templateX86LibnodePresent = templateFreshness.x86LibnodePresent
        val templateX86BridgePresent = templateFreshness.x86BridgePresent
        val templateX86NodeRuntimePresent = templateFreshness.x86NodeRuntimePresent
        val templateBridgeLibContainsLegacyLifecycleSymbol = templateFreshness.bridgeLibContainsLegacyLifecycleSymbol
        val templateBridgeLibContainsLifecycleWithModulesSymbol = templateFreshness.bridgeLibContainsLifecycleWithModulesSymbol
        val templateBridgeLibContainsCurrentLifecycleWithModulesSymbol =
            templateFreshness.bridgeLibContainsCurrentLifecycleWithModulesSymbol
        val templateManifestServiceReady = templateFreshness.manifestServiceReady
        val templateManifestPermissionsReady = templateFreshness.manifestPermissionsReady
        val templateDexBridgeReady = templateFreshness.dexBridgeReady
        val templateInrtNodeProjectRunnerReady = templateFreshness.inrtNodeProjectRunnerReady
        val templateInrtNodeProjectLaunchSourceReady = templateFreshness.inrtNodeProjectLaunchSourceReady
        val templateInrtNodeProjectStableRequestDiagnosticsReady =
            templateFreshness.inrtNodeProjectStableRequestDiagnosticsReady
        val templateInrtNodeProjectStableFailureDiagnosticsReady =
            templateFreshness.inrtNodeProjectStableFailureDiagnosticsReady
        val templateNativeLibsReady = templateFreshness.nativeLibsReady
        val hostAppReady = appDebugUniversalHasLibnode &&
                appDebugUniversalHasBridge &&
                appDebugUniversalHasLibcxx &&
                appDebugUniversalManifest.declaresEmbeddedScriptService &&
                appDebugUniversalManifest.embeddedScriptServiceUsesNodeProcess &&
                appDebugUniversalManifest.embeddedScriptServiceUsesSpecialUseFgs &&
                appDebugUniversalManifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty &&
                appDebugUniversalManifest.declaresForegroundServicePermission &&
                appDebugUniversalManifest.declaresForegroundServiceSpecialUsePermission &&
                appDebugUniversalDexDefinesScriptService
        val packagedTemplateReady = templateFreshness.ready
        val packagedTemplateMissing = templateFreshness.missing

        val report = buildString {
            appendLine("Embedded Node.js packaging preflight report: $embeddedNodePackagingReportVariant")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine()

            appendLine("Build gate and backend:")
            appendLine("  autojs.nodejs.backend: $runtimeBackend")
            appendLine("  $embeddedNodePackagingReleaseFlavorProperty: $releaseFlavor")
            appendLine("  release flavor expected ABI source: ${releaseFlavorPolicy.expectedAbisSource}")
            appendLine("  release flavor expected ABIs: ${if (releaseExpectedAbis.isEmpty()) "none" else releaseExpectedAbis.joinToString()}")
            appendLine("  release flavor unsupported ABIs: ${if (releaseUnsupportedAbis.isEmpty()) "none" else releaseUnsupportedAbis.joinToString()}")
            appendLine("  native runtime requested: $nativeRuntimeRequested")
            appendLine("  autojs.nodejs.embedded.script.enabled: $scriptExecutionEnabled")
            appendLine("  default-safe expectation: embedded script execution should remain false unless explicitly enabled")
            appendLine()

            appendLine("Tools:")
            appendLine("  Android SDK: ${tools?.sdkDir?.absolutePath ?: "unavailable"}")
            appendLine("  aapt2: ${tools?.aapt2?.absolutePath ?: "unavailable"}")
            appendLine()

            appendLine("Manifest checks:")
            appendLine("  AndroidManifest.xml: ${manifest.absolutePath}")
            appendLine("  declares FOREGROUND_SERVICE permission: ${manifestText.contains(embeddedNodePackagingForegroundServicePermission)}")
            appendLine("  declares FOREGROUND_SERVICE_SPECIAL_USE permission: ${manifestText.contains(embeddedNodePackagingForegroundServiceSpecialUsePermission)}")
            appendLine("  declares NativeNodeEmbeddedProbeService: ${manifestText.contains("org.autojs.autojs.engine.NativeNodeEmbeddedProbeService")}")
            appendLine("  declares NativeNodeEmbeddedScriptService: ${manifestText.contains(embeddedNodePackagingScriptServiceClassName)}")
            appendLine(
                "  script service isolated process :nodejs_embedded: " +
                        manifestText.containsAllFragments(
                            "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService",
                            "android:process=\":nodejs_embedded\"",
                        )
            )
            appendLine(
                "  embedded services use specialUse foreground service type: " +
                        manifestText.containsAllFragments(
                            "NativeNodeEmbeddedProbeService",
                            "NativeNodeEmbeddedScriptService",
                            "android:foregroundServiceType=\"specialUse\"",
                        )
            )
            appendLine()

            appendLine("appDebug universal APK manifest diagnostics:")
            appendEmbeddedNodePackagingManifestInfo("appDebug universal", appDebugUniversalManifest)
            appendLine("  appDebug universal DEX entries: ${if (appDebugUniversalDexEntries.isEmpty()) "none" else appDebugUniversalDexEntries.joinToString()}")
            appendLine("  appDebug universal dex contains NativeNodeEmbeddedScriptService class_def: $appDebugUniversalDexDefinesScriptService")
            appendLine()

            appendLine("template.apk manifest diagnostics:")
            appendEmbeddedNodePackagingManifestInfo("template", templateManifest)
            appendLine("  template DEX entries: ${if (templateDexEntries.isEmpty()) "none" else templateDexEntries.joinToString()}")
            appendLine("  template dex contains NativeNodeEmbeddedScriptService class_def: $templateDexDefinesScriptService")
            appendLine("  template dex contains NativeNodeEmbeddedRuntimeBridge class_def: $templateDexDefinesRuntimeBridge")
            appendLine("  template dex contains $embeddedNodePackagingLegacyLifecycleMethodName: $templateDexContainsLegacyLifecycleMethod")
            appendLine("  template dex contains legacy $embeddedNodePackagingLifecycleWithModulesMethodName: $templateDexContainsLifecycleWithModulesMethod")
            appendLine("  template dex contains current $embeddedNodePackagingCurrentLifecycleWithModulesMethodName: $templateDexContainsCurrentLifecycleWithModulesMethod")
            appendLine("  template dex contains inrt node project runner marker: $templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  template dex contains inrt node project launch source marker: $templateDexContainsInrtNodeProjectLaunchSourceMarker")
            templateDexContainsInrtNodeProjectStableRequestMarkers.forEach { (marker, present) ->
                appendLine("  template dex contains inrt node project request marker $marker: $present")
            }
            templateDexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
                appendLine("  template dex contains inrt node project failure marker $marker: $present")
            }
            appendLine()

            appendLine("Source native libraries:")
            embeddedNodePackagingKnownAbis.forEach { abi ->
                appendLine("  ABI: $abi")
                embeddedNodePackagingNativeLibs.forEach { libName ->
                    appendLine("    $libName: ${embeddedNodePackagingFormatFile(sourceJniLibsRoot.resolve("$abi/$libName"))}")
                }
            }
            appendLine("  release flavor source libnode ABIs: ${if (sourceLibnodeReadyAbis.isEmpty()) "none" else sourceLibnodeReadyAbis.joinToString()}")
            appendLine("  required Node ABI set present in src/main/jniLibs: ${embeddedNodePackagingRequiredNodeAbis.all { sourceJniLibsRoot.resolve("$it/libnode.so").isFile }}")
            appendLine("  x86 libnode.so present: ${sourceJniLibsRoot.resolve("x86/libnode.so").isFile}")
            appendLine("  x86 Node runtime present: $sourceX86NodeRuntimePresent")
            appendLine()

            appendLine("Merged appDebug native libraries:")
            embeddedNodePackagingKnownAbis.forEach { abi ->
                appendLine("  ABI: $abi")
                embeddedNodePackagingNativeLibs.forEach { libName ->
                    appendLine("    $libName: ${embeddedNodePackagingFormatFile(mergedNativeLibsRoot.resolve("$abi/$libName"))}")
                }
            }
            appendLine("  release flavor complete merged ABIs: ${if (mergedCompleteReadyAbis.isEmpty()) "none" else mergedCompleteReadyAbis.joinToString()}")
            appendLine("  x86 Node runtime present: $mergedX86NodeRuntimePresent")
            appendLine()

            appendLine("appDebug APK native entries:")
            if (appDebugApks.isEmpty()) {
                appendLine("  no appDebug APK files found in ${appDebugApkDir.absolutePath}")
            } else {
                appDebugApks.forEach { apk ->
                    appendLine("  APK: ${apk.name}")
                    embeddedNodePackagingNativeLibs.forEach { libName ->
                        val entries = embeddedNodePackagingApkNativeEntries(apk, libName)
                        appendLine("    $libName ABI entries: ${if (entries.isEmpty()) "none" else embeddedNodePackagingApkNativeAbis(apk, libName).joinToString()}")
                        entries.forEach { entry -> appendLine("      $entry") }
                    }
                }
            }
            appendLine()

            appendLine("Native library ABI matrices:")
            if (appDebugUniversalApk != null) {
                appendEmbeddedNodePackagingAbiMatrix("appDebug universal", appDebugUniversalApk, embeddedNodePackagingKnownAbis)
            } else {
                appendLine("  appDebug universal APK missing")
            }
            appendEmbeddedNodePackagingAbiMatrix("template", templateApk, embeddedNodePackagingKnownAbis)
            appendLine()

            appendLine("Release flavor native policy:")
            appendLine("  flavor: $releaseFlavor")
            appendLine("  expected ABIs: ${if (releaseExpectedAbis.isEmpty()) "none" else releaseExpectedAbis.joinToString()}")
            appendLine("  appDebug universal complete ABIs: ${if (appDebugUniversalCompleteReadyAbis.isEmpty()) "none" else appDebugUniversalCompleteReadyAbis.joinToString()}")
            appendLine("  appDebug universal x86 Node runtime present: $appDebugUniversalX86NodeRuntimePresent")
            appendLine("  release flavor policy ready: $releaseFlavorPolicyReady")
            appendLine("  release flavor policy missing: ${if (releaseFlavorPolicyMissing.isEmpty()) "none" else releaseFlavorPolicyMissing.joinToString()}")
            appendLine()

            appendLine("Packaged app / inrt template preflight:")
            appendLine("  template.apk: ${embeddedNodePackagingFormatFile(templateApk)}")
            if (templateApk.isFile) {
                embeddedNodePackagingNativeLibs.forEach { libName ->
                    val entries = embeddedNodePackagingApkNativeEntries(templateApk, libName)
                    appendLine("  template $libName ABI entries: ${if (entries.isEmpty()) "none" else embeddedNodePackagingApkNativeAbis(templateApk, libName).joinToString()}")
                    entries.forEach { entry -> appendLine("    $entry") }
                }
                appendLine("  template arm64-v8a libnode.so present: $templateArm64HasLibnode")
                appendLine("  template arm64-v8a libautojs6-node.so present: $templateArm64HasBridge")
                appendLine("  template arm64-v8a libc++_shared.so present: $templateArm64HasLibcxx")
                appendLine("  template x86 Node runtime present: $templateX86NodeRuntimePresent")
                appendLine("  template libautojs6-node.so contains legacy JNI symbol: $templateBridgeLibContainsLegacyLifecycleSymbol")
                appendLine("  template libautojs6-node.so contains legacy WithModules JNI symbol: $templateBridgeLibContainsLifecycleWithModulesSymbol")
                appendLine("  template libautojs6-node.so contains current WithModules JNI symbol: $templateBridgeLibContainsCurrentLifecycleWithModulesSymbol")
            } else {
                appendLine("  template inspection skipped because template.apk is missing.")
                appendLine("  Generate it with the existing inrt assemble/template copy flow before validating packaged-app embedded Node distribution.")
            }
            appendLine()

            appendLine("Embedded Node.js inrt template freshness verification:")
            appendLine("  template APK exists: ${templateApk.isFile}")
            appendLine("  template dex contains NativeNodeEmbeddedScriptService: $templateDexDefinesScriptService")
            appendLine("  template dex contains NativeNodeEmbeddedRuntimeBridge: $templateDexDefinesRuntimeBridge")
            appendLine("  template dex contains legacy $embeddedNodePackagingLifecycleWithModulesMethodName: $templateDexContainsLifecycleWithModulesMethod")
            appendLine("  template dex contains current $embeddedNodePackagingCurrentLifecycleWithModulesMethodName: $templateDexContainsCurrentLifecycleWithModulesMethod")
            appendLine("  template dex contains inrt node project runner marker: $templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  template manifest service ready: $templateManifestServiceReady")
            appendLine("  template manifest foreground-service permissions ready: $templateManifestPermissionsReady")
            appendLine("  template inrt node project runner ready: $templateInrtNodeProjectRunnerReady")
            appendLine("  template inrt node project launch source ready: $templateInrtNodeProjectLaunchSourceReady")
            appendLine("  template inrt node project stable request diagnostics ready: $templateInrtNodeProjectStableRequestDiagnosticsReady")
            templateDexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
                appendLine("  template dex contains inrt node project failure marker $marker: $present")
            }
            appendLine("  template inrt node project stable failure diagnostics ready: $templateInrtNodeProjectStableFailureDiagnosticsReady")
            appendLine("  template native libs ready: $templateNativeLibsReady")
            appendLine("  template bridge JNI symbols report-only:")
            appendLine("    legacy $embeddedNodePackagingLegacyLifecycleMethodName: $templateBridgeLibContainsLegacyLifecycleSymbol")
            appendLine("    legacy $embeddedNodePackagingLifecycleWithModulesMethodName: $templateBridgeLibContainsLifecycleWithModulesSymbol")
            appendLine("    current $embeddedNodePackagingCurrentLifecycleWithModulesMethodName: $templateBridgeLibContainsCurrentLifecycleWithModulesSymbol")
            appendLine("  template freshness ready: $packagedTemplateReady")
            appendLine("  template freshness missing: ${if (packagedTemplateMissing.isEmpty()) "none" else packagedTemplateMissing.joinToString()}")
            appendLine()

            appendLine("Preflight summary:")
            appendLine("  appDebug has APK outputs: ${appDebugApks.isNotEmpty()}")
            appendLine("  appDebug universal APK has libnode.so: $appDebugUniversalHasLibnode")
            appendLine("  appDebug universal APK has libautojs6-node.so: $appDebugUniversalHasBridge")
            appendLine("  appDebug universal APK has libc++_shared.so: $appDebugUniversalHasLibcxx")
            appendLine("  packaged template present: ${templateApk.isFile}")
            appendLine("  packaged template has libnode.so: $templateHasLibnode")
            appendLine("  packaged template has libautojs6-node.so: $templateHasBridge")
            appendLine("  packaged template has libc++_shared.so: $templateHasLibcxx")
            appendLine("  packaged template embedded service declaration inspected: ${templateManifest.inspected}")
            appendLine("  packaged template has embedded service declaration: ${templateManifest.declaresEmbeddedScriptService}")
            appendLine("  packaged template has embedded service dex class_def: $templateDexDefinesScriptService")
            appendLine("  packaged template has runtime bridge dex class_def: $templateDexDefinesRuntimeBridge")
            appendLine("  packaged template has current WithModules bridge method: $templateDexContainsCurrentLifecycleWithModulesMethod")
            appendLine("  packaged template has inrt node project runner marker: $templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  packaged template has inrt node project launch source marker: $templateDexContainsInrtNodeProjectLaunchSourceMarker")
            appendLine("  packaged template has inrt node project stable request diagnostics: $templateInrtNodeProjectStableRequestDiagnosticsReady")
            appendLine("  packaged template has inrt node project stable failure diagnostics: $templateInrtNodeProjectStableFailureDiagnosticsReady")
            appendLine("  packaged template has FOREGROUND_SERVICE permission: ${templateManifest.declaresForegroundServicePermission}")
            appendLine("  packaged template has FOREGROUND_SERVICE_SPECIAL_USE permission: ${templateManifest.declaresForegroundServiceSpecialUsePermission}")
            appendLine("  embedded.node.host.app.ready=$hostAppReady")
            appendLine("  embedded.node.release.flavor=$releaseFlavor")
            appendLine("  embedded.node.release.expected_abis=${if (releaseExpectedAbis.isEmpty()) "none" else releaseExpectedAbis.joinToString()}")
            appendLine("  embedded.node.release.flavor_policy.ready=$releaseFlavorPolicyReady")
            appendLine("  embedded.node.release.flavor_policy.missing=${if (releaseFlavorPolicyMissing.isEmpty()) "none" else releaseFlavorPolicyMissing.joinToString()}")
            appendLine("  embedded.node.packaged.template.exists=${templateApk.isFile}")
            appendLine("  embedded.node.packaged.template.dex.bridge.ready=$templateDexBridgeReady")
            appendLine("  embedded.node.packaged.template.dex.contains.NativeNodeEmbeddedRuntimeBridge=$templateDexDefinesRuntimeBridge")
            appendLine("  embedded.node.packaged.template.dex.contains.$embeddedNodePackagingLifecycleWithModulesMethodName=$templateDexContainsLifecycleWithModulesMethod")
            appendLine("  embedded.node.packaged.template.dex.contains.$embeddedNodePackagingCurrentLifecycleWithModulesMethodName=$templateDexContainsCurrentLifecycleWithModulesMethod")
            appendLine("  embedded.node.packaged.template.dex.contains.inrt_node_project_runner_marker=$templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  embedded.node.packaged.template.dex.contains.inrt_node_project_launch_source_marker=$templateDexContainsInrtNodeProjectLaunchSourceMarker")
            templateDexContainsInrtNodeProjectStableRequestMarkers.forEach { (marker, present) ->
                appendLine("  embedded.node.packaged.template.dex.contains.inrt_node_project_request_marker.${marker.removeSuffix("=")}=$present")
            }
            templateDexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
                appendLine("  embedded.node.packaged.template.dex.contains.inrt_node_project_failure_marker.${marker.replace('.', '_')}=$present")
            }
            appendLine("  embedded.node.packaged.template.manifest.service.ready=$templateManifestServiceReady")
            appendLine("  embedded.node.packaged.template.manifest.fgs_permission.ready=$templateManifestPermissionsReady")
            appendLine("  embedded.node.packaged.template.inrt.node_project_runner.ready=$templateInrtNodeProjectRunnerReady")
            appendLine("  embedded.node.packaged.template.inrt.node_project_launch_source.ready=$templateInrtNodeProjectLaunchSourceReady")
            appendLine("  embedded.node.packaged.template.inrt.node_project_stable_request_diagnostics.ready=$templateInrtNodeProjectStableRequestDiagnosticsReady")
            appendLine("  embedded.node.packaged.template.inrt.node_project_stable_failure_diagnostics.ready=$templateInrtNodeProjectStableFailureDiagnosticsReady")
            appendLine("  embedded.node.packaged.template.native_libs.ready=$templateNativeLibsReady")
            appendLine("  embedded.node.packaged.template.native_symbols.legacy_lifecycle.present=$templateBridgeLibContainsLegacyLifecycleSymbol")
            appendLine("  embedded.node.packaged.template.native_symbols.legacy_with_modules_lifecycle.present=$templateBridgeLibContainsLifecycleWithModulesSymbol")
            appendLine("  embedded.node.packaged.template.native_symbols.with_modules_lifecycle.present=$templateBridgeLibContainsCurrentLifecycleWithModulesSymbol")
            appendLine("  embedded.node.packaged.template.ready=$packagedTemplateReady")
            appendLine("  embedded.node.packaged.template.dex.contains.NativeNodeEmbeddedScriptService=$templateDexDefinesScriptService")
            appendLine("  embedded.node.packaged.template.missing=${if (packagedTemplateMissing.isEmpty()) "none" else packagedTemplateMissing.joinToString()}")
            appendLine()

            appendLine("Notes:")
            appendLine("  This task is diagnostic-only and does not enable embedded script execution.")
            appendLine("  It intentionally does not open require/npm/node_modules/fs/import/worker/child_process.")
            appendLine("  It should be used before changing packaged-app/template behavior.")
            appendLine("  To refresh a stale release template, run: .\\gradlew.bat --console=plain :app:$embeddedNodePackagingRefreshTaskName")
            appendLine("  Fallback release flow: .\\gradlew.bat --console=plain :app:assembleInrtRelease -x :app:lintVitalAnalyzeInrtRelease")
        }

        reportFile.writeText(report)
        logger.lifecycle("Embedded Node.js packaging preflight report written to ${reportFile.absolutePath}")
        if (!releaseFlavorPolicyReady) {
            throw GradleException(
                "Embedded Node.js release flavor policy failed for $releaseFlavor. " +
                        "Missing: ${if (releaseFlavorPolicyMissing.isEmpty()) "unknown" else releaseFlavorPolicyMissing.joinToString()}. " +
                        "See ${reportFile.absolutePath}"
            )
        }
        if (nativeRuntimeRequested && scriptExecutionEnabled && !packagedTemplateReady) {
            throw GradleException(
                "Embedded Node.js packaged inrt template is stale or incomplete. " +
                        "Missing: ${if (packagedTemplateMissing.isEmpty()) "unknown" else packagedTemplateMissing.joinToString()}. " +
                        "Run :app:$embeddedNodePackagingRefreshTaskName, then rerun this task. " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}

val embeddedNodePackagedAppIntegrationDryRunAppDebug = tasks.register("embeddedNodePackagedAppIntegrationDryRunAppDebug") {
    group = "verification"
    description = "Writes a diagnostic-only Embedded Node.js packaged-app template integration dry-run report."

    dependsOn(embeddedNodePackagingPreflightAppDebug)
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-app-integration-dry-run-$embeddedNodePackagingReportVariant.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/packaged-app-integration-dry-run-$embeddedNodePackagingReportVariant.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend").orElse("rhino").get()
            .trim()
            .lowercase()
            .let { if (it in setOf("rhino", "native", "embedded", "auto")) it else "rhino" }
        val scriptExecutionEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled")
            .orElse("false")
            .get()
            .toEmbeddedNodePackagingBoolean()
        val nativeRuntimeRequested = runtimeBackend in setOf("native", "embedded", "auto")
        val sourceJniLibsRoot = projectDir.resolve("src/main/jniLibs")
        val mergedNativeLibsRoot = layout.buildDirectory
            .dir("intermediates/merged_native_libs/$embeddedNodePackagingReportVariant/merge${embeddedNodePackagingReportVariantTaskSuffix}NativeLibs/out/lib")
            .get()
            .asFile
        val appDebugApkDir = layout.buildDirectory.dir("outputs/apk/app/debug").get().asFile
        val appDebugApks = appDebugApkDir
            .listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
            ?.sortedBy(File::getName)
            .orEmpty()
        val appDebugUniversalApk = appDebugApks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        val templateApk = projectDir.resolve("src/main/assets-app/template.apk")
        val manifest = projectDir.resolve("src/main/AndroidManifest.xml")
        val manifestText = manifest.takeIf(File::isFile)?.readText().orEmpty()
        val apkBuilder = projectDir.resolve("src/main/java/org/autojs/autojs/apkbuilder/ApkBuilder.kt")
        val manifestEditor = projectDir.resolve("src/main/java/org/autojs/autojs/apkbuilder/ManifestEditor.java")
        val buildScript = projectDir.resolve("build.gradle.kts")
        val buildUtils = rootProject.projectDir.resolve("build-logic/convention/src/main/kotlin/org/autojs/build/Utils.kt")
        val tools = findEmbeddedNodePackagingTools(rootProject.projectDir)
        val templateManifest = embeddedNodePackagingManifestInfo(tools, templateApk)
        val appDebugUniversalDexEntries = appDebugUniversalApk?.let { embeddedNodePackagingApkDexEntries(it) }.orEmpty()
        val appDebugUniversalDexDefinesScriptService = appDebugUniversalApk
            ?.let { embeddedNodePackagingApkDexDefinesClass(it, embeddedNodePackagingScriptServiceDexDescriptor) } == true
        val templateDexEntries = embeddedNodePackagingApkDexEntries(templateApk)
        val templateDexDefinesScriptService =
            embeddedNodePackagingApkDexDefinesClass(templateApk, embeddedNodePackagingScriptServiceDexDescriptor)
        val templateDexContainsInrtNodeProjectRunnerMarker =
            embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingInrtNodeProjectRunnerMarker)
        val templateDexContainsInrtNodeProjectLaunchSourceMarker =
            embeddedNodePackagingApkDexContainsString(templateApk, embeddedNodePackagingInrtNodeProjectLaunchSourceMarker)
        val templateDexContainsInrtNodeProjectStableFailureMarkers =
            embeddedNodePackagingInrtNodeProjectStableFailureMarkers.associateWith { marker ->
                embeddedNodePackagingApkDexContainsString(templateApk, marker)
            }
        val templateInrtNodeProjectStableFailureDiagnosticsReady =
            templateDexContainsInrtNodeProjectStableFailureMarkers.values.all { it }

        val sourceJniLibsReadyAbis = embeddedNodePackagingKnownAbis.filter { abi ->
            embeddedNodePackagingAllNativeLibsInSource(sourceJniLibsRoot, abi)
        }
        val hostProviderReadyAbis = embeddedNodePackagingKnownAbis.filter { abi ->
            when {
                appDebugUniversalApk != null -> embeddedNodePackagingAllNativeLibsInApk(appDebugUniversalApk, abi)
                else -> embeddedNodePackagingAllNativeLibsInDirectory(mergedNativeLibsRoot, abi)
            }
        }
        val templateReadyAbis = embeddedNodePackagingKnownAbis.filter { abi ->
            embeddedNodePackagingAllNativeLibsInApk(templateApk, abi)
        }
        val injectableAbis = hostProviderReadyAbis.filterNot { it in templateReadyAbis }
        val unsupportedAbis = embeddedNodePackagingKnownAbis.filterNot { it in hostProviderReadyAbis }
        val requiredAbiSetReady = embeddedNodePackagingRequiredNodeAbis.all { it in hostProviderReadyAbis }

        val mainManifestDeclaresScriptService = embeddedNodePackagingSourceContains(
            manifest,
            "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService",
            "android:process=\":nodejs_embedded\"",
            "android:foregroundServiceType=\"specialUse\"",
        )
        val mainManifestDeclaresEmbeddedNodeFgsPermissions = manifestText.contains(embeddedNodePackagingForegroundServicePermission) &&
                manifestText.contains(embeddedNodePackagingForegroundServiceSpecialUsePermission)
        val inrtTemplateCopyFlowPresent = embeddedNodePackagingSourceContains(
            buildUtils,
            "registerTemplateApkCopy",
            "assembleInrtRelease",
            "template.apk",
        ) && embeddedNodePackagingSourceContains(buildScript, "registerTemplateApkCopy(project)")
        val packagingCopiesNativeLibsByProjectConfig = embeddedNodePackagingSourceContains(
            apkBuilder,
            "copyLibrariesByConfig",
            "config.abis.forEach",
            "mLibsIncludes.distinct()",
        )
        val projectConfigSupportsLibSelection = embeddedNodePackagingSourceContains(
            rootProject.projectDir.resolve("app/src/main/java/org/autojs/autojs/project/ProjectConfig.java"),
            "@SerializedName(value = \"libs\")",
            "setLibs",
        )
        val apkBuilderNodeLibEntryPresent = embeddedNodePackagingSourceContains(
            apkBuilder,
            "libnode.so",
            "libautojs6-node.so",
        )
        val manifestEditorCanFilterExistingPermissions = embeddedNodePackagingSourceContains(
            manifestEditor,
            "isPermissionRequired",
            "uses-permission",
        )
        val manifestEditorCanSynthesizeServiceNodes = embeddedNodePackagingSourceContainsAny(
            manifestEditor,
            "NativeNodeEmbeddedScriptService",
            "new StringItem(\"org.autojs.autojs.engine.NativeNodeEmbeddedScriptService\")",
        )
        val manifestEditorCanSynthesizeEmbeddedFgsPermissions = embeddedNodePackagingSourceContains(
            manifestEditor,
            "addEmbeddedNodeForegroundServicePermissionsIfMissing",
            embeddedNodePackagingForegroundServiceSpecialUsePermission,
        )
        val apkBuilderCanExtractHostNativeLibsFromApk = embeddedNodePackagingSourceContains(
            apkBuilder,
            "extractFirstMatchedSoFromHostApks",
            "getAppApkPaths",
        )
        val apkBuilderValidatesEmbeddedNodeAbiLibs = embeddedNodePackagingSourceContains(
            apkBuilder,
            "ensureEmbeddedNodeLibrariesAvailableOrThrow",
            "Supported Embedded Node.js ABIs",
        )
        val apkBuilderValidatesEmbeddedNodeScriptExecutionEnabled = embeddedNodePackagingSourceContains(
            apkBuilder,
            "NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED",
            "autojs.nodejs.embedded.script.enabled=true",
        )

        val nativeLibInjectionReady = requiredAbiSetReady &&
                packagingCopiesNativeLibsByProjectConfig &&
                projectConfigSupportsLibSelection
        val manifestTemplateRegenerationReady = mainManifestDeclaresScriptService &&
                mainManifestDeclaresEmbeddedNodeFgsPermissions &&
                inrtTemplateCopyFlowPresent &&
                appDebugUniversalDexDefinesScriptService
        val currentTemplateReadyForEmbedded = templateReadyAbis.containsAll(embeddedNodePackagingRequiredNodeAbis) &&
                templateManifest.declaresEmbeddedScriptService &&
                templateManifest.embeddedScriptServiceUsesNodeProcess &&
                templateManifest.embeddedScriptServiceUsesSpecialUseFgs &&
                templateManifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty &&
                templateManifest.declaresForegroundServicePermission &&
                templateManifest.declaresForegroundServiceSpecialUsePermission &&
                templateDexDefinesScriptService &&
                templateDexContainsInrtNodeProjectRunnerMarker &&
                templateDexContainsInrtNodeProjectLaunchSourceMarker &&
                templateInrtNodeProjectStableFailureDiagnosticsReady
        val runtimeManifestServiceReady = (templateManifest.declaresEmbeddedScriptService &&
                templateManifest.embeddedScriptServiceUsesNodeProcess &&
                templateManifest.embeddedScriptServiceUsesSpecialUseFgs &&
                templateManifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty) || manifestEditorCanSynthesizeServiceNodes
        val runtimeFgsPermissionReady = (templateManifest.declaresForegroundServicePermission &&
                templateManifest.declaresForegroundServiceSpecialUsePermission) || manifestEditorCanSynthesizeEmbeddedFgsPermissions
        val runtimeDexReady = templateDexDefinesScriptService &&
                templateDexContainsInrtNodeProjectRunnerMarker &&
                templateDexContainsInrtNodeProjectLaunchSourceMarker &&
                templateInrtNodeProjectStableFailureDiagnosticsReady
        val packagedRuntimeIntegrationReady = nativeLibInjectionReady &&
                apkBuilderNodeLibEntryPresent &&
                apkBuilderCanExtractHostNativeLibsFromApk &&
                runtimeManifestServiceReady &&
                runtimeFgsPermissionReady &&
                runtimeDexReady &&
                apkBuilderValidatesEmbeddedNodeAbiLibs &&
                apkBuilderValidatesEmbeddedNodeScriptExecutionEnabled &&
                scriptExecutionEnabled
        val blockingItems = buildList {
            if (!requiredAbiSetReady) add("required Node native ABI set is incomplete in the host native-lib provider")
            if (!packagingCopiesNativeLibsByProjectConfig) add("ApkBuilder native-lib copy-by-config path was not detected")
            if (!projectConfigSupportsLibSelection) add("ProjectConfig libs selection was not detected")
            if (!mainManifestDeclaresScriptService) add("main manifest embedded script service declaration was not detected")
            if (!mainManifestDeclaresEmbeddedNodeFgsPermissions) add("main manifest embedded Node FGS permissions were not detected")
            if (!scriptExecutionEnabled) add("embedded script execution build support is disabled; rebuild with -Pautojs.nodejs.embedded.script.enabled=true for runnable packaged Embedded Node APKs")
            if (!appDebugUniversalDexDefinesScriptService) add("appDebug universal APK does not define NativeNodeEmbeddedScriptService")
            if (!inrtTemplateCopyFlowPresent) add("inrt template copy flow was not detected")
        }
        val dryRunReady = blockingItems.isEmpty()
        val dryRunReadyForOutputVerification = dryRunReady && packagedRuntimeIntegrationReady
        val dryRunReadyForNextImplementation = dryRunReady &&
                nativeLibInjectionReady &&
                manifestTemplateRegenerationReady &&
                !packagedRuntimeIntegrationReady
        val nextImplementationItems = buildList {
            if (!apkBuilderNodeLibEntryPresent) {
                add("Add an ApkBuilder.Lib entry, for example \"Embedded Node.js\", with libnode.so, libautojs6-node.so, and libc++_shared.so.")
            }
            if (!templateManifest.declaresEmbeddedScriptService && !manifestEditorCanSynthesizeServiceNodes) {
                add("Regenerate template.apk from the current manifest or add a safe binary-manifest insertion path for NativeNodeEmbeddedScriptService.")
            }
            if (!templateManifest.embeddedScriptServiceUsesNodeProcess && !manifestEditorCanSynthesizeServiceNodes) {
                add("Ensure template NativeNodeEmbeddedScriptService uses android:process=\":nodejs_embedded\".")
            }
            if (!templateManifest.embeddedScriptServiceUsesSpecialUseFgs && !manifestEditorCanSynthesizeServiceNodes) {
                add("Ensure template NativeNodeEmbeddedScriptService keeps foregroundServiceType=\"specialUse\".")
            }
            if (!templateManifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty && !manifestEditorCanSynthesizeServiceNodes) {
                add("Ensure template NativeNodeEmbeddedScriptService keeps android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE.")
            }
            if ((!templateManifest.declaresForegroundServicePermission || !templateManifest.declaresForegroundServiceSpecialUsePermission) && !manifestEditorCanSynthesizeEmbeddedFgsPermissions) {
                add("Add a safe binary-manifest insertion path for FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE permissions.")
            }
            if (!templateDexDefinesScriptService) {
                add("Regenerate template.apk so dex contains NativeNodeEmbeddedScriptService; manifest synthesis alone cannot add bytecode.")
            }
            if (!templateDexContainsInrtNodeProjectRunnerMarker) {
                add("Regenerate template.apk so dex contains $embeddedNodePackagingInrtNodeProjectRunnerMarker.")
            }
            if (!templateDexContainsInrtNodeProjectLaunchSourceMarker) {
                add("Regenerate template.apk so dex contains $embeddedNodePackagingInrtNodeProjectLaunchSourceMarker.")
            }
            templateDexContainsInrtNodeProjectStableFailureMarkers
                .filterValues { present -> !present }
                .keys
                .forEach { marker -> add("Regenerate template.apk so dex contains $marker.") }
            if (injectableAbis.isNotEmpty() && !apkBuilderCanExtractHostNativeLibsFromApk) {
                add("Inject Node native libs for ABIs: ${injectableAbis.joinToString()}.")
            }
            if (unsupportedAbis.isNotEmpty() && !apkBuilderValidatesEmbeddedNodeAbiLibs) {
                add("Keep unsupported ABIs disabled or show a packaging warning: ${unsupportedAbis.joinToString()}.")
            }
            if (!apkBuilderValidatesEmbeddedNodeScriptExecutionEnabled) {
                add("Fail fast when Embedded Node.js is selected but the host/native bridge was not built with embedded script execution enabled.")
            }
        }

        val report = buildString {
            appendLine("Embedded Node.js packaged-app template integration dry-run report: $embeddedNodePackagingReportVariant")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine()

            appendLine("Scope:")
            appendLine("  diagnostic_only=true")
            appendLine("  modifies_template_apk=false")
            appendLine("  modifies_packaging_logic=false")
            appendLine("  intended_next_step=verify_real_packaged_output_or_continue_capability_expansion")
            appendLine()

            appendLine("Current build inputs:")
            appendLine("  autojs.nodejs.backend=$runtimeBackend")
            appendLine("  native_runtime_requested=$nativeRuntimeRequested")
            appendLine("  autojs.nodejs.embedded.script.enabled=$scriptExecutionEnabled")
            appendLine("  template.apk=${embeddedNodePackagingFormatFile(templateApk)}")
            appendLine()

            appendLine("Template generation flow:")
            appendLine("  registerTemplateApkCopy flow detected: $inrtTemplateCopyFlowPresent")
            appendLine("  expected template source task: assembleInrtRelease")
            appendLine("  expected template destination: app/src/main/assets-app/template.apk")
            appendLine("  main manifest has embedded script service declaration: $mainManifestDeclaresScriptService")
            appendLine("  main manifest has embedded Node FGS permissions: $mainManifestDeclaresEmbeddedNodeFgsPermissions")
            appendLine("  appDebug universal DEX entries: ${if (appDebugUniversalDexEntries.isEmpty()) "none" else appDebugUniversalDexEntries.joinToString()}")
            appendLine("  appDebug universal dex contains NativeNodeEmbeddedScriptService class_def: $appDebugUniversalDexDefinesScriptService")
            appendLine("  template manifest currently has embedded script service declaration: ${templateManifest.declaresEmbeddedScriptService}")
            appendLine("  template manifest currently has :nodejs_embedded script process: ${templateManifest.embeddedScriptServiceUsesNodeProcess}")
            appendLine("  template manifest currently has script specialUse FGS: ${templateManifest.embeddedScriptServiceUsesSpecialUseFgs}")
            appendLine("  template manifest currently has script specialUse subtype property: ${templateManifest.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty}")
            appendLine("  template manifest currently has FOREGROUND_SERVICE permission: ${templateManifest.declaresForegroundServicePermission}")
            appendLine("  template manifest currently has FOREGROUND_SERVICE_SPECIAL_USE permission: ${templateManifest.declaresForegroundServiceSpecialUsePermission}")
            appendLine("  template DEX entries: ${if (templateDexEntries.isEmpty()) "none" else templateDexEntries.joinToString()}")
            appendLine("  template dex contains NativeNodeEmbeddedScriptService class_def: $templateDexDefinesScriptService")
            appendLine("  template dex contains inrt node project runner marker: $templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  template dex contains inrt node project launch source marker: $templateDexContainsInrtNodeProjectLaunchSourceMarker")
            templateDexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
                appendLine("  template dex contains inrt node project failure marker $marker: $present")
            }
            appendLine()

            appendLine("Native library source plan:")
            appendLine("  required Node ABI set: ${embeddedNodePackagingRequiredNodeAbis.joinToString()}")
            appendLine("  src/main/jniLibs complete-ready ABIs: ${if (sourceJniLibsReadyAbis.isEmpty()) "none" else sourceJniLibsReadyAbis.joinToString()}")
            appendLine("  host-provider-ready ABIs: ${if (hostProviderReadyAbis.isEmpty()) "none" else hostProviderReadyAbis.joinToString()}")
            appendLine("  template-ready ABIs: ${if (templateReadyAbis.isEmpty()) "none" else templateReadyAbis.joinToString()}")
            appendLine("  injectable ABIs from host provider to packaged output: ${if (injectableAbis.isEmpty()) "none" else injectableAbis.joinToString()}")
            appendLine("  unsupported ABIs for Embedded Node: ${if (unsupportedAbis.isEmpty()) "none" else unsupportedAbis.joinToString()}")
            appendLine("  required Node ABI set ready in host provider: $requiredAbiSetReady")
            appendLine("  host provider APK: ${appDebugUniversalApk?.absolutePath ?: "unavailable, using merged native libs if present"}")
            appendLine("  merged native libs root: ${mergedNativeLibsRoot.absolutePath}")
            appendLine("  note: libautojs6-node.so is a build output; host APK / installed native lib dir is the realistic packaged-app injection source.")
            embeddedNodePackagingKnownAbis.forEach { abi ->
                appendLine("  ABI: $abi")
                embeddedNodePackagingNativeLibs.forEach { libName ->
                    appendLine("    src/main/jniLibs $libName: ${sourceJniLibsRoot.resolve("$abi/$libName").isFile}")
                    appendLine("    host-provider $libName: ${appDebugUniversalApk?.let { "lib/$abi/$libName" in embeddedNodePackagingApkNativeEntries(it, libName) } ?: mergedNativeLibsRoot.resolve("$abi/$libName").isFile}")
                    appendLine("    template $libName: ${embeddedNodePackagingAllNativeLibsInApk(templateApk, abi) && "lib/$abi/$libName" in embeddedNodePackagingApkNativeEntries(templateApk, libName)}")
                }
            }
            appendLine()

            appendLine("Runtime packaging code readiness:")
            appendLine("  ProjectConfig libs selection detected: $projectConfigSupportsLibSelection")
            appendLine("  ApkBuilder native lib copy-by-config path detected: $packagingCopiesNativeLibsByProjectConfig")
            appendLine("  ApkBuilder Embedded Node lib entry present: $apkBuilderNodeLibEntryPresent")
            appendLine("  ApkBuilder host APK native lib fallback detected: $apkBuilderCanExtractHostNativeLibsFromApk")
            appendLine("  ApkBuilder Embedded Node ABI/lib validation detected: $apkBuilderValidatesEmbeddedNodeAbiLibs")
            appendLine("  ApkBuilder Embedded Node script execution build flag validation detected: $apkBuilderValidatesEmbeddedNodeScriptExecutionEnabled")
            appendLine("  ManifestEditor can filter existing permissions: $manifestEditorCanFilterExistingPermissions")
            appendLine("  ManifestEditor can synthesize embedded script service nodes: $manifestEditorCanSynthesizeServiceNodes")
            appendLine("  ManifestEditor can synthesize embedded Node FGS permissions: $manifestEditorCanSynthesizeEmbeddedFgsPermissions")
            appendLine("  runtime manifest service ready via template or synthesis: $runtimeManifestServiceReady")
            appendLine("  runtime FGS permissions ready via template or synthesis: $runtimeFgsPermissionReady")
            appendLine("  runtime dex class ready from current template: $runtimeDexReady")
            appendLine("  runtime inrt node project runner marker ready from current template: $templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  runtime inrt node project launch source marker ready from current template: $templateDexContainsInrtNodeProjectLaunchSourceMarker")
            appendLine("  runtime inrt node project stable failure diagnostics ready from current template: $templateInrtNodeProjectStableFailureDiagnosticsReady")
            appendLine()

            appendLine("Recommended integration strategy:")
            appendLine("  1. Keep template service/dex readiness separate from Node native lib inclusion.")
            appendLine("  2. Keep template.apk regenerated from current inrt sources so NativeNodeEmbeddedScriptService stays present in manifest and dex.")
            appendLine("  3. Add an explicit packaged-app library option for Embedded Node native libs instead of bloating every packaged app by default.")
            appendLine("  4. Reuse ApkBuilder's ProjectConfig.libs + abis copy path for libnode.so, libautojs6-node.so, and libc++_shared.so.")
            appendLine("  5. Reject or warn on x86 for Embedded Node until libnode.so exists for x86.")
            appendLine("  6. Keep embedded script execution enabled by default; use -Pautojs.nodejs.embedded.script.enabled=false only for explicit diagnostics fallback.")
            appendLine()

            appendLine("Next implementation candidates:")
            nextImplementationItems.forEach { appendLine("  - $it") }
            if (nextImplementationItems.isEmpty()) {
                appendLine("  - none")
            }
            appendLine()

            appendLine("Dry-run summary:")
            appendLine("  embedded.node.packaged.current.template.ready=$currentTemplateReadyForEmbedded")
            appendLine("  embedded.node.packaged.native.lib.injection.ready=$nativeLibInjectionReady")
            appendLine("  embedded.node.packaged.manifest.template.regeneration.ready=$manifestTemplateRegenerationReady")
            appendLine("  embedded.node.packaged.runtime.manifest.service.ready=$runtimeManifestServiceReady")
            appendLine("  embedded.node.packaged.runtime.fgs_permission.ready=$runtimeFgsPermissionReady")
            appendLine("  embedded.node.packaged.runtime.dex.ready=$runtimeDexReady")
            appendLine("  embedded.node.packaged.runtime.dex.contains.inrt_node_project_runner_marker=$templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  embedded.node.packaged.runtime.dex.contains.inrt_node_project_launch_source_marker=$templateDexContainsInrtNodeProjectLaunchSourceMarker")
            templateDexContainsInrtNodeProjectStableFailureMarkers.forEach { (marker, present) ->
                appendLine("  embedded.node.packaged.runtime.dex.contains.inrt_node_project_failure_marker.${marker.replace('.', '_')}=$present")
            }
            appendLine("  embedded.node.packaged.runtime.integration.ready=$packagedRuntimeIntegrationReady")
            appendLine("  embedded.node.packaged.template.dex.contains.NativeNodeEmbeddedScriptService=$templateDexDefinesScriptService")
            appendLine("  embedded.node.packaged.template.inrt.node_project_runner.ready=$templateDexContainsInrtNodeProjectRunnerMarker")
            appendLine("  embedded.node.packaged.template.inrt.node_project_launch_source.ready=$templateDexContainsInrtNodeProjectLaunchSourceMarker")
            appendLine("  embedded.node.packaged.template.inrt.node_project_stable_failure_diagnostics.ready=$templateInrtNodeProjectStableFailureDiagnosticsReady")
            appendLine("  embedded.node.packaged.dry_run.ready=$dryRunReady")
            appendLine("  embedded.node.packaged.dry_run.ready_for_output_verification=$dryRunReadyForOutputVerification")
            appendLine("  embedded.node.packaged.dry_run.ready_for_next_implementation=$dryRunReadyForNextImplementation")
            appendLine("  embedded.node.packaged.dry_run.blocking=${if (blockingItems.isEmpty()) "none" else blockingItems.joinToString()}")
            appendLine()

            appendLine("Notes:")
            appendLine("  This dry-run does not modify template.apk, ProjectConfig, ApkBuilder, or ManifestEditor.")
            appendLine("  It does not enable require/npm/node_modules/fs/import/worker/child_process.")
            appendLine("  It should be used before real packaged-app output verification and after template/runtime integration changes.")
        }

        reportFile.writeText(report)
        logger.lifecycle("Embedded Node.js packaged-app integration dry-run report written to ${reportFile.absolutePath}")
    }
}

private fun embeddedNodePackagingVerifyPackagedApkOutput(
    rootDir: File,
    packagedApkProperty: String?,
    expectedAbiProperty: String?,
    releaseFlavorPolicy: EmbeddedNodeReleaseFlavorPolicy,
    runtimeBackend: String,
    scriptExecutionEnabled: Boolean,
): EmbeddedNodePackagedOutputVerification {
    val currentBuildRuntimeConfigReady = runtimeBackend == "embedded" && scriptExecutionEnabled
    val packagedApk = embeddedNodePackagingResolveFile(rootDir, packagedApkProperty)
    val packagedApkExists = packagedApk?.isFile == true
    val releaseFlavor = releaseFlavorPolicy.flavor
    val expectedAbis = releaseFlavorPolicy.expectedAbis
    val unknownExpectedAbis = expectedAbis.filterNot { it in embeddedNodePackagingKnownAbis }
    val unsupportedExpectedAbis = expectedAbis.filterNot { it in embeddedNodePackagingRequiredNodeAbis }
    val tools = findEmbeddedNodePackagingTools(rootDir)
    val manifestInfo = if (packagedApkExists) {
        embeddedNodePackagingManifestInfo(tools, packagedApk!!)
    } else {
        embeddedNodePackagingMissingManifestInfo(
            tools,
            packagedApk?.let { "packaged APK is missing: ${it.absolutePath}" }
                ?: "packaged APK path was not provided; use -Pautojs.embeddedNode.packagedApk=<apk>",
        )
    }
    val dexEntries = packagedApk?.takeIf { it.isFile }?.let { embeddedNodePackagingApkDexEntries(it) }.orEmpty()
    val dexDefinesScriptService = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkDexDefinesClass(it, embeddedNodePackagingScriptServiceDexDescriptor) } == true
    val nativeEntriesByLib = embeddedNodePackagingNativeLibs.associateWith { libName ->
        packagedApk?.takeIf { it.isFile }?.let { embeddedNodePackagingApkNativeEntries(it, libName) }.orEmpty()
    }
    val apkEntryNames = packagedApk?.takeIf { it.isFile }?.let { embeddedNodePackagingApkEntryNames(it) }.orEmpty()
    val projectConfigText = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkEntryText(it, embeddedNodePackagingProjectConfigEntry) }
    val packageJsonText = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkEntryText(it, embeddedNodePackagingPackageJsonEntry) }
    val bridgeCapabilityReport = embeddedNodePackagingBridgeCapabilityReport(
        projectConfigText = projectConfigText,
        packageJsonText = packageJsonText,
        manifestInfo = manifestInfo,
    )
    val projectMainEntry = embeddedNodePackagingProjectScriptEntry(projectConfigText)
    val mainScriptBytes = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkEntryBytes(it, projectMainEntry) }
    val mainScriptText = mainScriptBytes?.toString(Charsets.UTF_8).orEmpty()
    val runtimeMatrixText = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkEntryText(it, embeddedNodePackagingRuntimeMatrixEntry) }
    val preloadBudgetText = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingApkEntryText(it, embeddedNodePackagingPreloadBudgetEntry) }
    val preloadBudgetValues = embeddedNodePackagingPropertyMap(preloadBudgetText)
    val completeReadyAbis = packagedApk
        ?.takeIf { it.isFile }
        ?.let { embeddedNodePackagingCompleteReadyAbisInApk(it) }
        .orEmpty()
    val unexpectedCompleteReadyAbis = if (expectedAbis.isEmpty()) {
        emptyList()
    } else {
        completeReadyAbis.filterNot { it in expectedAbis }
    }
    val nativeLibsReady = unknownExpectedAbis.isEmpty() && unsupportedExpectedAbis.isEmpty() && if (expectedAbis.isEmpty()) {
        completeReadyAbis.isNotEmpty()
    } else {
        expectedAbis.all { it in completeReadyAbis } && unexpectedCompleteReadyAbis.isEmpty()
    }
    val x86LibnodePresent = "lib/x86/libnode.so" in nativeEntriesByLib.getValue("libnode.so")
    val x86BridgePresent = "lib/x86/libautojs6-node.so" in nativeEntriesByLib.getValue("libautojs6-node.so")
    val x86LibcxxPresent = "lib/x86/libc++_shared.so" in nativeEntriesByLib.getValue("libc++_shared.so")
    val x86NodeRuntimePresent = x86LibnodePresent || x86BridgePresent
    val nodeRuntimeNativeEntriesPresent =
        nativeEntriesByLib.getValue("libnode.so").isNotEmpty() ||
                nativeEntriesByLib.getValue("libautojs6-node.so").isNotEmpty()
    val releaseFlavorNativePolicyReady = when (releaseFlavor) {
        "rhinoOnly" -> !nodeRuntimeNativeEntriesPresent && !x86NodeRuntimePresent
        else -> nativeLibsReady && !x86NodeRuntimePresent
    }
    val manifestServiceReady = manifestInfo.declaresEmbeddedScriptService &&
            manifestInfo.embeddedScriptServiceUsesNodeProcess &&
            manifestInfo.embeddedScriptServiceUsesSpecialUseFgs &&
            manifestInfo.embeddedScriptServiceDeclaresSpecialUseSubtypeProperty
    val manifestPermissionsReady = manifestInfo.declaresForegroundServicePermission &&
            manifestInfo.declaresForegroundServiceSpecialUsePermission
    val projectConfigPresent = embeddedNodePackagingProjectConfigEntry in apkEntryNames
    val projectConfigSelectsEmbeddedNode = projectConfigText?.contains("\"Embedded Node.js\"") == true
    val projectConfigMainReady = projectMainEntry.startsWith("assets/project/") && projectMainEntry in apkEntryNames
    val projectConfigExpectedAbisReady = if (expectedAbis.isEmpty()) {
        true
    } else {
        unknownExpectedAbis.isEmpty() && expectedAbis.all { abi -> projectConfigText?.contains("\"$abi\"") == true }
    }
    val bundledProjectConfigReady = projectConfigPresent &&
            projectConfigSelectsEmbeddedNode &&
            projectConfigMainReady &&
            projectConfigExpectedAbisReady
    val mainScriptPresent = mainScriptBytes != null
    val mainScriptEncrypted = mainScriptBytes?.isNotEmpty() == true &&
            !mainScriptText.contains("\"nodejs\";") &&
            !mainScriptText.contains("require(\"fs\")") &&
            !mainScriptText.contains("packaged.node.version")
    val bundledMainScriptReady = mainScriptPresent && mainScriptEncrypted
    val runtimeMatrixPresent = embeddedNodePackagingRuntimeMatrixEntry in apkEntryNames
    val runtimeMatrixMarkersReady = runtimeMatrixText != null &&
            embeddedNodePackagingFullSmokeMarkers.all { marker -> runtimeMatrixText.contains(marker) }
    val moduleSourcesBudgetRequested = runtimeMatrixText?.contains("packaged.node.version=v24.5.0") == true
    val preloadBudgetPresent = embeddedNodePackagingPreloadBudgetEntry in apkEntryNames
    val moduleSourcesEstimatedCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.count")
    val moduleSourcesBudgetLimit =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.limit")
    val moduleSourcesMaxBytesPerSource =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.max_bytes_per_source")
    val moduleSourcesMaxBytesTotal =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.max_bytes_total")
    val moduleSourcesTotalBytes =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.total_bytes")
    val moduleSourcesMaxObservedBytes =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.module_sources.max_observed_bytes")
    val moduleSourcesPackageJsonCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.package_json.count")
    val moduleSourcesJsonModuleCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.json_module.count")
    val moduleSourcesStaticRequireCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.static_require.count")
    val moduleSourcesDynamicRequireIgnoredCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.dynamic_require_ignored.count")
    val moduleSourcesUnresolvedStaticRequireCount =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.unresolved_static_require.count")
    val moduleSourcesMaxDepth =
            preloadBudgetValues.embeddedNodePackagingIntValue("packaged.preload.max_depth")
    val moduleSourcesReadyMarker = preloadBudgetValues["packaged.preload.module_sources.ready"] == "true"
    val moduleSourceBudgetEntries = embeddedNodePackagingModuleSourceBudgetEntries(preloadBudgetValues)
    val moduleSourceLargestEntries = embeddedNodePackagingModuleSourceBudgetEntries(
            preloadBudgetValues,
            prefix = "packaged.preload.module_sources.largest.",
    )
    val moduleSourcesCountReady =
            moduleSourcesEstimatedCount != null &&
                    moduleSourcesBudgetLimit == embeddedNodePackagingModuleSourcesLimit &&
                    moduleSourcesEstimatedCount <= embeddedNodePackagingModuleSourcesLimit
    val moduleSourcesTotalBytesReady =
            moduleSourcesTotalBytes != null &&
                    moduleSourcesMaxBytesTotal == embeddedNodePackagingModuleSourcesMaxBytes &&
                    moduleSourcesTotalBytes <= embeddedNodePackagingModuleSourcesMaxBytes
    val moduleSourcesPerModuleBytesReady =
            moduleSourcesMaxBytesPerSource == embeddedNodePackagingModuleSourceMaxBytes &&
                    moduleSourcesMaxObservedBytes != null &&
                    moduleSourcesMaxObservedBytes <= embeddedNodePackagingModuleSourceMaxBytes &&
                    moduleSourcesEstimatedCount != null &&
                    moduleSourceBudgetEntries.size == moduleSourcesEstimatedCount &&
                    moduleSourceBudgetEntries.all { entry ->
                        entry.bytes != null && entry.bytes <= embeddedNodePackagingModuleSourceMaxBytes
                    }
    val moduleSourcesBudgetReady = !moduleSourcesBudgetRequested ||
            (preloadBudgetPresent &&
                    moduleSourcesReadyMarker &&
                    moduleSourcesCountReady &&
                    moduleSourcesTotalBytesReady &&
                    moduleSourcesPerModuleBytesReady)
    val realNpmCorpusRequested = runtimeMatrixText?.contains(embeddedNodePackagingRealNpmCorpusReadyMarker) == true
    val realNpmCorpusMarkersReady = !realNpmCorpusRequested ||
            embeddedNodePackagingRealNpmCorpusMarkers.all { marker -> runtimeMatrixText?.contains(marker) == true }
    val realNpmCorpusPackageJsonPresent = !realNpmCorpusRequested ||
            embeddedNodePackagingRealNpmPackageJsonEntries.all { entry -> entry in apkEntryNames }
    val realNpmCorpusJavaScriptPresent = !realNpmCorpusRequested ||
            embeddedNodePackagingRealNpmJavaScriptEntries.all { entry -> entry in apkEntryNames }
    val realNpmCorpusJavaScriptEncrypted = !realNpmCorpusRequested ||
            embeddedNodePackagingRealNpmJavaScriptEntries.all { entry ->
                val text = packagedApk
                    ?.takeIf { it.isFile }
                    ?.let { embeddedNodePackagingApkEntryText(it, entry) }
                    .orEmpty()
                text.isNotEmpty() &&
                        !text.contains("module.exports") &&
                        !text.contains("require(\"") &&
                        !text.contains("pkg:test")
            }
    val realNpmCorpusReady = !realNpmCorpusRequested ||
            (realNpmCorpusMarkersReady &&
                    realNpmCorpusPackageJsonPresent &&
                    realNpmCorpusJavaScriptPresent &&
                    realNpmCorpusJavaScriptEncrypted)
    val cjsResolverRequested = runtimeMatrixText?.contains(embeddedNodePackagingCjsResolverReadyMarker) == true
    val cjsResolverMarkersReady = !cjsResolverRequested ||
            embeddedNodePackagingCjsResolverMarkers.all { marker -> runtimeMatrixText?.contains(marker) == true }
    val cjsResolverPackageJsonPresent = !cjsResolverRequested ||
            embeddedNodePackagingCjsResolverPackageJsonEntries.all { entry -> entry in apkEntryNames }
    val cjsResolverJavaScriptPresent = !cjsResolverRequested ||
            embeddedNodePackagingCjsResolverJavaScriptEntries.all { entry -> entry in apkEntryNames }
    val cjsResolverJavaScriptEncrypted = !cjsResolverRequested ||
            embeddedNodePackagingCjsResolverJavaScriptEntries.all { entry ->
                val text = packagedApk
                    ?.takeIf { it.isFile }
                    ?.let { embeddedNodePackagingApkEntryText(it, entry) }
                    .orEmpty()
                text.isNotEmpty() &&
                        !text.contains("module.exports") &&
                        !text.contains("exports.value") &&
                        !text.contains("require(\"") &&
                        !text.contains("resolver-edge-pkg") &&
                        !text.contains("imports-edge-pkg")
            }
    val cjsResolverReady = !cjsResolverRequested ||
            (cjsResolverMarkersReady &&
                    cjsResolverPackageJsonPresent &&
                    cjsResolverJavaScriptPresent &&
                    cjsResolverJavaScriptEncrypted)
    val dependencyResolutionRequested =
            runtimeMatrixText?.contains(embeddedNodePackagingDependencyResolutionReadyMarker) == true
    val dependencyResolutionMarkersReady = !dependencyResolutionRequested ||
            embeddedNodePackagingDependencyResolutionMarkers.all { marker ->
                runtimeMatrixText?.contains(marker) == true
            }
    val dependencyResolutionPackageJsonPresent = !dependencyResolutionRequested ||
            embeddedNodePackagingDependencyResolutionPackageJsonEntries.all { entry -> entry in apkEntryNames }
    val dependencyResolutionJavaScriptPresent = !dependencyResolutionRequested ||
            embeddedNodePackagingDependencyResolutionJavaScriptEntries.all { entry -> entry in apkEntryNames }
    val dependencyResolutionJavaScriptEncrypted = !dependencyResolutionRequested ||
            embeddedNodePackagingDependencyResolutionJavaScriptEntries.all { entry ->
                val text = packagedApk
                    ?.takeIf { it.isFile }
                    ?.let { embeddedNodePackagingApkEntryText(it, entry) }
                    .orEmpty()
                text.isNotEmpty() &&
                        !text.contains("module.exports") &&
                        !text.contains("exports.value") &&
                        !text.contains("require(\"") &&
                        !text.contains("__autojs6Shadow") &&
                        !text.contains("resolution-shared")
            }
    val dependencyResolutionReady = !dependencyResolutionRequested ||
            (dependencyResolutionMarkersReady &&
                    dependencyResolutionPackageJsonPresent &&
                    dependencyResolutionJavaScriptPresent &&
                    dependencyResolutionJavaScriptEncrypted)
    val diagnosticsRequested = runtimeMatrixText?.contains(embeddedNodePackagingDiagnosticsReadyMarker) == true
    val diagnosticsMarkersReady = !diagnosticsRequested ||
            embeddedNodePackagingDiagnosticsMarkers.all { marker -> runtimeMatrixText?.contains(marker) == true }
    val diagnosticsPackageJsonPresent = !diagnosticsRequested ||
            embeddedNodePackagingDiagnosticsPackageJsonEntries.all { entry -> entry in apkEntryNames }
    val diagnosticsJavaScriptPresent = !diagnosticsRequested ||
            embeddedNodePackagingDiagnosticsJavaScriptEntries.all { entry -> entry in apkEntryNames }
    val diagnosticsJavaScriptEncrypted = !diagnosticsRequested ||
            embeddedNodePackagingDiagnosticsJavaScriptEntries.all { entry ->
                val text = packagedApk
                    ?.takeIf { it.isFile }
                    ?.let { embeddedNodePackagingApkEntryText(it, entry) }
                    .orEmpty()
                text.isNotEmpty() &&
                        !text.contains("packaged.diagnostics") &&
                        !text.contains("diagnostic-boom") &&
                        !text.contains("ERR_AUTOJS6_EMBEDDED_NODE") &&
                        !text.contains("setInterval") &&
                        !text.contains("import(")
            }
    val diagnosticsReady = !diagnosticsRequested ||
            (diagnosticsMarkersReady &&
                    diagnosticsPackageJsonPresent &&
                    diagnosticsJavaScriptPresent &&
                    diagnosticsJavaScriptEncrypted)
    val fullCompatibilitySmokeReady = bundledProjectConfigReady &&
            bundledMainScriptReady &&
            runtimeMatrixMarkersReady
    val packagedOutputReady = if (releaseFlavor == "rhinoOnly") {
        packagedApkExists && releaseFlavorNativePolicyReady
    } else {
        packagedApkExists &&
                manifestInfo.inspected &&
                manifestServiceReady &&
                manifestPermissionsReady &&
                releaseFlavorNativePolicyReady &&
                dexDefinesScriptService &&
                bundledProjectConfigReady &&
                bundledMainScriptReady &&
                moduleSourcesBudgetReady &&
                realNpmCorpusReady &&
                cjsResolverReady &&
                dependencyResolutionReady &&
                diagnosticsReady
    }

    val report = buildString {
        appendLine("Embedded Node.js packaged APK output verification report")
        appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
        appendLine()

        appendLine("Inputs:")
        appendLine("  packaged APK property: ${packagedApkProperty ?: "not set"}")
        appendLine("  packaged APK: ${packagedApk?.let { embeddedNodePackagingFormatFile(it) } ?: "not provided"}")
        appendLine("  expected ABI property: ${expectedAbiProperty ?: "not set"}")
        appendLine("  release flavor: $releaseFlavor")
        appendLine("  expected ABI source: ${releaseFlavorPolicy.expectedAbisSource}")
        appendLine("  expected ABIs: ${if (expectedAbis.isEmpty()) "none; rhinoOnly verifier requires no Embedded Node native runtime" else expectedAbis.joinToString()}")
        appendLine("  unknown expected ABIs: ${if (unknownExpectedAbis.isEmpty()) "none" else unknownExpectedAbis.joinToString()}")
        appendLine("  unsupported expected ABIs: ${if (unsupportedExpectedAbis.isEmpty()) "none" else unsupportedExpectedAbis.joinToString()}")
        appendLine()

        appendLine("Current build runtime config:")
        appendLine("  autojs.nodejs.backend: $runtimeBackend")
        appendLine("  autojs.nodejs.embedded.script.enabled: $scriptExecutionEnabled")
        appendLine("  current build runtime config ready: $currentBuildRuntimeConfigReady")
        appendLine()

        appendLine("Manifest verification:")
        appendEmbeddedNodePackagingManifestInfo("packaged APK", manifestInfo)
        appendLine("  manifest service ready: $manifestServiceReady")
        appendLine("  manifest FGS permissions ready: $manifestPermissionsReady")
        appendLine()

        appendLine("DEX verification:")
        appendLine("  DEX entries: ${if (dexEntries.isEmpty()) "none" else dexEntries.joinToString()}")
        appendLine("  dex contains NativeNodeEmbeddedScriptService class_def: $dexDefinesScriptService")
        appendLine()

        appendLine("Project runtime verification:")
        appendLine("  project config entry: $embeddedNodePackagingProjectConfigEntry")
        appendLine("  project config present: $projectConfigPresent")
        appendLine("  project config selects Embedded Node.js: $projectConfigSelectsEmbeddedNode")
        appendLine("  project config expected ABI policy ready: $projectConfigExpectedAbisReady")
        appendLine("  project main script entry: $projectMainEntry")
        appendLine("  project main script present: $mainScriptPresent")
        appendLine("  project main script appears encrypted: $mainScriptEncrypted")
        appendLine("  bundled project config ready: $bundledProjectConfigReady")
        appendLine("  bundled main script ready: $bundledMainScriptReady")
        appendLine("  full compatibility smoke marker entry: $embeddedNodePackagingRuntimeMatrixEntry")
        appendLine("  full compatibility smoke marker present: $runtimeMatrixPresent")
        appendLine("  full compatibility smoke markers ready: $runtimeMatrixMarkersReady")
        embeddedNodePackagingFullSmokeMarkers.forEach { marker ->
            appendLine("    marker '$marker': ${runtimeMatrixText?.contains(marker) == true}")
        }
        appendLine()

        appendLine("Node bridge capability manifest verification:")
        appendLine("  capability manifest enforced: ${bridgeCapabilityReport.enforced}")
        appendLine("  capability manifest sources: ${if (bridgeCapabilityReport.sources.isEmpty()) "none" else bridgeCapabilityReport.sources.joinToString()}")
        appendLine("  declared bridge capabilities: ${if (bridgeCapabilityReport.capabilities.isEmpty()) "none" else bridgeCapabilityReport.capabilities.joinToString()}")
        appendLine("  unknown bridge capabilities: ${if (bridgeCapabilityReport.unknownCapabilities.isEmpty()) "none" else bridgeCapabilityReport.unknownCapabilities.joinToString()}")
        appendLine("  required Android manifest permissions: ${if (bridgeCapabilityReport.requiredAndroidPermissions.isEmpty()) "none" else bridgeCapabilityReport.requiredAndroidPermissions.joinToString()}")
        appendLine("  declared relevant Android manifest permissions: ${if (bridgeCapabilityReport.declaredRelevantAndroidPermissions.isEmpty()) "none" else bridgeCapabilityReport.declaredRelevantAndroidPermissions.joinToString()}")
        appendLine("  missing required Android manifest permissions: ${if (bridgeCapabilityReport.missingAndroidPermissions.isEmpty()) "none" else bridgeCapabilityReport.missingAndroidPermissions.joinToString()}")
        appendLine("  unused relevant Android manifest permissions: ${if (bridgeCapabilityReport.unusedRelevantAndroidPermissions.isEmpty()) "none" else bridgeCapabilityReport.unusedRelevantAndroidPermissions.joinToString()}")
        appendLine("  required Android manifest permissions ready: ${bridgeCapabilityReport.requiredManifestReady}")
        appendLine("  relevant Android manifest permissions required-only clean: ${bridgeCapabilityReport.requiredOnlyReady}")
        appendLine()

        appendLine("Packaged moduleSources budget verification:")
        appendLine("  moduleSources budget requested by full smoke marker: $moduleSourcesBudgetRequested")
        appendLine("  moduleSources budget entry: $embeddedNodePackagingPreloadBudgetEntry")
        appendLine("  moduleSources budget present: $preloadBudgetPresent")
        appendLine("  moduleSources estimated count: ${moduleSourcesEstimatedCount ?: "unknown"}")
        appendLine("  moduleSources limit: ${moduleSourcesBudgetLimit ?: embeddedNodePackagingModuleSourcesLimit}")
        appendLine("  moduleSources count ready: $moduleSourcesCountReady")
        appendLine("  moduleSources total bytes: ${moduleSourcesTotalBytes ?: "unknown"}")
        appendLine("  moduleSources total bytes limit: ${moduleSourcesMaxBytesTotal ?: embeddedNodePackagingModuleSourcesMaxBytes}")
        appendLine("  moduleSources total bytes ready: $moduleSourcesTotalBytesReady")
        appendLine("  moduleSources max observed bytes: ${moduleSourcesMaxObservedBytes ?: "unknown"}")
        appendLine("  moduleSources per-module bytes limit: ${moduleSourcesMaxBytesPerSource ?: embeddedNodePackagingModuleSourceMaxBytes}")
        appendLine("  moduleSources per-module bytes ready: $moduleSourcesPerModuleBytesReady")
        appendLine("  moduleSources ready marker: $moduleSourcesReadyMarker")
        appendLine("  moduleSources budget failure code: $embeddedNodePackagingModuleSourceBudgetExceededCode")
        appendLine("  package.json count: ${moduleSourcesPackageJsonCount ?: "unknown"}")
        appendLine("  JSON module count: ${moduleSourcesJsonModuleCount ?: "unknown"}")
        appendLine("  static require count: ${moduleSourcesStaticRequireCount ?: "unknown"}")
        appendLine("  dynamic require ignored count: ${moduleSourcesDynamicRequireIgnoredCount ?: "unknown"}")
        appendLine("  unresolved static require count: ${moduleSourcesUnresolvedStaticRequireCount ?: "unknown"}")
        appendLine("  max depth: ${moduleSourcesMaxDepth ?: "unknown"}")
        appendLine("  largest moduleSources:")
        if (moduleSourceLargestEntries.isEmpty()) {
            appendLine("    none")
        } else {
            moduleSourceLargestEntries.forEach { entry ->
                appendLine("    ${entry.sourceName}: ${entry.bytes ?: "unknown"} bytes")
            }
        }
        appendLine("  moduleSources budget ready: $moduleSourcesBudgetReady")
        appendLine()

        appendLine("Packaged real npm corpus verification:")
        appendLine("  real npm corpus requested by marker: $realNpmCorpusRequested")
        appendLine("  real npm corpus markers ready: $realNpmCorpusMarkersReady")
        embeddedNodePackagingRealNpmCorpusMarkers.forEach { marker ->
            appendLine("    marker '$marker': ${runtimeMatrixText?.contains(marker) == true}")
        }
        appendLine("  real npm corpus package.json entries present: $realNpmCorpusPackageJsonPresent")
        embeddedNodePackagingRealNpmPackageJsonEntries.forEach { entry ->
            appendLine("    package_json '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  real npm corpus JavaScript entries present: $realNpmCorpusJavaScriptPresent")
        embeddedNodePackagingRealNpmJavaScriptEntries.forEach { entry ->
            appendLine("    javascript '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  real npm corpus JavaScript entries appear encrypted: $realNpmCorpusJavaScriptEncrypted")
        appendLine("  real npm corpus ready: $realNpmCorpusReady")
        appendLine()

        appendLine("Packaged CommonJS resolver corpus verification:")
        appendLine("  CommonJS resolver corpus requested by marker: $cjsResolverRequested")
        appendLine("  CommonJS resolver corpus markers ready: $cjsResolverMarkersReady")
        embeddedNodePackagingCjsResolverMarkers.forEach { marker ->
            appendLine("    marker '$marker': ${runtimeMatrixText?.contains(marker) == true}")
        }
        appendLine("  CommonJS resolver corpus package.json entries present: $cjsResolverPackageJsonPresent")
        embeddedNodePackagingCjsResolverPackageJsonEntries.forEach { entry ->
            appendLine("    package_json '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  CommonJS resolver corpus JavaScript entries present: $cjsResolverJavaScriptPresent")
        embeddedNodePackagingCjsResolverJavaScriptEntries.forEach { entry ->
            appendLine("    javascript '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  CommonJS resolver corpus JavaScript entries appear encrypted: $cjsResolverJavaScriptEncrypted")
        appendLine("  CommonJS resolver corpus ready: $cjsResolverReady")
        appendLine()

        appendLine("Packaged dependency resolution verification:")
        appendLine("  dependency resolution requested by marker: $dependencyResolutionRequested")
        appendLine("  dependency resolution markers ready: $dependencyResolutionMarkersReady")
        embeddedNodePackagingDependencyResolutionMarkers.forEach { marker ->
            appendLine("    marker '$marker': ${runtimeMatrixText?.contains(marker) == true}")
        }
        appendLine("  dependency resolution package.json entries present: $dependencyResolutionPackageJsonPresent")
        embeddedNodePackagingDependencyResolutionPackageJsonEntries.forEach { entry ->
            appendLine("    package_json '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  dependency resolution JavaScript entries present: $dependencyResolutionJavaScriptPresent")
        embeddedNodePackagingDependencyResolutionJavaScriptEntries.forEach { entry ->
            appendLine("    javascript '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  dependency resolution JavaScript entries appear encrypted: $dependencyResolutionJavaScriptEncrypted")
        appendLine("  dependency resolution ready: $dependencyResolutionReady")
        appendLine()

        appendLine("Packaged diagnostics verification:")
        appendLine("  diagnostics requested by marker: $diagnosticsRequested")
        appendLine("  diagnostics markers ready: $diagnosticsMarkersReady")
        embeddedNodePackagingDiagnosticsMarkers.forEach { marker ->
            appendLine("    marker '$marker': ${runtimeMatrixText?.contains(marker) == true}")
        }
        appendLine("  diagnostics package.json entries present: $diagnosticsPackageJsonPresent")
        embeddedNodePackagingDiagnosticsPackageJsonEntries.forEach { entry ->
            appendLine("    package_json '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  diagnostics JavaScript entries present: $diagnosticsJavaScriptPresent")
        embeddedNodePackagingDiagnosticsJavaScriptEntries.forEach { entry ->
            appendLine("    javascript '$entry': ${entry in apkEntryNames}")
        }
        appendLine("  diagnostics JavaScript entries appear encrypted: $diagnosticsJavaScriptEncrypted")
        appendLine("  diagnostics ready: $diagnosticsReady")
        appendLine()

        appendLine("Native library verification:")
        embeddedNodePackagingNativeLibs.forEach { libName ->
            val entries = nativeEntriesByLib.getValue(libName)
            appendLine("  $libName entries: ${if (entries.isEmpty()) "none" else entries.joinToString()}")
            entries.forEach { entry ->
                appendLine("    $entry size: ${packagedApk?.let { embeddedNodePackagingApkEntrySize(it, entry) }?.toString() ?: "unknown"}")
                appendLine("    $entry sha256: ${packagedApk?.let { embeddedNodePackagingApkEntrySha256(it, entry) } ?: "unavailable"}")
            }
        }
        appendLine("  complete Embedded Node ABIs: ${if (completeReadyAbis.isEmpty()) "none" else completeReadyAbis.joinToString()}")
        appendLine("  unexpected complete Embedded Node ABIs for expected ABI policy: ${if (unexpectedCompleteReadyAbis.isEmpty()) "none" else unexpectedCompleteReadyAbis.joinToString()}")
        appendLine("  native libs ready for expected ABI policy: $nativeLibsReady")
        appendLine("  Node runtime native entries present: $nodeRuntimeNativeEntriesPresent")
        appendLine("  release flavor native policy ready: $releaseFlavorNativePolicyReady")
        appendLine("  x86 libnode.so present: $x86LibnodePresent")
        appendLine("  x86 libautojs6-node.so present: $x86BridgePresent")
        appendLine("  x86 libc++_shared.so present: $x86LibcxxPresent")
        appendLine("  x86 Node runtime present: $x86NodeRuntimePresent")
        packagedApk?.takeIf { it.isFile }?.let { apk ->
            appendEmbeddedNodePackagingAbiMatrix("packaged APK", apk, embeddedNodePackagingKnownAbis)
        }
        appendLine()

        appendLine("Output summary:")
        appendLine("  embedded.node.packaged.output.apk.exists=$packagedApkExists")
        appendLine("  embedded.node.packaged.output.manifest.service.ready=$manifestServiceReady")
        appendLine("  embedded.node.packaged.output.manifest.fgs_permission.ready=$manifestPermissionsReady")
        appendLine("  embedded.node.packaged.output.dex.contains.NativeNodeEmbeddedScriptService=$dexDefinesScriptService")
        appendLine("  embedded.node.packaged.output.project_config.ready=$bundledProjectConfigReady")
        appendLine("  embedded.node.packaged.output.bridge_capabilities.enforced=${bridgeCapabilityReport.enforced}")
        appendLine("  embedded.node.packaged.output.bridge_capabilities.required_android_permissions.ready=${bridgeCapabilityReport.requiredManifestReady}")
        appendLine("  embedded.node.packaged.output.bridge_capabilities.required_only.ready=${bridgeCapabilityReport.requiredOnlyReady}")
        appendLine("  embedded.node.packaged.output.main_script.ready=$bundledMainScriptReady")
        appendLine("  embedded.node.packaged.output.full_compatibility_smoke.ready=$fullCompatibilitySmokeReady")
        appendLine("  embedded.node.packaged.output.module_sources.requested=$moduleSourcesBudgetRequested")
        appendLine("  embedded.node.packaged.output.module_sources.budget.present=$preloadBudgetPresent")
        appendLine("  embedded.node.packaged.output.module_sources.estimated_count=${moduleSourcesEstimatedCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.limit=${moduleSourcesBudgetLimit ?: embeddedNodePackagingModuleSourcesLimit}")
        appendLine("  embedded.node.packaged.output.module_sources.count.ready=$moduleSourcesCountReady")
        appendLine("  embedded.node.packaged.output.module_sources.total_bytes=${moduleSourcesTotalBytes ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.max_bytes_total=${moduleSourcesMaxBytesTotal ?: embeddedNodePackagingModuleSourcesMaxBytes}")
        appendLine("  embedded.node.packaged.output.module_sources.total_bytes.ready=$moduleSourcesTotalBytesReady")
        appendLine("  embedded.node.packaged.output.module_sources.max_observed_bytes=${moduleSourcesMaxObservedBytes ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.max_bytes_per_source=${moduleSourcesMaxBytesPerSource ?: embeddedNodePackagingModuleSourceMaxBytes}")
        appendLine("  embedded.node.packaged.output.module_sources.per_module_bytes.ready=$moduleSourcesPerModuleBytesReady")
        appendLine("  embedded.node.packaged.output.module_sources.package_json_count=${moduleSourcesPackageJsonCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.json_module_count=${moduleSourcesJsonModuleCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.static_require_count=${moduleSourcesStaticRequireCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.dynamic_require_ignored_count=${moduleSourcesDynamicRequireIgnoredCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.unresolved_static_require_count=${moduleSourcesUnresolvedStaticRequireCount ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.max_depth=${moduleSourcesMaxDepth ?: "unknown"}")
        appendLine("  embedded.node.packaged.output.module_sources.error_code=${if (moduleSourcesBudgetReady) "none" else embeddedNodePackagingModuleSourceBudgetExceededCode}")
        appendLine("  embedded.node.packaged.output.module_sources.ready=$moduleSourcesBudgetReady")
        appendLine("  embedded.node.packaged.output.real_npm_corpus.requested=$realNpmCorpusRequested")
        appendLine("  embedded.node.packaged.output.real_npm_corpus.package_json.present=$realNpmCorpusPackageJsonPresent")
        appendLine("  embedded.node.packaged.output.real_npm_corpus.node_modules.present=$realNpmCorpusJavaScriptPresent")
        appendLine("  embedded.node.packaged.output.real_npm_corpus.encrypted_js.ready=$realNpmCorpusJavaScriptEncrypted")
        appendLine("  embedded.node.packaged.output.real_npm_corpus.ready=$realNpmCorpusReady")
        appendLine("  embedded.node.packaged.output.cjs_resolver.requested=$cjsResolverRequested")
        appendLine("  embedded.node.packaged.output.cjs_resolver.markers.ready=$cjsResolverMarkersReady")
        appendLine("  embedded.node.packaged.output.cjs_resolver.package_json.present=$cjsResolverPackageJsonPresent")
        appendLine("  embedded.node.packaged.output.cjs_resolver.scripts.present=$cjsResolverJavaScriptPresent")
        appendLine("  embedded.node.packaged.output.cjs_resolver.encrypted_js.ready=$cjsResolverJavaScriptEncrypted")
        appendLine("  embedded.node.packaged.output.cjs_resolver.ready=$cjsResolverReady")
        appendLine("  embedded.node.packaged.output.dependency_resolution.requested=$dependencyResolutionRequested")
        appendLine("  embedded.node.packaged.output.dependency_resolution.markers.ready=$dependencyResolutionMarkersReady")
        appendLine("  embedded.node.packaged.output.dependency_resolution.package_json.present=$dependencyResolutionPackageJsonPresent")
        appendLine("  embedded.node.packaged.output.dependency_resolution.scripts.present=$dependencyResolutionJavaScriptPresent")
        appendLine("  embedded.node.packaged.output.dependency_resolution.encrypted_js.ready=$dependencyResolutionJavaScriptEncrypted")
        appendLine("  embedded.node.packaged.output.dependency_resolution.ready=$dependencyResolutionReady")
        appendLine("  embedded.node.packaged.output.diagnostics.requested=$diagnosticsRequested")
        appendLine("  embedded.node.packaged.output.diagnostics.markers.ready=$diagnosticsMarkersReady")
        appendLine("  embedded.node.packaged.output.diagnostics.package_json.present=$diagnosticsPackageJsonPresent")
        appendLine("  embedded.node.packaged.output.diagnostics.scripts.present=$diagnosticsJavaScriptPresent")
        appendLine("  embedded.node.packaged.output.diagnostics.encrypted_js.ready=$diagnosticsJavaScriptEncrypted")
        appendLine("  embedded.node.packaged.output.diagnostics.ready=$diagnosticsReady")
        appendLine("  embedded.node.packaged.output.current_build_config.ready=$currentBuildRuntimeConfigReady")
        appendLine("  embedded.node.packaged.output.release.flavor=$releaseFlavor")
        appendLine("  embedded.node.packaged.output.release.expected_abis=${if (expectedAbis.isEmpty()) "none" else expectedAbis.joinToString()}")
        appendLine("  embedded.node.packaged.output.release.native_policy.ready=$releaseFlavorNativePolicyReady")
        appendLine("  embedded.node.packaged.output.native_runtime_entries.present=$nodeRuntimeNativeEntriesPresent")
        appendLine("  embedded.node.packaged.output.native_libs.ready=$nativeLibsReady")
        appendLine("  embedded.node.packaged.output.native_libs.unexpected_complete_abis=${if (unexpectedCompleteReadyAbis.isEmpty()) "none" else unexpectedCompleteReadyAbis.joinToString()}")
        appendLine("  embedded.node.packaged.output.x86_node_runtime.present=$x86NodeRuntimePresent")
        appendLine("  embedded.node.packaged.output.ready=$packagedOutputReady")
        appendLine()

        appendLine("Notes:")
        appendLine("  This task verifies an already-created packaged APK and does not build or install it.")
        appendLine("  Pass a real output APK with -Pautojs.embeddedNode.packagedApk=<apk>.")
        appendLine("  Optionally constrain native ABI expectations with -Pautojs.embeddedNode.expectedAbis=arm64-v8a,armeabi-v7a,x86_64.")
        appendLine("  Without an explicit expected ABI property, expected ABIs are derived from -P$embeddedNodePackagingReleaseFlavorProperty.")
    }


    return EmbeddedNodePackagedOutputVerification(
        report = report,
        ready = packagedOutputReady,
        moduleSourcesReady = moduleSourcesBudgetReady,
    )
}

val embeddedNodePackagedApkOutputVerify = tasks.register("embeddedNodePackagedApkOutputVerify") {
    group = "verification"
    description = "Writes an Embedded Node.js real packaged APK output verifier report. Use -Pautojs.embeddedNode.packagedApk=<apk>."

    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-apk-output-verify.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/packaged-apk-output-verify.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        var packagedApkProperty: String? = null
        for (propertyName in listOf("autojs.embeddedNode.packagedApk", "embeddedNodePackagedApk")) {
            val value = providers.gradleProperty(propertyName).orNull?.trim()
            if (!value.isNullOrEmpty()) {
                packagedApkProperty = value
                break
            }
        }
        var expectedAbiProperty: String? = null
        for (propertyName in listOf("autojs.embeddedNode.expectedAbis", "embeddedNodeExpectedAbis")) {
            val value = providers.gradleProperty(propertyName).orNull?.trim()
            if (!value.isNullOrEmpty()) {
                expectedAbiProperty = value
                break
            }
        }
        val releaseFlavorPolicy = embeddedNodePackagingReleaseFlavorPolicy(
            rawFlavor = providers.gradleProperty(embeddedNodePackagingReleaseFlavorProperty)
                .orElse(embeddedNodePackagingDefaultReleaseFlavor)
                .get(),
            rawReleaseAbis = providers.gradleProperty(embeddedNodePackagingReleaseAbisProperty).orNull,
            explicitExpectedAbis = embeddedNodePackagingCsvList(expectedAbiProperty),
        )

        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend")
            .orElse("embedded")
            .get()
            .trim()
            .lowercase()
        val scriptExecutionEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled")
            .orElse("true")
            .get()
            .toEmbeddedNodePackagingBoolean()
        val verification = embeddedNodePackagingVerifyPackagedApkOutput(
            rootDir = rootProject.projectDir,
            packagedApkProperty = packagedApkProperty,
            expectedAbiProperty = expectedAbiProperty,
            releaseFlavorPolicy = releaseFlavorPolicy,
            runtimeBackend = runtimeBackend,
            scriptExecutionEnabled = scriptExecutionEnabled,
        )
        reportFile.writeText(verification.report)
        logger.lifecycle("Embedded Node.js packaged APK output verifier report written to ${reportFile.absolutePath}")
    }
}

val embeddedNodePackagedDiagnosticReport = tasks.register("embeddedNodePackagedDiagnosticReport") {
    group = "verification"
    description = "Writes and gates an actionable Embedded Node.js packaged APK diagnostic report. Use -Pautojs.embeddedNode.packagedApk=<apk>."

    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-diagnostic-report.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportFile = layout.buildDirectory
            .file("reports/embedded-node/packaged-diagnostic-report.txt")
            .get()
            .asFile
        reportFile.parentFile.mkdirs()

        var packagedApkProperty: String? = null
        for (propertyName in listOf("autojs.embeddedNode.packagedApk", "embeddedNodePackagedApk")) {
            val value = providers.gradleProperty(propertyName).orNull?.trim()
            if (!value.isNullOrEmpty()) {
                packagedApkProperty = value
                break
            }
        }
        var expectedAbiProperty: String? = null
        for (propertyName in listOf("autojs.embeddedNode.expectedAbis", "embeddedNodeExpectedAbis")) {
            val value = providers.gradleProperty(propertyName).orNull?.trim()
            if (!value.isNullOrEmpty()) {
                expectedAbiProperty = value
                break
            }
        }
        val releaseFlavorPolicy = embeddedNodePackagingReleaseFlavorPolicy(
            rawFlavor = providers.gradleProperty(embeddedNodePackagingReleaseFlavorProperty)
                .orElse(embeddedNodePackagingDefaultReleaseFlavor)
                .get(),
            rawReleaseAbis = providers.gradleProperty(embeddedNodePackagingReleaseAbisProperty).orNull,
            explicitExpectedAbis = embeddedNodePackagingCsvList(expectedAbiProperty),
        )

        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend")
            .orElse("embedded")
            .get()
            .trim()
            .lowercase()
        val scriptExecutionEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled")
            .orElse("true")
            .get()
            .toEmbeddedNodePackagingBoolean()
        val verification = embeddedNodePackagingVerifyPackagedApkOutput(
            rootDir = rootProject.projectDir,
            packagedApkProperty = packagedApkProperty,
            expectedAbiProperty = expectedAbiProperty,
            releaseFlavorPolicy = releaseFlavorPolicy,
            runtimeBackend = runtimeBackend,
            scriptExecutionEnabled = scriptExecutionEnabled,
        )
        reportFile.writeText(verification.report)
        logger.lifecycle("Embedded Node.js packaged APK diagnostic report written to ${reportFile.absolutePath}")
        logger.lifecycle("Embedded Node.js packaged APK diagnostic ready: ${verification.ready}")
        if (!verification.ready) {
            throw GradleException(
                "Embedded Node.js packaged APK diagnostic report found issues. " +
                        "See ${reportFile.absolutePath}"
            )
        }
    }
}

val embeddedNodePackagedSmokeHostVerify = tasks.register(embeddedNodePackagedSmokeHostVerifyTaskName) {
    group = "verification"
    description = "Pulls exported Embedded Node packaged smoke APKs and verifies success/diagnostics APKs on the host."

    dependsOn(embeddedNodePullPackagedSmokeApks)
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-smoke-aggregate.txt"))
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-apk-output-verify-success.txt"))
    outputs.file(layout.buildDirectory.file("reports/embedded-node/packaged-apk-output-verify-diagnostics.txt"))
    outputs.upToDateWhen { false }

    doLast {
        val reportDir = layout.buildDirectory
            .dir("reports/embedded-node")
            .get()
            .asFile
        reportDir.mkdirs()
        val aggregateReportFile = reportDir.resolve("packaged-smoke-aggregate.txt")
        val successVerifierReportFile = reportDir.resolve("packaged-apk-output-verify-success.txt")
        val diagnosticsVerifierReportFile = reportDir.resolve("packaged-apk-output-verify-diagnostics.txt")

        val hostDir = layout.buildDirectory
            .dir(embeddedNodePackagingSmokeHostDirName)
            .get()
            .asFile
        val pulledFiles = embeddedNodePackagingSmokeApkNames.associateWith { apkName ->
            hostDir.resolve(apkName)
        }
        val missingPulledFiles = pulledFiles
            .filterValues { file -> !file.isFile || file.length() <= 0L }
            .keys
            .toList()
        val pullReady = missingPulledFiles.isEmpty()
        val successApk = hostDir.resolve("PackagedNodeSmoke_v1.0.0.apk")
        val diagnosticsApk = hostDir.resolve("PackagedNodeDiagnosticsSmoke_v1.0.0.apk")

        val runtimeBackend = providers.gradleProperty("autojs.nodejs.backend")
            .orElse("embedded")
            .get()
            .trim()
            .lowercase()
        val scriptExecutionEnabled = providers.gradleProperty("autojs.nodejs.embedded.script.enabled")
            .orElse("true")
            .get()
            .toEmbeddedNodePackagingBoolean()
        val expectedAbiProperty = providers.gradleProperty("autojs.embeddedNode.expectedAbis")
            .orElse("arm64-v8a")
            .get()
        val releaseFlavorPolicy = embeddedNodePackagingReleaseFlavorPolicy(
            rawFlavor = providers.gradleProperty(embeddedNodePackagingReleaseFlavorProperty)
                .orElse(embeddedNodePackagingDefaultReleaseFlavor)
                .get(),
            rawReleaseAbis = providers.gradleProperty(embeddedNodePackagingReleaseAbisProperty).orNull,
            explicitExpectedAbis = embeddedNodePackagingCsvList(expectedAbiProperty),
        )

        val successVerification = embeddedNodePackagingVerifyPackagedApkOutput(
            rootDir = rootProject.projectDir,
            packagedApkProperty = successApk.absolutePath,
            expectedAbiProperty = expectedAbiProperty,
            releaseFlavorPolicy = releaseFlavorPolicy,
            runtimeBackend = runtimeBackend,
            scriptExecutionEnabled = scriptExecutionEnabled,
        )
        val diagnosticsVerification = embeddedNodePackagingVerifyPackagedApkOutput(
            rootDir = rootProject.projectDir,
            packagedApkProperty = diagnosticsApk.absolutePath,
            expectedAbiProperty = expectedAbiProperty,
            releaseFlavorPolicy = releaseFlavorPolicy,
            runtimeBackend = runtimeBackend,
            scriptExecutionEnabled = scriptExecutionEnabled,
        )
        successVerifierReportFile.writeText(successVerification.report)
        diagnosticsVerifierReportFile.writeText(diagnosticsVerification.report)

        val tools = findEmbeddedNodePackagingTools(rootProject.projectDir)
        val templateApk = projectDir.resolve("src/main/assets-app/template.apk")
        val templateFreshness = embeddedNodePackagingTemplateFreshness(tools, templateApk)
        val aggregateReady = pullReady &&
                successVerification.ready &&
                diagnosticsVerification.ready &&
                templateFreshness.ready &&
                successVerification.moduleSourcesReady

        val report = buildString {
            appendLine("Embedded Node.js packaged smoke aggregate verification report")
            appendLine("Generated at: ${java.time.ZonedDateTime.now()}")
            appendLine()

            appendLine("Inputs:")
            appendLine("  host smoke APK dir: ${hostDir.absolutePath}")
            appendLine("  release flavor: ${releaseFlavorPolicy.flavor}")
            appendLine("  expected ABI property: $expectedAbiProperty")
            appendLine("  success APK: ${embeddedNodePackagingFormatFile(successApk)}")
            appendLine("  diagnostics APK: ${embeddedNodePackagingFormatFile(diagnosticsApk)}")
            appendLine("  template APK: ${embeddedNodePackagingFormatFile(templateApk)}")
            appendLine("  success verifier report: ${successVerifierReportFile.absolutePath}")
            appendLine("  diagnostics verifier report: ${diagnosticsVerifierReportFile.absolutePath}")
            appendLine()

            appendLine("Pulled smoke APKs:")
            pulledFiles.forEach { (apkName, file) ->
                appendLine("  $apkName:")
                appendLine("    path: ${file.absolutePath}")
                appendLine("    exists: ${file.isFile}")
                appendLine("    size: ${if (file.isFile) file.length().toString() else "missing"}")
                appendLine("    sha256: ${embeddedNodePackagingSha256(file) ?: "unavailable"}")
            }
            appendLine()

            appendLine("Template freshness:")
            appendEmbeddedNodePackagingTemplateFreshness("template", templateFreshness)
            appendLine()

            appendLine("Summary:")
            appendLine("  embedded.node.packaged.smoke.aggregate.host_dir=${hostDir.absolutePath}")
            appendLine("  embedded.node.packaged.smoke.aggregate.success.apk=${successApk.absolutePath}")
            appendLine("  embedded.node.packaged.smoke.aggregate.success.sha256=${embeddedNodePackagingSha256(successApk) ?: "unavailable"}")
            appendLine("  embedded.node.packaged.smoke.aggregate.diagnostics.apk=${diagnosticsApk.absolutePath}")
            appendLine("  embedded.node.packaged.smoke.aggregate.diagnostics.sha256=${embeddedNodePackagingSha256(diagnosticsApk) ?: "unavailable"}")
            appendLine("  embedded.node.packaged.smoke.pull.ready=$pullReady")
            appendLine("  embedded.node.packaged.smoke.pull.missing=${if (missingPulledFiles.isEmpty()) "none" else missingPulledFiles.joinToString()}")
            appendLine("  embedded.node.packaged.smoke.success.output.ready=${successVerification.ready}")
            appendLine("  embedded.node.packaged.smoke.diagnostics.output.ready=${diagnosticsVerification.ready}")
            appendLine("  embedded.node.packaged.smoke.template.ready=${templateFreshness.ready}")
            appendLine("  embedded.node.packaged.smoke.template.missing=${if (templateFreshness.missing.isEmpty()) "none" else templateFreshness.missing.joinToString()}")
            appendLine("  embedded.node.packaged.smoke.module_sources.ready=${successVerification.moduleSourcesReady}")
            appendLine("  embedded.node.packaged.smoke.aggregate.ready=$aggregateReady")
            appendLine()

            appendLine("Notes:")
            appendLine("  Run EmbeddedNodePackagedApkSmokeTest before this task so device public storage contains current smoke APKs.")
            appendLine("  This task does not build, install, or launch packaged APKs; it verifies pulled APK artifacts on the host.")
            appendLine("  It does not recursively invoke Gradle.")
        }

        aggregateReportFile.writeText(report)
        logger.lifecycle("Embedded Node.js packaged smoke aggregate report written to ${aggregateReportFile.absolutePath}")
        logger.lifecycle("Embedded Node.js packaged smoke aggregate ready: $aggregateReady")
        if (!aggregateReady) {
            throw GradleException(
                "Embedded Node.js packaged smoke host verification failed. " +
                        "See ${aggregateReportFile.absolutePath}"
            )
        }
    }
}

val verifyEmbeddedNodeDebug = tasks.register("verifyEmbeddedNodeDebug") {
    group = "verification"
    description = "Runs Embedded Node debug build, native dependency, and packaging preflight gates."

    dependsOn(
        "compileAppDebugKotlin",
        "compileAppDebugAndroidTestKotlin",
        "externalNativeBuildAppDebug",
        "nativeNodeDependencyReportAppDebug",
        "nativeNodeSymbolArchiveReport",
        embeddedNodePackagingPreflightAppDebug,
    )
}

val verifyEmbeddedNodePackagedDebug = tasks.register("verifyEmbeddedNodePackagedDebug") {
    group = "verification"
    description = "Runs Embedded Node packaged debug host verification gates."

    val explicitPackagedApkProperty = listOf("autojs.embeddedNode.packagedApk", "embeddedNodePackagedApk")
        .firstNotNullOfOrNull { propertyName ->
            providers.gradleProperty(propertyName).orNull?.trim()?.takeIf { it.isNotEmpty() }
        }

    dependsOn(
        embeddedNodePackagingPreflightAppDebug,
        verifyNodeTemplateReproducibility,
        embeddedNodePackagedAppIntegrationDryRunAppDebug,
        embeddedNodePackagedSmokeHostVerify,
    )
    if (explicitPackagedApkProperty != null) {
        dependsOn(
            embeddedNodePackagedApkOutputVerify,
            embeddedNodePackagedDiagnosticReport,
        )
    }
}

val verifyRhinoOnlyBuild = tasks.register("verifyRhinoOnlyBuild") {
    group = "verification"
    description = "Verifies the rhinoOnly rollback flavor does not package Embedded Node native libraries."

    val configuredReleaseFlavor = embeddedNodePackagingReleaseFlavorPolicy(
        rawFlavor = providers.gradleProperty(embeddedNodePackagingReleaseFlavorProperty)
            .orElse(embeddedNodePackagingDefaultReleaseFlavor)
            .get(),
        rawReleaseAbis = providers.gradleProperty(embeddedNodePackagingReleaseAbisProperty).orNull,
    ).flavor
    val reportFile = layout.buildDirectory
        .file("reports/embedded-node/packaging-preflight-$embeddedNodePackagingReportVariant.txt")
        .get()
        .asFile

    if (configuredReleaseFlavor == "rhinoOnly") {
        dependsOn(embeddedNodePackagingPreflightAppDebug)
    }

    outputs.upToDateWhen { false }

    doLast {
        if (configuredReleaseFlavor != "rhinoOnly") {
            throw GradleException(
                "verifyRhinoOnlyBuild must be run with -Pautojs.nodejs.releaseFlavor=rhinoOnly. " +
                        "Current release flavor is $configuredReleaseFlavor."
            )
        }
        if (!reportFile.isFile) {
            throw GradleException(
                "rhinoOnly packaging preflight report is missing: ${reportFile.absolutePath}"
            )
        }
        val reportText = reportFile.readText()
        val missingMarkers = listOf(
            "embedded.node.release.flavor=rhinoOnly",
            "embedded.node.release.flavor_policy.ready=true",
            "embedded.node.release.flavor_policy.missing=none",
        ).filterNot { marker -> reportText.contains(marker) }
        if (missingMarkers.isNotEmpty()) {
            throw GradleException(
                "rhinoOnly packaging preflight report is not ready; missing marker(s): " +
                        missingMarkers.joinToString() +
                        ". See ${reportFile.absolutePath}"
            )
        }
        logger.lifecycle("rhinoOnly rollback build verification passed. See ${reportFile.absolutePath}")
    }
}

val generateDesktopNodeDifferentialReport = tasks.register(
    "generateDesktopNodeDifferentialReport",
    org.gradle.api.tasks.Exec::class,
) {
    group = "verification"
    description = "Runs the desktop Node differential harness for fixed real npm corpus fixtures."

    workingDir(rootProject.projectDir)
    commandLine("node", rootProject.projectDir.resolve("tools/nodejs/differential/run-differential-tests.js").absolutePath)
}

val verifyNodePrGate = tasks.register("verifyNodePrGate") {
    group = "verification"
    description = "Runs fast host-side Embedded Node PR checks without connected-device stress or packaged smoke launch."

    dependsOn(
        "compileAppDebugKotlin",
        "compileAppDebugAndroidTestKotlin",
        embeddedNodePackagingPreflightAppDebug,
        verifyNodeTemplateReproducibility,
        embeddedNodePackagedAppIntegrationDryRunAppDebug,
        "verifyNodeCompatibilityProfile",
        "verifyNodeResolverVisualizerFixtures",
        "verifyNodeDependencyBundlerFixtures",
    )
}

val verifyNodeNightlyGate = tasks.register("verifyNodeNightlyGate") {
    group = "verification"
    description = "Runs heavy Embedded Node nightly checks; requires an explicit sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn(
            verifyNodePrGate,
            generateDesktopNodeDifferentialReport,
            verifyEmbeddedNodeDebug,
            verifyEmbeddedNodePackagedDebug,
            "connectedAppDebugAndroidTest",
        )
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodePackagedEsmGate = tasks.register("verifyNodePackagedEsmGate") {
    group = "verification"
    description = "Runs the focused Node Profile v1.1 packaged ESM/dynamic import APK gate on a connected Sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn("connectedAppDebugAndroidTest")
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodeLiveProviderGate = tasks.register("verifyNodeLiveProviderGate") {
    group = "verification"
    description = "Runs the focused Node Phase 7 bridge/live-provider readiness gate on a connected Sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn("connectedAppDebugAndroidTest")
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodeLongRunningGate = tasks.register("verifyNodeLongRunningGate") {
    group = "verification"
    description = "Runs the focused Node Phase 7 long-running/resource-lifecycle readiness gate on a connected Sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn("connectedAppDebugAndroidTest")
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodeWorkerGate = tasks.register("verifyNodeWorkerGate") {
    group = "verification"
    description = "Runs the focused Node Phase 7 worker_threads gate on a connected Sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn("connectedAppDebugAndroidTest")
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodeModernCoreGate = tasks.register("verifyNodeModernCoreGate") {
    group = "verification"
    description = "Runs the focused Node Phase 7 modern-core compatibility gate on a connected Sony/full device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn("connectedAppDebugAndroidTest")
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodePhase7Gate = tasks.register("verifyNodePhase7Gate") {
    group = "verification"
    description = "Runs the current focused Node Phase 7 gate set without expanding to the full release gate."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn(
            verifyNodePackagedEsmGate,
            verifyNodeLiveProviderGate,
            verifyNodeLongRunningGate,
            verifyNodeWorkerGate,
            verifyNodeModernCoreGate,
        )
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

val verifyNodeReleaseGate = tasks.register("verifyNodeReleaseGate") {
    group = "verification"
    description = "Runs the Embedded Node release gate with RC host checks, desktop differential, and explicit heavy device profile."

    val deviceProfile = embeddedNodePackagingTestDeviceProfile()
    if (deviceProfile in setOf("sony", "full")) {
        dependsOn(
            "generateNodeProfileReleaseManifest",
            generateDesktopNodeDifferentialReport,
            "verifyEmbeddedNodeReleaseCandidate",
            "connectedAppDebugAndroidTest",
        )
    }
    doFirst {
        embeddedNodePackagingRequiresHeavyDeviceProfile(name, deviceProfile)
    }
}

tasks.register("verifyEmbeddedNodeReleaseCandidate") {
    group = "verification"
    description = "Runs the local Embedded Node release-candidate verification gate."

    dependsOn(
        verifyEmbeddedNodeDebug,
        verifyEmbeddedNodePackagedDebug,
    )
}
