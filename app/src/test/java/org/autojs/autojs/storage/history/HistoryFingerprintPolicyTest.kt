package org.autojs.autojs.storage.history

import org.autojs.autojs.storage.history.HistoryFingerprintPolicy.Attach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryFingerprintPolicyTest {

    @Test
    fun pathMatchAlwaysWins() {
        assertEquals(Attach.EXISTING_BY_PATH, HistoryFingerprintPolicy.decideAttach(true, true, true))
        assertEquals(Attach.EXISTING_BY_PATH, HistoryFingerprintPolicy.decideAttach(true, false, false))
    }

    @Test
    fun fingerprintReattachOnlyWhenOldPathIsGone() {
        assertEquals(Attach.REATTACH_BY_FINGERPRINT, HistoryFingerprintPolicy.decideAttach(false, true, false))
        // An identical copy whose original still exists must not steal the original's history.
        assertEquals(Attach.CREATE_NEW, HistoryFingerprintPolicy.decideAttach(false, true, true))
    }

    @Test
    fun noMatchCreatesNewEntry() {
        assertEquals(Attach.CREATE_NEW, HistoryFingerprintPolicy.decideAttach(false, false, false))
        assertEquals(Attach.CREATE_NEW, HistoryFingerprintPolicy.decideAttach(false, false, true))
    }

    @Test
    fun duplicateSnapshotRequiresSameHashAndSize() {
        assertTrue(HistoryFingerprintPolicy.isDuplicateSnapshot("ABC", 10, "abc", 10))
        assertFalse(HistoryFingerprintPolicy.isDuplicateSnapshot("abc", 11, "abc", 10))
        assertFalse(HistoryFingerprintPolicy.isDuplicateSnapshot("abd", 10, "abc", 10))
        assertFalse(HistoryFingerprintPolicy.isDuplicateSnapshot(null, null, "abc", 10))
        assertFalse(HistoryFingerprintPolicy.isDuplicateSnapshot("abc", null, "abc", 10))
    }
}
