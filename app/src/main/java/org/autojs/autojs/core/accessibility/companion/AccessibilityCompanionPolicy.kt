package org.autojs.autojs.core.accessibility.companion

import org.autojs.plugin.common.api.AutoJs6AccessibilityCompanionContract as Contract

/**
 * Pure rules of the accessibility companion contract: which plugin-info capabilities describe a
 * companion the host may manage, and whether the companion service must be attached or detached
 * to mirror the host service. Android-free so that the rules are unit tested.
 * zh-CN: 无障碍伴生契约的纯规则: 哪些插件信息能力描述了宿主可管理的伴生插件, 以及伴生服务是否需要
 * 附加或分离以镜像宿主服务. 不依赖 Android, 便于单元测试.
 *
 * Created by SuperMonster003 on Sep 15, 2026.
 */
object AccessibilityCompanionPolicy {

    enum class Decision { NONE, ATTACH, DETACH }

    /** A companion the host discovered; [serviceComponent] is a flattened `package/class` name. */
    data class Companion(
        val packageName: String,
        val serviceComponent: String,
        val policy: String,
    ) {
        val followsHost: Boolean get() = policy == Contract.SERVICE_POLICY_FOLLOW_HOST
    }

    val KNOWN_POLICIES = setOf(
        Contract.SERVICE_POLICY_FOLLOW_HOST,
        Contract.SERVICE_POLICY_ENABLED,
        Contract.SERVICE_POLICY_DISABLED,
    )

    /**
     * Builds a [Companion] from the published capabilities, or null when the plugin predates the
     * contract (no policy), publishes an unknown policy, or names a service outside its own package.
     * A companion without the capability keeps the pre-contract behavior: the host leaves it alone.
     */
    fun companionOf(packageName: String, serviceComponent: String?, policy: String?): Companion? {
        val component = serviceComponent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (component.substringBefore('/') != packageName || !component.contains('/')) return null
        val normalizedPolicy = policy?.trim() ?: return null
        if (normalizedPolicy !in KNOWN_POLICIES) return null
        return Companion(packageName, component, normalizedPolicy)
    }

    /**
     * Only `follow-host` companions are managed; the host never overrides an explicit `enabled` or
     * `disabled` choice made in the companion. [hostServiceEnabled] is the listed state of the host
     * service, not its binding, so a host restart (unbind then bind) does not flap the companion.
     */
    fun decide(companion: Companion, hostServiceEnabled: Boolean, companionServiceEnabled: Boolean): Decision {
        if (!companion.followsHost) return Decision.NONE
        return when {
            hostServiceEnabled == companionServiceEnabled -> Decision.NONE
            hostServiceEnabled -> Decision.ATTACH
            else -> Decision.DETACH
        }
    }

}
