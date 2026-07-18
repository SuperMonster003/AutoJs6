package org.autojs.autojs.ui.edit.editor.ace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AceInstalledFontRouteTest {

    @Test
    fun parsesSarasaRouteServedByAppAssetsOrigin() {
        val sha = "db29e32c750ac2bf0ec236b3e6225088d5ba6cd4a6d6b37dafcbf3ee614d6a90"

        assertEquals(
            AceInstalledFontRoute("sarasa_term_sc_nerd", sha),
            AceInstalledFontRoute.parse("sarasa_term_sc_nerd/$sha.woff2"),
        )
        assertEquals(
            "https://appassets.androidplatform.net/autojs6-fonts/sarasa_term_sc_nerd/$sha.woff2",
            AceFontDescriptor.installed("sarasa_term_sc_nerd", "Sarasa Term SC Nerd", sha).url,
        )
    }

    @Test
    fun rejectsTraversalAndMalformedRoutes() {
        val sha = "a".repeat(64)

        assertNull(AceInstalledFontRoute.parse("../sarasa_term_sc_nerd/$sha.woff2"))
        assertNull(AceInstalledFontRoute.parse("sarasa-term-sc-nerd/$sha.woff2"))
        assertNull(AceInstalledFontRoute.parse("sarasa_term_sc_nerd/${sha.uppercase()}.woff2"))
        assertNull(AceInstalledFontRoute.parse("sarasa_term_sc_nerd/$sha.woff2?cache=1"))
    }
}
