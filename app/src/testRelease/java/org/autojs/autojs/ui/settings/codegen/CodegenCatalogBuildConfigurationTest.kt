package org.autojs.autojs.ui.settings.codegen

import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CodegenCatalogBuildConfigurationTest {
    @Test
    fun releaseAlwaysUsesTheProductionPinsAndResources() {
        val selected = CodegenCatalogBuildConfiguration.resources()
        assertSame(CodegenCatalogResources.PRODUCTION, selected)
        assertSame(ModelCatalogConfiguration.PRODUCTION, selected.configuration.requireProduction())
        assertEquals(R.raw.codegen_model_catalog_public_key_v1, selected.publicKeyResource)
        assertEquals(R.raw.codegen_model_catalog_v1, selected.bootstrapResource)
    }
}
