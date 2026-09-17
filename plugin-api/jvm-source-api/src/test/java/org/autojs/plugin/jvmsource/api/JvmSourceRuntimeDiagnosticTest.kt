package org.autojs.plugin.jvmsource.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmSourceRuntimeDiagnosticTest {
    @Test
    fun protocol14RoundTripsTheRequiredRuntimeExceptionClass() {
        val diagnostic = runtimeDiagnostic("java.lang.IllegalStateException")

        assertEquals(
            diagnostic,
            JvmSourceCodec.decodeDiagnostic(JvmSourceCodec.encodeDiagnostic(diagnostic)),
        )
        JvmSourceValidation.validateDiagnosticAgainst(diagnostic, JvmSourceFixtures.request())
    }

    @Test
    fun protocol13DiagnosticWithoutTheNewFieldRemainsDecodable() {
        val legacy = runtimeDiagnostic(null)

        val decoded = JvmSourceCodec.decodeDiagnostic(JvmSourceCodec.encodeDiagnostic(legacy))

        assertEquals(legacy, decoded)
        assertNull(decoded.runtimeExceptionClassName)
        JvmSourceValidation.validateDiagnosticAgainst(
            decoded,
            JvmSourceFixtures.request().copy(protocolVersion = JvmProtocolVersion(1, 3)),
        )
    }

    @Test
    fun protocol13ReaderRejectsTheProtocol14RequiredField() {
        val encoded = JvmSourceCodec.encodeDiagnostic(
            runtimeDiagnostic("java.lang.IllegalStateException"),
        )

        val failure = assertThrows(TaggedWireException::class.java) {
            TaggedWireDocument.decode(encoded).rejectUnknownRequiredFields((1..7).toSet())
        }

        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, failure.error)
    }

    @Test
    fun acceptsOnlyThePublicRuntimeExceptionClassProfile() {
        listOf(
            "java.lang.IllegalStateException",
            "javax.net.ssl.SSLException",
            JvmSourceContract.USER_RUNTIME_EXCEPTION_CLASS_NAME,
        ).forEach { className ->
            JvmSourceValidation.validateDiagnosticAgainst(
                runtimeDiagnostic(className),
                JvmSourceFixtures.request(),
            )
        }

        listOf(
            "org.example.SecretFailure",
            "android.os.DeadObjectException",
            "kotlin.KotlinNullPointerException",
            "java.lang.IllegalStateException[]",
        ).forEach { className ->
            assertEquals(
                JvmSourceContractViolation.INVALID_VALUE,
                assertThrows(JvmSourceContractException::class.java) {
                    JvmSourceValidation.validateDiagnostic(runtimeDiagnostic(className))
                }.violation,
            )
        }
    }

    @Test
    fun bindsTheRuntimeClassToCodeLocationAndNegotiatedProtocol() {
        val protocol14Request = JvmSourceFixtures.request()
        val protocol13Request = protocol14Request.copy(protocolVersion = JvmProtocolVersion(1, 3))

        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateDiagnosticAgainst(runtimeDiagnostic(null), protocol14Request)
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateDiagnosticAgainst(
                    runtimeDiagnostic("java.lang.IllegalStateException"),
                    protocol13Request,
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateDiagnostic(
                    runtimeDiagnostic("java.lang.IllegalStateException").copy(
                        code = "ECJ_ERROR",
                    ),
                )
            }.violation,
        )
        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                JvmSourceValidation.validateDiagnostic(
                    runtimeDiagnostic("java.lang.IllegalStateException").copy(
                        sourceFileName = null,
                    ),
                )
            }.violation,
        )
    }

    private fun runtimeDiagnostic(exceptionClassName: String?) = JvmSourceDiagnostic(
        requestId = JvmSourceFixtures.requestId,
        severity = JvmDiagnosticSeverity.ERROR,
        code = "JAVA_RUNTIME_EXCEPTION",
        message = "Java execution failed",
        sourceFileName = "Main.java",
        line = 4,
        column = 1,
        runtimeExceptionClassName = exceptionClassName,
    )
}
