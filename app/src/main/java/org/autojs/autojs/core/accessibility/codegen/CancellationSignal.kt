package org.autojs.autojs.core.accessibility.codegen

/** Cooperative cancellation shared by the Android and offline code generators.
 * zh-CN: Android 与离线代码生成器共用的协作式取消信号. */
class CancellationSignal {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() { isCancelled = true }

    fun throwIfCancelled() {
        if (isCancelled || Thread.currentThread().isInterrupted) throw CodeGenCancelledException()
    }
}

class CodeGenCancelledException : RuntimeException("Code generation was cancelled")
