package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ComposeUiContractTest {
    @Test fun frozenV1LiteralsInterfacesAndCatalogMatchReviewedSnapshot() {
        val actual = ContractSnapshot.render()
        File(requireNotNull(System.getProperty("compose.api.reportDir")), "v1-actual.txt").apply { parentFile.mkdirs(); writeText(actual) }
        val expected = javaClass.getResourceAsStream("/compose-ui-v1.snapshot")?.bufferedReader()?.use { it.readText().replace("\r\n", "\n") }
            ?: "Missing reviewed V1 snapshot"
        assertEquals(expected, actual)
    }
    @Test fun versionGateRejectsTheSpikeAndFutureVersions() {
        assertTrue(ComposeUiContract.supports(1))
        listOf(-1, 0, 2, Int.MAX_VALUE).forEach { assertFalse(ComposeUiContract.supports(it)) }
        assertEquals(30, ComponentCatalog.V1.components.size)
        assertEquals(20, ComponentCatalog.V1.modifiers.size)
    }
    @Test fun retryableErrorsAreExactlyThePluginSelectionFailures() {
        val errors = ComposeUiErrorCodes::class.java.declaredFields.filter { it.type == String::class.java }.map { it.get(null) as String }
        assertEquals(18, errors.size)
        errors.forEach { assertEquals(it.startsWith("PLUGIN_"), ComposeUiContractException(it).retryable) }
    }
}
