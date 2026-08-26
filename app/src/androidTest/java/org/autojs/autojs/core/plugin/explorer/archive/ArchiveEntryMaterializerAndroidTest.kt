package org.autojs.autojs.core.plugin.explorer.archive

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveEntryMaterializerAndroidTest {

    @Test
    fun materializeAndCloseUseApisAvailableOnTheHostMinimumSdk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDirectory = File(context.cacheDir, "archive-materializer-${UUID.randomUUID()}")
        val bytes = "minimum-sdk-preview".toByteArray()

        try {
            val materializer = ArchiveEntryMaterializer(cacheDirectory, "minimum-sdk-session")
            val preview = materializer.materialize(
                entryId = "entry-id",
                displayName = "preview.txt",
                expectedSize = bytes.size.toLong(),
                maxBytes = 1_024L,
                input = ByteArrayInputStream(bytes),
            )

            assertTrue(preview.isFile)
            materializer.close()
            assertFalse(preview.exists())
        } finally {
            cacheDirectory.deleteRecursively()
        }
    }
}
