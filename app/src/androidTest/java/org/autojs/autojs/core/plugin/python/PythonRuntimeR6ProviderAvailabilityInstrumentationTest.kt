package org.autojs.autojs.core.plugin.python

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.script.PythonFileSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Production-client availability probe used between phase-wise package lifecycle mutations.
 *
 * Package update/disable/uninstall is controlled by the exact-serial runner, not by this test.
 * The test contains no shell access and cannot mutate package state.
 */
class PythonRuntimeR6ProviderAvailabilityInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun productionClientMatchesExplicitProviderAvailabilityPhase() {
        val expectedName = InstrumentationRegistry.getArguments().getString(ARG_EXPECTED).orEmpty()
        assumeTrue(
            "R6 provider availability probe requires -e $ARG_EXPECTED MISSING|DISABLED|AVAILABLE",
            expectedName in enumValues<Expected>().map(Expected::name),
        )
        val expected = Expected.valueOf(expectedName)
        assertEquals(HOST_PACKAGE, context.packageName)

        val sourceFile = File(
            context.cacheDir,
            "python-r6-provider-availability-${UUID.randomUUID()}.py",
        )
        val token = "R6_PROVIDER_${UUID.randomUUID()}"
        sourceFile.writeText("print(\"$token\", flush=True)\n", Charsets.UTF_8)
        try {
            val source = PythonFileSource(sourceFile)
            val execution = PythonRuntimeClient(
                context,
                AutoJs.instance.globalConsole,
            ).createExecution(source)
            val outcome = runCatching { execution.run() }

            when (expected) {
                Expected.MISSING -> assertFailure(outcome.exceptionOrNull(), PythonRuntimeHostError.PLUGIN_MISSING)
                Expected.DISABLED -> assertFailure(outcome.exceptionOrNull(), PythonRuntimeHostError.PLUGIN_DISABLED)
                Expected.AVAILABLE -> {
                    val result = outcome.getOrThrow()
                    assertEquals("Recovered provider returned a non-zero exit code", 0, result.exitCode)
                    assertTrue("Recovered provider did not account for stdout", result.stdoutBytes > 0L)
                    assertEquals("Recovered provider unexpectedly emitted stderr", 0L, result.stderrBytes)
                }
            }
        } finally {
            check(!sourceFile.exists() || sourceFile.delete()) {
                "Unable to remove R6 provider availability source: $sourceFile"
            }
        }
    }

    private fun assertFailure(error: Throwable?, expected: PythonRuntimeHostError) {
        assertNotNull("Expected ${expected.stableCode}, but execution succeeded", error)
        val hostError = error as? PythonRuntimeHostException
            ?: throw AssertionError(
                "Expected PythonRuntimeHostException, actual=${error?.javaClass?.name}",
            ).apply { error?.let(::initCause) }
        assertEquals("Stable provider availability error drifted", expected, hostError.error)
        assertTrue(
            "Stable provider availability code is absent from the exception message",
            hostError.message.orEmpty().contains(expected.stableCode),
        )
    }

    private enum class Expected {
        MISSING,
        DISABLED,
        AVAILABLE,
    }

    private companion object {
        const val ARG_EXPECTED = "autojs.python.r6.providerAvailability.expected"
        const val HOST_PACKAGE = "org.autojs.autojs6"
    }
}
