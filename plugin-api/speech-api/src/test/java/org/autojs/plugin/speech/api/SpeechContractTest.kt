package org.autojs.plugin.speech.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Snapshot of every literal of contract version 1 (3-Macaw Speech roadmap D21, D27, D28, appendix C.1). The plugin, the
 * host client and the script API repeat these values, so a change here is a protocol change: append new values, never
 * edit or remove frozen ones.
 *
 * zh-CN: 契约版本 1 全部字面值的快照 (3-Macaw Speech 路线图 D21, D27, D28, 附录 C.1). 插件, 宿主客户端与脚本 API 都重复使用这些值,
 * 因此此处的变化即协议变化: 只能追加, 不能修改或删除已冻结的值.
 */
class SpeechContractTest {

    @Test
    fun discoveryAndIdentityAreFrozen() {
        assertEquals(PluginActions.INFO, SpeechPluginActions.INFO)
        assertEquals("org.autojs.plugin.SPEECH", SpeechPluginActions.SPEECH)
        assertEquals("speech", SpeechPluginActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", SpeechPluginActions.PLUGIN_PERMISSION)

        assertEquals("three-macaw-speech", SpeechPluginIds.OFFICIAL_PLUGIN_ID)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.macaw.speech", SpeechPluginIds.OFFICIAL_PACKAGE_NAME)
        assertEquals("speech", SpeechPluginIds.ENGINE)
        assertEquals("speech", SpeechPluginIds.CATEGORY)
        assertEquals(SpeechPluginActions.SERVICE_CATEGORY, SpeechPluginIds.CATEGORY)
        assertEquals("default", SpeechPluginIds.VARIANT_DEFAULT)
        // Roadmap D28: above the roadmap baseline 5448 (host 33a1514b74); P6.4 raises it to the final 6.8.0 build.
        assertEquals(5451L, SpeechPluginIds.REQUIRED_HOST_VERSION_CODE)
        assertTrue(SpeechPluginIds.REQUIRED_HOST_VERSION_CODE > 5448L)
        assertEquals("org.autojs.plugin.speech.api.ISpeechPlugin", ISpeechPlugin.DESCRIPTOR)
        assertEquals("org.autojs.plugin.speech.api.ISpeechSession", ISpeechSession.DESCRIPTOR)
        assertEquals("org.autojs.plugin.speech.api.ISpeechSessionCallback", ISpeechSessionCallback.DESCRIPTOR)
        assertEquals("org.autojs.plugin.speech.api.ISpeechWakeCallback", ISpeechWakeCallback.DESCRIPTOR)
        assertEquals("org.autojs.plugin.speech.api.ISpeechProgressCallback", ISpeechProgressCallback.DESCRIPTOR)
    }

    @Test
    fun contractVersionAndCapabilityKeysAreFrozen() {
        assertEquals(1, SpeechContract.CONTRACT_VERSION)
        assertEquals(1, SpeechContract.MIN_CONTRACT_VERSION)
        assertEquals(1, SpeechContract.MAX_CONTRACT_VERSION)
        assertTrue(SpeechContract.supportsContractVersion(1))
        assertFalse(SpeechContract.supportsContractVersion(0))
        assertFalse(SpeechContract.supportsContractVersion(2))

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, SpeechCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals(
            listOf(
                "speechContractVersion", "speechFeatures", "speechCatalogVersion", "speechMaxPcmSessions", "speechTtsEnginePackage",
                "speechEngineName", "speechEngineVersion", "speechMicrophoneGranted",
            ),
            listOf(
                SpeechCapabilityKeys.CONTRACT_VERSION, SpeechCapabilityKeys.FEATURES, SpeechCapabilityKeys.CATALOG_VERSION,
                SpeechCapabilityKeys.MAX_PCM_SESSIONS, SpeechCapabilityKeys.TTS_ENGINE_PACKAGE, SpeechCapabilityKeys.ENGINE_NAME,
                SpeechCapabilityKeys.ENGINE_VERSION, SpeechCapabilityKeys.MICROPHONE_GRANTED,
            ),
        )
        val keys = constantsOf(SpeechCapabilityKeys::class.java, "")
        assertEquals(keys.size, keys.values.toSet().size)
    }

    @Test
    fun bundleKeysAreUniqueAndFrozen() {
        assertEquals("contractVersion", SpeechContract.KEY_CONTRACT_VERSION)
        assertEquals("errorCode", SpeechContract.KEY_ERROR_CODE)
        assertEquals("errorMessage", SpeechContract.KEY_ERROR_MESSAGE)

        val options = constantsOf(SpeechOptionKeys::class.java, "")
        assertEquals(options.size, options.values.toSet().size)
        assertEquals(
            listOf(
                "acceptLicense", "boost", "displayName", "format", "hotwords", "installed", "keyword", "keywords", "kind", "language",
                "languages", "maxDurationMillis", "minIntervalMillis", "minSilenceMillis", "minSpeechMillis", "model", "modelType", "name",
                "punctuation", "sampleRate", "silenceEndMillis", "source", "speaker", "speed", "text", "thenListen", "threshold",
                "timeoutMillis", "vad", "vadThreshold",
            ),
            options.values.sorted(),
        )

        val results = constantsOf(SpeechResultKeys::class.java, "")
        assertEquals(results.size, results.values.toSet().size)
        assertEquals(
            listOf(
                "active", "catalogVersion", "durationMillis", "emotion", "endMillis", "event", "keyword", "language", "model", "modelId",
                "models", "reason", "removed", "round", "sampleRate", "score", "segmentIndex", "segments", "sessionId", "sizeBytes",
                "speakerId", "startMillis", "state", "tags", "text", "timestampMillis", "updated", "warnings",
            ),
            results.values.sorted(),
        )

        val models = constantsOf(SpeechModelKeys::class.java, "")
        assertEquals(models.size, models.values.toSet().size)
        assertEquals(
            listOf(
                "commercialUse", "id", "installed", "installedBytes", "isDefault", "kind", "languages", "license", "modelType", "name",
                "noticeUrl", "recommended", "sampleRate", "sizeBytes", "source", "spdx", "speakers", "version",
            ),
            models.values.sorted(),
        )
    }

    @Test
    fun closedValueSetsAreFrozen() {
        assertEquals(setOf("ready", "partial", "final", "endpoint", "vad", "wake", "warning", "error", "end"), SpeechContract.EVENTS)
        assertEquals(setOf("stopped", "timeout", "maxDuration", "cancelled", "error"), SpeechContract.REASONS)
        assertEquals(setOf("pending", "active", "ended"), SpeechContract.STATES)
        assertEquals(setOf("mic", "external"), SpeechContract.SOURCES)
        assertEquals(setOf("asr", "tts", "kws", "vad"), SpeechContract.KINDS)
        assertEquals(setOf("catalog", "imported"), SpeechContract.MODEL_SOURCES)
        assertEquals(setOf("asr", "asrStreaming", "vad", "kws", "tts", "ttsSystemEngine", "models"), SpeechContract.FEATURES)
        assertEquals(setOf("wav"), SpeechContract.FORMATS)
        assertEquals(constants("EVENT_").values.toSet(), SpeechContract.EVENTS)
        assertEquals(constants("REASON_").values.toSet(), SpeechContract.REASONS)
        assertEquals(constants("STATE_").values.toSet(), SpeechContract.STATES)
        assertEquals(constants("SOURCE_").values.toSet(), SpeechContract.SOURCES)
        assertEquals(constants("KIND_").values.toSet(), SpeechContract.KINDS)
        assertEquals(constants("MODEL_SOURCE_").values.toSet(), SpeechContract.MODEL_SOURCES)
        assertEquals(constants("FEATURE_").values.toSet(), SpeechContract.FEATURES)
        assertEquals(constants("FORMAT_").values.toSet(), SpeechContract.FORMATS)
    }

    @Test
    fun limitsAreFrozenAndLayered() {
        assertEquals(1, SpeechLimits.MAX_MIC_SESSIONS_PER_TOKEN)
        assertEquals(4, SpeechLimits.MAX_PCM_SESSIONS_PER_TOKEN)
        assertEquals(1_048_576, SpeechLimits.MAX_FEED_BYTES)
        assertEquals(209_715_200L, SpeechLimits.MAX_AUDIO_FILE_BYTES)
        assertEquals(1_800_000L, SpeechLimits.MAX_AUDIO_MILLIS)
        assertEquals(8000, SpeechLimits.MAX_SYNTH_TEXT_CHARS)
        assertEquals(104_857_600L, SpeechLimits.MAX_SYNTH_OUTPUT_BYTES)
        assertEquals(50, SpeechLimits.MAX_KEYWORDS)
        assertEquals(200, SpeechLimits.MAX_HOTWORDS)
        assertEquals(128, SpeechLimits.MAX_TOKEN_CHARS)
        assertEquals(16_000, SpeechLimits.EXTERNAL_SAMPLE_RATE)
        assertEquals(300_000L, SpeechLimits.DEFAULT_MAX_DURATION_MILLIS)
        assertEquals(1200L, SpeechLimits.DEFAULT_SILENCE_END_MILLIS)
        assertEquals(15_000L, SpeechLimits.DEFAULT_TIMEOUT_MILLIS)
        assertEquals(1500L, SpeechLimits.DEFAULT_MIN_INTERVAL_MILLIS)
        assertEquals(0.5, SpeechLimits.MIN_SPEED, 0.0)
        assertEquals(2.0, SpeechLimits.MAX_SPEED, 0.0)
        assertEquals(4096, SpeechLimits.MAX_ERROR_MESSAGE_BYTES)
        assertEquals(65_536, SpeechLimits.MAX_OPTIONS_BYTES)
        assertEquals(262_144, SpeechLimits.MAX_EVENT_BYTES)
        assertEquals(512, SpeechLimits.MAX_MODELS)
        assertEquals(30_000L, SpeechContract.CALL_TIMEOUT_MS)
        assertEquals(600_000L, SpeechContract.FILE_CALL_TIMEOUT_MS)
        assertEquals(600_000L, SpeechContract.SYNTHESIS_TIMEOUT_MS)
        assertEquals(120_000L, SpeechContract.CATALOG_TIMEOUT_MS)

        // One feed call and one event stay well inside the 1 MiB Binder buffer shared by the process.
        assertTrue(SpeechLimits.MAX_FEED_BYTES <= 1024 * 1024)
        assertTrue(SpeechLimits.MAX_EVENT_BYTES <= 256 * 1024)
        assertTrue(SpeechLimits.MAX_ERROR_MESSAGE_BYTES < SpeechLimits.MAX_OPTIONS_BYTES)
        assertTrue(SpeechLimits.MAX_OPTIONS_BYTES < SpeechLimits.MAX_EVENT_BYTES)
        assertTrue(SpeechLimits.DEFAULT_MAX_DURATION_MILLIS <= SpeechLimits.MAX_AUDIO_MILLIS)
        assertTrue(SpeechLimits.MIN_SPEED < 1.0 && 1.0 < SpeechLimits.MAX_SPEED)
        assertTrue(SpeechContract.CALL_TIMEOUT_MS < SpeechContract.FILE_CALL_TIMEOUT_MS)
        // A whole 30 minute file fits the file call timeout only with a real-time factor below 1 (roadmap P3.5 measures it).
        assertTrue(SpeechContract.FILE_CALL_TIMEOUT_MS >= SpeechLimits.MAX_AUDIO_MILLIS / 3)
    }

    @Test
    fun errorCodesAreFrozen() {
        assertEquals(
            listOf(
                "PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE", "PERMISSION_DENIED", "MODEL_MISSING",
                "MODEL_DOWNLOAD_FAILED", "MODEL_CHECKSUM_MISMATCH", "MODEL_LICENSE_NOT_ACCEPTED", "INVALID_OPTIONS",
                "UNSUPPORTED_FORMAT", "LIMIT_EXCEEDED", "SESSION_BUSY", "SESSION_CLOSED", "SESSION_CANCELLED", "ENGINE_FAILED",
                "IO_FAILED", "TIMEOUT", "INTERNAL",
            ),
            SpeechErrorCodes.ALL,
        )
        assertEquals(SpeechErrorCodes.ALL.size, SpeechErrorCodes.ALL.toSet().size)
        assertEquals(constantsOf(SpeechErrorCodes::class.java, "").values.toSet(), SpeechErrorCodes.ALL.toSet())
        SpeechErrorCodes.ALL.forEach { code -> assertTrue(code, SpeechErrorCodes.isKnown(code)) }
        assertFalse(SpeechErrorCodes.isKnown(null))
        assertFalse(SpeechErrorCodes.isKnown("NOT_A_CODE"))
        assertEquals(setOf("PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_INCOMPATIBLE"), SpeechErrorCodes.HOST_ONLY)
    }

    @Test
    fun exceptionMessagesRoundTripThroughEncodeAndDecode() {
        assertEquals("MODEL_MISSING: asr", SpeechErrorCodes.encode(SpeechErrorCodes.MODEL_MISSING, "asr"))
        assertEquals("SESSION_CLOSED", SpeechErrorCodes.encode(SpeechErrorCodes.SESSION_CLOSED, "  "))
        assertEquals("INVALID_OPTIONS" to "a: b", SpeechErrorCodes.decode(" INVALID_OPTIONS: a: b "))
        assertEquals("SESSION_BUSY" to "", SpeechErrorCodes.decode("SESSION_BUSY"))
        assertNull(SpeechErrorCodes.decode(null))
        assertNull(SpeechErrorCodes.decode("Something went wrong"))
        assertNull(SpeechErrorCodes.decode("SESSION_CLOSED_LATER: x"))
        assertTrue(runCatching { SpeechErrorCodes.encode("NOT_A_CODE") }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun constants(prefix: String): Map<String, String> = constantsOf(SpeechContract::class.java, prefix)

    private fun constantsOf(type: Class<*>, prefix: String): Map<String, String> {
        return type.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith(prefix) }
            .filter { it.name != "INSTANCE" && it.name != "SEPARATOR" }
            .associate { field -> field.name to (field.apply { isAccessible = true }.get(null) as String) }
    }
}
