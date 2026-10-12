package org.autojs.autojs.core.plugin.center.wizard

import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.ACCESSIBILITY
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.APK_BUILDER
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.COMPILER
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.DOCS
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.EDITOR
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.FILE_MANAGER
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.IMAGE
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.PLAYER
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.RUNTIME
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.TEXT_CONVERSION
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.TOOLS
import org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardCategory.UI

/**
 * One official plugin known to the wizard.
 *
 * [fallbackTitle] is only shown when the plugin index has no entry for the package,
 * the index title wins otherwise so the wizard matches the Plugin Center list.
 *
 * zh-CN: 向导已知的一个官方插件. [fallbackTitle] 仅在插件索引缺少该包时显示,
 * 否则以索引标题为准, 与插件中心列表保持一致.
 */
data class PluginInstallWizardCatalogEntry(
    val packageName: String,
    val fallbackTitle: String,
    val category: PluginInstallWizardCategory,
    /** Member of the built-in "Standard" profile. */
    val inStandard: Boolean = false,
    /** Member of the built-in "Minimal" profile (always a subset of "Standard"). */
    val inMinimal: Boolean = false,
)

/**
 * Built-in catalog of the plugin install wizard: category membership and the
 * built-in profile markers of every official plugin. Package names come from the
 * official plugin index; entries that later disappear from the index are skipped.
 *
 * zh-CN: 插件安装向导的内置目录: 每个官方插件的分类归属及内置配置标记.
 * 包名来自官方插件索引; 索引中不再存在的条目会被跳过.
 */
object PluginInstallWizardCatalog {

    private const val OFFICIAL_PREFIX = "io.github.supermonster003.autojs6.plugin."

    private fun official(suffix: String) = OFFICIAL_PREFIX + suffix

    private fun entry(
        packageName: String,
        fallbackTitle: String,
        category: PluginInstallWizardCategory,
        standard: Boolean = false,
        minimal: Boolean = false,
    ) = PluginInstallWizardCatalogEntry(
        packageName = packageName,
        fallbackTitle = fallbackTitle,
        category = category,
        inStandard = standard || minimal,
        inMinimal = minimal,
    )

    val entries: List<PluginInstallWizardCatalogEntry> = listOf(
        // Accessibility service.
        entry(official("three.adapt.a11y"), "Three Adapt A11y", ACCESSIBILITY),

        // Code editor.
        entry(official("ace.editor"), "Ace Editor", EDITOR, minimal = true),

        // UI.
        entry(official("three.block.compose"), "Three Block Compose", UI),
        entry(official("imgui"), "ImGui", UI),

        // APK builder.
        entry("org.autojs.plugin.apkbuilder.template", "APK Builder Template", APK_BUILDER, minimal = true),

        // File manager.
        entry(official("apkinspector"), "APK Inspector", FILE_MANAGER, standard = true),
        entry(official("three.stack.archive"), "Three Stack Archive", FILE_MANAGER, standard = true),
        entry(official("fileinspector"), "File Inspector", FILE_MANAGER),
        entry(official("htmlpreviewer"), "HTML Previewer", FILE_MANAGER, standard = true),
        entry(official("markdownpreviewer"), "Markdown Previewer", FILE_MANAGER, standard = true),
        entry(official("mediainfo"), "MediaInfo", FILE_MANAGER, standard = true),
        entry(official("three.folio.epub"), "Three Folio EPUB", FILE_MANAGER),

        // Script runtimes.
        entry(official("bun.runtime"), "Bun Runtime", RUNTIME),
        entry(official("java.runtime"), "Java Runtime", RUNTIME),
        entry(official("kotlin.runtime"), "Kotlin Runtime", RUNTIME),
        entry(official("lua.runtime"), "Lua Runtime", RUNTIME),
        entry(official("nodejs"), "NodeJs Runtime", RUNTIME, standard = true),
        entry(official("python.runtime"), "Python Runtime", RUNTIME),
        entry(official("quickjs.runtime"), "QuickJS Runtime", RUNTIME),
        entry(official("typescript"), "TypeScript Engine", RUNTIME, standard = true),

        // Images.
        entry(official("imagequant"), "Image Quantization", IMAGE),
        entry(official("three.maple.image"), "Three Maple Image", IMAGE, standard = true),
        entry(official("mlkit.barcode"), "MLKit Barcode", IMAGE),
        entry(official("mlkitocr"), "MLKit OCR", IMAGE, standard = true),
        entry(official("opencv"), "OpenCV", IMAGE, standard = true),
        entry(official("paddleocr.v3"), "Paddle OCR PP OCRv3", IMAGE),
        entry(official("paddleocr.v4.mobile"), "Paddle OCR PP OCRv4", IMAGE),
        entry(official("paddleocr.v5"), "Paddle OCR PP OCRv5", IMAGE, standard = true),
        entry(official("paddleocr.v6.small"), "Paddle OCR PP OCRv6", IMAGE, standard = true),
        entry(official("rapidocr.ppocrv3"), "Rapid OCR PP OCRv3", IMAGE),
        entry(official("rapidocr.ppocrv6"), "Rapid OCR PP OCRv6", IMAGE, standard = true),
        entry(official("yolo.ncnn"), "Yolo NCNN", IMAGE),

        // Compilers.
        entry(official("dexcompiler"), "DEX Compiler", COMPILER),
        entry(official("r8compiler"), "R8 Compiler", COMPILER),

        // Text conversion.
        entry(official("three.hanzi.cc"), "Three Hanzi CC", TEXT_CONVERSION),
        entry(official("pinyin"), "Pinyin", TEXT_CONVERSION),
        entry(official("pinyin4j"), "Pinyin4j", TEXT_CONVERSION),

        // Documentation.
        entry(official("offlinedocs"), "Offline Docs", DOCS, minimal = true),

        // Players.
        entry(official("three.amber.player"), "Three Amber Player", PLAYER),
        entry(official("audioplayer"), "Three Terra Player", PLAYER),

        // Tools.
        entry(official("three.stove.agent"), "Three Stove Agent", TOOLS),
        entry(official("three.stamp.mail"), "Three Stamp Mail", TOOLS),
        entry(official("three.setup.installer"), "Three Setup Installer", TOOLS, standard = true),
        entry(official("three.shell.terminal"), "Three Shell Terminal", TOOLS),
        entry(official("mcp.server"), "MCP Server", TOOLS, standard = true),
        entry(official("threestoneai"), "Three Stone AI", TOOLS, standard = true),
        entry(official("three.color.picker"), "Three Color Picker", TOOLS),
    )

    private val entriesByPackage: Map<String, PluginInstallWizardCatalogEntry> = entries.associateBy { it.packageName }

    fun find(packageName: String): PluginInstallWizardCatalogEntry? = entriesByPackage[packageName]

    fun contains(packageName: String): Boolean = packageName in entriesByPackage

    val standardPackageNames: Set<String>
        get() = entries.filter { it.inStandard }.mapTo(LinkedHashSet()) { it.packageName }

    val minimalPackageNames: Set<String>
        get() = entries.filter { it.inMinimal }.mapTo(LinkedHashSet()) { it.packageName }

}
