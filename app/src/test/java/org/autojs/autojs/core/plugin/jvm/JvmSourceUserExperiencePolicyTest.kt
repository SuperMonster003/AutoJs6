package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceUserExperiencePolicyTest {
    @Test
    fun discoveryStatesMapToStableNonFallbackUserFailures() {
        val cases = listOf(
            emptyList<JvmSourceServiceCandidate>() to JvmSourceUserFailureCode.PROVIDER_MISSING,
            listOf(candidate(pluginEnabled = false)) to JvmSourceUserFailureCode.PROVIDER_DISABLED,
            listOf(candidate(authorized = false)) to JvmSourceUserFailureCode.PROVIDER_UNTRUSTED,
            listOf(candidate(compilerProcess = "$PACKAGE:other")) to JvmSourceUserFailureCode.PROVIDER_INCOMPATIBLE,
        )

        cases.forEach { (candidates, expected) ->
            val failure = requireNotNull(JvmSourceUserExperiencePolicy.providerAdmission(candidates, COMPONENT))
            assertEquals(expected, failure.code)
            assertFalse(failure.scriptEngineFallbackAllowed)
            assertFalse(failure.message.contains(PACKAGE))
        }
        assertNull(JvmSourceUserExperiencePolicy.providerAdmission(listOf(candidate()), COMPONENT))
    }

    @Test
    fun freshInstallWithoutSelectionDistinguishesMissingDisabledAndSignatureFailure() {
        val cases = listOf(
            emptyList<JvmSourceServiceCandidate>() to JvmSourceUserFailureCode.PROVIDER_MISSING,
            listOf(candidate(pluginEnabled = false)) to JvmSourceUserFailureCode.PROVIDER_DISABLED,
            listOf(candidate(authorized = false)) to JvmSourceUserFailureCode.PROVIDER_UNTRUSTED,
        )

        cases.forEach { (candidates, expected) ->
            val failure = JvmSourceUserExperiencePolicy.providerUnavailableWithoutSelection(candidates)
            assertEquals(expected, failure.code)
            assertFalse(failure.scriptEngineFallbackAllowed)
            assertFalse(failure.message.contains(PACKAGE))
        }
        assertTrue(
            JvmSourceUserExperiencePolicy.providerUnavailableWithoutSelection(emptyList())
                .message.contains("Install"),
        )
        assertTrue(
            JvmSourceUserExperiencePolicy.providerUnavailableWithoutSelection(
                listOf(candidate(pluginEnabled = false)),
            ).message.contains("Enable"),
        )
        assertTrue(
            JvmSourceUserExperiencePolicy.providerUnavailableWithoutSelection(
                listOf(candidate(authorized = false)),
            ).message.contains("signed"),
        )
    }

    @Test
    fun providerTerminalsMapCompileRuntimeBusyTimeoutAndCompatibilityWithoutProviderText() {
        val cases = listOf(
            JvmSourceErrorCode.COMPILATION_FAILED to JvmSourceUserFailureCode.COMPILE_FAILED,
            JvmSourceErrorCode.DEXING_FAILED to JvmSourceUserFailureCode.COMPILE_FAILED,
            JvmSourceErrorCode.EXECUTION_FAILED to JvmSourceUserFailureCode.RUNTIME_FAILED,
            JvmSourceErrorCode.WORKER_DIED to JvmSourceUserFailureCode.RUNTIME_FAILED,
            JvmSourceErrorCode.BUSY to JvmSourceUserFailureCode.PROVIDER_BUSY,
            JvmSourceErrorCode.TIMEOUT to JvmSourceUserFailureCode.TIMEOUT,
            JvmSourceErrorCode.UNSUPPORTED_PROTOCOL to JvmSourceUserFailureCode.PROVIDER_INCOMPATIBLE,
        )

        cases.forEach { (providerCode, userCode) ->
            val providerText = "C:\\private\\classes.dex signer=${"a".repeat(64)} Binder uid=1000 pid=2000"
            val failure = JvmSourceUserExperiencePolicy.providerError(
                JvmSourceError(
                    requestId = REQUEST_ID,
                    code = providerCode,
                    phase = JvmSourceFailurePhase.COMPILATION,
                    message = providerText,
                    retryable = false,
                ),
            )
            assertEquals(userCode, failure.code)
            assertFalse(failure.message.contains(providerText))
            assertFalse(failure.scriptEngineFallbackAllowed)
        }
    }

    @Test
    fun invalidRequestUsesPhaseToDistinguishSourcePolicyFromProtocolShape() {
        listOf(JvmSourceFailurePhase.INPUT, JvmSourceFailurePhase.COMPILATION).forEach { phase ->
            val sourceFailure = JvmSourceUserExperiencePolicy.providerError(error(
                code = JvmSourceErrorCode.INVALID_REQUEST,
                phase = phase,
            ))
            assertEquals(JvmSourceUserFailureCode.COMPILE_FAILED, sourceFailure.code)
            assertEquals(JvmSourceRecoveryAction.FIX_SOURCE, sourceFailure.recoveryAction)
        }

        val negotiationFailure = JvmSourceUserExperiencePolicy.providerError(error(
            code = JvmSourceErrorCode.INVALID_REQUEST,
            phase = JvmSourceFailurePhase.NEGOTIATION,
        ))
        assertEquals(JvmSourceUserFailureCode.PROVIDER_INCOMPATIBLE, negotiationFailure.code)
        assertEquals(JvmSourceRecoveryAction.UPDATE_HOST_OR_PROVIDER, negotiationFailure.recoveryAction)

        val localKotlinFailure = JvmSourceUserExperiencePolicy.sourcePolicyViolation(JvmSourceLanguage.KOTLIN)
        assertEquals(JvmSourceUserFailureCode.COMPILE_FAILED, localKotlinFailure.code)
        assertEquals("Kotlin source does not satisfy the supported source profile", localKotlinFailure.message)
        assertEquals(JvmSourceRecoveryAction.FIX_SOURCE, localKotlinFailure.recoveryAction)
    }

    @Test
    fun artifactIntegrityFailureNeverSuggestsEditingSource() {
        val beforeDispatch = JvmSourceUserExperiencePolicy.providerError(error(
            code = JvmSourceErrorCode.ARTIFACT_INVALID,
            phase = JvmSourceFailurePhase.DEXING,
        ))
        assertEquals(JvmSourceUserFailureCode.HOST_UNAVAILABLE, beforeDispatch.code)
        assertEquals(JvmSourceRecoveryAction.RETRY_LATER, beforeDispatch.recoveryAction)
        assertTrue(beforeDispatch.retryable)

        val inWorker = JvmSourceUserExperiencePolicy.providerError(error(
            code = JvmSourceErrorCode.ARTIFACT_INVALID,
            phase = JvmSourceFailurePhase.EXECUTION,
        ))
        assertEquals(JvmSourceUserFailureCode.RUNTIME_FAILED, inWorker.code)
        assertEquals(JvmSourceRecoveryAction.RETRY_LATER, inWorker.recoveryAction)
        assertTrue(inWorker.retryable)

        listOf(beforeDispatch, inWorker).forEach { failure ->
            assertFalse(failure.recoveryAction == JvmSourceRecoveryAction.FIX_SOURCE)
            assertFalse(failure.message.contains("private"))
        }
    }

    @Test
    fun updateRollbackAndQuarantineRemainDistinctCoarseStates() {
        assertEquals(
            JvmSourceUserFailureCode.PROVIDER_UPDATE_DRIFT,
            JvmSourceUserExperiencePolicy.lifecycleLoss(JvmSourceProviderLifecyclePolicy.Loss.UPDATED).code,
        )
        assertEquals(
            JvmSourceUserFailureCode.PROVIDER_ROLLBACK_REQUIRED,
            JvmSourceUserExperiencePolicy.lifecycleLoss(JvmSourceProviderLifecyclePolicy.Loss.ROLLED_BACK).code,
        )
        val quarantined = JvmSourceUserExperiencePolicy.providerQuarantined()
        assertEquals(JvmSourceUserFailureCode.PROVIDER_QUARANTINED, quarantined.code)
        assertEquals(JvmSourceContainment.QUARANTINED, quarantined.containment)
    }

    @Test
    fun diagnosticProjectionKeepsOnlyStableCodeAndSafeMainLocation() {
        val sensitive = "C:\\private\\Main.java classpath=/data/user/0/x signer=${"a".repeat(64)} " +
            "Binder@123 uid=1000 pid=2000 $PACKAGE/$SERVICE"
        val projected = JvmSourcePublicDiagnosticPolicy.project(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.ERROR,
                code = "ECJ_ERROR",
                message = sensitive,
                sourceFileName = "Main.java",
                line = 7,
                column = 9,
            ),
        )

        assertEquals("ECJ_ERROR", projected.code)
        assertEquals("Main.java", projected.sourceFileName)
        assertEquals(7, projected.line)
        assertEquals(9, projected.column)
        assertEquals("Java compilation error", projected.message)
        assertFalse(projected.toString().contains("private"))
        assertFalse(projected.toString().contains("signer"))
        assertFalse(projected.toString().contains("Binder"))

        val unsafe = JvmSourcePublicDiagnosticPolicy.project(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.WARNING,
                code = "../../raw code",
                message = sensitive,
                sourceFileName = "/data/user/0/x/Main.java",
                line = Int.MAX_VALUE,
                column = 1,
            ),
        )
        assertEquals("COMPILER_DIAGNOSTIC", unsafe.code)
        assertNull(unsafe.sourceFileName)
        assertNull(unsafe.line)
        assertNull(unsafe.column)
        val componentOnly = JvmSourcePublicDiagnosticPolicy.project(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.ERROR,
                code = "ECJ_ERROR",
                message = "component=org.example.provider/.CompilerService",
                sourceFileName = "Main.java",
                line = 1,
                column = 1,
            ),
        )
        assertEquals("Java compilation error", componentOnly.message)
        assertEquals(
            "COMPILER_DIAGNOSTIC",
            JvmSourcePublicDiagnosticPolicy.project(
                JvmSourceDiagnostic(
                    requestId = REQUEST_ID,
                    severity = JvmDiagnosticSeverity.ERROR,
                    code = "PID_2000",
                    message = sensitive,
                    sourceFileName = "Main.java",
                    line = 1,
                    column = 1,
                ),
            ).code,
        )

        val sanitizedWireShape = JvmSourcePublicDiagnosticPolicy.sanitize(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.ERROR,
                code = "ECJ_ERROR",
                message = sensitive,
                sourceFileName = "/data/user/0/x/Main.java",
                line = 7,
                column = 9,
            ),
        )
        assertEquals(REQUEST_ID, sanitizedWireShape.requestId)
        assertEquals("Java compilation error", sanitizedWireShape.message)
        assertNull(sanitizedWireShape.sourceFileName)
        assertFalse(sanitizedWireShape.toString().contains("/data/user"))
    }

    @Test
    fun kotlinDiagnosticsAndTerminalFailuresRemainLanguageSpecific() {
        val projected = JvmSourcePublicDiagnosticPolicy.project(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.ERROR,
                code = "KOTLIN_ERROR",
                message = "Unresolved reference 'println2'.",
                sourceFileName = "Main.kt",
                line = 4,
                column = 13,
            ),
        )
        assertEquals("KOTLIN_ERROR", projected.code)
        assertEquals("Main.kt", projected.sourceFileName)
        assertEquals("Unresolved reference 'println2'.", projected.message)

        val redacted = JvmSourcePublicDiagnosticPolicy.project(
            JvmSourceDiagnostic(
                requestId = REQUEST_ID,
                severity = JvmDiagnosticSeverity.ERROR,
                code = "KOTLIN_ERROR",
                message = "/data/user/0/private/Main.kt",
                sourceFileName = "Main.kt",
                line = 1,
                column = 1,
            ),
        )
        assertEquals("Kotlin compilation error", redacted.message)

        val failure = JvmSourceUserExperiencePolicy.providerError(
            error(JvmSourceErrorCode.COMPILATION_FAILED, JvmSourceFailurePhase.COMPILATION),
            JvmSourceLanguage.KOTLIN,
        )
        assertEquals(JvmSourceUserFailureCode.COMPILE_FAILED, failure.code)
        assertEquals("Kotlin source compilation failed", failure.message)
    }

    @Test
    fun userFacingExceptionDropsInternalCauseChain() {
        val publicFailure = JvmSourceUserFacingException(JvmSourceUserExperiencePolicy.hostUnavailable())

        assertNull(publicFailure.cause)
        assertTrue(publicFailure.message.orEmpty().contains("JVM_SOURCE_HOST_UNAVAILABLE"))
        assertFalse(publicFailure.message.orEmpty().contains(PACKAGE))
        assertFalse(publicFailure.message.orEmpty().contains("private"))
    }

    private fun candidate(
        pluginEnabled: Boolean = true,
        authorized: Boolean = true,
        compilerProcess: String = "$PACKAGE:compiler",
    ) = JvmSourceServiceCandidate(
        component = COMPONENT,
        declaredAction = JvmSourceContract.SERVICE_ACTION,
        exported = true,
        applicationEnabled = true,
        serviceEnabled = true,
        pluginEnabled = pluginEnabled,
        providerAuthorized = authorized,
        requiredPermission = JvmSourceContract.PLUGIN_PERMISSION,
        compilerProcessName = compilerProcess,
        packageUid = 10_001,
        versionCode = 7,
        lastUpdateTime = 123,
        signerSha256 = setOf("a".repeat(64)),
    )

    private fun error(
        code: JvmSourceErrorCode,
        phase: JvmSourceFailurePhase,
    ) = JvmSourceError(
        requestId = REQUEST_ID,
        code = code,
        phase = phase,
        message = "C:\\private\\classes.dex Binder uid=1000 pid=2000",
        retryable = false,
    )

    private companion object {
        const val PACKAGE = "org.example.jvmprovider"
        const val SERVICE = "org.example.jvmprovider.CompilerService"
        val COMPONENT = JvmSourceComponent(PACKAGE, SERVICE)
        val REQUEST_ID = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT))
    }
}
