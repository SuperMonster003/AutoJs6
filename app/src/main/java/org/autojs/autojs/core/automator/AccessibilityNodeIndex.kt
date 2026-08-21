package org.autojs.autojs.core.automator

internal fun <T> findIndexInParent(
    childCount: Int,
    childAt: (Int) -> T?,
    target: T,
): Int {
    return try {
        for (index in 0 until childCount) {
            if (childAt(index) == target) {
                return index
            }
        }
        -1
    } catch (_: RuntimeException) {
        -1
    }
}
