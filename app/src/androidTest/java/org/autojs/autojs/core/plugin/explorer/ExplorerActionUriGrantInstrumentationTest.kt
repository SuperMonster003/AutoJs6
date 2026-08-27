package org.autojs.autojs.core.plugin.explorer

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExplorerActionUriGrantInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext

    @Test
    fun everyClipItemIsReadableOnlyForTheReceivingActivityLifetime() {
        assumeTrue("Archive Manager Debug URI probe is not installed", probeIsInstalled())
        val root = File(targetContext.cacheDir, "archive-manager-uri-grant-${System.nanoTime()}")
        check(root.mkdir()) { "Could not create URI grant fixture directory" }
        val files = listOf(
            File(root, "first.bin").apply { writeBytes("first-payload".toByteArray()) },
            File(root, "second.bin").apply { writeBytes(ByteArray(4096) { index -> index.toByte() }) },
        )
        val uris = files.map { file -> AppFileProvider.getUriForFile(targetContext, file) }

        try {
            val granted = runProbe(probeIntent(uris, grantRead = true))
            assertEquals(files.map(::sha256), granted.digests)
            assertTrue(granted.failures.isEmpty())

            val afterActivityDestroyed = runProbe(probeIntent(uris, grantRead = false))
            assertTrue(afterActivityDestroyed.digests.isEmpty())
            assertEquals(uris.size, afterActivityDestroyed.failures.size)
        } finally {
            uris.forEach { uri ->
                targetContext.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            root.deleteRecursively()
        }
    }

    private fun probeIntent(uris: List<Uri>, grantRead: Boolean): Intent {
        require(uris.isNotEmpty())
        val clipData = ClipData.newRawUri("Archive Manager URI grant probe", uris.first()).apply {
            uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
        }
        return Intent()
            .setComponent(ComponentName(PLUGIN_PACKAGE, PROBE_ACTIVITY))
            .setDataAndType(uris.first(), "application/octet-stream")
            .apply {
                this.clipData = clipData
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (grantRead) {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                    )
                }
            }
    }

    private fun runProbe(intent: Intent): ProbeResult {
        val readResult = AtomicReference<ProbeResult>()
        val readComplete = CountDownLatch(1)
        val activityDestroyed = CountDownLatch(1)
        val callback = Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MESSAGE_READ_COMPLETE -> {
                    val resultData = message.data
                        readResult.set(
                            ProbeResult(
                                digests = resultData?.getStringArrayList(KEY_DIGESTS).orEmpty(),
                                failures = resultData?.getStringArrayList(KEY_FAILURES).orEmpty(),
                            ),
                        )
                        readComplete.countDown()
                }
                MESSAGE_ACTIVITY_DESTROYED -> activityDestroyed.countDown()
            }
            true
        })
        intent.putExtra(EXTRA_CALLBACK, callback)
        targetContext.startActivity(intent)

        assertTrue("URI probe did not report its reads", readComplete.await(10, TimeUnit.SECONDS))
        assertTrue("URI probe activity was not destroyed", activityDestroyed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        return checkNotNull(readResult.get())
    }

    private fun probeIsInstalled(): Boolean = runCatching {
        targetContext.packageManager.getActivityInfo(
            ComponentName(PLUGIN_PACKAGE, PROBE_ACTIVITY),
            0,
        )
    }.isSuccess

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private data class ProbeResult(
        val digests: List<String>,
        val failures: List<String>,
    )

    private companion object {
        const val PLUGIN_PACKAGE =
            "io.github.supermonster003.autojs6.plugin.archivemanager"
        const val PROBE_ACTIVITY =
            "$PLUGIN_PACKAGE.UriGrantProbeActivity"
        const val EXTRA_CALLBACK =
            "$PLUGIN_PACKAGE.debug.CALLBACK"
        const val MESSAGE_READ_COMPLETE = 1
        const val MESSAGE_ACTIVITY_DESTROYED = 2
        const val KEY_DIGESTS = "digests"
        const val KEY_FAILURES = "failures"
    }
}
