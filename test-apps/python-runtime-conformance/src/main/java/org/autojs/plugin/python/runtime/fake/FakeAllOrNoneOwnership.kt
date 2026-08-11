package org.autojs.plugin.python.runtime.fake

/** Pure model for all-or-none adoption of complete Binder receiver-side PFD objects. */
object FakeAllOrNoneOwnership {
    fun <I : Any> adopt(
        incoming: List<I?>,
        validateOne: (I, Int) -> Unit = { _, _ -> },
        closeReceiverCopy: (I) -> Unit,
    ): List<I> {
        try {
            incoming.forEachIndexed { index, value ->
                validateOne(requireNotNull(value) { "Descriptor $index is null" }, index)
            }
            return incoming.map { checkNotNull(it) }
        } catch (error: Throwable) {
            incoming.filterNotNull().forEach { runCatching { closeReceiverCopy(it) } }
            throw error
        }
    }
}
