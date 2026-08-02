package org.autojs.plugin.dexcompiler.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DexCompilerCompatibilityTest {

    @Test
    fun negotiatesHighestVersionInTheIntersection() {
        val provider = DexCompilerFixtures.info(
            min = DexProtocolVersion(1, 0),
            max = DexProtocolVersion(1, 4),
        )

        assertEquals(
            DexProtocolVersion(1, 2),
            DexCompilerNegotiation.negotiate(
                provider,
                hostMin = DexProtocolVersion(1, 1),
                hostMax = DexProtocolVersion(1, 2),
            ),
        )
    }

    @Test
    fun returnsNoVersionForDisjointProtocolRanges() {
        val provider = DexCompilerFixtures.info(
            min = DexProtocolVersion(2, 0),
            max = DexProtocolVersion(2, 1),
        )

        assertNull(DexCompilerNegotiation.negotiate(provider))
        assertEquals(
            DexCompilerContractViolation.PROTOCOL_INCOMPATIBLE,
            assertThrows(DexCompilerContractException::class.java) {
                DexCompilerNegotiation.requireNegotiated(provider)
            }.violation,
        )
    }

    @Test
    fun acceptsNewerSchemaMinorWithUnknownOptionalField() {
        val encoded = rawInfo(schemaMinor = 9)
            .string(99, "future optional value")
            .encode()

        assertEquals("autojs6-d8", DexCompilerCodec.decodeInfo(encoded).providerId)
    }

    @Test
    fun rejectsUnknownRequiredField() {
        val encoded = rawInfo()
            .string(99, "future required value", requiredForReader = true)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) {
            DexCompilerCodec.decodeInfo(encoded)
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun rejectsDifferentSchemaMajor() {
        val encoded = rawInfo(schemaMajor = 2).encode()

        val error = assertThrows(TaggedWireException::class.java) {
            DexCompilerCodec.decodeInfo(encoded)
        }
        assertEquals(TaggedWireError.SCHEMA_MISMATCH, error.error)
    }

    @Test
    fun rejectsUnknownEnumValue() {
        val encoded = rawInfo(abiMode = 999).encode()

        val error = assertThrows(DexCompilerContractException::class.java) {
            DexCompilerCodec.decodeInfo(encoded)
        }
        assertEquals(DexCompilerContractViolation.UNKNOWN_ENUM, error.violation)
    }

    @Test
    fun validatesRequestAgainstNegotiatedCapabilities() {
        DexCompilerValidation.validateRequestAgainst(
            DexCompilerFixtures.request(),
            DexCompilerFixtures.capabilities(),
            DexProtocolVersion(1, 0),
        )

        val error = assertThrows(DexCompilerContractException::class.java) {
            DexCompilerValidation.validateRequestAgainst(
                DexCompilerFixtures.request(minApi = 23),
                DexCompilerFixtures.capabilities(),
                DexProtocolVersion(1, 0),
            )
        }
        assertEquals(DexCompilerContractViolation.CAPABILITY_INCOMPATIBLE, error.violation)
    }

    private fun rawInfo(
        schemaMajor: Int = DexCompilerContract.SCHEMA_MAJOR,
        schemaMinor: Int = DexCompilerContract.SCHEMA_MINOR,
        abiMode: Int = DexCompilerAbiMode.ANY.wireCode,
    ): TaggedWireWriter = TaggedWireWriter(
        DexCompilerContract.SCHEMA_COMPILER_INFO,
        schemaMajor,
        schemaMinor,
    )
        .int32(1, 1, requiredForReader = true)
        .int32(2, 0, requiredForReader = true)
        .int32(3, 1, requiredForReader = true)
        .int32(4, 0, requiredForReader = true)
        .string(5, "autojs6-d8", requiredForReader = true)
        .string(6, "1.0.0", requiredForReader = true)
        .int64(7, 4L, requiredForReader = true)
        .int32(10, abiMode, requiredForReader = true)
}
