package org.autojs.plugin.apkbuilder.template

object ApkBuildRequestExtraKeys {
    const val ARCHIVE_FORMAT_VERSION = "archiveFormatVersion"
    const val SOURCE_KIND = "sourceKind"
    const val SOURCE_PATH = "sourcePath"
    const val SOURCE_ROOT_PATH = "sourceRootPath"
    const val PROJECT_ARCHIVE_UNCOMPRESSED_SIZE_BYTES =
        "projectArchiveUncompressedSizeBytes"
    const val NATIVE_LIBRARIES_ARCHIVE_UNCOMPRESSED_SIZE_BYTES =
        "nativeLibrariesArchiveUncompressedSizeBytes"
    const val ICON_PATH = "iconPath"
    const val HOST_OUTPUT_FILE_NAME = "hostOutputFileName"
    const val TYPESCRIPT_STAGING_ENCRYPTION_VERSION =
        "typeScriptStagingEncryptionVersion"
    const val TYPESCRIPT_STAGING_ENCRYPTION_KEY = "typeScriptStagingEncryptionKey"
    const val TYPESCRIPT_STAGING_ENCRYPTED_PATHS = "typeScriptStagingEncryptedPaths"

    const val SOURCE_KIND_DIRECTORY = "directory"
    const val SOURCE_KIND_FILE = "file"
}
