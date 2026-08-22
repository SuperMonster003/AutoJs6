package org.autojs.autojs.core.plugin.jvm

import android.content.ComponentName
import android.content.Context
import org.autojs.plugin.jvmsource.api.JvmSourceContract

/**
 * Roadmap v2 M0: when no provider has been selected yet and exactly one installed candidate
 * would pass full admission (same signer, exported, enabled, dedicated compiler process),
 * select it automatically so no picker UI is required for the single-provider case.
 * zh-CN: M0: 未选择 provider 且恰好一个候选可通过完整准入时自动选用, 单 provider 场景无需选择 UI.
 */
internal object JvmSourceProviderAutoSelection {

    fun getOrAutoSelect(context: Context, inspector: JvmSourceProviderInspector): JvmSourceComponent? {
        JvmSourceProviderSelection.getComponent(context)?.let { persisted ->
            val inspected = runCatching { inspector.inspectExact(persisted) }.getOrNull()
            if (retainPersisted(persisted, inspected) != null) return persisted
            runCatching { JvmSourceProviderSelection.setExact(context, null) }
        }
        val candidates = inspector.discoverExactAction(JvmSourceContract.SERVICE_ACTION)
        val single = singleAdmissible(candidates) ?: return null
        runCatching {
            JvmSourceProviderSelection.setExact(
                context,
                ComponentName(single.component.packageName, single.component.className),
            )
        }
        return single.component
    }

    /** A removed, disabled or trust-changed persisted component must not block package migration. */
    internal fun retainPersisted(
        persisted: JvmSourceComponent,
        inspected: JvmSourceServiceCandidate?,
    ): JvmSourceComponent? = persisted.takeIf {
        inspected != null && JvmSourceProviderPolicy.rejection(inspected, persisted) == null
    }

    internal fun singleAdmissible(
        candidates: Collection<JvmSourceServiceCandidate>,
    ): JvmSourceServiceCandidate? = candidates.filter { candidate ->
        JvmSourceProviderPolicy.selectAndPin(candidates, candidate.component) != null
    }.singleOrNull()
}
