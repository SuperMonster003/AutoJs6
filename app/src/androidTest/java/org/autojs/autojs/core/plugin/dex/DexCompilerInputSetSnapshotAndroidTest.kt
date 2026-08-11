package org.autojs.autojs.core.plugin.dex

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executor

class DexCompilerInputSetSnapshotAndroidTest {

    @Test
    fun classpathSourceCloseFailureRemovesEveryUnownedInputArtifact() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedNoBackup = File(
            targetContext.cacheDir,
            "dex-input-set-close-${UUID.randomUUID()}",
        )
        assertTrue(isolatedNoBackup.mkdirs())
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = isolatedNoBackup
        }

        try {
            val store = AndroidDexCompilerArtifactStore(context, Executor(Runnable::run))

            val error = assertThrows(IOException::class.java) {
                store.snapshotInputSet(
                    programInput = { ByteArrayInputStream("program".toByteArray()) },
                    classpathInputs = listOf({ CloseThrowingInputStream("classpath".toByteArray()) }),
                )
            }

            assertEquals("source close failed", error.message)
            val transactionRoot = File(isolatedNoBackup, "dex-compiler-experimental")
            val leftovers = transactionRoot.listFiles().orEmpty().filter { file ->
                file.name.startsWith("input-") || file.extension == "bundle"
            }
            assertTrue("Input-set rollback left private input artifacts: $leftovers", leftovers.isEmpty())
        } finally {
            isolatedNoBackup.deleteRecursively()
        }
    }

    @Test
    fun cancellationDuringFrozenBundleCopyRemovesEveryInputArtifact() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedNoBackup = File(
            targetContext.cacheDir,
            "dex-input-set-cancel-${UUID.randomUUID()}",
        )
        assertTrue(isolatedNoBackup.mkdirs())
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = isolatedNoBackup
        }
        val transactionRoot = File(isolatedNoBackup, "dex-compiler-experimental")
        var cancellationReachedBundlePayload = false

        try {
            val store = AndroidDexCompilerArtifactStore(context, Executor(Runnable::run))

            assertThrows(DexCompilerStreamCancelledException::class.java) {
                store.snapshotInputSet(
                    programInput = { ByteArrayInputStream(ByteArray(32 * 1024) { 1 }) },
                    classpathInputs = listOf({ ByteArrayInputStream(ByteArray(32 * 1024) { 2 }) }),
                    isCancelled = {
                        val bundleHasFraming = transactionRoot.listFiles().orEmpty().any { file ->
                            file.extension == "bundle" && file.length() > 0L
                        }
                        if (bundleHasFraming) cancellationReachedBundlePayload = true
                        bundleHasFraming
                    },
                )
            }

            assertTrue("Cancellation did not reach the frozen bundle-copy phase", cancellationReachedBundlePayload)
            val leftovers = transactionRoot.listFiles().orEmpty().filter { file ->
                file.name.startsWith("input-") || file.extension == "bundle"
            }
            assertTrue("Cancelled input-set left private input artifacts: $leftovers", leftovers.isEmpty())
        } finally {
            isolatedNoBackup.deleteRecursively()
        }
    }

    private class CloseThrowingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        override fun close() {
            super.close()
            throw IOException("source close failed")
        }
    }
}
