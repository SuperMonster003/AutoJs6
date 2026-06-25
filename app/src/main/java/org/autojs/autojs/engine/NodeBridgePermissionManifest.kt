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
    const val MEDIA = "media"
    const val MEDIA_AUDIO = "media.audio"
    const val MEDIA_METADATA = "media.metadata"
    const val MEDIA_RECORDING = "media.recording"
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
    const val PACKAGE_MANAGER = "package_manager"
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
    const val ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE =
        "android.permission.FOREGROUND_SERVICE_MICROPHONE"
    const val ANDROID_PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"

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
        MEDIA,
        MEDIA_AUDIO,
        MEDIA_METADATA,
        MEDIA_RECORDING,
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
        PACKAGE_MANAGER,
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
        "audio" to MEDIA_AUDIO,
        "media:audio" to MEDIA_AUDIO,
        "media-audio" to MEDIA_AUDIO,
        "media.metadata" to MEDIA_METADATA,
        "media:metadata" to MEDIA_METADATA,
        "mediainfo" to MEDIA_METADATA,
        "media-info" to MEDIA_METADATA,
        "media_info" to MEDIA_METADATA,
        "media.record" to MEDIA_RECORDING,
        "media:recording" to MEDIA_RECORDING,
        "media-recording" to MEDIA_RECORDING,
        "recorder" to MEDIA_RECORDING,
        "audio.recording" to MEDIA_RECORDING,
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
        "package-manager" to PACKAGE_MANAGER,
        "package.manager" to PACKAGE_MANAGER,
        "android_package_manager" to PACKAGE_MANAGER,
        "android-package-manager" to PACKAGE_MANAGER,
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
        MEDIA_RECORDING to listOf(
            ANDROID_PERMISSION_RECORD_AUDIO,
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
        ),
        NETWORK to listOf(ANDROID_PERMISSION_INTERNET),
        SCREEN_CAPTURE to listOf(
            ANDROID_PERMISSION_FOREGROUND_SERVICE,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
            ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
        ),
    )

    private val androidPermissionOrder = listOf(
        ANDROID_PERMISSION_POST_NOTIFICATIONS,
        ANDROID_PERMISSION_RECORD_AUDIO,
        ANDROID_PERMISSION_INTERNET,
        ANDROID_PERMISSION_FOREGROUND_SERVICE,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_MEDIA_PROJECTION,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_SPECIAL_USE,
        ANDROID_PERMISSION_FOREGROUND_SERVICE_MICROPHONE,
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
        val packagedMetadata = PackagedMetadataBuilder()
        projectJsonText?.let { text ->
            val json = parseJsonObject(text, PROJECT_JSON, warnings)
            if (json != null) {
                collectProjectJsonPermissions(json, sources, warnings)
                collectProjectJsonPackagedMetadata(json, packagedMetadata, warnings)
            }
        }
        packageJsonText?.let { text ->
            val json = parseJsonObject(text, PACKAGE_JSON, warnings)
            if (json != null) {
                collectPackageJsonPermissions(json, sources, warnings)
                collectPackageJsonPackagedMetadata(json, packagedMetadata, warnings)
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
            packagedMetadata = packagedMetadata,
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
            NodeBridgeModules.MEDIA -> listOf(MEDIA, MEDIA_AUDIO)
            NodeBridgeModules.MEDIAINFO -> listOf(MEDIA, MEDIA_METADATA)
            NodeBridgeModules.RECORDER -> listOf(MEDIA, MEDIA_RECORDING)
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
            NodeBridgeModules.PACKAGE_MANAGER -> listOf(PACKAGE_MANAGER)
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
        if (
            required == SHELL_ROOT ||
            required == SHELL_SHIZUKU ||
            required == NOTIFICATIONS_SETTINGS ||
            required == DEVICE_POWER ||
            required == MEDIA_AUDIO ||
            required == MEDIA_METADATA ||
            required == MEDIA_RECORDING
        ) {
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
        packagedMetadata: PackagedMetadataBuilder = PackagedMetadataBuilder(),
    ): Manifest {
        val unknown = permissions.filterNot { it in definedCapabilitySet }.sorted()
        val normalizedPermissions = permissions.map(::normalizeCapability).filter { it.isNotEmpty() }.distinct()
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
            packagedMetadata = packagedMetadata.build(normalizedPermissions),
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

    private fun collectProjectJsonPackagedMetadata(
        json: JSONObject,
        metadata: PackagedMetadataBuilder,
        warnings: MutableList<String>,
    ) {
        val node = json.optJSONObject("node") ?: return
        collectPackagedMetadata(node, "project.json:node", metadata, warnings)
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

    private fun collectPackageJsonPackagedMetadata(
        json: JSONObject,
        metadata: PackagedMetadataBuilder,
        warnings: MutableList<String>,
    ) {
        val autojs6 = json.optJSONObject("autojs6") ?: return
        collectPackagedMetadata(autojs6, "package.json:autojs6", metadata, warnings)
        autojs6.optJSONObject("node")?.let { node ->
            collectPackagedMetadata(node, "package.json:autojs6.node", metadata, warnings)
        }
    }

    private fun collectPackagedMetadata(
        json: JSONObject,
        sourceName: String,
        metadata: PackagedMetadataBuilder,
        warnings: MutableList<String>,
    ) {
        collectOptionalString(json, "profile", "$sourceName.profile", warnings)?.let { profile ->
            metadata.profiles += profile
            metadata.sources += "$sourceName.profile"
        }
        collectOptionalStringArray(json, "profiles", "$sourceName.profiles", warnings).let { profiles ->
            if (profiles.isNotEmpty()) {
                metadata.profiles += profiles
                metadata.sources += "$sourceName.profiles"
            }
        }
        collectOptionalStringArray(json, "builtins", "$sourceName.builtins", warnings).let { builtins ->
            if (builtins.isNotEmpty()) {
                metadata.builtins += builtins
                metadata.sources += "$sourceName.builtins"
            }
        }
        collectOptionalStringArray(json, "nodeBuiltins", "$sourceName.nodeBuiltins", warnings).let { builtins ->
            if (builtins.isNotEmpty()) {
                metadata.builtins += builtins
                metadata.sources += "$sourceName.nodeBuiltins"
            }
        }
        collectOptionalStringArray(json, "nativeAssets", "$sourceName.nativeAssets", warnings).let { nativeAssets ->
            if (nativeAssets.isNotEmpty()) {
                metadata.nativeAssets += nativeAssets
                metadata.sources += "$sourceName.nativeAssets"
            }
        }
        collectOptionalStringArray(json, "filesystemRoots", "$sourceName.filesystemRoots", warnings).let { roots ->
            if (roots.isNotEmpty()) {
                metadata.filesystemRoots += roots
                metadata.sources += "$sourceName.filesystemRoots"
            }
        }
        json.optJSONObject("filesystem")?.let { filesystem ->
            collectOptionalStringArray(filesystem, "roots", "$sourceName.filesystem.roots", warnings).let { roots ->
                if (roots.isNotEmpty()) {
                    metadata.filesystemRoots += roots
                    metadata.sources += "$sourceName.filesystem.roots"
                }
            }
        }
        collectOptionalString(json, "executionMode", "$sourceName.executionMode", warnings)?.let { executionMode ->
            if (metadata.executionMode == null) {
                metadata.executionMode = executionMode
            }
            metadata.sources += "$sourceName.executionMode"
        }
        if (json.has("network") && !json.isNull("network")) {
            val network = json.opt("network")
            when (network) {
                is Boolean -> {
                    metadata.networkRequested = metadata.networkRequested || network
                    metadata.sources += "$sourceName.network"
                }
                is JSONObject -> {
                    val enabled = network.opt("enabled")
                    if (enabled is Boolean) {
                        metadata.networkRequested = metadata.networkRequested || enabled
                        metadata.sources += "$sourceName.network.enabled"
                    } else {
                        warnings += "$sourceName.network.enabled must be a boolean when present."
                    }
                }
                else -> warnings += "$sourceName.network must be a boolean or object."
            }
        }
    }

    private fun collectOptionalString(
        json: JSONObject,
        key: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): String? {
        if (!json.has(key) || json.isNull(key)) {
            return null
        }
        val value = json.opt(key)
        if (value !is String || value.isBlank()) {
            warnings += "$sourceName must be a non-empty string."
            return null
        }
        return value.trim()
    }

    private fun collectOptionalStringArray(
        json: JSONObject,
        key: String,
        sourceName: String,
        warnings: MutableList<String>,
    ): List<String> {
        if (!json.has(key) || json.isNull(key)) {
            return emptyList()
        }
        val array = json.opt(key) as? JSONArray
        if (array == null) {
            warnings += "$sourceName must be a JSON string array."
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value !is String || value.isBlank()) {
                    warnings += "$sourceName[$index] must be a non-empty string."
                } else {
                    add(value.trim())
                }
            }
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
        val packagedMetadata: PackagedMetadata,
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
            put("packagedMetadata", packagedMetadata.toJsonObject())
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

    data class PackagedMetadata(
        val schema: String,
        val status: String,
        val profile: String,
        val requestedProfiles: List<String>,
        val builtins: List<String>,
        val nativeAssets: List<String>,
        val filesystemRoots: List<String>,
        val executionMode: String,
        val longRunning: Boolean,
        val network: NetworkMetadata,
        val metadataOnly: Boolean,
        val grantsAuthority: Boolean,
        val sources: List<String>,
        val warnings: List<String>,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("schema", schema)
            put("status", status)
            put("profile", profile)
            put("requestedProfiles", JSONArray(requestedProfiles))
            put("builtins", JSONArray(builtins))
            put("nativeAssets", JSONArray(nativeAssets))
            put("filesystemRoots", JSONArray(filesystemRoots))
            put("executionMode", executionMode)
            put("longRunning", longRunning)
            put("network", network.toJsonObject())
            put("metadataOnly", metadataOnly)
            put("grantsAuthority", grantsAuthority)
            put("sources", JSONArray(sources))
            put("warnings", JSONArray(warnings))
        }
    }

    data class NetworkMetadata(
        val requested: Boolean,
        val declaredCapability: Boolean,
        val androidPermissions: List<String>,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("requested", requested)
            put("declaredCapability", declaredCapability)
            put("androidPermissions", JSONArray(androidPermissions))
        }
    }

    private data class PackagedMetadataBuilder(
        val profiles: MutableList<String> = mutableListOf(),
        val builtins: MutableList<String> = mutableListOf(),
        val nativeAssets: MutableList<String> = mutableListOf(),
        val filesystemRoots: MutableList<String> = mutableListOf(),
        val sources: MutableList<String> = mutableListOf(),
        var executionMode: String? = null,
        var networkRequested: Boolean = false,
    ) {
        fun build(normalizedPermissions: List<String>): PackagedMetadata {
            val distinctProfiles = profiles.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct()
            val profile = distinctProfiles.firstOrNull() ?: "safe_default"
            val mode = executionMode?.let(::normalizeMetadataToken).orEmpty()
            val networkCapabilityDeclared = NETWORK in normalizedPermissions
            val androidPermissions = if (networkCapabilityDeclared || networkRequested) {
                androidPermissionsForCapabilities(listOf(NETWORK))
            } else {
                emptyList()
            }
            val hasMetadata =
                distinctProfiles.isNotEmpty() ||
                        builtins.isNotEmpty() ||
                        nativeAssets.isNotEmpty() ||
                        filesystemRoots.isNotEmpty() ||
                        mode.isNotEmpty() ||
                        networkRequested
            return PackagedMetadata(
                schema = PACKAGED_METADATA_SCHEMA,
                status = if (hasMetadata || normalizedPermissions.isNotEmpty()) "partial" else "absent",
                profile = profile,
                requestedProfiles = distinctProfiles.sorted(),
                builtins = builtins.map(::normalizeBuiltin).filter { it.isNotEmpty() }.distinct().sorted(),
                nativeAssets = nativeAssets.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct().sorted(),
                filesystemRoots = filesystemRoots.map(::normalizeMetadataToken).filter { it.isNotEmpty() }.distinct().sorted(),
                executionMode = mode,
                longRunning = mode == "interactive_long_running" || mode == "long_running",
                network = NetworkMetadata(
                    requested = networkRequested || networkCapabilityDeclared,
                    declaredCapability = networkCapabilityDeclared,
                    androidPermissions = androidPermissions,
                ),
                metadataOnly = true,
                grantsAuthority = false,
                sources = sources.distinct().ifEmpty { listOf("none") },
                warnings = emptyList(),
            )
        }

        private fun normalizeBuiltin(value: String): String {
            val normalized = normalizeMetadataToken(value)
            return normalized.removePrefix("node.")
        }

        private fun normalizeMetadataToken(value: String): String =
            value.trim().lowercase(Locale.US).replace(':', '.')
    }

    private const val PROJECT_JSON = "project.json"
    private const val PACKAGE_JSON = "package.json"
    private const val PACKAGED_METADATA_SCHEMA = "autojs6-packaged-capability-metadata-v1"
}
