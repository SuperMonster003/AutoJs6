package org.autojs.autojs.ui.settings.codegen

import android.content.Context
import androidx.annotation.RawRes
import okhttp3.OkHttpClient
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogRepository
import org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontCatalogFetcher
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R

/** Android resource/path adapter; the repository and its trust policy remain JVM-testable.
 * zh-CN: 仅适配 Android 资源与目录; 仓库与信任校验保留为可在 JVM 测试的纯 Kotlin. */
object CodegenCatalogFactory {
    fun create(context: Context, client: OkHttpClient = OkHttpClient()): ModelCatalogRepository {
        val app = context.applicationContext
        val selected = CodegenCatalogBuildConfiguration.resources()
        if (!BuildConfig.DEBUG) selected.configuration.requireProduction()
        return selected.configuration.createRepository(
            noBackupDirectory = app.noBackupFilesDir,
            hostVersionCode = BuildConfig.VERSION_CODE,
            publicKeyBytes = app.resources.openRawResource(selected.publicKeyResource).use { it.readBytes() },
            bootstrapEnvelopeBytes = app.resources.openRawResource(selected.bootstrapResource).use { it.readBytes() },
            fetcher = OkHttpFontCatalogFetcher(client),
        )
    }
}

internal data class CodegenCatalogResources(
    val configuration: ModelCatalogConfiguration,
    @param:RawRes val publicKeyResource: Int,
    @param:RawRes val bootstrapResource: Int,
) {
    companion object {
        val PRODUCTION = CodegenCatalogResources(
            ModelCatalogConfiguration.PRODUCTION,
            R.raw.codegen_model_catalog_public_key_v1,
            R.raw.codegen_model_catalog_v1,
        )
    }
}
