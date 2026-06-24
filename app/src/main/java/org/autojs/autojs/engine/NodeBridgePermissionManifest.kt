package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

object NodeBridgePermissionManifest {

    const val RUNTIME_MODULE_NAME = "autojs6:bridge-permissions"

    const val TOAST = "toast"
    const val APP = "app"
    const val APP_LAUNCH = "app.launch"
    const val APP_SETTINGS = "app.settings"
    const val APP_ACTIVITY = "app.activity"
    const val DIALOGS = "dialogs"
    const val ENGINES = "engines"
    const val ENGINES_EXEC = "engines.exec"
    const val ACCESSIBILITY = "accessibility"
    const val SCREEN_CAPTURE = "screen_capture"
    const val IMAGE = "image"
    const val OCR = "ocr"
    const val BARCODE = "barcode"
    const val STORAGE = "storage"
    const val DATABASE = "database"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATIONS_SETTINGS = "notifications.settings"
    const val SENSORS = "sensors"
    const val UI = "ui"
    const val UI_OVERLAY = "ui.overlay"
    const val UI_OVERLAY_PERMISSION = "ui.overlay.permission"
    const val UI_OVERLAY_FOREGROUND = "ui.overlay.foreground"
    const val UI_OVERLAY_PACKAGED = "ui.overlay.packaged"
    const val CLIPBOARD = "clipboard"
    const val DEVICE = "device"
    const val DEVICE_POWER = "device.power"
    const val SHELL = "shell"
    const val SHELL_ROOT = "shell.root"
    const val SHELL_SHIZUKU = "shell.shizuku"
    const val NETWORK = "network"
    const val WORK_MANAGER = "work_manager"
    const val JAVA_INTEROP = "java_interop"
    const val RHINO = "rhino"
    const val INPUT_OBSERVER = "input_observer"
    const val INPUT_OBSERVER_KEYS = "input_observer.keys"
    const val INPUT_OBSERVER_TOUCH = "input_observer.touch"
    const val INPUT_OBSERVER_INTERCEPT = "input_observer.intercept"
    const val FLOATY_RAW = "floaty.raw"
    const val FLOATY_TOUCH = "floaty.touch"

    const val ANDROID_PERMISSION_INTERNET = "android.permission.INTERNET"
    const val ANDROID_PERMISSION_FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
    const val ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION =
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"
    const val ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE =
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
    const val ANDROID_PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    val definedCapabilities: List<String> = listOf(
        TOAST,
        APP,
        APP_LAUNCH,
        APP_SETTINGS,
        APP_ACTIVITY,
        DIALOGS,
        ENGINES,
        ENGINES_EXEC,
        ACCESSIBILITY,
        SCREEN_CAPTURE,
        IMAGE,
        OCR,
        BARCODE,
        STORAGE,
        DATABASE,
        NOTIFICATIONS,
        NOTIFICATIONS_SETTINGS,
        SENSORS,
        UI,
        UI_OVERLAY,
        UI_OVERLAY_PERMISSION,
        UI_OVERLAY_FOREGROUND,
        UI_OVERLAY_PACKAGED,
        CLIPBOARD,
        DEVICE,
        DEVICE_POWER,
        SHELL,
        SHELL_ROOT,
        SHELL_SHIZUKU,
        NETWORK,
        WORK_MANAGER,
        JAVA_INTEROP,
        RHINO,
        INPUT_OBSERVER,
        INPUT_OBSERVER_KEYS,
        INPUT_OBSERVER_TOUCH,
        INPUT_OBSERVER_INTERCEPT,
        FLOATY_RAW,
        FLOATY_TOUCH,
    )

    private val definedCapabilitySet = definedCapabilities.toSet()

    private val aliases = mapOf(
        "media_projection" to SCREEN_CAPTURE,
        "media.projection" to SCREEN_CAPTURE,
        "screen.capture" to SCREEN_CAPTURE,
        "screencapture" to SCREEN_CAPTURE,
        "qrcode" to BARCODE,
        "qr_code" to BARCODE,
        "qr-code" to BARCODE,
        "barcode.detect" to BARCODE,
        "barcode.detectall" to BARCODE,
        "barcode.recognizetext" to BARCODE,
        "barcode.recognizetexts" to BARCODE,
        "qrcode.detect" to BARCODE,
        "qrcode.detectall" to BARCODE,
        "qrcode.recognizetext" to BARCODE,
        "qrcode.recognizetexts" to BARCODE,
        "notification" to NOTIFICATIONS,
        "notification.settings" to NOTIFICATIONS_SETTINGS,
        "notifications:settings" to NOTIFICATIONS_SETTINGS,
        "notifications-settings" to NOTIFICATIONS_SETTINGS,
        "device:power" to DEVICE_POWER,
        "device-power" to DEVICE_POWER,
        "power-manager" to DEVICE_POWER,
        "power_manager" to DEVICE_POWER,
        "engines:exec" to ENGINES_EXEC,
        "engine.exec" to ENGINES_EXEC,
        "shell:root" to SHELL_ROOT,
        "shell-root" to SHELL_ROOT,
        "shell:shizuku" to SHELL_SHIZUKU,
        "shell-shizuku" to SHELL_SHIZUKU,
        "storages" to STORAGE,
        "sqlite" to DATABASE,
        "java" to JAVA_INTEROP,
        "rhino.run" to RHINO,
        "input-observer" to INPUT_OBSERVER,
        "input.observer" to INPUT_OBSERVER,
        "inputobserver" to INPUT_OBSERVER,
        "input-observer.keys" to INPUT_OBSERVER_KEYS,
        "input.observer.keys" to INPUT_OBSERVER_KEYS,
        "input-observer.touch" to INPUT_OBSERVER_TOUCH,
        "input.observer.touch" to INPUT_OBSERVER_TOUCH,
        "input-observer.intercept" to INPUT_OBSERVER_INTERCEPT,
        "input.observer.intercept" to INPUT_OBSERVER_INTERCEPT,
        "ui-overlay" to UI_OVERLAY,
        "ui:overlay" to UI_OVERLAY,
        "overlay" to UI_OVERLAY,
        "overlay.permission" to UI_OVERLAY_PERMISSION,
        "ui-overlay.permission" to UI_OVERLAY_PERMISSION,
        "ui:overlay.permission" to UI_OVERLAY_PERMISSION,
        "overlay.foreground" to UI_OVERLAY_FOREGROUND,
        "ui-overlay.foreground" to UI_OVERLAY_FOREGROUND,
        "overlay.packaged" to UI_OVERLAY_PACKAGED,
        "ui-overlay.packaged" to UI_OVERLAY_PACKAGED,
    )

    private val androidPermissionsByCapability = linkedMapOf(
        NOTIFICATIONS to listOf(ANDROID_PERMISSION_POST_NOTIFICATIONS),
        NETWORK to listOf(ANDROID_PERMISSION_INTERNET),
        SCREEN_CAPTURE to listOf(
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
        ),
    )

    private val androidPermissionOrder = listOf(
        ANDROID_PERMISSION_POST_NOTIFICATIONS,
        ANDROID_PERMISSION_INTERNET,
        ANDROID_PERMISSION_FOREGROUND_SERVICE,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
    )

    fun normalizeCapability(value: String): String {
        val normalized = value
            .trim()
            .lowercase(Locale.US)
            .replace(':', '.')
        return aliases[normalized] ?: normalized
    }

    fun fromWorkingDirectory(workingDirectory: String?): Manifest {
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            ?: return Manifest.empty()
        return fromJsonSources(
            projectJsonText = readTextIfFile(File(root, PROJECT_JSON)),
            packageJsonText = readTextIfFile(File(root, PACKAGE_JSON)),
        )
    }

    fun runtimeModuleSourceForWorkingDirectory(workingDirectory: String?): String =
        fromWorkingDirectory(workingDirectory).toJson()

    fun androidPermissionsForWorkingDirectory(workingDirectory: String?): List<String> =
        fromWorkingDirectory(workingDirectory).androidPermissions

    fun fromDeclaredCapabilities(
        capabilities: Iterable<String>,
        enforced: Boolean = true,
        source: String = "explicit",
    ): Manifest {
        val normalized = capabilities.map(::normalizeCapability).filter { it.isNotEmpty() }.distinct()
        return buildManifest(
            enforced = enforced,
            permissions = normalized,
            sources = if (normalized.isEmpty()) emptyList() else listOf(source),
            warnings = emptyList(),
        )
    }

    fun fromJsonSources(projectJsonText: String?, packageJsonText: String?): Manifest {
        val sources = mutableListOf<PermissionSource>()
        val warnings = mutableListOf<String>()
        projectJsonText?.let { text ->
            val json = parseJsonObject(text, PROJECT_JSON, warnings)
            if (json != null) {
                collectProjectJsonPermissions(json, sources, warnings)
            }
        }
        packageJsonText?.let { text ->
            val json = parseJsonObject(text, PACKAGE_JSON, warnings)
            if (json != null) {
                collectPackageJsonPermissions(json, sources, warnings)
            }
        }
        val permissions = sources
            .flatMap { it.permissions }
            .map(::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return buildManifest(
            enforced = sources.isNotEmpty(),
            permissions = permissions,
            sources = sources.map { it.name }.distinct(),
            warnings = warnings,
        )
    }

    fun androidPermissionsForCapabilities(capabilities: Iterable<String>): List<String> {
        val values = capabilities
            .map(::normalizeCapability)
            .flatMap { capability -> androidPermissionsByCapability[capability].orEmpty() }
            .distinct()
        return values.sortedWith(compareBy({ androidPermissionOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }, { it }))
    }

    fun missingAndroidPermissionsForCapabilities(
        capabilities: Iterable<String>,
        declaredAndroidPermissions: Iterable<String>,
    ): List<String> {
        val declared = declaredAndroidPermissions.toSet()
        return androidPermissionsForCapabilities(capabilities).filterNot { it in declared }
    }

    fun requiredCapabilitiesFor(request: NodeBridgeRequest): List<String> {
        return requiredCapabilitiesFor(request.module, request.method, request.argsJson)
    }

    fun requiredCapabilitiesFor(module: String, method: String, argsJson: String = "[]"): List<String> {
        if (!NodeBridgeModules.supports(module, method)) {
            return emptyList()
        }
        val required = when (module) {
            NodeBridgeModules.TOAST -> listOf(TOAST)
            NodeBridgeModules.APP -> when (method) {
                "launchPackage", "launchApp" -> listOf(APP_LAUNCH)
                "openAppSetting" -> listOf(APP_SETTINGS)
                "startActivity" -> listOf(APP_ACTIVITY)
                else -> listOf(APP)
            }
            NodeBridgeModules.ENGINES -> when (method) {
                "execScript", "execScriptFile" -> listOf(ENGINES, ENGINES_EXEC)
                else -> listOf(ENGINES)
            }
            NodeBridgeModules.DIALOGS -> listOf(DIALOGS)
            NodeBridgeModules.ACCESSIBILITY -> listOf(ACCESSIBILITY)
            NodeBridgeModules.MEDIA_PROJECTION -> listOf(SCREEN_CAPTURE)
            NodeBridgeModules.IMAGE, NodeBridgeModules.IMAGES -> when (method) {
                "captureScreen" -> listOf(IMAGE, SCREEN_CAPTURE)
                else -> listOf(IMAGE)
            }
            NodeBridgeModules.OCR -> listOf(OCR, IMAGE)
            NodeBridgeModules.BARCODE -> listOf(BARCODE, IMAGE)
            NodeBridgeModules.STORAGE, NodeBridgeModules.STORAGES -> listOf(STORAGE)
            NodeBridgeModules.DATABASE, NodeBridgeModules.SQLITE -> listOf(DATABASE)
            NodeBridgeModules.NOTIFICATIONS -> when (method) {
                "openSettings" -> listOf(NOTIFICATIONS, NOTIFICATIONS_SETTINGS)
                else -> listOf(NOTIFICATIONS)
            }
            NodeBridgeModules.SENSORS -> listOf(SENSORS)
            NodeBridgeModules.UI -> listOf(UI)
            NodeBridgeModules.UI_OVERLAY -> when (method) {
                "hasPermission", "openPermissionSettings" -> listOf(UI_OVERLAY_PERMISSION)
                else -> listOf(UI_OVERLAY)
            }
            NodeBridgeModules.CLIPBOARD -> listOf(CLIPBOARD)
            NodeBridgeModules.DEVICE -> when (method) {
                "isIgnoringBatteryOptimizations", "openBatteryOptimizationSettings" -> listOf(DEVICE, DEVICE_POWER)
                else -> listOf(DEVICE)
            }
            NodeBridgeModules.SHELL -> shellRequiredCapabilities(method, argsJson)
            NodeBridgeModules.FETCH, NodeBridgeModules.WEBSOCKET -> listOf(NETWORK)
            NodeBridgeModules.WORK_MANAGER -> listOf(WORK_MANAGER)
            NodeBridgeModules.JAVA -> listOf(JAVA_INTEROP)
            NodeBridgeModules.RHINO -> listOf(RHINO)
            NodeBridgeModules.INPUT_OBSERVER -> when (method) {
                "getAvailableSources" -> listOf(INPUT_OBSERVER)
                else -> listOf(INPUT_OBSERVER, INPUT_OBSERVER_KEYS)
            }
            else -> emptyList()
        }
        return required.map(::normalizeCapability).distinct()
    }

    fun missingRequestCapabilitiesFor(request: NodeBridgeRequest): List<String> {
        return missingCapabilities(
            declaredCapabilities = request.permissions,
            requiredCapabilities = requiredCapabilitiesFor(request),
        )
    }

    fun missingManifestCapabilitiesFor(request: NodeBridgeRequest, manifest: Manifest): List<String> {
        if (!manifest.enforced) {
            return emptyList()
        }
        return missingCapabilities(
            declaredCapabilities = manifest.permissions,
            requiredCapabilities = requiredCapabilitiesFor(request),
        )
    }

    fun missingCapabilities(
        declaredCapabilities: Iterable<String>,
        requiredCapabilities: Iterable<String>,
    ): List<String> {
        val declared = declaredCapabilities
            .map(::normalizeCapability)
            .filter { it.isNotEmpty() }
            .toSet()
        return requiredCapabilities
            .map(::normalizeCapability)
            .filter { it.isNotEmpty() }
            .distinct()
            .filterNot { required -> declaresCapability(declared, required) }
    }

    private fun shellRequiredCapabilities(method: String, argsJson: String): List<String> {
        if (method != "exec") {
            return listOf(SHELL)
        }
        val root = runCatching {
            val args = JSONArray(argsJson.ifBlank { "[]" })
            args.optJSONObject(1)?.optBoolean("root", false) ?: false
        }.getOrDefault(false)
        return if (root) {
            listOf(SHELL, SHELL_ROOT)
        } else {
            listOf(SHELL)
        }
    }

    private fun declaresCapability(declared: Set<String>, required: String): Boolean {
        if (required == SHELL_ROOT || required == SHELL_SHIZUKU || required == NOTIFICATIONS_SETTINGS || required == DEVICE_POWER) {
            return required in declared
        }
        if (required in declared) {
            return true
        }
        val dotIndex = required.indexOf('.')
        return dotIndex > 0 && required.substring(0, dotIndex) in declared
    }

    private fun buildManifest(
        enforced: Boolean,
        permissions: List<String>,
        sources: List<String>,
        warnings: List<String>,
    ): Manifest {
        val unknown = permissions.filterNot { it in definedCapabilitySet }.sorted()
        return Manifest(
            version = 1,
            enforced = enforced,
            permissions = permissions,
            sources = sources.ifEmpty { listOf("none") },
            knownCapabilities = definedCapabilities,
            unknownPermissions = unknown,
            androidPermissions = androidPermissionsForCapabilities(permissions),
            serviceRequired = permissions.isNotEmpty(),
            warnings = (warnings + unknown.map { "Unknown Node bridge capability: $it" }).distinct(),
        )
    }

    private fun collectProjectJsonPermissions(
        json: JSONObject,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        val node = json.optJSONObject("node") ?: return
        collectArray(node, "permissions", "project.json:node.permissions", sources, warnings)
        collectArray(node, "bridgePermissions", "project.json:node.bridgePermissions", sources, warnings)
    }

    private fun collectPackageJsonPermissions(
        json: JSONObject,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        val autojs6 = json.optJSONObject("autojs6") ?: return
        collectArray(autojs6, "permissions", "package.json:autojs6.permissions", sources, warnings)
        collectArray(autojs6, "bridgePermissions", "package.json:autojs6.bridgePermissions", sources, warnings)
        autojs6.optJSONObject("node")?.let { node ->
            collectArray(node, "permissions", "package.json:autojs6.node.permissions", sources, warnings)
            collectArray(node, "bridgePermissions", "package.json:autojs6.node.bridgePermissions", sources, warnings)
        }
    }

    private fun collectArray(
        json: JSONObject,
        key: String,
        sourceName: String,
        sources: MutableList<PermissionSource>,
        warnings: MutableList<String>,
    ) {
        if (!json.has(key) || json.isNull(key)) {
            return
        }
        val array = json.opt(key) as? JSONArray
        if (array == null) {
            warnings += "$sourceName must be a JSON string array."
            return
        }
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            if (value !is String || value.isBlank()) {
                warnings += "$sourceName[$index] must be a non-empty string."
            } else {
                values += value
            }
        }
        sources += PermissionSource(sourceName, values)
    }

    private fun parseJsonObject(
        text: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): JSONObject? {
        return runCatching { JSONObject(text) }
            .onFailure { error ->
                warnings += "$sourceName could not be parsed for Node bridge permissions: ${error.message.orEmpty()}"
            }
            .getOrNull()
    }

    private fun readTextIfFile(file: File): String? {
        return if (file.isFile) {
            runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        } else {
            null
        }
    }

    data class Manifest(
        val version: Int,
        val enforced: Boolean,
        val permissions: List<String>,
        val sources: List<String>,
        val knownCapabilities: List<String>,
        val unknownPermissions: List<String>,
        val androidPermissions: List<String>,
        val serviceRequired: Boolean,
        val warnings: List<String>,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("version", version)
            put("enforced", enforced)
            put("permissions", JSONArray(permissions))
            put("sources", JSONArray(sources))
            put("knownCapabilities", JSONArray(knownCapabilities))
            put("unknownPermissions", JSONArray(unknownPermissions))
            put("androidPermissions", JSONArray(androidPermissions))
            put("serviceRequired", serviceRequired)
            put("warnings", JSONArray(warnings))
        }

        fun toJson(): String = toJsonObject().toString()

        companion object {
            fun empty(): Manifest = buildManifest(
                enforced = false,
                permissions = emptyList(),
                sources = emptyList(),
                warnings = emptyList(),
            )
        }
    }

    private data class PermissionSource(
        val name: String,
        val permissions: List<String>,
    )

    private const val PROJECT_JSON = "project.json"
    private const val PACKAGE_JSON = "package.json"
}
