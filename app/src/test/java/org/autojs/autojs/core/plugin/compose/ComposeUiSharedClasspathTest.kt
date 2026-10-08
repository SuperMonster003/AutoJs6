package org.autojs.autojs.core.plugin.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Regression guard for Q1(b): release and inrt must use the same proven parent classpath. */
class ComposeUiSharedClasspathTest {
    private val lock = File(requireNotNull(System.getProperty("compose.host.shared.lock")))
        .readLines().filter { it.isNotBlank() && !it.startsWith('#') }
    private val snapshots = File(requireNotNull(System.getProperty("compose.host.shared.snapshot")))
        .readLines().filter(String::isNotBlank).groupBy({ it.substringBefore('|') }, { it.substringAfter('|') })

    @Test fun allProductionVariantsKeepTheVerifiedDependencyFingerprint() {
        assertEquals(setOf("appDebug", "appRelease", "inrtDebug", "inrtRelease"), snapshots.keys)
        assertEquals(lock.size, lock.map { it.substringBefore('=') }.toSet().size)
        val lockedIds = lock.map { it.substringBefore('=') }.toSet()
        val expected = fingerprint(lock)
        // P0.2 plugin snapshot: update both repositories deliberately when the shared ABI changes.
        assertEquals("f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576", expected)
        snapshots.forEach { (variant, dependencies) ->
            val actual = dependencies.filter { it.substringBefore('=') in lockedIds }
            assertEquals("$variant must supply every shared component", lock.size, actual.size)
            assertEquals("$variant drifted from the plugin's shared fingerprint", expected, fingerprint(actual))
        }
    }

    @Test fun productionClasspathsContainNoComposeImplementation() {
        snapshots.forEach { (variant, dependencies) ->
            assertFalse("$variant bundles a Compose component", dependencies.any { it.startsWith("androidx.compose") })
        }
    }

    @Test fun productionSourcesUseTheFrozenContractAndNoTemporaryActivity() {
        val app = File(requireNotNull(System.getProperty("compose.host.project")), "app")
        listOf("main", "debug", "release", "app", "inrt").forEach { sourceSet ->
            val source = File(app, "src/$sourceSet")
            val violations = source.walkTopDown().filter {
                it.isFile && (it.extension == "kt" || it.extension == "java" || it.name == "AndroidManifest.xml")
            }.filter {
                val text = it.readText()
                text.contains("org.autojs.plugin.compose.api.spike") || text.contains("ComposeSpikeActivity")
            }.map { it.relativeTo(app).path }.toList()
            assertTrue("Temporary P0 entry leaked into $sourceSet: $violations", violations.isEmpty())
        }
        assertFalse(File(app, "src/androidTest/AndroidManifest.xml").readText().contains("ComposeSpikeActivity"))
        assertTrue(File(app, "src/main/java/org/autojs/autojs/core/plugin/compose/ComposeSession.kt").isFile)
        assertTrue(File(app, "src/androidTest/java/org/autojs/autojs/core/plugin/compose/ComposeUiLoaderTest.kt").isFile)
    }

    private fun fingerprint(records: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(records.sortedBy { it.substringBefore('=') }.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
