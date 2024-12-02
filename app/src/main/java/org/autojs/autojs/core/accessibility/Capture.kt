package org.autojs.autojs.core.accessibility

import org.autojs.autojs.util.ObjectUtils

/**
 * Created by SuperMonster003 on May 18, 2024.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on May 18, 2024.
class Capture(val windows: List<WindowInfo>, var root: NodeInfo) {

    operator fun component1() = windows

    operator fun component2() = root

    fun copy(windows: List<WindowInfo>, root: NodeInfo) = Capture(windows, root)

    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Capture -> false
        !ObjectUtils.isEqual(windows, other.windows) -> false
        ObjectUtils.isEqual(root, other.root) -> true
        else -> false
    }

    override fun hashCode() = windows.hashCode() * 31 + root.hashCode()

    override fun toString() = "Capture(windows=${windows}, root=${root})"

}
