package org.autojs.autojs.core.plugin.epub

import org.autojs.plugin.epub.api.EpubContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract version negotiation of the host client (plugin roadmap P9.4): the version every
 * request carries is the plugin's newest version bounded by the host's, never below the
 * plugin's baseline, and the appended calls need both the version and the feature.
 */
class EpubPeerTest {

    @Test
    fun theNegotiatedVersionIsThePluginsNewestBoundedByTheHosts() {
        // A version-1 plugin without the max key stays at 1.
        assertEquals(1, EpubPeer.negotiate(base = 1, max = null))
        // The official plugin from 1.1.0: baseline 1, newest 2.
        assertEquals(2, EpubPeer.negotiate(base = 1, max = 2))
        // A future plugin newer than this host: the host's maximum.
        assertEquals(EpubContract.MAX_CONTRACT_VERSION, EpubPeer.negotiate(base = 1, max = 9))
        // A max below the baseline is ignored.
        assertEquals(2, EpubPeer.negotiate(base = 2, max = 1))
        // The baseline itself is never lowered, even above the host's maximum (discovery refuses such a plugin anyway).
        assertEquals(3, EpubPeer.negotiate(base = 3, max = 3))
        assertEquals(EpubContract.MAX_CONTRACT_VERSION, EpubPeer.negotiate(base = 1, max = EpubContract.MAX_CONTRACT_VERSION))
    }

    @Test
    fun annotationsNeedVersionTwoAndTheFeature() {
        assertFalse(EpubPeer.LEGACY.supportsAnnotations)
        assertEquals(EpubContract.MIN_CONTRACT_VERSION, EpubPeer.LEGACY.contractVersion)
        assertFalse(EpubPeer(1, setOf(EpubContract.FEATURE_ANNOTATIONS)).supportsAnnotations)
        assertFalse(EpubPeer(2, setOf(EpubContract.FEATURE_SEARCH)).supportsAnnotations)
        assertTrue(EpubPeer(2, setOf(EpubContract.FEATURE_SEARCH, EpubContract.FEATURE_ANNOTATIONS)).supportsAnnotations)
    }
}
