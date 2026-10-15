package org.autojs.plugin.batchrename.api

/**
 * Keys of one parent directory Bundle of [BatchRenameRequestKeys.PARENTS]. The plugin needs the names already present
 * in a directory to detect conflicts with entries that are not renamed.
 *
 * zh-CN: [BatchRenameRequestKeys.PARENTS] 中单个父目录 Bundle 的键. 插件需要目录中已有的名称, 才能判定与未改名条目的冲突.
 */
object BatchRenameParentKeys {

    /** String: opaque id the items refer to; unique within the request. */
    const val PARENT_ID = "parentId"

    /** ArrayList of String: names of every entry of the directory, including the targets themselves. */
    const val EXISTING_NAMES = "existingNames"

    /** Boolean: names differing only in case collide (FAT32 / exFAT or a case-folding mount). */
    const val CASE_INSENSITIVE = "caseInsensitive"

    /** Boolean: the file system rejects `\ : * ? " < > |` (FAT32 / exFAT); such names become invalid. */
    const val RESTRICTED_CHARACTERS = "restrictedCharacters"
}
