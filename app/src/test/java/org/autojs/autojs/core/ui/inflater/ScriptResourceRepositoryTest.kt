package org.autojs.autojs.core.ui.inflater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.xml.sax.SAXParseException
import java.io.File

class ScriptResourceRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sourceInsideLayoutDiscoversItsResourceRoot() {
        val layout = resourceFile(
            "res/layout/main.xml",
            "<LinearLayout />",
        )
        val repository = ScriptResourceRepository()

        repository.configure(sourceFile = layout)

        assertTrue(repository.hasResourceRoot())
        assertEquals(resourceDirectory().canonicalFile, repository.resourceRoot?.canonicalFile)
    }

    @Test
    fun projectEntryScriptAndWorkingDirectoryDiscoverSiblingResources() {
        val entryScript = resourceFile("main.js", "'ui';")
        resourceFile("res/layout/main.xml", "<LinearLayout />")

        val fromSource = ScriptResourceRepository().apply {
            configure(sourceFile = entryScript)
        }
        val fromWorkingDirectory = ScriptResourceRepository().apply {
            configure(workingDirectory = entryScript.parentFile)
        }

        assertEquals(resourceDirectory().canonicalFile, fromSource.resourceRoot?.canonicalFile)
        assertEquals(resourceDirectory().canonicalFile, fromWorkingDirectory.resourceRoot?.canonicalFile)
    }

    @Test
    fun scalarValuesAreDecodedAndReferencesAreResolvedRecursively() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        resourceFile(
            "res/values/base.xml",
            """
            <resources>
                <string name="application_name">AutoJs6</string>
                <color name="brand_color">#123456</color>
                <dimen name="content_spacing">16dp</dimen>
                <bool name="animations_enabled">true</bool>
                <item name="application_alias" type="string">@string/application_name</item>
                <string name="screen_title">@string/application_alias</string>
                <string name="escaped_text">"First\nSecond\t\"quoted\""</string>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        assertEquals("AutoJs6", repository.resolveValue("@string/screen_title"))
        assertEquals("#123456", repository.resolveValue("@color/brand_color"))
        assertEquals("16dp", repository.resolveValue("@dimen/content_spacing"))
        assertEquals("true", repository.resolveValue("@bool/animations_enabled"))
        assertEquals("First\nSecond\t\"quoted\"", repository.resolveValue("@string/escaped_text"))
        assertEquals("@string/missing", repository.resolveValue("@string/missing"))
    }

    @Test
    fun arrayResourcesPreserveTheirDeclaredItemOrder() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        resourceFile(
            "res/values/arrays.xml",
            """
            <resources>
                <string-array name="labels">
                    <item>Alpha</item>
                    <item>Beta</item>
                    <item>Gamma</item>
                </string-array>
                <integer-array name="levels">
                    <item>1</item>
                    <item>2</item>
                    <item>3</item>
                </integer-array>
                <array name="dimensions">
                    <item>8dp</item>
                    <item>16dp</item>
                </array>
                <string name="resolved_label">Resolved</string>
                <string-array name="referenced_labels">
                    <item>@string/resolved_label</item>
                    <item>Literal</item>
                </string-array>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        assertEquals("Alpha|Beta|Gamma", repository.resolveValue("@array/labels"))
        assertEquals("1|2|3", repository.resolveValue("@array/levels"))
        assertEquals("8dp|16dp", repository.resolveValue("@array/dimensions"))
        assertEquals("Resolved|Literal", repository.resolveValue("@array/referenced_labels"))
    }

    @Test
    fun explicitStyleParentIsMergedBeforeChildItems() {
        val repository = repositoryWithStyles()

        val resolved = repository.resolveStyle("@style/Emphasis")

        assertEquals(
            listOf("android:textColor", "android:padding", "android:textSize"),
            resolved?.keys?.toList(),
        )
        assertEquals("#222222", resolved?.get("android:textColor"))
        assertEquals("8dp", resolved?.get("android:padding"))
        assertEquals("18sp", resolved?.get("android:textSize"))
    }

    @Test
    fun dottedStyleNameImplicitlyInheritsAndRemainsReadyForInlineOverrides() {
        val repository = repositoryWithStyles()

        val resolved = repository.resolveStyle("@style/Base.Card.Compact")

        assertEquals(
            listOf(
                "android:textColor",
                "android:padding",
                "android:elevation",
                "android:minHeight",
            ),
            resolved?.keys?.toList(),
        )
        assertEquals("#111111", resolved?.get("android:textColor"))
        assertEquals("12dp", resolved?.get("android:padding"))
        assertEquals("2dp", resolved?.get("android:elevation"))
        assertEquals("40dp", resolved?.get("android:minHeight"))

        val attributes = linkedMapOf<String, String>().apply {
            putAll(resolved.orEmpty())
            putAll(
                linkedMapOf(
                    "android:padding" to "24dp",
                    "android:text" to "Inline",
                ),
            )
        }
        assertEquals("24dp", attributes["android:padding"])
        assertEquals("Inline", attributes["android:text"])
    }

    @Test
    fun qualifiedDirectoriesFollowTheConfiguration() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        val landscape = resourceFile("res/layout-land/main.xml", "<FrameLayout />")
        resourceFile("res/values/strings.xml", "<resources><string name=\"title\">Title</string><string name=\"only_base\">Base</string></resources>")
        resourceFile("res/values-zh-rCN/strings.xml", "<resources><string name=\"title\">标题</string></resources>")
        resourceFile("res/values/colors.xml", "<resources><color name=\"surface\">#ffffff</color><color name=\"accent\">@color/surface</color></resources>")
        resourceFile("res/values-night/colors.xml", "<resources><color name=\"surface\">#000000</color></resources>")
        resourceFile("res/values-sw600dp/dimens.xml", "<resources><dimen name=\"gutter\">24dp</dimen></resources>")
        resourceFile("res/values/dimens.xml", "<resources><dimen name=\"gutter\">8dp</dimen></resources>")
        resourceFile("res/values-night/styles.xml", "<resources><style name=\"Card\"><item name=\"android:background\">#000000</item></style></resources>")
        resourceFile("res/values/styles.xml", "<resources><style name=\"Card\"><item name=\"android:background\">#ffffff</item></style></resources>")
        resourceFile("res/values-large/strings.xml", "<resources><string name=\"title\">Large</string></resources>")
        val mdpi = resourceFile("res/drawable-mdpi/icon.png", "mdpi")
        val xxhdpi = resourceFile("res/drawable-xxhdpi/icon.png", "xxhdpi")
        val night = resourceFile("res/drawable-night/card.xml", "<shape />")
        val day = resourceFile("res/drawable/card.xml", "<shape />")

        val base = ResourceConfiguration(locales = listOf(java.util.Locale.US), widthDp = 400, heightDp = 800, smallestWidthDp = 400, densityDpi = 160, sdkInt = 33)
        val repository = ScriptResourceRepository()
        repository.configure(sourceFile = source, configuration = base)
        assertEquals("Title", repository.resolveValue("@string/title"))
        assertEquals("#ffffff", repository.resolveValue("@color/accent"))
        assertEquals("8dp", repository.resolveValue("@dimen/gutter"))
        assertEquals("#ffffff", repository.resolveStyle("@style/Card")?.get("android:background"))
        assertEquals(source.readText(), repository.readLayout("@layout/main"))
        assertEquals(mdpi.canonicalFile, repository.findDrawable("@drawable/icon")?.canonicalFile)
        assertEquals(day.canonicalFile, repository.findDrawable("@drawable/card")?.canonicalFile)
        // values-large has a qualifier the repository does not support.
        assertEquals(setOf("values-large"), repository.ignoredDirectories)

        // Every resource from the directory that suits the new configuration; a value it does not override stays.
        repository.configure(
            sourceFile = source,
            configuration = base.copy(locales = listOf(java.util.Locale.SIMPLIFIED_CHINESE), night = true, landscape = true, widthDp = 900, smallestWidthDp = 600, densityDpi = 420),
        )
        assertEquals("标题", repository.resolveValue("@string/title"))
        assertEquals("Base", repository.resolveValue("@string/only_base"))
        assertEquals("#000000", repository.resolveValue("@color/accent"))
        assertEquals("24dp", repository.resolveValue("@dimen/gutter"))
        assertEquals("#000000", repository.resolveStyle("@style/Card")?.get("android:background"))
        assertEquals(landscape.readText(), repository.readLayout("@layout/main"))
        assertEquals(xxhdpi.canonicalFile, repository.findDrawable("@drawable/icon")?.canonicalFile)
        assertEquals(night.canonicalFile, repository.findDrawable("@drawable/card")?.canonicalFile)
    }

    @Test
    fun theDefaultConfigurationUsesUnqualifiedDirectories() {
        val source = resourceFile(
            "res/layout/main.xml",
            "<LinearLayout android:id=\"@+id/root\" />",
        )
        resourceFile(
            "res/layout-land/main.xml",
            "<FrameLayout />",
        )
        resourceFile(
            "res/layout-sw600dp/details.xml",
            "<FrameLayout />",
        )
        val shape = resourceFile(
            "res/drawable/card_background.xml",
            "<shape />",
        )
        resourceFile(
            "res/drawable-night/card_background.xml",
            "<selector />",
        )
        resourceFile(
            "res/drawable-night/night_background.xml",
            "<selector />",
        )
        val icon = resourceFile(
            "res/mipmap/app_icon.png",
            "not-an-image",
        )
        val ninePatch = resourceFile(
            "res/drawable/panel.9.png",
            "not-an-image",
        )
        val color = resourceFile(
            "res/color/button_text.xml",
            "<selector />",
        )
        val repository = configuredRepository(source)

        assertEquals(source.readText(), repository.readLayout("@layout/main"))
        assertNull(repository.readLayout("@layout/details"))
        assertEquals(shape.canonicalFile, repository.findDrawable("@drawable/card_background")?.canonicalFile)
        assertNull(repository.findDrawable("@drawable/night_background"))
        assertEquals(icon.canonicalFile, repository.findDrawable("@mipmap/app_icon")?.canonicalFile)
        assertEquals(ninePatch.canonicalFile, repository.findDrawable("@drawable/panel")?.canonicalFile)
        assertEquals(color.canonicalFile, repository.findColor("@color/button_text")?.canonicalFile)
        assertNull(repository.readLayout("@drawable/card_background"))
        assertNull(repository.findColor("@drawable/card_background"))
        assertNull(repository.findDrawable("@drawable/../secret"))
    }

    @Test
    fun circularScalarReferencesAreRejectedWithTheirResolutionPath() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        resourceFile(
            "res/values/circular.xml",
            """
            <resources>
                <string name="first">@string/second</string>
                <string name="second">@string/third</string>
                <string name="third">@string/first</string>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveValue("@string/first")
        }

        assertTrue(error.message.orEmpty().startsWith("Circular Android resource reference:"))
        listOf("string/first", "string/second", "string/third").forEach { key ->
            assertTrue(error.message.orEmpty().contains(key))
        }
    }

    @Test
    fun circularStyleInheritanceIsRejectedWithItsResolutionPath() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        resourceFile(
            "res/values/styles.xml",
            """
            <resources>
                <style name="First" parent="@style/Second" />
                <style name="Second" parent="@style/Third" />
                <style name="Third" parent="@style/First" />
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveStyle("@style/First")
        }

        assertTrue(error.message.orEmpty().startsWith("Circular Android style inheritance:"))
        listOf("First", "Second", "Third").forEach { style ->
            assertTrue(error.message.orEmpty().contains(style))
        }
    }

    @Test
    fun projectStylePreservesItsFinalFrameworkParent() {
        val source = resourceFile("res/layout/main.xml", "<Button />")
        resourceFile(
            "res/values/styles.xml",
            """
            <resources>
                <style name="NativeButton" parent="@android:style/Widget.Button">
                    <item name="android:textAllCaps">false</item>
                </style>
                <style name="NativeButton.Emphasis">
                    <item name="android:textStyle">bold</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        val resolved = repository.resolveStyleDetails("@style/NativeButton.Emphasis")

        assertEquals("@android:style/Widget.Button", resolved?.compiledParent)
        assertEquals("false", resolved?.attributes?.get("android:textAllCaps"))
        assertEquals("bold", resolved?.attributes?.get("android:textStyle"))
    }

    @Test
    fun emptyStyleParentDisablesImplicitDottedInheritance() {
        val source = resourceFile("res/layout/main.xml", "<TextView />")
        resourceFile(
            "res/values/styles.xml",
            """
            <resources>
                <style name="Base">
                    <item name="android:textColor">#111111</item>
                </style>
                <style name="Base.Standalone" parent="">
                    <item name="android:textSize">18sp</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        val resolved = repository.resolveStyle("@style/Base.Standalone")

        assertNull(resolved?.get("android:textColor"))
        assertEquals("18sp", resolved?.get("android:textSize"))
    }

    @Test
    fun failedValuesLoadDoesNotLeaveAPartialCache() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        val values = resourceFile(
            "res/values/strings.xml",
            "<resources><string name=\"title\">Broken",
        )
        val repository = configuredRepository(source)

        assertThrows(SAXParseException::class.java) {
            repository.resolveValue("@string/title")
        }

        values.writeText(
            """
            <resources>
                <string name="title">Recovered</string>
            </resources>
            """.trimIndent(),
        )
        assertEquals("Recovered", repository.resolveValue("@string/title"))
    }

    @Test
    fun doctypeDeclarationsAreRejected() {
        val repository = ScriptResourceRepository()

        assertThrows(SAXParseException::class.java) {
            repository.parseDocument(
                """
                <!DOCTYPE resources>
                <resources />
                """.trimIndent(),
            )
        }
    }

    @Test
    fun declarationTextInsideCommentsAndCdataIsSafe() {
        val document = ScriptResourceRepository().parseDocument(
            """
            <resources>
                <!-- Documentation may mention <!DOCTYPE resources> without declaring one. -->
                <string name="example"><![CDATA[<!ENTITY example>]]></string>
            </resources>
            """.trimIndent(),
        )

        assertEquals("resources", document.documentElement.tagName)
    }

    @Test
    fun excessiveElementDepthIsRejectedBeforeDomParsing() {
        val xml = buildString {
            repeat(256) { append("<LinearLayout>") }
            repeat(256) { append("</LinearLayout>") }
        }

        val error = assertThrows(SAXParseException::class.java) {
            ScriptResourceRepository().parseDocument(xml)
        }

        assertTrue(error.message.orEmpty().contains("element depth exceeds"))
    }

    @Test
    fun externalEntitiesCannotReadLocalFiles() {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        val secret = temporaryFolder.newFile("xxe-secret.txt").apply {
            writeText("must-not-be-readable")
        }
        resourceFile(
            "res/values/external-entity.xml",
            """
            <!DOCTYPE resources [
                <!ENTITY xxe SYSTEM "${secret.toURI().toASCIIString()}">
            ]>
            <resources>
                <string name="leaked">&xxe;</string>
            </resources>
            """.trimIndent(),
        )
        val repository = configuredRepository(source)

        val error = assertThrows(SAXParseException::class.java) {
            repository.resolveValue("@string/leaked")
        }

        assertFalse(error.message.orEmpty().contains("must-not-be-readable"))
    }

    private fun repositoryWithStyles(): ScriptResourceRepository {
        val source = resourceFile("res/layout/main.xml", "<LinearLayout />")
        resourceFile(
            "res/values/styles.xml",
            """
            <resources>
                <style name="Base">
                    <item name="android:textColor">#111111</item>
                    <item name="android:padding">8dp</item>
                </style>
                <style name="Emphasis" parent="@style/Base">
                    <item name="android:textColor">#222222</item>
                    <item name="android:textSize">18sp</item>
                </style>
                <style name="Base.Card">
                    <item name="android:padding">12dp</item>
                    <item name="android:elevation">2dp</item>
                </style>
                <style name="Base.Card.Compact">
                    <item name="android:minHeight">40dp</item>
                </style>
            </resources>
            """.trimIndent(),
        )
        return configuredRepository(source)
    }

    private fun configuredRepository(source: File): ScriptResourceRepository =
        ScriptResourceRepository().apply {
            configure(sourceFile = source)
        }

    private fun resourceDirectory() = File(temporaryFolder.root, "project/res")

    private fun resourceFile(relativePath: String, contents: String): File =
        File(temporaryFolder.root, "project/$relativePath").apply {
            parentFile?.mkdirs()
            writeText(contents)
        }
}
