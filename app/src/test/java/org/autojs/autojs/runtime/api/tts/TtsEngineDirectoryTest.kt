package org.autojs.autojs.runtime.api.tts

import org.junit.Assert.*
import org.junit.Test

class TtsEngineDirectoryTest {
    @Test fun inventoryPreservesRequestedFlagsAndAndroidDefaultSelectionWithoutBinding() {
        val entries=listOf(TtsEngineDirectory.Entry("third","Third",false,100),TtsEngineDirectory.Entry("system-low","Low",true,1),TtsEngineDirectory.Entry("system-high","High",true,2))
        val result=TtsEngineDirectory.select(entries,null,"third")
        assertEquals(listOf("system-high","system-low","third"),result.map { it.name })
        assertTrue(result[0].isDefault);assertTrue(result[2].isRequested)
        assertEquals("third",TtsEngineDirectory.select(entries,"third",null).single { it.isDefault }.name)
        assertEquals("system-high",TtsEngineDirectory.select(entries,"missing",null).single { it.isDefault }.name)
        assertFalse(TtsEngineDirectory.select(entries.filterNot { it.system },null,null).any { it.isDefault })
    }
}
