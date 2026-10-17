package org.autojs.autojs.ui.explorer

/** The current page belongs to the view; this stores the pages on either side of it. */
internal class ExplorerNavigationHistory<T : Any> {

    private val backward = mutableListOf<T>()
    private val forward = mutableListOf<T>()

    val canGoBack: Boolean
        get() = backward.isNotEmpty()

    val canGoForward: Boolean
        get() = forward.isNotEmpty()

    val backwardEntries: List<T>
        get() = backward.toList()

    val forwardEntries: List<T>
        get() = forward.toList()

    fun recordNavigation(current: T) {
        backward += current
        forward.clear()
    }

    fun goBack(current: T): T? {
        if (backward.isEmpty()) return null
        forward += current
        return backward.removeAt(backward.lastIndex)
    }

    fun goForward(current: T): T? {
        if (forward.isEmpty()) return null
        backward += current
        return forward.removeAt(forward.lastIndex)
    }

    fun restore(backwardEntries: List<T>, forwardEntries: List<T> = emptyList()) {
        clear()
        backward.addAll(backwardEntries)
        forward.addAll(forwardEntries)
    }

    fun clear() {
        backward.clear()
        forward.clear()
    }
}
