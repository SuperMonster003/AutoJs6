package org.autojs.autojs.core.plugin.center

/** Dedicated artwork is an opt-in convention, independent of launcher alias selection. */
internal object PluginIconResourcePolicy {
    private val legacyTransparentResources = mapOf(
        "io.github.supermonster003.autojs6.plugin.threestoneai" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.audioplayer" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.amber.player" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.stove.agent" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.adapt.a11y" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.shell.terminal" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.setup.installer" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.color.picker" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.three.stamp.mail" to "ic_launcher",
        "io.github.supermonster003.autojs6.plugin.opencc" to "ic_launcher_transparent",
        "io.github.supermonster003.autojs6.plugin.r8compiler" to "ic_launcher_transparent",
        "io.github.supermonster003.autojs6.plugin.three.folio.epub" to "ic_launcher_transparent",
    )

    fun candidates(packageName: String): List<String> =
        listOfNotNull("ic_plugin_center", legacyTransparentResources[packageName])
}
