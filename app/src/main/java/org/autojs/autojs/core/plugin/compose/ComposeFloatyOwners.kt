package org.autojs.autojs.core.plugin.compose

import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.io.Closeable

/** One owner per window, attached before WindowManager.addView and destroyed on close. */
class ComposeFloatyOwners : LifecycleOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val backObservers = LinkedHashSet<() -> Unit>()
    override val onBackPressedDispatcher = OnBackPressedDispatcher(null, Consumer { backObservers.toList().forEach { it() } })
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry

    init { savedState.performAttach(); savedState.performRestore(null); registry.currentState = Lifecycle.State.CREATED }

    fun attach(view: View) {
        check(registry.currentState != Lifecycle.State.DESTROYED)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeOnBackPressedDispatcherOwner(this)
        registry.currentState = Lifecycle.State.RESUMED
    }

    internal fun observeBackCallbacks(observer: () -> Unit): Closeable {
        check(registry.currentState != Lifecycle.State.DESTROYED)
        backObservers += observer
        observer()
        return Closeable { backObservers -= observer }
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = Lifecycle.State.DESTROYED
        backObservers.clear()
    }
}
