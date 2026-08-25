package org.autojs.autojs.engine

import org.autojs.plugin.jvmsource.api.JvmSourceContractException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmExecutionArgumentMapperTest {

    @Test
    fun takesADeterministicJsonSnapshotOfExecutionConfigValues() {
        val nested = mutableListOf<Any?>("before", 2)
        val encoded = JvmExecutionArgumentMapper.encode(
            linkedMapOf(
                "z" to nested,
                "a" to true,
            ),
        )
        nested[0] = "after"

        assertEquals("{\"a\":true,\"z\":[\"before\",2]}", encoded)
    }

    @Test
    fun rejectsHostObjectsCyclesAndNonFiniteNumbers() {
        val cycle = arrayListOf<Any?>()
        cycle.add(cycle)
        listOf(
            mapOf("value" to Any()),
            mapOf("value" to cycle),
            mapOf("value" to Double.POSITIVE_INFINITY),
        ).forEach { arguments ->
            assertThrows(JvmSourceContractException::class.java) {
                JvmExecutionArgumentMapper.encode(arguments)
            }
        }
    }
}
