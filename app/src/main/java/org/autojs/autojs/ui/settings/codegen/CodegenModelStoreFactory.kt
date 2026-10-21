package org.autojs.autojs.ui.settings.codegen

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.store.CurrentModelSelection
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogRepository
import org.autojs.autojs.core.accessibility.codegen.store.ModelStore
import org.autojs.autojs.core.pref.keys.LayoutInspectorKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.SharedPreferencesStore
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException

/** Supplies device paths and durable preferences without adding Android dependencies to the store.
 * zh-CN: 注入设备目录与持久偏好, 模型存储本身保持无 Android 依赖. */
object CodegenModelStoreFactory {
    fun create(context: Context, catalogRepository: ModelCatalogRepository): ModelStore {
        val app = context.applicationContext
        val configuration = CodegenCatalogBuildConfiguration.resources().configuration
        if (!BuildConfig.DEBUG) configuration.requireProduction()
        return ModelStore(
            rootDirectory = configuration.modelDirectory(app.noBackupFilesDir),
            reader = ModelPackageReader(hostVersionCode = BuildConfig.VERSION_CODE, temporaryDirectory = app.cacheDir),
            verifiedCatalogs = { listOfNotNull(catalogRepository.loadBestAvailable(), catalogRepository.bootstrap()) },
            // The selection must reach the disk before success is reported, so it commits on the preferences of the store.
            selection = SharedPreferencesCurrentModelSelection(
                (SettingsStores.default as SharedPreferencesStore).preferences,
                configuration.selectionPreferenceKey(LayoutInspectorKeys.CODEGEN_MODEL_CURRENT.name),
            ),
        )
    }
}

/** A successful selection change must be durable before the store reports success.
 * zh-CN: 当前模型选择同步落盘成功后, 存储层才可报告操作成功. */
internal class SharedPreferencesCurrentModelSelection(
    private val preferences: SharedPreferences,
    private val preferenceKey: String,
) : CurrentModelSelection {
    override var currentKey: String?
        get() = preferences.getString(preferenceKey, null)
        set(value) {
            val editor = preferences.edit()
            if (value == null) editor.remove(preferenceKey) else editor.putString(preferenceKey, value)
            if (!editor.commit()) throw IOException("Unable to persist the selected code generation model")
        }
}
