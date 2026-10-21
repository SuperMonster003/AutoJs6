package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.CREDENTIALS
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain

/**
 * Credentials and authorizations (docs/settings/ROADMAP.md P3.4, appendix A.1 `credentials`, D2): the GitHub token,
 * the plugins authorized per signing fingerprint, the confirmed signers of the MCP Server and 3-Stove Agent plugins and
 * the host capabilities granted to Bun scripts, each in its own named store ([NamedStores]). Every key is sensitive:
 * the category is never selected by default, exporting it writes plain text after a warning (P6.3), scripts and auto
 * backups leave it out (T15 / T20) and no value may reach a log, a toast or a crash report (R6).
 *
 * The signers and the capability ids are the names themselves, so those stores are families of every name; their
 * readers keep their own fallbacks (false for an unconfirmed signer, the per-capability default of
 * `BunHostCapabilityGrants`).
 *
 * zh-CN: 凭据与授权 (ROADMAP P3.4, 附录 A.1 `credentials`, D2): GitHub 令牌, 按签名指纹授权的插件, MCP Server 与 3-Stove Agent
 * 插件已确认的签名, 授予 Bun 脚本的宿主能力, 各在自己的命名存储中. 每个键都是敏感项: 分类默认不勾选, 导出时警告后以明文写入
 * (P6.3), 脚本与自动备份不包含 (T15 / T20), 值不得出现在日志, toast 与崩溃信息中 (R6). 签名与能力 id 本身就是名称, 因此这些
 * 存储是全部名称的键族; 读取方保留各自的回退值 (未确认的签名为 false, 能力为 `BunHostCapabilityGrants` 中各自的默认值).
 */
object CredentialKeys : SettingsDomain {

    /** The sanitized GitHub token (`GitHubTokenStore`); missing when none is set. */
    @JvmField
    val GITHUB_TOKEN = SettingKey.Str(
        name = "github_token", category = CREDENTIALS, default = SettingDefault.None, sensitive = true, store = NamedStores.GITHUB_AUTH,
    )

    /** A plugin authorized for one signing fingerprint: `<package name>$_<fingerprint>` after the prefix (`PluginAuthorizationStore`). */
    @JvmField
    val PLUGIN_AUTHORIZATION = KeyFamily("key_\$_plugin_auth_\$_") { name ->
        SettingKey.Bool(name = name, category = CREDENTIALS, default = SettingDefault.of(false), sensitive = true, store = NamedStores.PLUGIN_CENTER_AUTHORIZATION)
    }

    /** The MCP Server plugin confirmed for a signer, named by its sorted fingerprints (`McpServerPluginInspector`). */
    @JvmField
    val MCP_SERVER_TRUST_CONFIRMATION = KeyFamily.everyName { name ->
        SettingKey.Bool(name = name, category = CREDENTIALS, default = SettingDefault.of(false), sensitive = true, store = NamedStores.MCP_SERVER_TRUST_CONFIRMATION)
    }

    /** The 3-Stove Agent plugin confirmed for a signer, named by its sorted fingerprints (`ThreeStoveAgentPluginInspector`). */
    @JvmField
    val THREE_STOVE_AGENT_TRUST_CONFIRMATION = KeyFamily.everyName { name ->
        SettingKey.Bool(name = name, category = CREDENTIALS, default = SettingDefault.of(false), sensitive = true, store = NamedStores.THREE_STOVE_AGENT_TRUST_CONFIRMATION)
    }

    /** A host capability granted to Bun scripts, named by its capability id (`BunHostCapabilityGrants`); defaults differ per capability. */
    @JvmField
    val BUN_HOST_CAPABILITY_GRANT = KeyFamily.everyName { name ->
        SettingKey.Bool(name = name, category = CREDENTIALS, default = SettingDefault.None, sensitive = true, store = NamedStores.BUN_HOST_CAPABILITY_GRANTS)
    }

    override val keys: List<PrefKey<*>> = listOf(GITHUB_TOKEN)

    override val families: List<KeyFamily<*, *>> = listOf(
        PLUGIN_AUTHORIZATION, MCP_SERVER_TRUST_CONFIRMATION, THREE_STOVE_AGENT_TRUST_CONFIRMATION, BUN_HOST_CAPABILITY_GRANT,
    )
}
