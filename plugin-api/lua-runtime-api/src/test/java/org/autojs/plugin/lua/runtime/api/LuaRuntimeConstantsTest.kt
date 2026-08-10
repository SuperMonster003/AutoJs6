package org.autojs.plugin.lua.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LuaRuntimeConstantsTest {
    @Test
    fun pluginIdentityAndRuntimeSlotAreDistinct() {
        assertEquals("lua-runtime", LuaPluginIds.ID)
        assertEquals("lua", LuaPluginIds.ENGINE)
        assertEquals("lua54", LuaRuntimeContract.RUNTIME_SLOT_LUA54)
        assertEquals("puc-lua54", LuaPluginIds.VARIANT_PUC_LUA_5_4)
        assertEquals("org.autojs.plugin.INFO", LuaPluginActions.INFO)
        assertEquals("org.autojs.plugin.lua.RUNTIME", LuaPluginActions.RUNTIME)
    }

    @Test
    fun generatedAidlDoesNotClaimStrictTrailingDataRejection() {
        assertFalse(LuaRuntimeContract.GENERATED_AIDL_ENFORCES_NO_TRAILING_DATA)
    }
}
