package org.autojs.plugin.ai.text.fake

/** Ensures an accepted terminal transition always releases its session resources. */
object FakeTerminalFinalizer {
    fun run(cleanup: () -> Unit, publish: () -> Unit) {
        try {
            publish()
        } finally {
            cleanup()
        }
    }
}
