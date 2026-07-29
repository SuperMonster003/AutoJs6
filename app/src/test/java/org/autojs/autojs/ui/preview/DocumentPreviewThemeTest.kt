package org.autojs.autojs.ui.preview

import org.junit.Assert.assertSame
import org.junit.Test

class DocumentPreviewThemeTest {

    @Test
    fun invalidOrMissingThemeIdFallsBackToGithubAuto() {
        assertSame(DocumentPreviewTheme.GITHUB_AUTO, DocumentPreviewTheme.fromId(null))
        assertSame(DocumentPreviewTheme.GITHUB_AUTO, DocumentPreviewTheme.fromId(""))
        assertSame(DocumentPreviewTheme.GITHUB_AUTO, DocumentPreviewTheme.fromId("not-a-theme"))
        assertSame(DocumentPreviewTheme.GITHUB_AUTO, DocumentPreviewTheme.fromId("GITHUB_DARK"))
    }

    @Test
    fun knownThemeIdsRoundTrip() {
        DocumentPreviewTheme.entries.forEach { theme ->
            assertSame(theme, DocumentPreviewTheme.fromId(theme.id))
        }
    }
}
