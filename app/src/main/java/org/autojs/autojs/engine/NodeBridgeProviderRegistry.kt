package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

enum class NodeBridgeProviderStatus(val wireName: String, val phase10WireName: String = wireName) {
    UNAVAILABLE("unavailable"),
    FAKE("fake"),
    AVAILABLE("available"),
    PERMISSION_REQUIRED("permission-required", "permission_required"),
    DEGRADED("degraded"),
    FAILED("failed"),
}

enum class NodeBridgePackagedSupportStatus(val wireName: String) {
    SUPPORTED("supported"),
    PROVIDER_DEPENDENT("provider-dependent"),
    DEGRADED("degraded"),
    UNSUPPORTED("unsupported"),
    DEBUG_ONLY("debug-only"),
}

data class NodeBridgePackagedSupport(
    val status: NodeBridgePackagedSupportStatus,
    val notes: String,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("status", status.wireName)
        put("notes", notes)
    }
}

data class NodeBridgeProviderDefinition(
    val id: String,
    val displayName: String = id,
    val modules: List<String>,
    val version: String = NodeBridgeProviderRegistry.PROVIDER_VERSION,
    val requiredCapabilities: List<String>,
    val requiredAndroidPermissions: List<String> =
        NodeBridgePermissionManifest.androidPermissionsForCapabilities(requiredCapabilities),
    val packagedSupport: NodeBridgePackagedSupport,
    val healthCheckName: String = "androidHealth",
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("schema", NodeBridgeProviderRegistry.PROVIDER_SCHEMA)
        put("registryVersion", NodeBridgeProviderRegistry.REGISTRY_VERSION)
        put("id", id)
        put("displayName", displayName)
        put("version", version)
        put("modules", JSONArray(modules))
        put("methods", methodsJson())
        put("requiredCapabilities", JSONArray(requiredCapabilities))
        put("requiredAndroidPermissions", JSONArray(requiredAndroidPermissions))
        put("statusVocabulary", JSONArray(NodeBridgeProviderRegistry.statusVocabulary))
        put(
            "healthCheck",
            JSONObject()
                .put("name", healthCheckName)
                .put("status", "not_run")
                .put("async", false),
        )
        put("packagedSupport", packagedSupport.toJsonObject())
    }

    private fun methodsJson(): JSONObject = JSONObject().apply {
        modules.forEach { module ->
            put(module, JSONArray(NodeBridgeModules.supportedMethodsByModule[module].orEmpty().sorted()))
        }
    }
}

data class NodeBridgeProviderHealth(
    val definition: NodeBridgeProviderDefinition,
    val status: NodeBridgeProviderStatus,
    val lastFailure: String? = null,
    val activeResourceCount: Int = 0,
    val details: Map<String, String> = emptyMap(),
) {
    fun toJsonObject(): JSONObject = definition.toJsonObject().apply {
        val activeCount = activeResourceCount.coerceAtLeast(0)
        put("status", status.wireName)
        put("statusCode", status.phase10WireName)
        put("lastFailure", lastFailure ?: JSONObject.NULL)
        put("lastError", lastFailure?.let { JSONObject().put("message", it) } ?: JSONObject.NULL)
        put("activeResourceCount", activeCount)
        put(
            "activeResources",
            JSONObject()
                .put("count", activeCount),
        )
        put(
            "healthCheck",
            JSONObject()
                .put("name", definition.healthCheckName)
                .put("status", status.phase10WireName)
                .put("lastError", lastFailure ?: JSONObject.NULL)
                .put("activeResourceCount", activeCount),
        )
        put("details", JSONObject(details))
    }
}

object NodeBridgeProviderRegistry {

    const val REGISTRY_VERSION = "autojs6-bridge-provider-registry-v2"
    const val PROVIDER_SCHEMA = "autojs6-node-bridge-provider-v2"
    const val PROVIDER_VERSION = "autojs6-bridge-provider-v2"

    val statusVocabulary: List<String> =
        NodeBridgeProviderStatus.values().map { it.phase10WireName }.distinct()

    private const val FETCH_PROVIDER = "fetch"
    private const val WEBSOCKET_PROVIDER = "websocket"
    private const val OCR_PROVIDER = "ocr"
    private const val BARCODE_PROVIDER = "barcode"
    private const val STORAGE_PROVIDER = "storage"
    private const val DATABASE_PROVIDER = "database"
    private const val NOTIFICATIONS_PROVIDER = "notifications"
    private const val SENSORS_PROVIDER = "sensors"
    private const val ACCESSIBILITY_PROVIDER = "accessibility"
    private const val MEDIA_PROJECTION_PROVIDER = "media_projection"
    private const val IMAGE_PROVIDER = "image"
    private const val UI_PROVIDER = "ui"
    private const val UI_OVERLAY_PROVIDER = "ui.overlay"
    private const val WORK_MANAGER_PROVIDER = "work_manager"
    private const val SHELL_PROVIDER = "shell"
    private const val RHINO_PROVIDER = "rhino"
    private const val INPUT_OBSERVER_PROVIDER = "input_observer"

    val definitions: List<NodeBridgeProviderDefinition> = listOf(
        provider(
            id = FETCH_PROVIDER,
            displayName = "fetch/axios",
            modules = listOf(NodeBridgeModules.FETCH),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
            packagedSupport = packagedProviderDependent("requires packaged network capability and the experimental network provider flag"),
        ),
        provider(
            id = WEBSOCKET_PROVIDER,
            modules = listOf(NodeBridgeModules.WEBSOCKET),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
            packagedSupport = packagedProviderDependent("requires packaged network capability and the experimental network provider flag"),
        ),
        provider(
            id = OCR_PROVIDER,
            modules = listOf(NodeBridgeModules.OCR),
            capabilities = listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            packagedSupport = packagedProviderDependent(
                "P13-20 partial ML Kit OCR provider; packaged promotion requires model/fallback policy, image-handle assets, and capture/input evidence",
            ),
        ),
        provider(
            id = BARCODE_PROVIDER,
            modules = listOf(NodeBridgeModules.BARCODE),
            capabilities = listOf(NodeBridgePermissionManifest.BARCODE, NodeBridgePermissionManifest.IMAGE),
            packagedSupport = packagedProviderDependent("requires device barcode engine availability and packaged image-handle assets; capture input remains permission-gated"),
        ),
        provider(
            id = STORAGE_PROVIDER,
            modules = listOf(NodeBridgeModules.STORAGE, NodeBridgeModules.STORAGES),
            capabilities = listOf(NodeBridgePermissionManifest.STORAGE),
            packagedSupport = packagedSupported("packaged project namespace is isolated from the main app namespace"),
        ),
        provider(
            id = DATABASE_PROVIDER,
            displayName = "database/sqlite",
            modules = listOf(NodeBridgeModules.DATABASE, NodeBridgeModules.SQLITE),
            capabilities = listOf(NodeBridgePermissionManifest.DATABASE),
            packagedSupport = packagedSupported("database files remain scoped to the packaged project working directory"),
        ),
        provider(
            id = NOTIFICATIONS_PROVIDER,
            modules = listOf(NodeBridgeModules.NOTIFICATIONS),
            capabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
            packagedSupport = packagedProviderDependent("requires Android notification permission and channel policy"),
        ),
        provider(
            id = SENSORS_PROVIDER,
            modules = listOf(NodeBridgeModules.SENSORS),
            capabilities = listOf(NodeBridgePermissionManifest.SENSORS),
            packagedSupport = packagedProviderDependent("depends on device sensor availability"),
        ),
        provider(
            id = ACCESSIBILITY_PROVIDER,
            modules = listOf(NodeBridgeModules.ACCESSIBILITY),
            capabilities = listOf(NodeBridgePermissionManifest.ACCESSIBILITY),
            packagedSupport = packagedProviderDependent(
                "P13-19 partial selector provider; requires accessibility service connection, explicit capability, packaged service/settings lifecycle, and real-device evidence",
            ),
        ),
        provider(
            id = MEDIA_PROJECTION_PROVIDER,
            modules = listOf(NodeBridgeModules.MEDIA_PROJECTION),
            capabilities = listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
            packagedSupport = packagedUnsupported(
                "P13-20 records optional real-device screen-capture smoke, but the Node bridge grant flow is not promoted for packaged execution",
            ),
        ),
        provider(
            id = IMAGE_PROVIDER,
            modules = listOf(NodeBridgeModules.IMAGE, NodeBridgeModules.IMAGES),
            capabilities = listOf(NodeBridgePermissionManifest.IMAGE, NodeBridgePermissionManifest.SCREEN_CAPTURE),
            packagedSupport = packagedDegraded(
                "P13-20 partial image-handle provider: readImage/recycle are live for scoped files; capture, save, transforms, matching, and color search remain fail-closed without promoted media/image providers",
            ),
        ),
        provider(
            id = UI_PROVIDER,
            modules = listOf(NodeBridgeModules.UI),
            capabilities = listOf(NodeBridgePermissionManifest.UI),
            packagedSupport = packagedProviderDependent(
                "P13-17 records live main-app Activity UI provider evidence; live packaged UI promotion still requires Back/Recents, orientation, relaunch, crash, and disclosure evidence",
            ),
        ),
        provider(
            id = UI_OVERLAY_PROVIDER,
            modules = listOf(NodeBridgeModules.UI_OVERLAY),
            capabilities = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            packagedSupport = packagedProviderDependent("P13-18 records partial overlay promotion evidence over the P12-14 live gate; live packaged overlay creation remains blocked until a promoted show/update/drainEvents/close provider and foreground disclosure flow land"),
        ),
        provider(
            id = INPUT_OBSERVER_PROVIDER,
            modules = listOf(NodeBridgeModules.INPUT_OBSERVER),
            capabilities = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
            packagedSupport = packagedProviderDependent("fake source is available for tests; accessibility live source requires an active one-shot script or interactive visible session and rejects scheduled/background launch surfaces"),
        ),
        provider(
            id = WORK_MANAGER_PROVIDER,
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
            packagedSupport = packagedSupported("packaged project entries are validated and executed through WorkManager metadata"),
        ),
        provider(
            id = SHELL_PROVIDER,
            modules = listOf(NodeBridgeModules.SHELL),
            capabilities = listOf(NodeBridgePermissionManifest.SHELL),
            packagedSupport = packagedProviderDependent("controlled shell bridge requires explicit capability and remains separate from child_process"),
        ),
        provider(
            id = RHINO_PROVIDER,
            displayName = "rhino JSON-only shim",
            modules = listOf(NodeBridgeModules.RHINO),
            capabilities = listOf(NodeBridgePermissionManifest.RHINO),
            packagedSupport = packagedUnsupported(
                "P12-26 rejects packaged Rhino JSON-only execution until explicit shim metadata authorizes only JSON-only run and packaged timeout/crash/destroy evidence exists",
            ),
        ),
    )

    val definitionsById: Map<String, NodeBridgeProviderDefinition> = definitions.associateBy { it.id }

    fun androidHealth(
        context: Context,
        activeResourceCounts: Map<String, Int> = emptyMap(),
        lastFailures: Map<String, String> = emptyMap(),
    ): List<NodeBridgeProviderHealth> {
        val appContext = context.applicationContext
        return definitions.map { definition ->
            NodeBridgeProviderHealth(
                definition = definition,
                status = androidStatus(definition.id, appContext),
                lastFailure = lastFailures[definition.id],
                activeResourceCount = activeResourceCounts[definition.id] ?: 0,
                details = androidDetails(definition.id, appContext),
            )
        }
    }

    fun fakeHealth(activeResourceCounts: Map<String, Int> = emptyMap()): List<NodeBridgeProviderHealth> {
        return definitions.map { definition ->
            NodeBridgeProviderHealth(
                definition = definition,
                status = NodeBridgeProviderStatus.FAKE,
                activeResourceCount = activeResourceCounts[definition.id] ?: 0,
            )
        }
    }

    fun unavailableHealth(lastFailure: String? = null): List<NodeBridgeProviderHealth> {
        return definitions.map { definition ->
            NodeBridgeProviderHealth(
                definition = definition,
                status = NodeBridgeProviderStatus.UNAVAILABLE,
                lastFailure = lastFailure,
            )
        }
    }

    fun toJsonArray(health: List<NodeBridgeProviderHealth>): JSONArray = JSONArray().apply {
        health.forEach { put(it.toJsonObject()) }
    }

    private fun provider(
        id: String,
        displayName: String = id,
        modules: List<String>,
        capabilities: List<String>,
        packagedSupport: NodeBridgePackagedSupport,
    ): NodeBridgeProviderDefinition {
        return NodeBridgeProviderDefinition(
            id = id,
            displayName = displayName,
            modules = modules,
            requiredCapabilities = capabilities.map(NodeBridgePermissionManifest::normalizeCapability).distinct(),
            packagedSupport = packagedSupport,
        )
    }

    private fun packagedSupported(notes: String): NodeBridgePackagedSupport =
        NodeBridgePackagedSupport(NodeBridgePackagedSupportStatus.SUPPORTED, notes)

    private fun packagedProviderDependent(notes: String): NodeBridgePackagedSupport =
        NodeBridgePackagedSupport(NodeBridgePackagedSupportStatus.PROVIDER_DEPENDENT, notes)

    private fun packagedDegraded(notes: String): NodeBridgePackagedSupport =
        NodeBridgePackagedSupport(NodeBridgePackagedSupportStatus.DEGRADED, notes)

    private fun packagedUnsupported(notes: String): NodeBridgePackagedSupport =
        NodeBridgePackagedSupport(NodeBridgePackagedSupportStatus.UNSUPPORTED, notes)

    private fun androidStatus(id: String, context: Context): NodeBridgeProviderStatus = when (id) {
        FETCH_PROVIDER, WEBSOCKET_PROVIDER -> if (BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.UNAVAILABLE
        }
        OCR_PROVIDER -> if (mlKitOcrAvailable()) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        BARCODE_PROVIDER -> if (mlKitBarcodeAvailable()) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        STORAGE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        DATABASE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        NOTIFICATIONS_PROVIDER -> notificationStatus(context)
        SENSORS_PROVIDER -> sensorsStatus(context)
        ACCESSIBILITY_PROVIDER -> accessibilityStatus()
        MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE
        IMAGE_PROVIDER -> NodeBridgeProviderStatus.DEGRADED
        UI_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        UI_OVERLAY_PROVIDER -> NodeBridgeProviderStatus.DEGRADED
        INPUT_OBSERVER_PROVIDER -> inputObserverStatus()
        WORK_MANAGER_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        SHELL_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        RHINO_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        else -> NodeBridgeProviderStatus.UNAVAILABLE
    }

    private fun androidDetails(id: String, context: Context): Map<String, String> = when (id) {
        OCR_PROVIDER -> ocrDetails(context)
        BARCODE_PROVIDER -> barcodeDetails(context)
        MEDIA_PROJECTION_PROVIDER -> linkedMapOf(
            "reservedMethods" to "requestScreenCapture,nextImage,stop",
            "blockedMethods" to "requestScreenCapture,nextImage,stop",
            "optionalSmoke" to "NodeScreenCaptureRealDeviceSmokeInstrumentationTest",
            "capability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "phase13Promotion" to "P13-20 partial screenshot/image/OCR promotion evidence gate",
            "permissionPromptPolicy" to "Activity-owned ScreenCaptureRequester smoke exists; Node bridge provider must not prompt directly until promoted",
            "foregroundDisclosure" to "ScreenCapturerForegroundService smoke exists; packaged/bridge disclosure ownership is not promoted",
            "capturerCleanupPolicy" to "optional smoke verifies one-frame capture, bitmap recycle, capturer shutdown, and foreground service stop",
            "packagedPromotion" to "blocked_until_activity_owned_media_projection_provider_packaged_disclosure_and_denial_evidence",
            "rawObjectPolicy" to "no raw MediaProjection, ImageReader, ScreenCapturer, Image, Bitmap, or ImageWrapper crosses the bridge",
            "statusReason" to "P13-20 partial gate keeps media_projection unavailable while documenting optional real-device capture evidence",
        )
        IMAGE_PROVIDER -> linkedMapOf(
            "liveMethods" to "readImage,recycle",
            "fakeMethods" to "captureScreen,readImage,saveImage,clip,resize,grayscale,threshold,findImage,matchTemplate,findColor,findMultiColors,recycle",
            "blockedMethods" to "captureScreen,saveImage,clip,resize,grayscale,threshold,findImage,matchTemplate,findColor,findMultiColors",
            "capability" to NodeBridgePermissionManifest.IMAGE,
            "captureCapability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "resourceType" to NodeBridgeResourceType.IMAGE_HANDLE.name,
            "phase13Promotion" to "P13-20 partial screenshot/image/OCR promotion evidence gate",
            "capturePromotion" to "blocked_until_promoted_media_projection_provider",
            "advancedProcessingPromotion" to "blocked_until_owned_image_processing_provider_for_save_transform_match_color",
            "packagedPromotion" to "blocked_until_packaged_image_assets_capture_permission_and_processing_evidence",
            "cleanupPolicy" to "provider-owned image handles are ResourceRegistry-owned; recycle and provider destroy must return active handle count to zero",
            "rawObjectPolicy" to "no raw Bitmap, Mat, ImageWrapper, OpenCV object, native pointer, or filesystem path handle crosses the bridge",
            "statusReason" to "P13-20 partial gate: scoped readImage/recycle are live for OCR; screenshot and advanced image operations remain fail-closed",
        )
        ACCESSIBILITY_PROVIDER -> linkedMapOf(
            "liveMethods" to "isEnabled,ensureEnabled,findByText,clickText,findOne,findAll,click,longClick,setText,scrollForward,scrollBackward,back,home,recentApps",
            "selectorFields" to "text,desc,id,className,textContains,descContains,textMatches,descMatches,idMatches,classNameMatches,clickable,enabled,depth,boundsInside,boundsContains",
            "compatMethods" to "selector builders,findOne(number),find,findAll,click,longClick,setText,scrollForward,scrollBackward,exists,coordinate click,back,home,recentApps,recents",
            "blockedMethods" to "swipe,gesture,powerDialog,waitFor,rawNode,rawUiObject,rootOrShizukuGestures",
            "liveServiceAvailable" to accessibilityOperational().toString(),
            "capability" to NodeBridgePermissionManifest.ACCESSIBILITY,
            "phase13Promotion" to "P13-19 partial accessibility selector promotion evidence gate",
            "packagedPromotion" to "blocked_until_packaged_accessibility_service_settings_lifecycle_and_disclosure_evidence",
            "coordinateClickPolicy" to "direct JS facade supports click(x,y); Android live provider currently accepts real clicks only through selector descriptors",
            "gesturePolicy" to "gesture,root,Shizuku,raw input, and shell fallback paths require separate capabilities and must not be implied by accessibility",
            "rawObjectPolicy" to "no raw AccessibilityService, AccessibilityNodeInfo, UiObject, selector object, Context, Binder, or Java object crosses the bridge",
            "statusReason" to "P13-19 partial gate: selector v2 JSON descriptors and controlled accessibility actions are live when an operational service is visible; advanced Pro parity remains blocked",
        )
        UI_PROVIDER -> linkedMapOf(
            "liveMethods" to "showLayout,update,batchUpdate,drainEvents,close",
            "components" to "Column,Row,Text,Button,Input,Image,ScrollView,List,Switch,Checkbox,Progress,Tabs",
            "eventDelivery" to "bounded drainEvents JSON snapshots",
            "maxActiveSessionsPerExecution" to "4",
            "phase13Promotion" to "P13-17 partial live provider evidence gate",
            "packagedPromotion" to "blocked_until_live_packaged_back_recents_orientation_relaunch_crash_evidence",
            "rawObjectPolicy" to "no raw View, Activity, Context, NativeView, or Java object crosses the bridge",
            "statusReason" to "Activity-owned declarative UI MVP",
        )
        UI_OVERLAY_PROVIDER -> linkedMapOf(
            "fakeMethods" to "show,update,drainEvents,close,closeAll,hasPermission,openPermissionSettings",
            "liveGateMethods" to "hasPermission,openPermissionSettings,closeAll",
            "blockedLiveMethods" to "show,update,drainEvents,close",
            "capability" to NodeBridgePermissionManifest.UI_OVERLAY,
            "permissionCapability" to NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION,
            "foregroundCapability" to NodeBridgePermissionManifest.UI_OVERLAY_FOREGROUND,
            "packagedCapability" to NodeBridgePermissionManifest.UI_OVERLAY_PACKAGED,
            "packagedManifestPermission" to "android.permission.SYSTEM_ALERT_WINDOW",
            "packagedManifestDeclared" to packageDeclaresPermission(context, "android.permission.SYSTEM_ALERT_WINDOW").toString(),
            "defaultProjectPermission" to "android.permission.SYSTEM_ALERT_WINDOW",
            "permissionGranted" to runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false).toString(),
            "resourceType" to NodeBridgeResourceType.OVERLAY_SESSION.name,
            "p12_13LiveProviderState" to "not_promoted",
            "liveProvider" to "permission_settings_cleanup_gate_only",
            "phase13Promotion" to "P13-18 partial overlay promotion evidence gate",
            "liveCreationPromotion" to "blocked_until_promoted_show_update_drainEvents_close_provider",
            "packagedPromotion" to "blocked_until_live_packaged_overlay_permission_foreground_disclosure_cleanup_and_leak_evidence",
            "packagedPolicy" to "SYSTEM_ALERT_WINDOW metadata is recorded; scheduled/background silent overlays remain rejected until a foreground disclosure provider is promoted",
            "foregroundDisclosure" to "required_before_live_overlay",
            "cleanupPolicy" to "closeAll no-op cleanup summary plus provider destroy clears overlay session registry",
            "skipPolicy" to "device/live overlay creation is skipped until a controlled JSON-only Android overlay provider is promoted",
            "rawWindowLeakPolicy" to "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed",
            "statusReason" to "P13-18 partial gate over P12-14 live permission/settings/cleanup evidence; Android show/update/drainEvents/close creation path fails closed",
        )
        INPUT_OBSERVER_PROVIDER -> linkedMapOf(
            "liveMethods" to "getAvailableSources,observeKeys,drainEvents,close",
            "availableSource" to "fake",
            "liveSource" to "accessibility",
            "liveSourceAvailable" to accessibilityOperational().toString(),
            "maxActiveSubscriptionsPerExecution" to "8",
            "eventDelivery" to "bounded drainEvents JSON snapshots",
            "packagedPolicy" to "scheduled/background launch surfaces reject accessibility live observation; fake source remains available for tests",
            "statusReason" to "P12-07 cleanup/security gate covers bounded queues, listener cleanup, and live-source launch-surface rejection",
        )
        WORK_MANAGER_PROVIDER -> linkedMapOf(
            "liveMethods" to "scheduleOnce,schedulePeriodic,cancel,list",
            "scheduler" to NodeWorkManagerBridgeScheduler.SCHEDULER_NAME,
            "constraints" to "charging,network,idle",
            "minimumPeriodicIntervalMs" to ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS.toString(),
            "retryPolicy" to "no implicit retry; workers never return Result.retry",
            "android16JobQuotaPolicy" to NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics().optString("quotaState"),
            "statusReason" to "WorkManager-backed embedded Node task scheduling MVP",
        )
        RHINO_PROVIDER -> linkedMapOf(
            "liveMethods" to "run",
            "mode" to "json-only-poc",
            "defaultPolicy" to "rhino.run without experimental:true rejects with ERR_AUTOJS6_RHINO_SHIM_DISABLED",
            "packagedSupport" to "unsupported",
            "packagedPolicy" to "P12-26 rejects packaged Rhino JSON-only execution until explicit shim metadata is designed; future metadata may authorize only JSON-only run, never install or globals",
            "installPolicy" to "rhino.install remains unsupported by default; P13-14 allows explicit experimental Java proxy globals only when Java interop is enabled",
            "globalsPolicy" to "no global importClass, importPackage, JavaAdapter, compat.rhino, or unrestricted Rhino globals are installed into V8; P13-14 Packages/java/android proxies route only through the Java allowlist experiment; P13-15 module import helpers and P13-16 JavaAdapter return stable unsupported errors",
            "timeoutPolicy" to "timeoutMs is required; provider calls forceStop and destroy before reporting timeout lifecycle",
            "statusReason" to "P12-25 live provider POC is available only for explicit experimental one-shot requests; P12-26 keeps packaged APK execution fail-closed",
        )
        else -> emptyMap()
    }

    private fun ocrDetails(context: Context): Map<String, String> {
        val appContext = context.applicationContext
        val paddleVariant = when {
            hasAsset(appContext, "labels/ppocr_keys_ocrv5.txt") &&
                    hasAsset(appContext, "models/pp-ocrv5-arm/PP-OCRv5_mobile_det.nb") -> "v5"
            hasAsset(appContext, "labels/ppocr_keys_v1.txt") &&
                    hasAsset(appContext, "models/ocr_v3_for_cpu/det_opt.nb") -> "v3"
            else -> "none"
        }
        return linkedMapOf(
            "defaultEngine" to "mlkit-chinese",
            "mlkitAvailable" to mlKitOcrAvailable().toString(),
            "paddleEmbeddedVariant" to paddleVariant,
            "paddleEmbeddedAssets" to (paddleVariant != "none").toString(),
            "paddlePluginServiceCount" to queryPaddleOcrPluginServiceCount(appContext).toString(),
            "liveMethods" to "recognize,recognizeText,detectTextBounds",
            "inputModes" to "providerImageHandle,compatPathViaTemporaryImageHandle,compatCaptureExistingPermission",
            "capability" to NodeBridgePermissionManifest.OCR,
            "imageCapability" to NodeBridgePermissionManifest.IMAGE,
            "phase13Promotion" to "P13-20 partial screenshot/image/OCR promotion evidence gate",
            "modelSelection" to "options.lang/model accepted by API; live provider currently routes to MLKit Chinese recognizer",
            "fallbackPromotion" to "blocked_until_Paddle_Rapid_plugin_model_selection_and_packaged_asset_policy_evidence",
            "cleanupPolicy" to "OCR consumes provider-owned image handles; unknown or recycled handles reject and active OCR/image resources return to zero",
            "rawObjectPolicy" to "no raw TextRecognizer, OCR engine, ML Kit Text, Paddle/Rapid model, Bitmap, Mat, ImageWrapper, or Java object crosses the bridge",
            "statusReason" to "P13-20 partial gate: ML Kit OCR over provider-owned image handles is live; packaged model/fallback/capture ownership remains blocked",
        )
    }

    private fun mlKitOcrAvailable(): Boolean {
        return runCatching {
            Class.forName("com.google.mlkit.vision.text.TextRecognition")
            Class.forName("com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions")
        }.isSuccess
    }

    private fun barcodeDetails(context: Context): Map<String, String> {
        return linkedMapOf(
            "defaultEngine" to "mlkit-barcode",
            "mlkitAvailable" to mlKitBarcodeAvailable().toString(),
            "liveMethods" to "detect,detectAll,recognizeText,recognizeTexts",
            "inputModes" to "providerImageHandle,pathViaTemporaryImageHandle,captureExistingPermission",
            "capability" to NodeBridgePermissionManifest.BARCODE,
            "imageCapability" to NodeBridgePermissionManifest.IMAGE,
            "captureCapability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "qrcodePolicy" to "P12-29 compat.qrcode must force QR_CODE over this provider",
            "statusReason" to "ML Kit barcode provider is exposed through JSON-only snapshots and provider-owned image handles",
        )
    }

    private fun mlKitBarcodeAvailable(): Boolean {
        return runCatching {
            Class.forName("com.google.mlkit.vision.barcode.BarcodeScanning")
            Class.forName("com.google.mlkit.vision.barcode.BarcodeScannerOptions")
            Class.forName("com.google.mlkit.vision.barcode.common.Barcode")
        }.isSuccess
    }

    private fun hasAsset(context: Context, path: String): Boolean {
        return runCatching { context.assets.open(path).use { true } }.getOrDefault(false)
    }

    private fun queryPaddleOcrPluginServiceCount(context: Context): Int {
        val intent = Intent("org.autojs.plugin.PADDLE_OCR")
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0)).size
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0).size
        }
    }

    private fun notificationStatus(context: Context): NodeBridgeProviderStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return NodeBridgeProviderStatus.PERMISSION_REQUIRED
        }
        return if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.PERMISSION_REQUIRED
        }
    }

    private fun sensorsStatus(context: Context): NodeBridgeProviderStatus {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return NodeBridgeProviderStatus.UNAVAILABLE
        val anySupportedSensor = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_LIGHT,
        ).any { sensorType -> manager.getDefaultSensor(sensorType) != null }
        return if (anySupportedSensor) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.DEGRADED
        }
    }

    private fun accessibilityStatus(): NodeBridgeProviderStatus {
        return if (accessibilityOperational()) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.PERMISSION_REQUIRED
        }
    }

    private fun inputObserverStatus(): NodeBridgeProviderStatus {
        return if (accessibilityOperational()) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.DEGRADED
        }
    }

    private fun accessibilityOperational(): Boolean {
        return AutoJsAccessibilityService.hasInstance() &&
                AutoJsAccessibilityService.hasOperationalState &&
                AutoJsAccessibilityService.instance != null
    }

    private fun packageDeclaresPermission(context: Context, permission: String): Boolean {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.contains(permission) == true
        }.getOrDefault(false)
    }
}
