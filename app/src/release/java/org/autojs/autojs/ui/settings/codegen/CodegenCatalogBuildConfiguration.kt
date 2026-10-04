package org.autojs.autojs.ui.settings.codegen

/** Release contains no test keys, bootstrap resources or source selection switches. */
internal object CodegenCatalogBuildConfiguration {
    fun resources(): CodegenCatalogResources = CodegenCatalogResources.PRODUCTION.also {
        it.configuration.requireProduction()
    }
}
