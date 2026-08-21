package org.autojs.plugin.yolo.fake

import org.json.JSONObject

/** Hostile behavior is selected only by an external, test-owned manifest. */
internal enum class FakeYoloScenario(val wireName: String) {
    DUPLICATE_TERMINAL("duplicate-terminal"),
    LATE_OLD_TERMINAL("late-old-terminal"),
    STALL_CANCEL_ACK("stall-cancel-ack"),
    STALL_CANCEL_NO_ACK("stall-cancel-no-ack"),
    WRONG_CALLBACK_UID("wrong-callback-uid"),
    PROVIDER_DEATH("provider-death"),
    ;

    companion object {
        private const val MANIFEST_KIND = "autojs6-yolo-hostile-conformance-v1"

        fun fromManifest(bytes: ByteArray): FakeYoloScenario {
            require(bytes.size in 1..MAX_MANIFEST_BYTES) {
                "Fake YOLO conformance manifest length is invalid"
            }
            val document = JSONObject(bytes.toString(Charsets.UTF_8))
            require(document.length() == 3) {
                "Fake YOLO conformance manifest contains unknown fields"
            }
            require(document.getInt("schemaVersion") == 1) {
                "Fake YOLO conformance manifest version is unsupported"
            }
            require(document.getString("kind") == MANIFEST_KIND) {
                "Fake YOLO conformance manifest kind is invalid"
            }
            val scenario = document.getString("scenario")
            return entries.singleOrNull { it.wireName == scenario }
                ?: throw IllegalArgumentException("Unknown fake YOLO scenario")
        }

        private const val MAX_MANIFEST_BYTES = 4 * 1024
    }
}
