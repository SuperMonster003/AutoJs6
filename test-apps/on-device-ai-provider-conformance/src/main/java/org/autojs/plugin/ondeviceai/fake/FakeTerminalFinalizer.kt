package org.autojs.plugin.ondeviceai.fake

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
