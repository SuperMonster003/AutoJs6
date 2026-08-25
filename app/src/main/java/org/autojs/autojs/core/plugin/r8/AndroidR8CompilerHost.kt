package org.autojs.autojs.core.plugin.r8

import android.content.Context
import android.os.Build
import android.os.Looper
import org.autojs.autojs6.BuildConfig
import java.io.File

/** Constructs R8-only host components without performing discovery or binding. */
internal object AndroidR8CompilerHost {
    @Volatile
    private var sharedRuntimeDispatcher: R8CompilerHostDispatcher? = null

    fun create(context: Context): R8CompilerHostControlPlane = R8CompilerHostControlPlane(
        transport = AndroidR8CompilerTransport(context.applicationContext),
        selectionOptions = R8CompilerOptInStore::read,
        hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
        hostRuntimeApi = Build.VERSION.SDK_INT,
    )

    fun createDispatcher(context: Context): R8CompilerHostDispatcher {
        val applicationContext = context.applicationContext
        val transactionRoot = File(applicationContext.cacheDir, TRANSACTION_DIRECTORY)
        R8CompilerTransactionWorkspace.recoverStale(transactionRoot)
        val cacheRoot = File(
            applicationContext.cacheDir,
            R8CompilerSemanticCacheKey.CACHE_DIRECTORY,
        )
        return R8CompilerHostDispatcher(
            controlPlane = create(applicationContext),
            transactionRunner = R8CompilerTransactionRunner(
                workspaceRoot = transactionRoot,
                descriptorFactory = AndroidR8CompilerDescriptorFactory,
                artifactStore = R8CompilerPersistentArtifactStore(cacheRoot),
            ),
            isMainThread = { Looper.myLooper() == Looper.getMainLooper() },
        )
    }

    /** One process-wide dispatcher keeps the host-side single-attempt gate effective for scripts. */
    fun runtimeDispatcher(context: Context): R8CompilerHostDispatcher {
        sharedRuntimeDispatcher?.let { return it }
        return synchronized(this) {
            sharedRuntimeDispatcher ?: createDispatcher(context.applicationContext).also {
                sharedRuntimeDispatcher = it
            }
        }
    }

    private const val TRANSACTION_DIRECTORY = "r8-compiler-transactions-v1"
}
