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
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.core.plugin.opencv.OpenCvPluginHost
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.runtime.api.augment.ocr.PaddleOcrEmbeddedEngine
import org.autojs.plugin.mediainfo.api.MediainfoSnapshotSchemas
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
    private const val MEDIA_PROVIDER = "media"
    private const val PINYIN_PROVIDER = "pinyin"
    private const val OPENCC_PROVIDER = "opencc"
    private const val PINYIN4J_PROVIDER = "pinyin4j"
    private const val NOTIFICATIONS_PROVIDER = "notifications"
    private const val DEVICE_PROVIDER = "device"
    private const val SENSORS_PROVIDER = "sensors"
    private const val ACCESSIBILITY_PROVIDER = "accessibility"
    private const val MEDIA_PROJECTION_PROVIDER = "media_projection"
    private const val IMAGE_PROVIDER = "image"
    private const val UI_PROVIDER = "ui"
    private const val UI_OVERLAY_PROVIDER = NodeBridgePermissionManifest.UI_OVERLAY
    private const val WORK_MANAGER_PROVIDER = "work_manager"
    private const val PACKAGE_MANAGER_PROVIDER = "package_manager"
    private const val SHELL_PROVIDER = "shell"
    private const val RHINO_PROVIDER = "rhino"
    private const val INPUT_OBSERVER_PROVIDER = "input_observer"
    private const val FILES_PROVIDER = "files"
    private const val CONSOLE_PROVIDER = "console"
    private const val EVENTS_PROVIDER = "events"
    private const val KEYS_PROVIDER = "keys"
    private const val DIALOGS_PROVIDER = "dialogs"

    val definitions: List<NodeBridgeProviderDefinition> = listOf(
        provider(id = NodeBridgeModules.AGENT, modules = listOf(NodeBridgeModules.AGENT),
            capabilities = listOf(NodeBridgePermissionManifest.AGENT, NodeBridgePermissionManifest.AGENT_EXEC),
            packagedSupport = packagedProviderDependent("registered scripts require a readable host-approved working directory; execution is separately permission-gated")),
        provider(id = KEYS_PROVIDER, modules = listOf(NodeBridgeModules.KEYS),
            capabilities = listOf(NodeBridgePermissionManifest.KEYS),
            packagedSupport = packagedProviderDependent("system actions require operational accessibility; Android may decline unsupported actions")),
        provider(id = DIALOGS_PROVIDER, modules = listOf(NodeBridgeModules.DIALOGS),
            capabilities = listOf(NodeBridgePermissionManifest.DIALOGS),
            packagedSupport = packagedSupported("Activity-owned dialogs and progress windows close with the script")),
        provider(
            id = EVENTS_PROVIDER,
            modules = listOf(NodeBridgeModules.EVENTS),
            capabilities = listOf(NodeBridgePermissionManifest.EVENTS, NodeBridgePermissionManifest.EVENTS_NOTIFICATION,
                NodeBridgePermissionManifest.EVENTS_TOAST, NodeBridgePermissionManifest.EVENTS_KEY),
            packagedSupport = packagedProviderDependent("notification access and accessibility must be granted for the selected event source"),
        ),
        provider(
            id = FETCH_PROVIDER,
            displayName = "fetch/axios",
            modules = listOf(NodeBridgeModules.FETCH),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
            packagedSupport = packagedProviderDependent("requires packaged network capability and the stable network provider"),
        ),
        provider(
            id = WEBSOCKET_PROVIDER,
            modules = listOf(NodeBridgeModules.WEBSOCKET),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
            packagedSupport = packagedProviderDependent("requires packaged network capability and the stable network provider"),
        ),
        provider(
            id = OCR_PROVIDER,
            modules = listOf(NodeBridgeModules.OCR),
            capabilities = listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
            packagedSupport = packagedProviderDependent(
                "P13-20 OCR provider; ML Kit OCR is supplied by the external OCR plugin service",
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
            id = MEDIA_PROVIDER,
            displayName = "media/mediainfo/media_store/recorder",
            modules = listOf(NodeBridgeModules.MEDIA, NodeBridgeModules.MEDIAINFO, NodeBridgeModules.MEDIA_STORE, NodeBridgeModules.RECORDER),
            capabilities = listOf(
                NodeBridgePermissionManifest.MEDIA,
                NodeBridgePermissionManifest.MEDIA_AUDIO,
                NodeBridgePermissionManifest.MEDIA_METADATA,
                NodeBridgePermissionManifest.MEDIA_RECORDING,
                NodeBridgePermissionManifest.MEDIA_PLAYBACK,
                NodeBridgePermissionManifest.MEDIA_LIBRARY,
                NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE,
            ),
            packagedSupport = packagedProviderDependent("metadata, audio stream control, recording, session playback and scoped MediaStore access follow the packaged app's Android permissions; playback needs the foreground media notification and MediaStore mutation is limited to items the packaged app created on Android 10+"),
        ),
        provider(
            id = PINYIN_PROVIDER,
            modules = listOf(NodeBridgeModules.PINYIN),
            capabilities = listOf(NodeBridgePermissionManifest.PINYIN),
            packagedSupport = packagedProviderDependent("requires the official Pinyin plugin and the packaged pinyin capability"),
        ),
        provider(
            id = OPENCC_PROVIDER,
            modules = listOf(NodeBridgeModules.OPENCC),
            capabilities = listOf(NodeBridgePermissionManifest.OPENCC),
            packagedSupport = packagedProviderDependent("requires an installed and enabled OpenCC provider"),
        ),
        provider(
            id = PINYIN4J_PROVIDER,
            modules = listOf(NodeBridgeModules.PINYIN4J),
            capabilities = listOf(NodeBridgePermissionManifest.PINYIN4J),
            packagedSupport = packagedProviderDependent("requires the official Pinyin4j plugin and the packaged pinyin4j capability"),
        ),
        provider(
            id = NOTIFICATIONS_PROVIDER,
            modules = listOf(NodeBridgeModules.NOTIFICATIONS),
            capabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS, NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS),
            packagedSupport = packagedProviderDependent("requires Android notification permission, channel policy, settings disclosure, and packaged dry-run evidence"),
        ),
        provider(
            id = DEVICE_PROVIDER,
            modules = listOf(NodeBridgeModules.DEVICE, NodeBridgeModules.SCREEN_METRICS),
            capabilities = listOf(NodeBridgePermissionManifest.DEVICE, NodeBridgePermissionManifest.DEVICE_POWER),
            packagedSupport = packagedProviderDependent("basic device methods use live or queued provider metadata; power settings require live bridge metadata and user-visible settings disclosure"),
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
                "Scoped image read/save/recycle are live; packaged capture is not validated and advanced image processing is not implemented",
            ),
        ),
        provider(
            id = UI_PROVIDER,
            modules = listOf(NodeBridgeModules.UI, NodeBridgeModules.COMPOSE),
            capabilities = listOf(NodeBridgePermissionManifest.UI),
            packagedSupport = packagedProviderDependent(
                "Activity layouts and portable Compose are available in main-app executions; Compose also requires its enabled, authorized renderer plugin. Packaged Compose is not validated; prior P13-17 layout evidence does not cover it",
            ),
        ),
        provider(
            id = UI_OVERLAY_PROVIDER,
            modules = listOf(NodeBridgeModules.UI_OVERLAY),
            capabilities = listOf(NodeBridgePermissionManifest.UI_OVERLAY),
            packagedSupport = packagedProviderDependent("the current package must declare and receive SYSTEM_ALERT_WINDOW; declarative windows support updates, push events, disclosure and script cleanup"),
        ),
        provider(
            id = INPUT_OBSERVER_PROVIDER,
            modules = listOf(NodeBridgeModules.INPUT_OBSERVER),
            capabilities = listOf(NodeBridgePermissionManifest.INPUT_OBSERVER, NodeBridgePermissionManifest.INPUT_OBSERVER_KEYS),
            packagedSupport = packagedProviderDependent("fake source is available for tests; live key observation requires a declared capability and operational accessibility service"),
        ),
        provider(
            id = WORK_MANAGER_PROVIDER,
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
            packagedSupport = packagedSupported("packaged project entries are validated and executed through WorkManager metadata"),
        ),
        provider(
            id = PACKAGE_MANAGER_PROVIDER,
            modules = listOf(NodeBridgeModules.PACKAGE_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.PACKAGE_MANAGER),
            packagedSupport = packagedDegraded("guarded install/update/remove/list/verify/prune access to the app-private Android package store is live; registry, npm CLI, lifecycle scripts, bin shims, and native payload execution remain unpromoted"),
        ),
        provider(
            id = SHELL_PROVIDER,
            modules = listOf(NodeBridgeModules.SHELL),
            capabilities = listOf(NodeBridgePermissionManifest.SHELL),
            packagedSupport = packagedProviderDependent("controlled shell bridge requires explicit capability and remains separate from child_process"),
        ),
        provider(
            id = FILES_PROVIDER,
            modules = listOf(NodeBridgeModules.FILES),
            capabilities = listOf(NodeBridgePermissionManifest.FILES, NodeBridgePermissionManifest.FILES_WRITE, NodeBridgePermissionManifest.FILES_DELETE),
            packagedSupport = packagedSupported("scoped to the working directory; write and delete need their own capability tokens"),
        ),
        provider(
            id = CONSOLE_PROVIDER,
            modules = listOf(NodeBridgeModules.CONSOLE),
            capabilities = listOf(NodeBridgePermissionManifest.CONSOLE),
            packagedSupport = packagedSupported("bounded global console tail and script console controls; showing a window requires overlay permission"),
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
        FETCH_PROVIDER, WEBSOCKET_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        NodeBridgeModules.AGENT -> NodeBridgeProviderStatus.AVAILABLE
        OCR_PROVIDER -> if (OcrPluginHost.availableEngines(context.applicationContext).isNotEmpty()) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.UNAVAILABLE
        }
        BARCODE_PROVIDER -> if (BarcodePluginHost.queryServiceCount(context) > 0) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        STORAGE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        DATABASE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        MEDIA_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        PINYIN_PROVIDER -> if (PinyinPluginHost.queryServiceCount(context) > 0) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        OPENCC_PROVIDER -> if (org.autojs.autojs.core.plugin.text.OpenccPluginHost.queryServiceCount(context) > 0) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        PINYIN4J_PROVIDER -> if (Pinyin4jPluginHost.queryServiceCount(context) > 0) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.UNAVAILABLE
        NOTIFICATIONS_PROVIDER -> notificationStatus(context)
        DEVICE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        EVENTS_PROVIDER -> NodeBridgeProviderStatus.DEGRADED
        KEYS_PROVIDER -> accessibilityStatus()
        DIALOGS_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        SENSORS_PROVIDER -> sensorsStatus(context)
        ACCESSIBILITY_PROVIDER -> accessibilityStatus()
        MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        IMAGE_PROVIDER -> if (OpenCvPluginHost.isAvailable(context)) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.DEGRADED
        UI_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        UI_OVERLAY_PROVIDER -> if (Settings.canDrawOverlays(context)) NodeBridgeProviderStatus.AVAILABLE else NodeBridgeProviderStatus.DEGRADED
        INPUT_OBSERVER_PROVIDER -> inputObserverStatus()
        WORK_MANAGER_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        PACKAGE_MANAGER_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        SHELL_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        RHINO_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        FILES_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        CONSOLE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        else -> NodeBridgeProviderStatus.UNAVAILABLE
    }

    private fun androidDetails(id: String, context: Context): Map<String, String> = when (id) {
        KEYS_PROVIDER -> mapOf("liveMethods" to NodeBridgeModules.keysMethods.joinToString(","),
            "powerDialog" to "opens the Android power menu; does not select power off or reboot",
            "screenshot" to "system screenshot action; does not return image data")
        DIALOGS_PROVIDER -> mapOf("liveMethods" to NodeBridgeModules.dialogsMethods.joinToString(","),
            "lifecycle" to "visible Activity, request timeout, dismissal or script termination")
        EVENTS_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.eventsMethods.joinToString(","),
            "notificationAccess" to org.autojs.autojs.core.notification.NotificationListenerService.isNotificationListenerEnabled(context).toString(),
            "accessibilityOperational" to AutoJsAccessibilityService.registry.isOperational.toString(),
            "transport" to "JNI push with drainEvents compatibility for file transport",
            "keyPolicy" to "observations only; hardware events are never consumed",
            "toastPolicy" to "external app Toast events from the existing accessibility observer",
        )
        OCR_PROVIDER -> ocrDetails(context)
        BARCODE_PROVIDER -> barcodeDetails(context)
        MEDIA_PROVIDER -> linkedMapOf(
            "liveMethods" to "media.getAudioStreamVolume,media.getAudioStreamMaxVolume,media.getAudioStreamInfo,media.setAudioStreamVolume,media.play,media.pause,media.resume,media.stop,media.seekTo,media.getPlaybackStatus,mediainfo.read,mediainfo.get,mediainfo.countGet,mediainfo.capabilities,media_store.capabilities,media_store.query,media_store.get,media_store.insert,media_store.update,media_store.delete,media_store.scanFile,media_store.exportFile,recorder.getStatus,recorder.start,recorder.stop",
            "capability" to NodeBridgePermissionManifest.MEDIA,
            "audioCapability" to NodeBridgePermissionManifest.MEDIA_AUDIO,
            "metadataCapability" to NodeBridgePermissionManifest.MEDIA_METADATA,
            "recordingCapability" to NodeBridgePermissionManifest.MEDIA_RECORDING,
            "playbackCapability" to NodeBridgePermissionManifest.MEDIA_PLAYBACK,
            "libraryCapability" to NodeBridgePermissionManifest.MEDIA_LIBRARY,
            "libraryMutateCapability" to NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE,
            "recordingAndroidPermissions" to NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf(NodeBridgePermissionManifest.MEDIA_RECORDING)).joinToString(","),
            "playbackAndroidPermissions" to NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf(NodeBridgePermissionManifest.MEDIA_PLAYBACK)).joinToString(","),
            "libraryAndroidPermissions" to NodeBridgePermissionManifest.androidPermissionsForCapabilities(listOf(NodeBridgePermissionManifest.MEDIA_LIBRARY, NodeBridgePermissionManifest.MEDIA_LIBRARY_MUTATE)).joinToString(","),
            "playbackStatusSchema" to NodeMediaPlayback.SCHEMA,
            "playbackSessionPolicy" to "one session per execution over the shared ScriptMusicService (same ExoPlayer, audio focus and foreground media notification as Rhino media.playMusic); a newer play replaces the previous session; stale session ids are inactive no-ops; execution cleanup stops playback",
            "playbackEligibilityPolicy" to "Android decides foreground media service eligibility; background schedules and boot do not guarantee audio eligibility",
            "mediaStoreCapabilitiesSchema" to NodeMediaStore.CAPABILITIES_SCHEMA,
            "mediaStoreItemSchema" to NodeMediaStore.ITEM_SCHEMA,
            "mediaStoreQueryPolicy" to "audio/images/video (downloads on Android 10+) with structured JSON filters, whitelisted columns, sort and limit/offset; no raw SQL; Android 10+ returns granted media permissions or app-owned items",
            "mediaStoreMutatePolicy" to "insert copies a script-readable file into a pending MediaStore item; update/delete apply to items AutoJs6 created on Android 10+ (WRITE_EXTERNAL_STORAGE below); no system consent flow is bridged",
            "audioStatusSchema" to "autojs6-node-media-audio-stream-v1",
            "mediainfoSchema" to "autojs6-node-mediainfo-snapshot-v1",
            "mediainfoPluginSchemas" to MediainfoSnapshotSchemas.VALUES.joinToString(","),
            "mediainfoCapabilitiesSchema" to "autojs6-node-mediainfo-capabilities-v1",
            "mediainfoSchemaPolicy" to "legacy_node_v1_by_default; plugin_v2_only_when_advertised",
            "recorderStatusSchema" to "autojs6-node-recorder-status-v1",
            "pathPolicy" to "mediainfo, image files and recorder accept absolute paths or paths relative to the working directory; Android file access applies",
            "recorderSessionPolicy" to "AAC/M4A output descriptor; microphone permission, foreground notification and execution cleanup",
            "recorderStartStopPolicy" to "one recording per execution; default 60 seconds, maximum 5 minutes; notification Stop stops all Node recordings",
            "recorderForegroundDisclosurePolicy" to "microphone_foreground_disclosure_and_stop_action_required_before_start",
            "recorderOutputPolicy" to "workspace output is retained; recorder and descriptor are released on stop or script cleanup",
            "recorderValidation" to "permission regression passed; physical microphone and packaged recording acceptance pending",
            "recorderPolicy" to "getStatus/start/stop use the execution-owned Android MediaRecorder provider",
            "rawObjectPolicy" to "no raw MediaPlayer, ExoPlayer, AudioManager, MediaRecorder, AudioRecord, AudioPlaybackCaptureConfiguration, MediaMetadataRetriever, MediaInfo, MediaStore cursor, ParcelFileDescriptor, Uri, file descriptor, foreground service handle, or Context object crosses the bridge; MediaStore rows carry URI strings only",
            "statusReason" to "metadata, audio stream control, recording, session playback and scoped MediaStore access are available",
        )
        OPENCC_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeOpenccBridge.METHODS.sorted().joinToString(","),
            "capability" to NodeBridgePermissionManifest.OPENCC,
            "pluginServiceCount" to org.autojs.autojs.core.plugin.text.OpenccPluginHost.queryServiceCount(context).toString(),
            "statusReason" to "Native and composed conversions use the selected OpenCC plugin",
        )
        PINYIN_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.pinyinMethods.sorted().joinToString(","),
            "capability" to NodeBridgePermissionManifest.PINYIN,
            "pluginServiceCount" to PinyinPluginHost.queryServiceCount(context).toString(),
            "customDictionaryPolicy" to "per_call_v1_64KiB_no_persistence",
            "rawObjectPolicy" to "JSON-only values cross the bridge; no Context, Binder, Bundle, database, or Java object is exposed",
            "statusReason" to "Pinyin conversion is supplied by the selected external Pinyin plugin through the host capability broker",
        )
        PINYIN4J_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.pinyin4jMethods.sorted().joinToString(","),
            "capability" to NodeBridgePermissionManifest.PINYIN4J,
            "pluginServiceCount" to Pinyin4jPluginHost.queryServiceCount(context).toString(),
            "readingsPolicy" to "capability_negotiated_v1_with_hanyu_legacy_fallback",
            "rawObjectPolicy" to "JSON-only values cross the bridge; no Context, Binder, Bundle, or Java object is exposed",
            "statusReason" to "Pinyin4j conversion is supplied by the selected external Pinyin4j plugin through the host capability broker",
        )
        NOTIFICATIONS_PROVIDER -> linkedMapOf(
            "liveMethods" to "notify,cancel,cancelAll,getPermissionStatus,openSettings",
            "capability" to NodeBridgePermissionManifest.NOTIFICATIONS,
            "settingsCapability" to NodeBridgePermissionManifest.NOTIFICATIONS_SETTINGS,
            "androidPermission" to NodeBridgePermissionManifest.ANDROID_PERMISSION_POST_NOTIFICATIONS,
            "permissionStatusSchema" to "autojs6-node-notification-permission-status-v1",
            "settingsResultSchema" to "autojs6-node-notification-permission-status-v1",
            "phase13Promotion" to "P13-25 partial notifications/settings/power manager evidence gate",
            "channelPolicy" to "notify creates stable app-owned channels; raw NotificationChannel objects never cross the bridge",
            "settingsPolicy" to "openSettings supports dryRun and requires notifications.settings when the manifest is enforced",
            "cleanupPolicy" to "owned notification ids are ResourceRegistry-owned; cancel/cancelAll/provider destroy remove active records",
            "phase14NotificationOwnershipPromotion" to "P14-22 notification ownership and foreground disclosure evidence gate",
            "notificationOwnershipPolicy" to "ResourceRegistry_owned_ids_cancel_unknown_noop_no_raw_handles",
            "notificationForegroundDisclosurePolicy" to "required_before_packaged_ongoing_or_high_privilege_promotion",
            "advancedNotificationPolicy" to "actions_groups_styles_remote_input_listener_RemoteViews_DND_stable_denied_or_future_decision",
            "blockedMethods" to "actions,listener,groups,styles,inline-reply,remote-input,do-not-disturb,exact-alarm,wakelock",
            "rawObjectPolicy" to "no raw Notification, NotificationCompat.Builder, PendingIntent, RemoteViews, Channel, Manager, Context, or Intent object crosses the bridge",
            "statusReason" to "P13-25 partial gate exposes status/settings evidence while advanced notification parity remains blocked",
        )
        DEVICE_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.deviceMethods.joinToString(","),
            "capability" to NodeBridgePermissionManifest.DEVICE,
            "powerCapability" to NodeBridgePermissionManifest.DEVICE_POWER,
            "settingsResultSchema" to "autojs6-node-battery-optimization-settings-v1",
            "phase13Promotion" to "P13-25 partial notifications/settings/power manager evidence gate",
            "powerPolicy" to "brightness and timed screen wake locks require device.power; brightness also requires Android modify-system-settings permission",
            "phase14SettingsPowerOemBackgroundMatrix" to "P14-23 settings/power OEM and background-launch matrix gate",
            "powerOemMatrixPolicy" to "android12_android15_battery_optimization_exact_alarm_background_launch_oem_notes_required_before_mutation",
            "powerMutationPolicy" to "screen wake lock is owned by one execution and released on timeout, explicit cancel or termination",
            "exactAlarmPolicy" to "state_matrix_only_no_schedule_exact_alarm_or_alarm_manager_authority",
            "privacyPolicy" to "device keeps hardware identifiers and sensitive profile fields absent from the Node surface",
            "blockedMethods" to "imei,androidId,serial,accounts,macAddress,ipAddress,rawPowerManager,ignoreBatteryOptimizationRequest,exactAlarm,backgroundLaunchBypass",
            "rawObjectPolicy" to "no raw PowerManager, WakeLock, AlarmManager, PendingIntent, Intent, Context, Vibrator, Build, Account, network, OEM manager, or device identifier object crosses the bridge",
            "statusReason" to "live device snapshot, brightness and timed screen controls; hardware identifiers stay unavailable",
        )
        MEDIA_PROJECTION_PROVIDER -> linkedMapOf(
            "liveMethods" to "requestScreenCapture,nextImage,stop",
            "blockedMethods" to "",
            "capability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "permissionPromptPolicy" to "Android ScreenCaptureRequester consent for each execution session",
            "foregroundDisclosure" to "ScreenCapturerForegroundService starts after consent, shared until all capturers release it",
            "capturerCleanupPolicy" to "stop, cancellation, system revocation and execution exit release capturer and pending consent",
            "packagedPromotion" to "not_validated",
            "rawObjectPolicy" to "only image and capturer handles cross the bridge",
        )
        IMAGE_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.imageMethods.sorted().joinToString(","),
            "byteTransport" to "PFD encoded images or straight-alpha RGBA8; JSON base64 and bounded signed ARGB32 pixel regions",
            "fakeMethods" to "captureScreen,readImage,saveImage,clip,resize,grayscale,threshold,findImage,matchTemplate,findColor,findMultiColors,recycle",
            "blockedMethods" to "",
            "analysisDependency" to "OpenCV plugin used by the host Images implementation",
            "opencvAvailable" to OpenCvPluginHost.isAvailable(context).toString(),
            "capability" to NodeBridgePermissionManifest.IMAGE,
            "captureCapability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "resourceType" to NodeBridgeResourceType.IMAGE_HANDLE.name,
            "packagedPromotion" to "capture_not_validated",
            "cleanupPolicy" to "provider-owned image handles are recycled on explicit recycle or execution exit",
            "rawObjectPolicy" to "no raw Android or OpenCV objects cross the bridge",
        )
        ACCESSIBILITY_PROVIDER -> linkedMapOf(
            "liveMethods" to "isEnabled,ensureEnabled,findByText,clickText,findOne,findAll,click,longClick,setText,scrollForward,scrollBackward,back,home,recentApps,swipe,gesture",
            "selectorFields" to "text,desc,id,className,textContains,descContains,textMatches,descMatches,idMatches,classNameMatches,clickable,enabled,depth,boundsInside,boundsContains",
            "compatMethods" to "selector builders,findOne(number),find,findAll,click,longClick,setText,scrollForward,scrollBackward,exists,coordinate click,back,home,recentApps,recents",
            "blockedMethods" to "powerDialog,waitFor,rawNode,rawUiObject,rootOrShizukuGestures",
            "liveServiceAvailable" to accessibilityOperational().toString(),
            "capability" to NodeBridgePermissionManifest.ACCESSIBILITY,
            "gestureCapability" to NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE,
            "phase13Promotion" to "P13-19 partial accessibility selector promotion evidence gate",
            "packagedPromotion" to "blocked_until_packaged_accessibility_service_settings_lifecycle_and_disclosure_evidence",
            "coordinateClickPolicy" to "direct JS facade supports click(x,y); Android live provider currently accepts real clicks only through selector descriptors",
            "gesturePolicy" to "swipe/gesture are live behind the dedicated accessibility.gesture capability (M3.2); root, Shizuku, raw input, and shell fallback paths still require their own capabilities and must not be implied by accessibility",
            "rawObjectPolicy" to "no raw AccessibilityService, AccessibilityNodeInfo, UiObject, selector object, Context, Binder, or Java object crosses the bridge",
            "statusReason" to "P13-19 partial gate: selector v2 JSON descriptors, controlled accessibility actions, and capability-gated coordinate gestures are live when an operational service is visible; advanced Pro parity remains blocked",
        )
        UI_PROVIDER -> linkedMapOf(
            "liveMethods" to "showLayout,update,batchUpdate,drainEvents,close",
            "components" to "Column,Row,Text,Button,Input,Image,ScrollView,List,Switch,Checkbox,Progress,Tabs",
            "eventDelivery" to "JNI callback push with bounded drainEvents compatibility",
            "maxActiveSessionsPerExecution" to "4",
            "phase13Promotion" to "P13-17 partial live provider evidence gate",
            "configurationChangePolicy" to "destroy_recreate_no_survival_claim",
            "packagedPromotion" to "blocked_until_live_packaged_task_relaunch_crash_disclosure_evidence",
            "rawObjectPolicy" to "no raw View, Activity, Context, NativeView, or Java object crosses the bridge",
            "statusReason" to "Activity-owned declarative UI MVP",
        )
        UI_OVERLAY_PROVIDER -> linkedMapOf(
            "liveMethods" to NodeBridgeModules.uiOverlayMethods.joinToString(","),
            "providerState" to "android_window_manager",
            "capability" to NodeBridgePermissionManifest.UI_OVERLAY,
            "permissionCapability" to NodeBridgePermissionManifest.UI_OVERLAY_PERMISSION,
            "foregroundCapability" to NodeBridgePermissionManifest.UI_OVERLAY_FOREGROUND,
            "packagedCapability" to NodeBridgePermissionManifest.UI_OVERLAY_PACKAGED,
            "packagedManifestPermission" to "android.permission.SYSTEM_ALERT_WINDOW",
            "packagedManifestDeclared" to packageDeclaresPermission(context, "android.permission.SYSTEM_ALERT_WINDOW").toString(),
            "defaultProjectPermission" to "android.permission.SYSTEM_ALERT_WINDOW",
            "permissionGranted" to runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false).toString(),
            "resourceType" to NodeBridgeResourceType.OVERLAY_SESSION.name,
            "liveProviderState" to "android_window_manager",
            "liveProvider" to "declarative_windows_with_push_events_and_execution_cleanup",
            "packagedPolicy" to "the current Android package must declare and be granted SYSTEM_ALERT_WINDOW",
            "foregroundDisclosure" to "declared_overlay_disclosure",
            "cleanupPolicy" to "close,closeAll,and provider destroy remove provider-owned overlay sessions",
            "rawWindowLeakPolicy" to "WindowManager, LayoutParams, FloatyWindow, WindowBridge, View, and NativeView are not exposed",
            "statusReason" to "declarative overlay windows support updates, dragging, push events and execution cleanup",
        )
        INPUT_OBSERVER_PROVIDER -> linkedMapOf(
            "liveMethods" to "getAvailableSources,observeKeys,drainEvents,close",
            "availableSource" to "fake",
            "liveSource" to "accessibility",
            "liveSourceAvailable" to accessibilityOperational().toString(),
            "maxActiveSubscriptionsPerExecution" to "8",
            "eventDelivery" to "JNI callback push with bounded drainEvents compatibility",
            "packagedPolicy" to "declared non-intercepting observation may run while the granted accessibility service is operational",
            "statusReason" to "shares the Android key event source with autojs6:events; fake source and bounded drainEvents remain compatible",
        )
        WORK_MANAGER_PROVIDER -> linkedMapOf(
            "liveMethods" to "scheduleOnce,schedulePeriodic,cancel,list",
            "scheduler" to NodeWorkManagerBridgeScheduler.SCHEDULER_NAME,
            "constraints" to "charging,network,idle",
            "minimumPeriodicIntervalMs" to ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS.toString(),
            "retryPolicy" to "no implicit retry; workers never return Result.retry",
            "android16JobQuotaPolicy" to NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics().optString("quotaState"),
            "phase13TaskDatabasePromotion" to "P13-21 partial daily/weekly/user task database provider evidence gate",
            "phase14TaskDatabasePromotion" to "P14-17 JSON-safe task database mutation contract gate",
            "compatTasksLiveMethods" to "addDisposableTask,queryTasks,queryTimedTasks,getTimedTask,removeTask,removeTimedTask",
            "blockedRhinoTaskMethods" to "addDailyTask,addWeeklyTask,updateTask,addIntentTask,queryIntentTasks,getIntentTask,removeIntentTask",
            "dailyWeeklyPromotion" to "blocked_until_user_task_database_schema_migration_packaged_policy_and_wall_clock_scheduler_evidence",
            "taskDatabaseMutationContract" to "add,update,enable,disable,remove,list,get,ownership_mapping,id_mapping,stable_errors",
            "taskDatabaseMutationPromotion" to "blocked_until_live_json_safe_database_mutation_provider_schema_migration_packaged_row_ownership_and_scheduler_reschedule_evidence",
            "phase14TaskSchemaMigrationPromotion" to "P14-18 task schema migration and rollback evidence contract gate",
            "taskDatabaseSchemaMigrationPolicy" to "versioned_json_descriptor_current_older_corrupt_unknown_fail_closed",
            "taskDatabaseRollbackPolicy" to "safe_downgrade_preserves_legacy_visibility_packaged_rows_disabled_or_removed_by_owner_policy",
            "phase14PackagedTaskSchedulingPromotion" to "P14-19 packaged task scheduling smoke evidence contract gate",
            "packagedTaskSchedulingSmokePolicy" to "packaged_work_manager_fake_smoke_and_live_project_entry_anchor_task_database_rows_not_promoted",
            "packagedTaskRowOwnershipPolicy" to "package_identity_scoped_no_main_app_alias_disable_or_remove_owned_rows_on_downgrade",
            "phase14IntentBroadcastProviderPromotion" to "P14-20 JSON-safe IntentTask/broadcast provider contract gate",
            "intentBroadcastDescriptorPolicy" to "provider_owned_json_descriptors_no_legacy_row_aliasing",
            "intentBroadcastManifestPolicy" to "packaged_manifest_synthesis_opt_in_non_exported_by_default_exported_denied",
            "intentBroadcastPayloadPolicy" to "bounded_json_snapshots_no_raw_intent_bundle_uri_receiver_handles",
            "phase14PackagedBroadcastTriggerRemovePromotion" to "P14-21 packaged broadcast trigger/remove smoke contract gate",
            "packagedBroadcastSmokePolicy" to "imports_packaged_intent_broadcast_boundary_positive_trigger_remove_not_promoted",
            "packagedBroadcastCleanupPolicy" to "owned_descriptor_receiver_patch_pending_execution_cleanup_required_before_promotion",
            "phase13IntentBroadcastPromotion" to "P13-22 partial IntentTask/broadcast task provider evidence gate",
            "blockedBroadcastTriggers" to "boot,user-unlocked,package-event,startup,exact-alarm,custom-broadcast,exported-receiver",
            "broadcastTaskPromotion" to "blocked_until_capability_split_receiver_authority_packaged_manifest_trigger_payload_install_trigger_remove_evidence",
            "broadcastReceiverPolicy" to "Node cannot register Android receivers, mutate manifests, or accept external broadcast wakeups until a dedicated user-visible provider lands",
            "rawTaskObjectPolicy" to "no raw TimedTask, IntentTask, WorkRequest, PendingIntent, AlarmManager, JobScheduler, WorkManager, BroadcastReceiver, Intent, or database cursor crosses the bridge",
            "taskDatabaseRawObjectPolicy" to "no raw TimedTask, scheduler, database cursor, mutable row handle, or IntentTask crosses the bridge",
            "rawBroadcastObjectPolicy" to "no raw BroadcastReceiver, Intent, IntentFilter, PendingIntent, Bundle, Uri, PackageManager, signature, icon, or package-list object crosses the bridge",
            "requireTasksPolicy" to "require(\"tasks\"), global tasks, and raw Rhino task objects remain unavailable until a dedicated Pro profile provider lands",
            "statusReason" to "WorkManager-backed external Node.js plugin task scheduling",
        )
        PACKAGE_MANAGER_PROVIDER -> linkedMapOf(
            "liveMethods" to "list,verify,prune,planInstall,planUpdate,planRemove,planRegistryInstall,planTarballInstall,install,update,remove",
            "blockedMethods" to "npmCli,registryDownload,tarballDownload,lifecycleScripts,binLinks,nativeBuild,nativePayloadExecution",
            "capability" to NodeBridgePermissionManifest.PACKAGE_MANAGER,
            "mutationCapability" to NodeBridgePermissionManifest.PACKAGE_MANAGER_MUTATE,
            "store" to "app_private",
            "androidRuntimeInstall" to "local_unpacked_only",
            "jsBridge" to "guarded_mutation_partial",
            "integrity" to "sha512_required",
            "transaction" to "locked_cancellable_atomic",
            "sourcePolicy" to "guarded_local_unpacked_mutation",
            "sourceAuthority" to "local_unpacked_host_managed_only",
            "allowedSources" to "local_unpacked_host_managed",
            "planOperations" to "planInstall,planUpdate,planRemove,planRegistryInstall,planTarballInstall",
            "mutationOperations" to "install,update,remove",
            "npmFacade" to "guarded_package_manager_alias_no_cli",
            "mutationConfirmation" to "allowMutation_required",
            "installAuthority" to NodeAndroidPackageInstallManager.INSTALL_AUTHORITY,
            "remoteTarballInstall" to "disabled",
            "unverifiedCacheReuse" to "disabled",
            "rawRegistryAccess" to "false",
            "rawExecutables" to "false",
            "rawObjectPolicy" to "no raw npm CLI, registry client, package tarball stream, executable, native addon, or filesystem escape handle crosses the bridge",
            "statusReason" to "P13-42 exposes guarded package_manager install/update/remove over the Android app-private local store while npm, registry, lifecycle, bin, and native payload authority remains fail-closed",
        )
        SHELL_PROVIDER -> linkedMapOf(
            "liveBridgeMethods" to "exec",
            "jsFacadeMethods" to "exec,execRoot,execShizuku,execFile,checkAccess,setDefaultTimeout",
            "capability" to NodeBridgePermissionManifest.SHELL,
            "rootCapability" to NodeBridgePermissionManifest.SHELL_ROOT,
            "shizukuCapability" to NodeBridgePermissionManifest.SHELL_SHIZUKU,
            "rootPolicy" to "root shell is explicit through execRoot or options.root and requires shell.root when the manifest is enforced",
            "shizukuPolicy" to "shell.shizuku is vocabulary-only in P13-24; execShizuku and options.shizuku reject before bridge dispatch",
            "packagedPolicy" to "queued packaged fallback must not execute shell; packaged execution requires live bridge metadata and explicit project capabilities",
            "cleanupPolicy" to "ProcessBuilder command is ResourceRegistry-owned, output is bounded, timeout closes the resource and destroys the process",
            "rawObjectPolicy" to "no Process, file descriptor, stream, signal handle, Binder, Shizuku service, root shell handle, or Android shell object crosses the bridge",
            "phase13Promotion" to "P13-24 partial shell/root/Shizuku capability evidence gate",
            "statusReason" to "controlled non-interactive shell bridge with explicit root gating and Shizuku stable denial",
        )
        RHINO_PROVIDER -> linkedMapOf(
            "liveMethods" to "run",
            "mode" to "json-only-poc",
            "defaultPolicy" to "rhino.run without explicit:true rejects with ERR_AUTOJS6_RHINO_SHIM_DISABLED",
            "packagedSupport" to "unsupported",
            "packagedPolicy" to "P12-26 rejects packaged Rhino JSON-only execution until explicit shim metadata is designed; future metadata may authorize only JSON-only run, never install or globals",
            "installPolicy" to "rhino.install remains unsupported by default; P13-14 allows explicit Java proxy globals only when Java interop is enabled",
            "globalsPolicy" to "no global importClass, importPackage, JavaAdapter, compat.rhino, or unrestricted Rhino globals are installed into V8; Packages/java/android proxies route only through the stable Java allowlist; module import helpers and JavaAdapter return stable unsupported errors",
            "timeoutPolicy" to "timeoutMs is required; provider calls forceStop and destroy before reporting timeout lifecycle",
            "statusReason" to "P12-25 live provider POC is available only for explicit one-shot requests; P12-26 keeps packaged APK execution fail-closed",
        )
        else -> emptyMap()
    }

    private fun ocrDetails(context: Context): Map<String, String> {
        val appContext = context.applicationContext
        val paddleVariant = PaddleOcrEmbeddedEngine.resolveVariantName(appContext) ?: "none"
        return linkedMapOf(
            "defaultEngine" to "auto",
            "automaticEngineOrder" to OcrPluginHost.AUTOMATIC_ENGINE_ORDER.joinToString(","),
            "availableEngines" to OcrPluginHost.availableEngines(appContext).joinToString(","),
            "mlkitPluginServiceCount" to queryOcrPluginServiceCount(appContext, "mlkit-ocr").toString(),
            "paddleEmbeddedVariant" to paddleVariant,
            "paddleEmbeddedAssets" to (paddleVariant != "none").toString(),
            "ocrPluginServiceCount" to queryOcrPluginServiceCount(appContext).toString(),
            "liveMethods" to "recognize,recognizeText,detectTextBounds",
            "inputModes" to "providerImageHandle,compatPathViaTemporaryImageHandle,compatCaptureExistingPermission",
            "capability" to NodeBridgePermissionManifest.OCR,
            "imageCapability" to NodeBridgePermissionManifest.IMAGE,
            "phase13Promotion" to "P13-20 partial screenshot/image/OCR promotion evidence gate",
            "modelSelection" to "options.engine/engineId/variant select a plugin; options.lang/model are forwarded to the selected OCR plugin as its profile",
            "fallbackPromotion" to "automatic ML Kit/Paddle/Rapid selection shared with accessibility.readScreenText; packaged asset policy evidence still pending",
            "cleanupPolicy" to "OCR consumes provider-owned image handles; unknown or recycled handles reject and active OCR/image resources return to zero",
            "rawObjectPolicy" to "no raw OCR engine, model, Bitmap, Mat, ImageWrapper, Binder, or Java object crosses the bridge",
            "statusReason" to "OCR is provided by enabled external OCR plugins (ML Kit, Paddle or Rapid) over provider-owned image handles",
        )
    }

    private fun barcodeDetails(context: Context): Map<String, String> {
        val pluginServiceCount = BarcodePluginHost.queryServiceCount(context)
        return linkedMapOf(
            "defaultEngine" to "mlkit-barcode",
            "mlkitAvailable" to (pluginServiceCount > 0).toString(),
            "barcodePluginServiceCount" to pluginServiceCount.toString(),
            "liveMethods" to "detect,detectAll,recognizeText,recognizeTexts",
            "inputModes" to "providerImageHandle,pathViaTemporaryImageHandle,captureExistingPermission",
            "capability" to NodeBridgePermissionManifest.BARCODE,
            "imageCapability" to NodeBridgePermissionManifest.IMAGE,
            "captureCapability" to NodeBridgePermissionManifest.SCREEN_CAPTURE,
            "qrcodePolicy" to "P12-29 compat.qrcode must force QR_CODE over this provider",
            "statusReason" to "ML Kit barcode provider is supplied by the official Barcode plugin through JSON-only snapshots and provider-owned image handles",
        )
    }

    private fun queryOcrPluginServiceCount(context: Context, category: String? = null): Int {
        val intent = Intent(OcrPluginHost.ACTION_OCR)
        if (!category.isNullOrBlank()) {
            intent.addCategory(category)
        }
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
        return AutoJsAccessibilityService.registry.isOperational
    }

    private fun packageDeclaresPermission(context: Context, permission: String): Boolean {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.contains(permission) == true
        }.getOrDefault(false)
    }
}
