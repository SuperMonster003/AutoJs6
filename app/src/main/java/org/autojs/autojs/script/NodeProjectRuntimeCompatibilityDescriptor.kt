package org.autojs.autojs.script

import org.autojs.autojs6.BuildConfig
import org.json.JSONObject
import java.io.File

object NodeProjectRuntimeCompatibilityDescriptor {

    const val DESCRIPTOR_FILE_NAME = ".autojs6-node-runtime.json"
    const val SCHEMA = "autojs6-node-runtime-compatibility-v1"
    const val NODE_PROFILE_V1_1 = "node-profile-v1.1"
    const val SAFE_NODE_PROFILE_V1_1 = "safe-node-profile-v1.1"

    private val SUPPORTED_NODE_PROFILES = setOf(NODE_PROFILE_V1_1, SAFE_NODE_PROFILE_V1_1)
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
    fun requireCompatibleOrDefault(projectRoot: File) {
        val descriptor = descriptorFile(projectRoot)
        if (descriptor.isFile) {
            requireCompatible(descriptor)
        }
    }

    @JvmStatic
    fun requireCompatible(descriptor: File) {
        val json = runCatching {
            JSONObject(descriptor.readText(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException(
                "Invalid packaged Node runtime compatibility descriptor: ${descriptor.absolutePath}",
            )
        }
        val schema = json.requiredString("schema", descriptor)
        require(schema == SCHEMA) {
            "Unsupported packaged Node runtime compatibility descriptor schema: $schema"
        }
        val profile = json.requiredString("nodeProfile", descriptor)
        require(profile in SUPPORTED_NODE_PROFILES) {
            "Unsupported packaged Node runtime compatibility descriptor profile: $profile"
        }
        json.optStringOrNull("runtimeBackend")?.let { runtimeBackend ->
            require(runtimeBackend == BuildConfig.NODEJS_RUNTIME_BACKEND) {
                "Packaged Node runtime compatibility descriptor backend mismatch: " +
                        "descriptor=$runtimeBackend runtime=${BuildConfig.NODEJS_RUNTIME_BACKEND}"
            }
        }

        val features = json.optJSONObject("features") ?: return
        features.optBooleanOrNull("esmPartial")?.let { required ->
            require(!required || BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED) {
                "Packaged Node runtime compatibility descriptor requires partial ESM support"
            }
        }
        features.optBooleanOrNull("dynamicImportLocal")?.let { required ->
            require(!required || BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED) {
                "Packaged Node runtime compatibility descriptor requires local dynamic import support"
            }
        }
        features.optBooleanOrNull("workerThreads")?.let { required ->
            require(!required || BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED) {
                "Packaged Node runtime compatibility descriptor requires worker_threads support"
            }
        }
        FORBIDDEN_FEATURE_KEYS.forEach { key ->
            require(features.optBooleanOrNull(key) != true) {
                "Packaged Node runtime compatibility descriptor requires forbidden feature: $key"
            }
        }
    }

    private fun JSONObject.requiredString(key: String, descriptor: File): String {
        return optStringOrNull(key)
            ?: throw IllegalArgumentException(
                "Missing packaged Node runtime compatibility descriptor field: $key " +
                        "in ${descriptor.absolutePath}",
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
}
