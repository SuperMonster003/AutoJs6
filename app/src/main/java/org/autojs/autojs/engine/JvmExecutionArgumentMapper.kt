package org.autojs.autojs.engine

import org.autojs.plugin.jvmsource.api.JvmJsonArguments

/** Takes the bounded protocol snapshot before any provider binding or user-code dispatch. */
internal object JvmExecutionArgumentMapper {
    fun encode(arguments: Map<String, *>): String = JvmJsonArguments.encode(arguments)
}
