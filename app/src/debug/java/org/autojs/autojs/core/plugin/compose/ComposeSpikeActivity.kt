package org.autojs.autojs.core.plugin.compose

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.autojs.plugin.compose.api.spike.ComposeUiRenderer
import org.autojs.plugin.compose.api.spike.SpikeContract
import org.autojs.plugin.compose.api.spike.UiNode

/** Same AppCompatActivity owner chain as ScriptExecuteActivity. No release manifest entry. */
class ComposeSpikeActivity : AppCompatActivity() {
    var renderer: ComposeUiRenderer? = null
        private set
    var loaded: ComposeSpikeLoader.Loaded? = null
        private set
    var error: String? = null
        private set
    var count = 0
        private set
    private val events = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tests can measure the same empty Activity before loading the plugin.
        if (!intent.getBooleanExtra("defer", false)) mount()
    }

    fun mount() {
        check(renderer == null)
        try {
            val plugin = ComposeSpikeLoader(this).load().also { loaded = it }
            val instance = plugin.factory.create(plugin.context(this)) { event ->
                // Always queue. No script/counter callback can run synchronously in composition or layout.
                events.post {
                    if (isDestroyed) return@post
                    if (event.name == SpikeContract.CLICK && event.callbackId == 1) {
                        count++
                        renderer?.apply(tree(count))
                    } else if (event.name == SpikeContract.ERROR) {
                        error = event.code
                    }
                }
            }.also { renderer = it }
            instance.apply(tree(count))
            setContentView(instance.view())
        } catch (failure: ComposeSpikeLoader.Failure) {
            error = failure.code.name
            setContentView(TextView(this).apply { text = failure.code.name })
        } catch (_: LinkageError) {
            error = ComposeSpikeLoader.Error.LOAD_FAILED.name
            setContentView(TextView(this).apply { text = error })
        } catch (_: RuntimeException) {
            renderer?.dispose()
            renderer = null
            error = SpikeContract.RENDER_FAILED
            setContentView(TextView(this).apply { text = error })
        }
    }

    override fun onDestroy() {
        events.removeCallbacksAndMessages(null)
        renderer?.dispose()
        renderer = null
        loaded = null
        super.onDestroy()
    }

    companion object {
        fun tree(count: Int) = UiNode(SpikeContract.COLUMN, children = listOf(
            UiNode(SpikeContract.TEXT, text = "Count: $count"),
            UiNode(SpikeContract.BUTTON, text = "+1", callbackId = 1),
        ))
    }
}
