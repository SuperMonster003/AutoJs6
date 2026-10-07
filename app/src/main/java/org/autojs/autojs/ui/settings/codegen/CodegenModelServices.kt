package org.autojs.autojs.ui.settings.codegen

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogRepository
import org.autojs.autojs.core.accessibility.codegen.store.ModelDownloader
import org.autojs.autojs.core.accessibility.codegen.store.ModelStore
import org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontDownloadSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Settings and floating UI share transfers. Construct on their I/O worker: startup reconciles disk.
 * zh-CN: 设置与悬浮窗共享下载任务; 首次构造需对账文件, 因此由界面的工作线程调用. */
class CodegenModelServices private constructor(context: Context) {
    private val client = OkHttpClient()
    val catalog: ModelCatalogRepository = CodegenCatalogFactory.create(context,
        client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build())
    val store: ModelStore = CodegenModelStoreFactory.create(context, catalog)
    val downloader: ModelDownloader = ModelDownloader(store, catalog, OkHttpFontDownloadSource(client),
        callbackExecutor = ContextCompat.getMainExecutor(context))
    private val mutableStoreRevision = MutableStateFlow(0L)
    val storeRevision = mutableStoreRevision.asStateFlow()

    /** Publish after every persistent operation, including failures which may have committed data.
     * Recreated screens receive the latest revision before reading another local snapshot. */
    fun notifyStoreChanged() {
        mutableStoreRevision.update { it + 1 }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, CodegenModelServices>()

        /** Local construction only; catalog refresh and model download are explicit user actions. */
        fun get(context: Context): CodegenModelServices {
            val app = context.applicationContext
            return instances.computeIfAbsent(app.noBackupFilesDir.canonicalPath) { CodegenModelServices(app) }
        }
    }
}
