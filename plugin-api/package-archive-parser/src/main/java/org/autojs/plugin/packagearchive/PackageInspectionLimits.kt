package org.autojs.plugin.packagearchive

/** Shared budgets for package inspection. Streaming sizes do not imply in-memory allocations. */
object PackageInspectionLimits {
    const val ARCHIVE_ENTRIES = 262_144
    const val GENERIC_APKS = 4_096
    const val ENTRY_NAME_CHARS = 4_096
    const val PACKAGE_BYTES = 8L * 1024 * 1024 * 1024
    const val TOTAL_DECLARED_BYTES = 64L * 1024 * 1024 * 1024
    const val TOTAL_SCAN_BYTES = 16L * 1024 * 1024 * 1024
    const val MANIFEST_BYTES = 16 * 1024 * 1024
    const val METADATA_BYTES = 4 * 1024 * 1024
    const val TOC_BYTES = 16 * 1024 * 1024
}
