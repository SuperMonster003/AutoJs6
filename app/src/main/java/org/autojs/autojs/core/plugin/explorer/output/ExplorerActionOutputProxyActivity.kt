package org.autojs.autojs.core.plugin.explorer.output

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal lifecycle owner for an output-producing Explorer Action.
 *
 * The plugin never receives this Activity's Intent. It receives only the explicit action Intent
 * assembled from the durable transaction journal by [ExplorerActionOutputCoordinator].
 */
internal class ExplorerActionOutputProxyActivity : ComponentActivity() {

    private var transactionId: String? = null
    private val completionStarted = AtomicBoolean(false)

    private val pluginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = transactionId ?: run {
            finish()
            return@registerForActivityResult
        }
        if (!completionStarted.compareAndSet(false, true)) return@registerForActivityResult
        completionScope.launch {
            try {
                ExplorerActionOutputCoordinator.complete(
                    context = applicationContext,
                    transactionId = id,
                    resultCode = result.resultCode,
                    resultData = result.data,
                )
            } catch (_: Throwable) {
                runCatching { ExplorerActionOutputCoordinator.abortLaunch(applicationContext, id) }
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    if (!isFinishing && !isDestroyed) finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = runCatching {
            savedInstanceState?.getString(STATE_TRANSACTION_ID)
                ?: intent.getStringExtra(EXTRA_TRANSACTION_ID)
        }.getOrNull()
        if (id == null || !ExplorerActionOutputSessionPolicy.isValidTransactionId(id)) {
            finish()
            return
        }
        transactionId = id
        try {
            when (ExplorerActionOutputCoordinator.transactionState(applicationContext, id)) {
                ExplorerActionOutputTransactionState.PREPARED -> launchPlugin(id)
                ExplorerActionOutputTransactionState.LAUNCHING -> rollbackAndFinish(id)
                ExplorerActionOutputTransactionState.LAUNCHED -> Unit
                else -> finish()
            }
        } catch (_: Throwable) {
            rollbackAndFinish(id)
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations && !completionStarted.get()) {
            transactionId?.let { id ->
                when (ExplorerActionOutputCoordinator.transactionState(applicationContext, id)) {
                    ExplorerActionOutputTransactionState.PREPARED,
                    ExplorerActionOutputTransactionState.LAUNCHING,
                    ExplorerActionOutputTransactionState.LAUNCHED,
                    -> runCatching { ExplorerActionOutputCoordinator.abortLaunch(applicationContext, id) }
                    else -> Unit
                }
            }
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        transactionId?.let { outState.putString(STATE_TRANSACTION_ID, it) }
        super.onSaveInstanceState(outState)
    }

    private fun launchPlugin(transactionId: String) {
        val pluginIntent = ExplorerActionOutputCoordinator
            .buildPluginIntent(applicationContext, transactionId)
            .getOrElse {
                rollbackAndFinish(transactionId)
                return
            }
        try {
            ExplorerActionOutputCoordinator.markLaunching(applicationContext, transactionId).getOrThrow()
            pluginLauncher.launch(pluginIntent)
            ExplorerActionOutputCoordinator.markLaunched(applicationContext, transactionId).getOrThrow()
        } catch (error: Throwable) {
            rollbackAndFinish(transactionId)
        }
    }

    private fun rollbackAndFinish(transactionId: String) {
        runCatching { ExplorerActionOutputCoordinator.abortLaunch(applicationContext, transactionId) }
        finish()
    }

    companion object {
        private const val EXTRA_TRANSACTION_ID =
            "org.autojs.autojs.core.plugin.explorer.output.extra.TRANSACTION_ID"
        private const val STATE_TRANSACTION_ID = "explorer_action_output_transaction_id"

        private val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun createIntent(context: Context, transactionId: String): Intent =
            Intent(context, ExplorerActionOutputProxyActivity::class.java).apply {
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
