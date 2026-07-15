package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIndexRepositoryTest {

    private val repository = PluginIndexRepository()

    @Test
    fun extractReleaseNotesFromMarkdownKeepsOnlyTargetVersionBullets() {
        val markdown = """
            ******

            ### Release History

            ******

            # v1.0.1

            ###### 2026/07/14

            * `Improvement` Current release item
            - `Fix` Current release fix

            # v1.0.0

            ###### 2026/07/13

            * `Feature` Previous release item
        """.trimIndent()

        val actual = invokeString(
            name = "extractReleaseNotesFromMarkdown",
            parameterTypes = arrayOf(String::class.java, String::class.java, String::class.java),
            markdown,
            "1.0.1",
            "v1.0.1",
        )

        assertEquals(
            "* `Improvement` Current release item\n- `Fix` Current release fix",
            actual,
        )
        assertFalse(actual.orEmpty().contains("Previous release item"))
        assertFalse(actual.orEmpty().contains("2026/07/14"))
    }

    @Test
    fun changelogLanguageCandidatesMatchOpenCcGeneratedFiles() {
        val androidTags = invokeStringList(
            name = "languageTagToAndroidChangelogTags",
            parameterTypes = arrayOf(String::class.java),
            "zh-Hant-HK",
        )
        val repositoryTags = invokeStringList(
            name = "languageTagToRepositoryChangelogTags",
            parameterTypes = arrayOf(String::class.java),
            "zh-Hans",
        )

        assertEquals("zh-rHK", androidTags.first())
        assertTrue("zh-Hant-HK" in androidTags)
        assertEquals("zh-Hans", repositoryTags.first())
        assertTrue("en" in repositoryTags)
    }

    @Test
    fun githubMarkdownAnchorMatchesGitHubVersionHeadingSlug() {
        assertEquals(
            "v101",
            invokeString(
                name = "githubMarkdownAnchorForVersion",
                parameterTypes = arrayOf(String::class.java, String::class.java),
                "1.0.1",
                null,
            ),
        )
        assertEquals(
            "v101",
            invokeString(
                name = "githubMarkdownAnchorForVersion",
                parameterTypes = arrayOf(String::class.java, String::class.java),
                "v1.0.1",
                null,
            ),
        )
        assertEquals(
            "v101-beta",
            invokeString(
                name = "githubMarkdownAnchorForVersion",
                parameterTypes = arrayOf(String::class.java, String::class.java),
                "1.0.1-beta",
                "v1.0.1-beta",
            ),
        )
    }

    private fun invokeString(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): String? = invokePrivate(name, parameterTypes, *args) as String?

    @Suppress("UNCHECKED_CAST")
    private fun invokeStringList(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): List<String> = invokePrivate(name, parameterTypes, *args) as List<String>

    private fun invokePrivate(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        val method = PluginIndexRepository::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method.invoke(repository, *args)
    }
}
