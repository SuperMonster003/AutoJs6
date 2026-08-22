package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback

/** Fails closed if a non-interactive instrumentation scenario receives a prompt. */
internal abstract class NonInteractivePythonExecutionCallback : IPythonExecutionCallback.Stub() {

    final override fun onInputPrompt(prompt: ByteArray?) {
        throw AssertionError(
            "Non-interactive Python Runtime instrumentation received an input prompt " +
                "(${prompt?.size ?: -1} encoded bytes)",
        )
    }
}
