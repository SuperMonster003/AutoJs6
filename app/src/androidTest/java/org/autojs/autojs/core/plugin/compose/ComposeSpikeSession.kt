package org.autojs.autojs.core.plugin.compose

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.autojs.plugin.compose.api.spike.ComposeUiRenderer
import org.autojs.plugin.compose.api.spike.SpikeContract
import org.autojs.plugin.compose.api.spike.UiNode

/** Instrumentation fixture using an existing host Activity. No temporary Android component. */
class ComposeSpikeSession(val activity: AppCompatActivity) : DefaultLifecycleObserver {
    val isDestroyed get() = activity.isDestroyed
    val resources get() = activity.resources
    val theme get() = activity.theme
    var renderer: ComposeUiRenderer? = null
        private set
    var loaded: ComposeSpikeLoader.Loaded? = null
        private set
    var error: String? = null
        private set
    var count = 0
        private set
    private val events = Handler(Looper.getMainLooper())

    init { activity.lifecycle.addObserver(this) }

    fun mount() {
        check(renderer == null)
        try {
            val plugin = ComposeSpikeLoader(activity).load().also { loaded = it }
            val instance = plugin.factory.create(plugin.context(activity)) { event ->
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
            activity.setContentView(instance.view())
        } catch (failure: ComposeSpikeLoader.Failure) {
            error = failure.code.name
            activity.setContentView(TextView(activity).apply { text = failure.code.name })
        } catch (_: LinkageError) {
            error = ComposeSpikeLoader.Error.LOAD_FAILED.name
            activity.setContentView(TextView(activity).apply { text = error })
        } catch (_: RuntimeException) {
            renderer?.dispose()
            renderer = null
            error = SpikeContract.RENDER_FAILED
            activity.setContentView(TextView(activity).apply { text = error })
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        events.removeCallbacksAndMessages(null)
        renderer?.dispose()
        renderer = null
        loaded = null
        activity.lifecycle.removeObserver(this)
    }

    fun finish() = activity.finish()

    companion object {
        fun tree(count: Int) = UiNode(SpikeContract.COLUMN, children = listOf(
            UiNode(SpikeContract.TEXT, text = "Count: $count"),
            UiNode(SpikeContract.BUTTON, text = "+1", callbackId = 1),
        ))
    }
}
