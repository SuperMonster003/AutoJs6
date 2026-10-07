package org.autojs.plugin.compose.api.interop

import android.os.Looper
import android.view.View
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.IdentityHashMap

/**
 * Parent-loaded process-wide ownership, shared by different plugin APK class loaders. Neither
 * keys nor values keep a View or renderer alive. Methods run on main before composition mutates
 * parents; a detached/offscreen View remains claimed while its accepted binding is still alive.
 */
object AndroidViewClaimsV1 {
    private val claims = AndroidViewClaimTable()

    fun validate(owner: Any, bindings: List<AndroidViewBindingV1>) {
        main()
        claims.validate(owner, bindings.map { AndroidViewClaimTable.Entry(it.nodeId, it.leaseId, it.view) })
    }

    /** Validates the complete replacement before releasing even one of [owner]'s old claims. */
    fun replace(owner: Any, bindings: List<AndroidViewBindingV1>) {
        main()
        claims.replace(owner, bindings.map { AndroidViewClaimTable.Entry(it.nodeId, it.leaseId, it.view) })
    }

    fun release(owner: Any) { main(); claims.release(owner) }

    fun isClaimed(view: View): Boolean { main(); return claims.isClaimed(view) }

    /** Read-only host preflight before replacing existing UI. A foreign live claim is never ours. */
    fun isOwnedBy(owner: Any, view: View, nodeId: Int, leaseId: Int): Boolean {
        main()
        return claims.isOwnedBy(owner, view, nodeId, leaseId)
    }

    private fun main() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw ComposeUiContractException(
            ComposeUiErrorCodes.INVALID_ARGUMENT, "AndroidView.view: Ownership requires the main thread", prop = AndroidViewInteropV1.PROP_VIEW)
    }
}

/** Pure ownership policy, independently testable without constructing Android Views. */
internal class AndroidViewClaimTable {
    data class Entry(val nodeId: Int, val leaseId: Int, val view: Any)
    private class IdentityReference(value: Any, queue: ReferenceQueue<Any>? = null) : WeakReference<Any>(value, queue) {
        private val hash = System.identityHashCode(value)
        override fun hashCode() = hash
        override fun equals(other: Any?) = this === other || other is IdentityReference && get()?.let { it === other.get() } == true
    }
    private class Claim(owner: Any, val nodeId: Int, val leaseId: Int) {
        val owner = WeakReference(owner)
    }
    private val queue = ReferenceQueue<Any>()
    private val entries = HashMap<IdentityReference, Claim>()
    val size: Int get() { drain(); return entries.size }

    @Synchronized fun validate(owner: Any, replacement: List<Entry>) {
        drain()
        if (replacement.size > ComposeUiLimits.MAX_NODES_PER_SESSION) failure(ComposeUiErrorCodes.LIMIT_EXCEEDED)
        val views = IdentityHashMap<Any, Boolean>()
        val nodes = HashSet<Int>()
        val leases = HashSet<Int>()
        for (entry in replacement) {
            if (entry.nodeId <= 0 || entry.leaseId <= 0 || !nodes.add(entry.nodeId) || !leases.add(entry.leaseId) || views.put(entry.view, true) != null) {
                failure(ComposeUiErrorCodes.INVALID_ARGUMENT, entry.nodeId)
            }
            entries[IdentityReference(entry.view)]?.let { previous ->
                if (previous.owner.get() !== owner) failure(ComposeUiErrorCodes.NODE_DETACHED, entry.nodeId)
                if (previous.nodeId != entry.nodeId || previous.leaseId != entry.leaseId) failure(ComposeUiErrorCodes.INVALID_ARGUMENT, entry.nodeId)
            }
        }
    }

    @Synchronized fun replace(owner: Any, replacement: List<Entry>) {
        validate(owner, replacement)
        entries.entries.removeAll { it.value.owner.get() === owner }
        replacement.forEach { entry -> entries[IdentityReference(entry.view, queue)] = Claim(owner, entry.nodeId, entry.leaseId) }
    }

    @Synchronized fun release(owner: Any) {
        drain()
        entries.entries.removeAll { it.value.owner.get() === owner }
    }

    @Synchronized fun isClaimed(view: Any): Boolean { drain(); return entries.containsKey(IdentityReference(view)) }

    @Synchronized fun isOwnedBy(owner: Any, view: Any, nodeId: Int, leaseId: Int): Boolean {
        drain()
        val claim = entries[IdentityReference(view)] ?: return false
        return claim.owner.get() === owner && claim.nodeId == nodeId && claim.leaseId == leaseId
    }

    private fun drain() {
        while (true) { val dead = queue.poll() as? IdentityReference ?: break; entries.remove(dead) }
        entries.entries.removeAll { it.value.owner.get() == null || it.key.get() == null }
    }
    private fun failure(code: String, nodeId: Int? = null): Nothing = throw ComposeUiContractException(
        code, "AndroidView.view: Invalid or already occupied View lease", nodeId?.takeIf { it > 0 }, AndroidViewInteropV1.PROP_VIEW)
}
