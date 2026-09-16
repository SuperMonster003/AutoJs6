package org.autojs.autojs.storage.history

import java.util.regex.Pattern

/**
 * Name generation for restoring a trash item next to an existing sibling.
 * zh-CN: 回收站条目恢复时与同名条目冲突的改名规则.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashRestoreNaming {

    /**
     * Split a name into base and extension (with dot). Directories never have an extension.
     * zh-CN: 将名称拆为主体与扩展名 (含点); 目录永远没有扩展名.
     */
    fun splitName(name: String, isDirectory: Boolean): Pair<String, String> {
        if (isDirectory) return name to ""
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    }

    /**
     * Generate the next "<base>-<n><ext>" by scanning existing siblings and taking the first missing n from 1.
     * zh-CN: 扫描同级已有的 "<base>-<n><ext>" 名称, 从 1 开始取第一个缺失的 n.
     */
    fun nextIndexedName(existingNames: Collection<String>, originalName: String, isDirectory: Boolean): String {
        val (base, ext) = splitName(originalName, isDirectory)
        val pattern = Regex("^" + Pattern.quote(base) + "-(\\d+)" + Pattern.quote(ext) + "$")

        val used = HashSet<Int>()
        for (name in existingNames) {
            val m = pattern.matchEntire(name) ?: continue
            m.groups[1]?.value?.toIntOrNull()?.let(used::add)
        }

        var n = 1
        while (used.contains(n)) n++

        return "$base-$n$ext"
    }
}
