package org.autojs.autojs.core.plugin.r8

import android.os.ParcelFileDescriptor
import java.io.File

/** Opens two distinct descriptors with the exact access modes enforced by the provider. */
internal object AndroidR8CompilerDescriptorFactory : R8CompilerDescriptorFactory {
    override fun openReadOnly(file: File): R8CompilerOwnedDescriptor = AndroidR8CompilerDescriptor(
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
    )

    override fun openWriteOnly(file: File): R8CompilerOwnedDescriptor = AndroidR8CompilerDescriptor(
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
        ),
    )
}
