package org.autojs.plugin.dexcompiler.fake

import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase

class FakeDexCompileFailure(
    val code: DexCompilerErrorCode,
    val phase: DexCompilerFailurePhase,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
