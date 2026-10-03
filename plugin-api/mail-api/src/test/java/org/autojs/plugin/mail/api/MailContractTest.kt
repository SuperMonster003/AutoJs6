package org.autojs.plugin.mail.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class MailContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, MailActions.INFO)
        assertEquals("org.autojs.plugin.MAIL", MailActions.SERVICE_ACTION)
        assertEquals("mail", MailActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", MailActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.plugin.MAIL_SETTINGS", MailActions.OPEN_SETTINGS)

        assertEquals("three-stamp-mail", MailIds.PLUGIN_ID)
        assertEquals("mail", MailIds.ENGINE)
        assertEquals("default", MailIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.stamp.mail", MailIds.DEFAULT_PACKAGE_NAME)
        assertEquals(5282L, MailIds.REQUIRED_HOST_VERSION_CODE)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, MailCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("mailContractVersion", MailCapabilityKeys.CONTRACT_VERSION)
        assertEquals("mailProtocols", MailCapabilityKeys.PROTOCOLS)
        assertEquals("mailAuthMechanisms", MailCapabilityKeys.AUTH_MECHANISMS)
        assertEquals("mailFeatures", MailCapabilityKeys.FEATURES)
        assertEquals("mailProvidersVersion", MailCapabilityKeys.PROVIDERS_VERSION)
        assertEquals("mailSettingsVersion", MailCapabilityKeys.SETTINGS_VERSION)
    }

    @Test
    fun contractVersionRangeIncludesItsBoundsAndRejectsNeighbours() {
        assertEquals(2, MailContract.CONTRACT_VERSION)
        assertEquals(1, MailContract.MIN_CONTRACT_VERSION)
        assertEquals(2, MailContract.TRIGGER_CONTRACT_VERSION)
        assertTrue(MailContract.supportsContractVersion(MailContract.TRIGGER_CONTRACT_VERSION))
        assertTrue(MailContract.MIN_CONTRACT_VERSION <= MailContract.MAX_CONTRACT_VERSION)
        assertTrue(MailContract.supportsContractVersion(MailContract.MIN_CONTRACT_VERSION))
        assertTrue(MailContract.supportsContractVersion(MailContract.CONTRACT_VERSION))
        assertTrue(MailContract.supportsContractVersion(MailContract.MAX_CONTRACT_VERSION))
        assertFalse(MailContract.supportsContractVersion(MailContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(MailContract.supportsContractVersion(MailContract.MAX_CONTRACT_VERSION + 1))
    }

    @Test
    fun bundleKeysAreUniqueAndFrozen() {
        val keys = constants("KEY_")
        assertEquals(keys.size, keys.values.toSet().size)
        assertEquals("accountJson", MailContract.KEY_ACCOUNT_JSON)
        assertEquals("secretPassword", MailContract.KEY_SECRET_PASSWORD)
        assertEquals("secretAccessToken", MailContract.KEY_SECRET_ACCESS_TOKEN)
        assertEquals("accountAlias", MailContract.KEY_ACCOUNT_ALIAS)
        assertEquals("requestJson", MailContract.KEY_REQUEST_JSON)
        assertEquals("responseJson", MailContract.KEY_RESPONSE_JSON)
        assertEquals("eventJson", MailContract.KEY_EVENT_JSON)
        assertEquals("statusJson", MailContract.KEY_STATUS_JSON)
        assertEquals("progressJson", MailContract.KEY_PROGRESS_JSON)
        assertEquals("watchOptionsJson", MailContract.KEY_WATCH_OPTIONS_JSON)
        assertEquals("providersJson", MailContract.KEY_PROVIDERS_JSON)
        assertEquals("accountsJson", MailContract.KEY_ACCOUNTS_JSON)
        assertEquals("hostVersionCode", MailContract.KEY_HOST_VERSION_CODE)
        assertEquals("generation", MailContract.KEY_GENERATION)
        assertEquals("contractVersion", MailContract.KEY_CONTRACT_VERSION)
        assertEquals("triggerOptionsJson", MailContract.KEY_TRIGGER_OPTIONS_JSON)
        assertEquals("triggersJson", MailContract.KEY_TRIGGERS_JSON)
        assertEquals("triggerId", MailContract.KEY_TRIGGER_ID)
    }

    @Test
    fun envelopeFieldsAreUnique() {
        val fields = constants("FIELD_")
        // FIELD_ERROR_* live inside the nested error object, so `message` may repeat there.
        val topLevel = fields.filterKeys { !it.startsWith("FIELD_ERROR_") }
        assertEquals(topLevel.size, topLevel.values.toSet().size)
        val nested = fields.filterKeys { it.startsWith("FIELD_ERROR_") }
        assertEquals(nested.size, nested.values.toSet().size)
        assertEquals("id", MailContract.FIELD_ID)
        assertEquals("op", MailContract.FIELD_OP)
        assertEquals("args", MailContract.FIELD_ARGS)
        assertEquals("ok", MailContract.FIELD_OK)
        assertEquals("result", MailContract.FIELD_RESULT)
        assertEquals("error", MailContract.FIELD_ERROR)
    }

    @Test
    fun operationsAreUniqueAndMatchAppendixB2() {
        val ops = constants("OP_")
        assertEquals(ops.size, ops.values.toSet().size)
        assertEquals(ops.values.toSet(), MailContract.OPS.toSet())
        assertEquals(MailContract.OPS.size, MailContract.OPS.toSet().size)
        assertEquals(
            listOf(
                "session.test",
                "folders.list", "folders.status", "folders.create", "folders.delete", "folders.rename",
                "messages.list", "messages.search", "messages.get", "messages.raw", "attachments.download",
                "messages.setFlags", "messages.move", "messages.copy", "messages.delete", "messages.expunge", "messages.append",
                "mail.send",
                "session.close",
            ),
            MailContract.OPS,
        )
        MailContract.OPS.forEach { op -> assertTrue(op, MailContract.isKnownOp(op)) }
        assertFalse(MailContract.isKnownOp(null))
        assertFalse(MailContract.isKnownOp("messages.purge"))
        assertTrue(MailContract.OPS_WITH_SINK.all { it in MailContract.OPS })
        assertTrue(MailContract.OPS_WITH_SOURCES.all { it in MailContract.OPS })
        assertTrue(MailContract.OPS_METADATA.all { it in MailContract.OPS })
        assertTrue((MailContract.OPS_WITH_SINK intersect MailContract.OPS_WITH_SOURCES).isEmpty())
        assertTrue((MailContract.OPS_WITH_SINK intersect MailContract.OPS_METADATA).isEmpty())
        assertTrue((MailContract.OPS_WITH_SOURCES intersect MailContract.OPS_METADATA).isEmpty())
    }

    @Test
    fun errorCodesMatchAppendixB4Snapshot() {
        assertEquals(
            listOf(
                "INVALID_ARGUMENT", "NO_DEFAULT_ACCOUNT", "ACCOUNT_NOT_FOUND", "PROVIDER_UNKNOWN",
                "AUTH_FAILED", "AUTH_MECHANISM_UNSUPPORTED", "CONNECT_FAILED", "TLS_FAILED",
                "TIMEOUT", "CANCELLED", "FOLDER_NOT_FOUND", "MESSAGE_NOT_FOUND", "ATTACHMENT_NOT_FOUND",
                "UNSUPPORTED_OPERATION", "LIMIT_EXCEEDED", "IO_FAILED", "SESSION_CLOSED", "WATCH_CLOSED",
                "SEND_REJECTED", "SERVER_ERROR", "PLUGIN_UNAVAILABLE", "INTERNAL",
            ),
            MailErrorCodes.ALL,
        )
        assertEquals(MailErrorCodes.ALL.size, MailErrorCodes.ALL.toSet().size)
        val declared = constantsOf(MailErrorCodes::class.java, "")
        assertEquals(declared.values.toSet(), MailErrorCodes.ALL.toSet())
        MailErrorCodes.ALL.forEach { code ->
            assertEquals(code, code.uppercase())
            assertTrue(code, MailErrorCodes.isKnown(code))
        }
        assertFalse(MailErrorCodes.isKnown(null))
        assertFalse(MailErrorCodes.isKnown("NOT_A_CODE"))
        assertEquals(
            setOf("CONNECT_FAILED", "TIMEOUT", "SESSION_CLOSED", "SERVER_ERROR", "PLUGIN_UNAVAILABLE"),
            MailErrorCodes.RETRYABLE_DEFAULTS,
        )
        assertTrue(MailErrorCodes.RETRYABLE_DEFAULTS.all(MailErrorCodes::isKnown))
        assertTrue(MailErrorCodes.isRetryableByDefault(MailErrorCodes.TIMEOUT))
        assertFalse(MailErrorCodes.isRetryableByDefault(MailErrorCodes.AUTH_FAILED))
        assertFalse(MailErrorCodes.isRetryableByDefault(null))
    }

    @Test
    fun closedValueSetsAreFrozen() {
        assertEquals(setOf("open", "closed"), MailContract.STATES)
        assertEquals(setOf("message", "error", "closed", "mode", "resync"), MailContract.EVENT_TYPES)
        assertEquals(setOf("idle", "poll"), MailContract.WATCH_MODES)
        assertEquals(setOf("imap", "pop3", "smtp"), MailContract.PROTOCOLS)
        assertEquals(setOf("password", "xoauth2"), MailContract.AUTH_MECHANISMS)
        assertEquals(setOf("ssl", "starttls", "none"), MailContract.TLS_MODES)
        assertEquals(setOf("idle", "savedAccounts", "append", "clientSearchFallback", "backgroundWatch"), MailContract.FEATURES)
        assertEquals("mail", MailContract.TRIGGER_EVENT_MAIL)
        assertEquals(setOf("stopped", "connecting", "connected", "failed"), MailContract.TRIGGER_STATES)
        assertEquals("org.autojs.autojs6.action.MAIL_TRIGGER", MailActions.HOST_TRIGGER_ACTION)
    }

    @Test
    fun ceilingsArePositiveAndLayered() {
        val ceilings = listOf(
            MailContract.MAX_ENVELOPE_BYTES.toLong(),
            MailContract.MAX_INLINE_BODY_BYTES.toLong(),
            MailContract.MAX_ATTACHMENT_BYTES,
            MailContract.MAX_ATTACHMENTS_PER_MESSAGE.toLong(),
            MailContract.MAX_RECIPIENTS.toLong(),
            MailContract.MAX_PAGE_SIZE.toLong(),
            MailContract.DEFAULT_PAGE_SIZE.toLong(),
            MailContract.MAX_CLIENT_FILTER.toLong(),
            MailContract.MAX_DESCRIPTORS.toLong(),
            MailContract.MAX_CONCURRENT_CALLS.toLong(),
            MailContract.MAX_QUEUED_CALLS.toLong(),
            MailContract.MAX_WATCHES_PER_SESSION.toLong(),
            MailContract.MAX_WATCH_QUEUE.toLong(),
            MailContract.MIN_POLL_INTERVAL_MS,
            MailContract.DEFAULT_POLL_INTERVAL_MS,
            MailContract.IDLE_RENEW_MS,
            MailContract.SESSION_IDLE_TIMEOUT_MS,
            MailContract.DEFAULT_CONNECT_TIMEOUT_MS,
            MailContract.DEFAULT_READ_TIMEOUT_MS,
            MailContract.MAX_ERROR_MESSAGE_BYTES.toLong(),
            MailContract.METADATA_CALL_TIMEOUT_MS,
            MailContract.BODY_CALL_TIMEOUT_MS,
            MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS,
            MailContract.TRANSFER_CALL_BYTES_PER_SECOND,
            MailContract.MAX_TRIGGERS.toLong(),
            MailContract.MAX_TRIGGER_SUBSCRIBERS.toLong(),
            MailContract.MAX_TRIGGER_RECORDS.toLong(),
            MailContract.MIN_TRIGGER_INTERVAL_MS,
        )
        ceilings.forEach { value -> assertTrue("ceiling $value must be positive", value > 0L) }

        assertEquals(512L * 1024, MailContract.MAX_ENVELOPE_BYTES.toLong())
        assertEquals(256L * 1024, MailContract.MAX_INLINE_BODY_BYTES.toLong())
        assertEquals(200L * 1024 * 1024, MailContract.MAX_ATTACHMENT_BYTES)
        assertTrue(MailContract.MAX_INLINE_BODY_BYTES < MailContract.MAX_ENVELOPE_BYTES)
        assertTrue(MailContract.MAX_ENVELOPE_BYTES < MailContract.MAX_ATTACHMENT_BYTES)
        assertTrue(MailContract.MAX_ERROR_MESSAGE_BYTES < MailContract.MAX_INLINE_BODY_BYTES)
        assertTrue(MailContract.DEFAULT_PAGE_SIZE <= MailContract.MAX_PAGE_SIZE)
        assertTrue(MailContract.MAX_PAGE_SIZE <= MailContract.MAX_CLIENT_FILTER)
        assertTrue(MailContract.MAX_ATTACHMENTS_PER_MESSAGE <= MailContract.MAX_DESCRIPTORS)
        assertTrue(MailContract.MAX_CONCURRENT_CALLS <= MailContract.MAX_QUEUED_CALLS)
        assertTrue(MailContract.MIN_POLL_INTERVAL_MS <= MailContract.DEFAULT_POLL_INTERVAL_MS)
        assertTrue(MailContract.IDLE_RENEW_MS < 29L * 60 * 1000)
        assertTrue(MailContract.DEFAULT_CONNECT_TIMEOUT_MS <= MailContract.DEFAULT_READ_TIMEOUT_MS)
        assertTrue(MailContract.METADATA_CALL_TIMEOUT_MS < MailContract.BODY_CALL_TIMEOUT_MS)
        assertTrue(MailContract.BODY_CALL_TIMEOUT_MS < MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS)
        assertTrue(MailContract.MAX_TRIGGER_SUBSCRIBERS <= MailContract.MAX_TRIGGERS)
        assertTrue(MailContract.MIN_TRIGGER_INTERVAL_MS < MailContract.MIN_POLL_INTERVAL_MS)
    }

    @Test
    fun callTimeoutFollowsTheOperationClass() {
        assertEquals(MailContract.METADATA_CALL_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_FOLDERS_LIST))
        assertEquals(MailContract.METADATA_CALL_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_SESSION_TEST, 1L shl 40))
        assertEquals(MailContract.BODY_CALL_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_MESSAGES_GET))
        assertEquals(MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_ATTACHMENTS_DOWNLOAD))
        assertEquals(MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_MAIL_SEND, 0L))
        assertEquals(MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS, MailContract.callTimeoutMs(MailContract.OP_MESSAGES_RAW, 1024L))
        // 200 MiB at 64 KiB/s is 3200 s, above the five minute floor.
        assertEquals(3_200_000L, MailContract.callTimeoutMs(MailContract.OP_ATTACHMENTS_DOWNLOAD, MailContract.MAX_ATTACHMENT_BYTES))
        assertEquals(MailContract.TRANSFER_CALL_MIN_TIMEOUT_MS, MailContract.callTimeoutMs("custom.op"))
    }

    private fun constants(prefix: String): Map<String, String> = constantsOf(MailContract::class.java, prefix)

    private fun constantsOf(type: Class<*>, prefix: String): Map<String, String> {
        return type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith(prefix) }
            .filter { it.name != "INSTANCE" }
            .associate { field -> field.name to (field.apply { isAccessible = true }.get(null) as String) }
    }
}
