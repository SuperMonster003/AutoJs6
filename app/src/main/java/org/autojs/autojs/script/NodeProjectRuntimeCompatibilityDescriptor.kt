package org.autojs.autojs.script

import android.content.res.AssetManager
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeRuntimeCapabilities
import org.json.JSONObject
import java.io.File

object NodeProjectRuntimeCompatibilityDescriptor {

    const val DESCRIPTOR_FILE_NAME = ".autojs6-node-runtime.json"
    const val INTERNAL_DESCRIPTOR_FILE_NAME = "autojs6-node-runtime.json"
    const val SCHEMA = "autojs6-node-runtime-compatibility-v1"
    const val NODE_PROFILE_V1_1 = "node-profile-v1.1"
    const val SAFE_NODE_PROFILE_V1_1 = "safe-node-profile-v1.1"
    const val VERSION_MISMATCH_CODE = "ERR_AUTOJS6_PACKAGED_RUNTIME_VERSION_MISMATCH"
    const val RUNTIME_BACKEND = "plugin"

    const val BOOTSTRAP_VERSION = "autojs6-node-bootstrap-v1.1"
    const val COMMONJS_LOADER_VERSION = "autojs6-commonjs-loader-v1.1"
    const val ESM_LOADER_VERSION = "autojs6-esm-loader-v1.1"
    const val DYNAMIC_IMPORT_VERSION = "autojs6-dynamic-import-local-v1.1"
    const val BUILTIN_PROFILE_VERSION = "autojs6-safe-builtin-profile-v1.1"
    const val BRIDGE_PROTOCOL_VERSION = "autojs6-bridge-protocol-v1.1"
    const val CAPABILITY_MANIFEST_VERSION = "autojs6-capability-manifest-v1"
    const val MODULE_SOURCES_SCHEMA_VERSION = "autojs6-module-sources-schema-v1"

    private val SUPPORTED_NODE_PROFILES = setOf(NODE_PROFILE_V1_1, SAFE_NODE_PROFILE_V1_1)
    val DESCRIPTOR_FILE_NAMES = listOf(DESCRIPTOR_FILE_NAME, INTERNAL_DESCRIPTOR_FILE_NAME)
    private val VERSION_FIELDS = linkedMapOf(
        "bootstrap" to BOOTSTRAP_VERSION,
        "commonJsLoader" to COMMONJS_LOADER_VERSION,
        "esmLoader" to ESM_LOADER_VERSION,
        "dynamicImport" to DYNAMIC_IMPORT_VERSION,
        "builtinProfile" to BUILTIN_PROFILE_VERSION,
        "bridgeProtocol" to BRIDGE_PROTOCOL_VERSION,
        "capabilityManifest" to CAPABILITY_MANIFEST_VERSION,
        "moduleSourcesSchema" to MODULE_SOURCES_SCHEMA_VERSION,
    )
    private val FORBIDDEN_FEATURE_KEYS = setOf(
        "nativeAddon",
        "unrestrictedChildProcess",
        "rawNetwork",
        "processBinding",
        "processDlopen",
        "unrestrictedJavaInterop",
        "unrestrictedVm",
        "inspector",
    )

    @JvmStatic
    fun descriptorFile(projectRoot: File): File = File(projectRoot, DESCRIPTOR_FILE_NAME)

    @JvmStatic
    fun descriptorFiles(projectRoot: File): List<File> = DESCRIPTOR_FILE_NAMES.map { name -> File(projectRoot, name) }

    @JvmStatic
    fun requireCompatibleOrDefault(projectRoot: File) {
        val descriptor = descriptorFiles(projectRoot).firstOrNull { descriptorFile -> descriptorFile.isFile }
        if (descriptor != null) {
            requireCompatible(descriptor)
        }
    }

    @JvmStatic
    fun requireCompatible(descriptor: File) {
        requireCompatibleText(
            text = descriptor.readText(Charsets.UTF_8),
            source = descriptor.absolutePath,
        )
    }

    @JvmStatic
    fun requireCompatibleAssetOrDefault(
        assets: AssetManager,
        assetsProjectDir: String,
    ) {
        val root = assetsProjectDir.trim('/').takeIf { it.isNotEmpty() }.orEmpty()
        DESCRIPTOR_FILE_NAMES.forEach { name ->
            val assetPath = listOf(root, name).filter { it.isNotEmpty() }.joinToString("/")
            val text = runCatching {
                assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            }.getOrNull() ?: return@forEach
            requireCompatibleText(text, "asset:$assetPath")
            return
        }
    }

    @JvmStatic
    fun requireCompatibleText(text: String, source: String = DESCRIPTOR_FILE_NAME) {
        val json = runCatching { JSONObject(text) }.getOrElse {
            throw IllegalArgumentException(
                "Invalid packaged Node runtime compatibility descriptor: $source",
            )
        }
        requireCompatible(json, source)
    }

    @JvmStatic
    fun requireCompatible(json: JSONObject, source: String = DESCRIPTOR_FILE_NAME) {
        val schema = json.requiredString("schema", source)
        require(schema == SCHEMA) {
            "Unsupported packaged Node runtime compatibility descriptor schema: $schema"
        }
        val profile = json.requiredString("nodeProfile", source)
        require(profile in SUPPORTED_NODE_PROFILES) {
            "Unsupported packaged Node runtime compatibility descriptor profile: $profile"
        }
        json.optStringOrNull("runtimeBackend")?.let { runtimeBackend ->
            require(runtimeBackend == RUNTIME_BACKEND) {
                "Packaged Node runtime compatibility descriptor backend mismatch: " +
                        "descriptor=$runtimeBackend runtime=$RUNTIME_BACKEND"
            }
        }
        requireVersionVector(json, source)

        val features = json.optJSONObject("features") ?: return
        features.optBooleanOrNull("esmPartial")?.let { required ->
            require(!required || DEFAULT_ESM_PARTIAL) {
                "Packaged Node runtime compatibility descriptor requires partial ESM support"
            }
        }
        features.optBooleanOrNull("dynamicImportLocal")?.let { required ->
            require(!required || DEFAULT_DYNAMIC_IMPORT_LOCAL) {
                "Packaged Node runtime compatibility descriptor requires local dynamic import support"
            }
        }
        features.optBooleanOrNull("workerThreads")?.let { required ->
            require(!required || DEFAULT_WORKER_THREADS) {
                "Packaged Node runtime compatibility descriptor requires worker_threads support"
            }
        }
        FORBIDDEN_FEATURE_KEYS.forEach { key ->
            require(features.optBooleanOrNull(key) != true) {
                "Packaged Node runtime compatibility descriptor requires forbidden feature: $key"
            }
        }
    }

    @JvmStatic
    fun currentJson(
        esmPartial: Boolean = DEFAULT_ESM_PARTIAL,
        dynamicImportLocal: Boolean = DEFAULT_DYNAMIC_IMPORT_LOCAL,
        workerThreads: Boolean = DEFAULT_WORKER_THREADS,
    ): JSONObject {
        return JSONObject()
            .put("schema", SCHEMA)
            .put("nodeProfile", NODE_PROFILE_V1_1)
            .put("runtimeBackend", RUNTIME_BACKEND)
            .put("versions", currentVersionsJson())
            .put(
                "features",
                JSONObject()
                    .put("esmPartial", esmPartial)
                    .put("dynamicImportLocal", dynamicImportLocal)
                    .put("workerThreads", workerThreads)
                    .put("nativeAddon", false)
                    .put("unrestrictedChildProcess", false)
                    .put("rawNetwork", false)
                    .put("processBinding", false)
                    .put("processDlopen", false)
                    .put("unrestrictedJavaInterop", false)
                    .put("unrestrictedVm", false)
                    .put("inspector", false),
            )
            .put(
                "capabilities",
                JSONObject()
                    .put("manifestVersion", CAPABILITY_MANIFEST_VERSION)
                    .put("knownCapabilities", NodeBridgePermissionManifest.definedCapabilities.size)
                    .put("scopedFs", true),
            )
            .put(
                "limits",
                JSONObject()
                    .put("moduleSourcesMaxCount", NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_COUNT)
                    .put("moduleSourceMaxBytes", NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCE_MAX_BYTES)
                    .put("moduleSourcesMaxBytes", NodeRuntimeCapabilities.RUNTIME_MODULE_SOURCES_MAX_BYTES),
            )
    }

    @JvmStatic
    fun currentVersionsJson(): JSONObject {
        return JSONObject().apply {
            VERSION_FIELDS.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun requireVersionVector(json: JSONObject, source: String) {
        val versions = json.optJSONObject("versions")
            ?: throw IllegalArgumentException(
                "$VERSION_MISMATCH_CODE: missing packaged Node runtime compatibility descriptor versions in $source",
            )
        VERSION_FIELDS.forEach { (key, expected) ->
            val actual = versions.optStringOrNull(key)
                ?: throw IllegalArgumentException(
                    "$VERSION_MISMATCH_CODE: missing version field versions.$key expected=$expected actual=<missing>",
                )
            require(actual == expected) {
                "$VERSION_MISMATCH_CODE: version mismatch for versions.$key expected=$expected actual=$actual"
            }
        }
    }

    private fun JSONObject.requiredString(key: String, source: String): String {
        return optStringOrNull(key)
            ?: throw IllegalArgumentException(
                "Missing packaged Node runtime compatibility descriptor field: $key " +
                        "in $source",
            )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        require(value is String) {
            "Packaged Node runtime compatibility descriptor field must be a string: $key"
        }
        return value.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        require(value is Boolean) {
            "Packaged Node runtime compatibility descriptor field must be a boolean: features.$key"
        }
        return value
    }

    private const val DEFAULT_ESM_PARTIAL = true
    private const val DEFAULT_DYNAMIC_IMPORT_LOCAL = true
    private const val DEFAULT_WORKER_THREADS = true
}
