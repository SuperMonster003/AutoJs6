package org.autojs.autojs.engine

/** Pure identity check shared by Binder callbacks and JVM race-regression tests. */
internal object NodeRuntimePluginBindingIdentity {

    fun matches(
        currentGeneration: Long,
        currentBinder: Any?,
        expectedGeneration: Long,
        expectedBinder: Any?,
    ): Boolean {
        return currentGeneration == expectedGeneration &&
                (expectedBinder == null || currentBinder === expectedBinder)
    }
}
