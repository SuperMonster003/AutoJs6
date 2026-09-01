package org.autojs.plugin.mediainfo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediainfoSnapshotSchemasTest {

    @Test
    fun missingAndBlankSchemaResolveToV1() {
        listOf(null, "", " ", "\t\r\n").forEach { schema ->
            assertEquals(MediainfoSnapshotSchemas.V1, MediainfoSnapshotSchemas.resolve(schema))
        }
    }

    @Test
    fun exactKnownSchemaIdentifiersAreAccepted() {
        MediainfoSnapshotSchemas.VALUES.forEach { schema ->
            assertEquals(schema, MediainfoSnapshotSchemas.resolve(schema))
        }
    }

    @Test
    fun unknownPaddedAndCaseChangedSchemaIdentifiersAreRejected() {
        listOf(
            "snapshot-v2",
            " ${MediainfoSnapshotSchemas.V2}",
            "${MediainfoSnapshotSchemas.V2} ",
            MediainfoSnapshotSchemas.V2.uppercase(),
        ).forEach { schema ->
            assertThrows(IllegalArgumentException::class.java) {
                MediainfoSnapshotSchemas.resolve(schema)
            }
        }
    }

    @Test
    fun v1RemainsAvailableWithoutCapabilityAdvertisement() {
        assertTrue(MediainfoSnapshotSchemas.isSupported(null, null))
        assertTrue(MediainfoSnapshotSchemas.isSupported(MediainfoSnapshotSchemas.V1, emptyArray()))
    }

    @Test
    fun optInSchemaRequiresAnExactCapabilityAdvertisement() {
        assertFalse(MediainfoSnapshotSchemas.isSupported(MediainfoSnapshotSchemas.V2, null))
        assertFalse(
            MediainfoSnapshotSchemas.isSupported(
                MediainfoSnapshotSchemas.V2,
                arrayOf(" ${MediainfoSnapshotSchemas.V2}"),
            ),
        )
        assertTrue(
            MediainfoSnapshotSchemas.isSupported(
                MediainfoSnapshotSchemas.V2,
                arrayOf(MediainfoSnapshotSchemas.V1, MediainfoSnapshotSchemas.V2),
            ),
        )
    }

    @Test
    fun unsupportedOptInSchemaHasAStableHostErrorPrefix() {
        val failure = assertThrows(UnsupportedOperationException::class.java) {
            MediainfoSnapshotSchemas.requireSupported(MediainfoSnapshotSchemas.V2, null)
        }
        assertTrue(failure.message.orEmpty().startsWith(MediainfoSnapshotSchemas.UNSUPPORTED_BY_PLUGIN_ERROR))
        assertTrue(failure.message.orEmpty().contains(MediainfoSnapshotSchemas.V2))
    }

    @Test
    fun publicKeysMatchTheWireContract() {
        assertEquals("schema", MediainfoOptionKeys.SCHEMA)
        assertEquals("snapshotSchemas", MediainfoPluginCapabilityKeys.SNAPSHOT_SCHEMAS)
        assertEquals("defaultSnapshotSchema", MediainfoPluginCapabilityKeys.DEFAULT_SNAPSHOT_SCHEMA)
        assertEquals("engineVersion", MediainfoPluginCapabilityKeys.ENGINE_VERSION)
    }

    @Test
    fun publicSchemaSetCannotBeMutatedThroughAForcedMutableCast() {
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (MediainfoSnapshotSchemas.VALUES as MutableSet<String>).add("forbidden")
        }
    }
}
