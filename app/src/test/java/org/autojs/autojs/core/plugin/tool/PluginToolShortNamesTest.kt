package org.autojs.autojs.core.plugin.tool

import org.autojs.autojs.core.plugin.tool.PluginToolShortNames.Claim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginToolShortNamesTest {

    @Test
    fun distinctNamesAreKept() {
        assertTrue(PluginToolShortNames.resolve(listOf(Claim("a", "opencc", 3), Claim("b", "yolo", 1))).isEmpty())
    }

    @Test
    fun theMoreTrustedClaimKeepsTheName() {
        val refused = PluginToolShortNames.resolve(listOf(Claim("user", "opencc", 1), Claim("official", "opencc", 3), Claim("trusted", "opencc", 2)))
        assertEquals(setOf("user", "trusted"), refused.keys)
        assertTrue(refused.getValue("user").contains("belongs to the more trusted plugin official"))
    }

    @Test
    fun aTieAtTheTopRefusesEveryClaim() {
        val refused = PluginToolShortNames.resolve(listOf(Claim("b", "opencc", 1), Claim("a", "opencc", 1)))
        assertEquals(setOf("a", "b"), refused.keys)
        assertTrue(refused.getValue("a").contains("declared by 2 plugins of the same trust level: a, b"))
    }

    @Test
    fun aTieAtTheTopAlsoRefusesLowerClaims() {
        val refused = PluginToolShortNames.resolve(listOf(Claim("t1", "opencc", 2), Claim("t2", "opencc", 2), Claim("u", "opencc", 1)))
        assertEquals(setOf("t1", "t2", "u"), refused.keys)
        assertTrue(refused.getValue("u").contains("belongs to more trusted plugins: t1, t2"))
    }
}
