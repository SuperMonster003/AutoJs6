package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

enum class NodeBridgeProviderStatus(val wireName: String) {
    UNAVAILABLE("unavailable"),
    FAKE("fake"),
    AVAILABLE("available"),
    PERMISSION_REQUIRED("permission-required"),
    DEGRADED("degraded"),
    FAILED("failed"),
}

data class NodeBridgeProviderDefinition(
    val id: String,
    val displayName: String = id,
    val modules: List<String>,
    val version: String = NodeBridgeProviderRegistry.PROVIDER_VERSION,
    val requiredCapabilities: List<String>,
    val requiredAndroidPermissions: List<String> =
        NodeBridgePermissionManifest.androidPermissionsForCapabilities(requiredCapabilities),
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("version", version)
        put("modules", JSONArray(modules))
        put("methods", methodsJson())
        put("requiredCapabilities", JSONArray(requiredCapabilities))
        put("requiredAndroidPermissions", JSONArray(requiredAndroidPermissions))
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
) {
    fun toJsonObject(): JSONObject = definition.toJsonObject().apply {
        put("status", status.wireName)
        put("lastFailure", lastFailure ?: JSONObject.NULL)
        put("activeResourceCount", activeResourceCount.coerceAtLeast(0))
    }
}

object NodeBridgeProviderRegistry {

    const val PROVIDER_VERSION = "autojs6-bridge-provider-v1"

    private const val FETCH_PROVIDER = "fetch"
    private const val WEBSOCKET_PROVIDER = "websocket"
    private const val OCR_PROVIDER = "ocr"
    private const val STORAGE_PROVIDER = "storage"
    private const val NOTIFICATIONS_PROVIDER = "notifications"
    private const val SENSORS_PROVIDER = "sensors"
    private const val ACCESSIBILITY_PROVIDER = "accessibility"
    private const val MEDIA_PROJECTION_PROVIDER = "media_projection"
    private const val IMAGE_PROVIDER = "image"
    private const val UI_PROVIDER = "ui"
    private const val WORK_MANAGER_PROVIDER = "work_manager"
    private const val SHELL_PROVIDER = "shell"

    val definitions: List<NodeBridgeProviderDefinition> = listOf(
        provider(
            id = FETCH_PROVIDER,
            displayName = "fetch/axios",
            modules = listOf(NodeBridgeModules.FETCH),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
        ),
        provider(
            id = WEBSOCKET_PROVIDER,
            modules = listOf(NodeBridgeModules.WEBSOCKET),
            capabilities = listOf(NodeBridgePermissionManifest.NETWORK),
        ),
        provider(
            id = OCR_PROVIDER,
            modules = listOf(NodeBridgeModules.OCR),
            capabilities = listOf(NodeBridgePermissionManifest.OCR, NodeBridgePermissionManifest.IMAGE),
        ),
        provider(
            id = STORAGE_PROVIDER,
            modules = listOf(NodeBridgeModules.STORAGE, NodeBridgeModules.STORAGES),
            capabilities = listOf(NodeBridgePermissionManifest.STORAGE),
        ),
        provider(
            id = NOTIFICATIONS_PROVIDER,
            modules = listOf(NodeBridgeModules.NOTIFICATIONS),
            capabilities = listOf(NodeBridgePermissionManifest.NOTIFICATIONS),
        ),
        provider(
            id = SENSORS_PROVIDER,
            modules = listOf(NodeBridgeModules.SENSORS),
            capabilities = listOf(NodeBridgePermissionManifest.SENSORS),
        ),
        provider(
            id = ACCESSIBILITY_PROVIDER,
            modules = listOf(NodeBridgeModules.ACCESSIBILITY),
            capabilities = listOf(NodeBridgePermissionManifest.ACCESSIBILITY),
        ),
        provider(
            id = MEDIA_PROJECTION_PROVIDER,
            modules = listOf(NodeBridgeModules.MEDIA_PROJECTION),
            capabilities = listOf(NodeBridgePermissionManifest.SCREEN_CAPTURE),
        ),
        provider(
            id = IMAGE_PROVIDER,
            modules = listOf(NodeBridgeModules.IMAGE, NodeBridgeModules.IMAGES),
            capabilities = listOf(NodeBridgePermissionManifest.IMAGE, NodeBridgePermissionManifest.SCREEN_CAPTURE),
        ),
        provider(
            id = UI_PROVIDER,
            modules = listOf(NodeBridgeModules.UI),
            capabilities = listOf(NodeBridgePermissionManifest.UI),
        ),
        provider(
            id = WORK_MANAGER_PROVIDER,
            modules = listOf(NodeBridgeModules.WORK_MANAGER),
            capabilities = listOf(NodeBridgePermissionManifest.WORK_MANAGER),
        ),
        provider(
            id = SHELL_PROVIDER,
            modules = listOf(NodeBridgeModules.SHELL),
            capabilities = listOf(NodeBridgePermissionManifest.SHELL),
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
    ): NodeBridgeProviderDefinition {
        return NodeBridgeProviderDefinition(
            id = id,
            displayName = displayName,
            modules = modules,
            requiredCapabilities = capabilities.map(NodeBridgePermissionManifest::normalizeCapability).distinct(),
        )
    }

    private fun androidStatus(id: String, context: Context): NodeBridgeProviderStatus = when (id) {
        FETCH_PROVIDER, WEBSOCKET_PROVIDER -> if (BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.UNAVAILABLE
        }
        OCR_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE
        STORAGE_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        NOTIFICATIONS_PROVIDER -> notificationStatus(context)
        SENSORS_PROVIDER -> sensorsStatus(context)
        ACCESSIBILITY_PROVIDER -> accessibilityStatus()
        MEDIA_PROJECTION_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE
        IMAGE_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE
        UI_PROVIDER -> NodeBridgeProviderStatus.UNAVAILABLE
        WORK_MANAGER_PROVIDER -> NodeBridgeProviderStatus.FAKE
        SHELL_PROVIDER -> NodeBridgeProviderStatus.AVAILABLE
        else -> NodeBridgeProviderStatus.UNAVAILABLE
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
        return if (AutoJsAccessibilityService.hasInstance() &&
            AutoJsAccessibilityService.hasOperationalState &&
            AutoJsAccessibilityService.instance != null
        ) {
            NodeBridgeProviderStatus.AVAILABLE
        } else {
            NodeBridgeProviderStatus.PERMISSION_REQUIRED
        }
    }
}
