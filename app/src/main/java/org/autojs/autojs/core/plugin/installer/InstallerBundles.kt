package org.autojs.autojs.core.plugin.installer

import android.os.Bundle
import org.autojs.plugin.installer.api.InstallerContract
import java.nio.charset.StandardCharsets

/** Bundle envelopes of the installer contract: every host bundle carries the contract and host versions. */
internal object InstallerBundles {

    fun request(requestJson: String, hostVersionCode: Long, hostSessionId: String? = null): Bundle = Bundle().apply {
        putInt(InstallerContract.KEY_CONTRACT_VERSION, InstallerContract.CONTRACT_VERSION)
        putLong(InstallerContract.KEY_HOST_VERSION_CODE, hostVersionCode)
        putString(InstallerContract.KEY_REQUEST_JSON, requestJson)
        hostSessionId?.let { putString(InstallerContract.KEY_HOST_SESSION_ID, it) }
    }

    /** The string under [key], or null when absent, blank or beyond the JSON ceiling. */
    fun string(bundle: Bundle?, key: String): String? {
        val value = bundle?.getString(key)?.takeIf { it.isNotBlank() } ?: return null
        return value.takeIf { it.toByteArray(StandardCharsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES }
    }
}
