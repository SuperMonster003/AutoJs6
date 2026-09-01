package org.autojs.plugin.mediainfo.api

import java.util.Collections

object MediainfoSnapshotSchemas {
    const val V1 = "autojs6-plugin-mediainfo-snapshot-v1"
    const val V2 = "autojs6-plugin-mediainfo-snapshot-v2"
    const val DEFAULT = V1
    const val UNSUPPORTED_BY_PLUGIN_ERROR = "MEDIAINFO_SNAPSHOT_SCHEMA_UNSUPPORTED"

    @JvmField
    val VALUES: Set<String> = Collections.unmodifiableSet(linkedSetOf(V1, V2))

    @JvmStatic
    fun resolve(requestedSchema: String?): String = when {
        requestedSchema.isNullOrBlank() -> DEFAULT
        requestedSchema in VALUES -> requestedSchema
        else -> throw IllegalArgumentException("Unsupported MediaInfo snapshot schema: $requestedSchema")
    }

    @JvmStatic
    fun isSupported(schema: String?, advertisedSchemas: Array<out String>?): Boolean {
        val resolvedSchema = resolve(schema)
        return resolvedSchema == V1 || advertisedSchemas?.any { it == resolvedSchema } == true
    }

    @JvmStatic
    fun requireSupported(schema: String?, advertisedSchemas: Array<out String>?): String {
        val resolvedSchema = resolve(schema)
        if (!isSupported(resolvedSchema, advertisedSchemas)) {
            throw UnsupportedOperationException(
                "$UNSUPPORTED_BY_PLUGIN_ERROR: MediaInfo plugin does not advertise snapshot schema $resolvedSchema",
            )
        }
        return resolvedSchema
    }
}
