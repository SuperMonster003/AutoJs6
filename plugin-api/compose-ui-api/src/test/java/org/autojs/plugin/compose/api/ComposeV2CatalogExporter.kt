package org.autojs.plugin.compose.api

import org.autojs.plugin.compose.api.v2.ComposeUiV2
import java.io.File

/** V2 is exported separately: consumers can prove every V1 entry was retained exactly. */
object ComposeV2CatalogExporter {
    const val SOURCE = "org.autojs.plugin.compose.api.v2.ComposeUiV2.catalog"
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: ComposeV2CatalogExporter <output.json>" }
        File(args.single()).apply {
            parentFile?.mkdirs()
            writeText(ComposeCatalogExporter.json(document()) + "\n", Charsets.UTF_8)
        }
        println("Exported ${ComposeUiV2.catalog.components.size} V2 components")
    }
    fun document() = ComposeCatalogExporter.document(ComposeUiV2.catalog, ComposeUiV2.VERSION, SOURCE)
}
