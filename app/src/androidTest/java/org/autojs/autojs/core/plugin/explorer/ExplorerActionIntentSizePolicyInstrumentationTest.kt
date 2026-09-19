package org.autojs.autojs.core.plugin.explorer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.plugin.explorer.api.ExplorerActionIntentExtras
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionTargetKeys
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExplorerActionIntentSizePolicyInstrumentationTest {

    @Test
    fun maximumCountTypicalEnvelopeFitsButDeepPathEnvelopeFailsBeforeBinder() {
        val typical = requestEnvelope(
            targetCount = ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST,
            sharedUriPathLength = 16,
            displayNameLength = 32,
            parentDisplayPathLength = 64,
        )
        val typicalMeasurement = ExplorerActionIntentSizePolicy.measure(typical)
        assertTrue(typicalMeasurement.byteCount < ExplorerActionIntentSizePolicy.MAX_REQUEST_BYTES)
        assertFalse(typicalMeasurement.hasFileDescriptors)
        assertTrue(ExplorerActionIntentSizePolicy.isSafe(typical))
        ExplorerActionIntentSizePolicy.requireSafe(typical)

        val deepPath = requestEnvelope(
            targetCount = ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST,
            sharedUriPathLength = 2_048,
            displayNameLength = 2_048,
            parentDisplayPathLength = ExplorerActionProtocol.MAX_PARENT_DISPLAY_PATH_LENGTH,
        )
        val deepMeasurement = ExplorerActionIntentSizePolicy.measure(deepPath)
        assertTrue(deepMeasurement.byteCount > ExplorerActionIntentSizePolicy.MAX_REQUEST_BYTES)
        assertFalse(deepMeasurement.hasFileDescriptors)
        assertFalse(ExplorerActionIntentSizePolicy.isSafe(deepPath))
        assertFailsWithIllegalArgument { ExplorerActionIntentSizePolicy.requireSafe(deepPath) }
    }

    private fun requestEnvelope(
        targetCount: Int,
        sharedUriPathLength: Int,
        displayNameLength: Int,
        parentDisplayPathLength: Int,
    ): Intent {
        val sharedPath = "p".repeat(sharedUriPathLength)
        val targets = ArrayList<Bundle>(targetCount)
        val uris = ArrayList<Uri>(targetCount)
        repeat(targetCount) { index ->
            val suffix = index.toString().padStart(3, '0')
            val uri = Uri.parse("content://org.autojs.test.fileprovider/root/$sharedPath/$suffix")
            uris += uri
            targets += Bundle().apply {
                putString(ExplorerActionTargetKeys.ID, "target-$suffix")
                putParcelable(ExplorerActionTargetKeys.URI, uri)
                putString(
                    ExplorerActionTargetKeys.DISPLAY_NAME,
                    "n".repeat(displayNameLength - suffix.length) + suffix,
                )
                putInt(ExplorerActionTargetKeys.KIND, ExplorerActionValues.TARGET_FILE)
                putString(ExplorerActionTargetKeys.MIME_TYPE, "application/octet-stream")
                putLong(ExplorerActionTargetKeys.SIZE, index.toLong())
                putLong(ExplorerActionTargetKeys.LAST_MODIFIED, index.toLong())
            }
        }
        val clipData = ClipData.newRawUri("Explorer Action targets", uris.first()).apply {
            uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
        }
        return Intent("org.autojs.plugin.EXPLORER_ACTION_EXECUTE")
            .setDataAndType(uris.first(), "*/*")
            .apply { this.clipData = clipData }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(
                ExplorerActionIntentExtras.PARENT_DISPLAY_PATH,
                "/" + "d".repeat(parentDisplayPathLength - 1),
            )
            .putParcelableArrayListExtra(ExplorerActionIntentExtras.TARGETS, targets)
    }

    private fun assertFailsWithIllegalArgument(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
