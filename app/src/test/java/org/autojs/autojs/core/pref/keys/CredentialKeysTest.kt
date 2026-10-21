package org.autojs.autojs.core.pref.keys

import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.network.GitHubTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3.4: [CredentialKeys] (the credential and authorization stores, all sensitive and in
 * `credentials`, D2) and their readers moved to the declared layer with the store names and key names unchanged (R1,
 * R2). Values are only compared, never printed (R6).
 *
 * zh-CN: ROADMAP P3.4: [CredentialKeys] (凭据与授权的存储, 全部为敏感项并归 `credentials`, D2) 及其读写方迁移到声明层, 存储名与键名
 * 不变 (R1, R2). 测试只比较值, 不输出值 (R6).
 */
class CredentialKeysTest : DomainKeysTestBase() {

    private val context: Context = ContextWrapper(null)

    @Test
    fun everyCredentialIsSensitiveInItsOwnStore() {
        val declared = CredentialKeys.keys + CredentialKeys.families.map { it.template }
        assertEquals(5, declared.size)
        declared.forEach { key ->
            assertTrue(key.name, (key as SettingKey<*>).sensitive)
            assertEquals(key.name, SettingCategory.CREDENTIALS, key.category)
            assertFalse(key.name, key.store.isDefault)
        }
        assertEquals(
            listOf("github_auth", "plugin_center_authorization", "mcp_server_trust_confirmation", "three_stove_agent_trust_confirmation", "bun_host_capability_grants"),
            declared.map { it.store.name },
        )
        assertTrue(CredentialKeys in SettingsRegistry.DOMAINS)
        val rows = SettingsRegistry.describe().filter { it.category == SettingCategory.CREDENTIALS.id }
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.sensitive })
        assertEquals(listOf("github_token", "key_\$_plugin_auth_\$_", "*", "*", "*"), rows.map { it.name })
        assertFalse(SettingCategory.CREDENTIALS.defaultSelected)
    }

    @Test
    fun gitHubTokenIsSanitizedAndRemovedWhenBlank() {
        val auth = store(NamedStores.GITHUB_AUTH)
        assertNull(GitHubTokenStore.getToken(context))
        GitHubTokenStore.setToken(context, "Authorization: Bearer abc123xyz\n")
        assertEquals("abc123xyz", auth.getString("github_token", null))
        assertTrue(GitHubTokenStore.hasToken(context))
        assertEquals("3xyz", GitHubTokenStore.getTokenSuffix(context))
        GitHubTokenStore.setToken(context, "   ")
        assertFalse(auth.contains("github_token"))
        // A stored value of another type throws like getString did.
        auth.put("github_token", 1)
        assertThrows(ClassCastException::class.java) { GitHubTokenStore.getToken(context) }
    }

    @Test
    fun pluginAuthorizationsAreKeyedByPackageAndFingerprint() {
        val auth = store(NamedStores.PLUGIN_CENTER_AUTHORIZATION)
        assertFalse(PluginAuthorizationStore.isGranted(context, "org.example.plugin", listOf("AA", "BB")))
        PluginAuthorizationStore.grant(context, "org.example.plugin", "BB")
        assertTrue(auth.getBoolean("key_\$_plugin_auth_\$_org.example.plugin\$_BB", false))
        assertTrue(PluginAuthorizationStore.isGranted(context, "org.example.plugin", listOf("AA", "BB")))
        assertFalse(PluginAuthorizationStore.isGranted(context, "org.example.plugin", emptyList()))
        val writes = auth.commitCount
        PluginAuthorizationStore.grant(context, "org.example.plugin", "BB")
        assertEquals("granting twice writes nothing", writes, auth.commitCount)
        assertEquals(listOf("key_\$_plugin_auth_\$_org.example.plugin\$_BB"), CredentialKeys.PLUGIN_AUTHORIZATION.storedNames())
        assertSame(CredentialKeys.PLUGIN_AUTHORIZATION, SettingsRegistry.findFamily("key_\$_plugin_auth_\$_org.example.plugin\$_BB", NamedStores.PLUGIN_CENTER_AUTHORIZATION))
        PluginAuthorizationStore.revoke(context, "org.example.plugin", "BB")
        assertTrue(auth.all().isEmpty())
    }

    @Test
    fun trustConfirmationsAreNamedBySigners() {
        listOf(
            CredentialKeys.MCP_SERVER_TRUST_CONFIRMATION to NamedStores.MCP_SERVER_TRUST_CONFIRMATION,
            CredentialKeys.THREE_STOVE_AGENT_TRUST_CONFIRMATION to NamedStores.THREE_STOVE_AGENT_TRUST_CONFIRMATION,
        ).forEach { (family, id) ->
            assertEquals("", family.prefix)
            assertEquals(KeyFamily.EVERY_NAME, family.template.name)
            val signer = "0A:1B,2C:3D"
            assertFalse(family.key(signer).get())
            family.key(signer).set(true)
            assertTrue(store(id).getBoolean(signer, false))
            assertEquals(listOf(signer), family.storedNames())
            assertSame(family, SettingsRegistry.findFamily(signer, id))
        }
    }

    @Test
    fun bunCapabilityGrantsKeepTheirOwnDefaults() {
        val grants = store(NamedStores.BUN_HOST_CAPABILITY_GRANTS)
        assertTrue(BunHostCapabilityGrants.isHostInfoGranted(context))
        assertTrue(BunHostCapabilityGrants.isTextPinyinGranted(context))
        assertFalse(BunHostCapabilityGrants.isScreenCaptureGranted(context))
        assertFalse(BunHostCapabilityGrants.isShellRootGranted(context))
        BunHostCapabilityGrants.setHostInfoGranted(context, false)
        BunHostCapabilityGrants.setShellRootGranted(context, true)
        assertFalse(grants.getBoolean("host_info", true))
        assertTrue(grants.getBoolean(BunHostCapabilityGrants.SHELL_ROOT, false))
        assertFalse(BunHostCapabilityGrants.isHostInfoGranted(context))
        assertTrue(BunHostCapabilityGrants.isShellRootGranted(context))
        assertEquals(setOf("host_info", "shell.root"), CredentialKeys.BUN_HOST_CAPABILITY_GRANT.storedNames().toSet())
        assertNull(CredentialKeys.BUN_HOST_CAPABILITY_GRANT.template.defaultOrNull())
    }
}
