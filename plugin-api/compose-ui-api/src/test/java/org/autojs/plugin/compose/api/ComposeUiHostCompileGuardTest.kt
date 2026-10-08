package org.autojs.plugin.compose.api

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComposeUiHostCompileGuardTest {
    @Test fun hostAndContractCodeNeverReferenceComposeImplementationTypes() {
        val root = File(requireNotNull(System.getProperty("compose.api.root")))
        val sourceRoots = listOf("app/src/main/java", "app/src/debug/java", "plugin-api/compose-ui-api/src/main")
        val imports = Regex("\\bandroidx\\.compose\\b")
        val violations = sourceRoots.flatMap { relative -> File(root, relative).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { imports.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }.toList() }
        assertTrue("Host/contract depends on Compose source types: $violations", violations.isEmpty())
    }
}
