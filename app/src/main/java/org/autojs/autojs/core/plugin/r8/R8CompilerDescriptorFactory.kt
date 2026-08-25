package org.autojs.autojs.core.plugin.r8

import java.io.File

internal interface R8CompilerDescriptorFactory {
    fun openReadOnly(file: File): R8CompilerOwnedDescriptor
    fun openWriteOnly(file: File): R8CompilerOwnedDescriptor
}
