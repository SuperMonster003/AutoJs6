package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal data class RuntimeAgentIdentity(val packageName: String, val uid: Int, val installedAt: Long, val signers: List<String>) {
    fun key(): String = JSONObject().put("package", packageName).put("uid", uid).put("installedAt", installedAt)
        .put("signers", JSONArray(signers)).toString()
}

/** Consent survives an update, but not a changed signer, UID or installation. */
internal class RuntimeAgentAccessStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("agent_runtime_access", Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    fun identity(packageName: String): RuntimeAgentIdentity? = runCatching {
        val info = context.packageManager.getPackageInfo(packageName, if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val certificates = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty()
        require(certificates.isNotEmpty())
        RuntimeAgentIdentity(packageName, requireNotNull(info.applicationInfo).uid, info.firstInstallTime, certificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.sorted())
    }.getOrNull()
    fun granted(identity: RuntimeAgentIdentity): Boolean = identity == identity(identity.packageName) &&
        context.packageManager.checkPermission(RuntimeAgentRemote.PERMISSION, identity.packageName) == PackageManager.PERMISSION_GRANTED &&
        prefs.getString("grant:" + identity.packageName, null) == identity.key()
    fun grant(identity: RuntimeAgentIdentity): Boolean = identity == identity(identity.packageName) &&
        prefs.edit().putString("grant:" + identity.packageName, identity.key()).commit()
    fun applications(): List<RuntimeAgentIdentity> = prefs.all.keys.filter { it.startsWith("grant:") }
        .mapNotNull { identity(it.removePrefix("grant:")) }.filter(::granted).sortedBy { it.packageName }
    fun revoke(identity: RuntimeAgentIdentity) {
        check(prefs.edit().remove("grant:" + identity.packageName).commit())
        RuntimeAgentProvider.revoke(identity)
        val ids = prefs.all.filter { (key, value) -> key.startsWith("run:") && value == identity.key() }.keys.map { it.removePrefix("run:") }
        Thread({
            val registry = org.autojs.autojs.core.plugin.agent.ThreeStoveAgentLinkController.get(context).scriptRuns
            registry.cancelTracked(ids.toSet(), "application-access-revoked")
        }, "agent-access-revoke").apply { isDaemon = true }.start()
    }
    fun reconcile(packageName: String) {
        val previous = runCatching {
            val row = JSONObject(prefs.getString("grant:$packageName", null) ?: return)
            val signers = row.getJSONArray("signers")
            RuntimeAgentIdentity(packageName, row.getInt("uid"), row.getLong("installedAt"), (0 until signers.length()).map(signers::getString))
        }.getOrNull() ?: return
        if (!granted(previous)) revoke(previous)
    }
    fun scope(identity: RuntimeAgentIdentity): RuntimeAgentAccess = object : RuntimeAgentAccess {
        override fun allowed() = granted(identity)
        override fun owns(id: String) = prefs.getString("run:$id", null) == identity.key()
        override fun created(id: String) {
            if (!allowed()) throw SecurityException("Agent application access was revoked")
            check(prefs.edit().putString("run:$id", identity.key()).commit())
            if (!allowed()) throw SecurityException("Agent application access was revoked")
        }
    }
}
