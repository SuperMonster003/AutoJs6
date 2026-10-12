package org.autojs.autojs.rhino

/**
 * Rhino name aliases for the frozen `com.stardust` compatibility package (roadmap P7.3, decision D18).
 *
 * A script that names one of the classes below (`importClass(com.stardust.pio.PFile)`, `new com.stardust...(...)`,
 * `x instanceof com.stardust...`, `Packages.com.stardust...`) receives the AutoJs6 class instead of the frozen copy,
 * so the class it names and the objects it already holds (`files`, `dialogs`, `events`, ...) agree. Only classes
 * whose public API the host class covers for a script are listed: `tools/stardust/compare_alias_signatures.py`
 * surveys the candidates and `StardustClassAliasesTest` re-checks every entry with reflection, so a host change
 * that drops a member a script could reach through the alias fails the unit tests instead of failing silently.
 *
 * The copies stay in the compatibility package: the compat engine stack (`com.stardust.autojs.runtime.ScriptRuntime`
 * and friends) still constructs them, and code loaded through `runtime.loadDex` links against them by JVM name.
 * The table is consulted only by [AndroidClassLoader.loadClass], and only when the lookup was issued by Rhino
 * itself; the VM (linking) and `java.lang.Class.forName` keep receiving the class they asked for, because ART
 * rejects a loader that answers with a class of a different name.
 *
 * zh-CN: 冻结的 `com.stardust` 兼容包在 Rhino 中的类名别名 (roadmap P7.3, 决策 D18). 脚本以下列名字引用类时拿到 AutoJs6 的
 * 实现, 与脚本已持有的 `files`, `dialogs`, `events` 等对象的类型一致. 只列出宿主类对脚本完整覆盖的类: 候选由
 * `tools/stardust/compare_alias_signatures.py` 普查, 每一项由 `StardustClassAliasesTest` 用反射复核, 宿主删掉脚本可经别名
 * 触及的成员时单元测试会失败而不是静默缺失. 副本仍保留在兼容包中: 兼容包的引擎栈仍会构造它们, `runtime.loadDex` 加载的
 * 代码也按 JVM 类名链接它们. 别名只由 [AndroidClassLoader.loadClass] 查询, 且只在查找由 Rhino 自身发起时生效; VM 链接与
 * `java.lang.Class.forName` 仍拿到所请求的类, 因为 ART 拒绝加载器返回名字不同的类.
 */
object StardustClassAliases {

    /** `com.stardust` class name to host class name; nested classes follow their outer class. */
    @JvmField
    val TABLE: Map<String, String> = linkedMapOf(
        "com.stardust.autojs.runtime.api.AbstractConsole" to "org.autojs.autojs.runtime.api.AbstractConsole",
        "com.stardust.autojs.runtime.api.AbstractShell" to "org.autojs.autojs.runtime.api.AbstractShell",
        "com.stardust.autojs.runtime.api.Dialogs" to "org.autojs.autojs.runtime.api.Dialogs",
        "com.stardust.autojs.runtime.api.Engines" to "org.autojs.autojs.runtime.api.Engines",
        "com.stardust.autojs.runtime.api.Events" to "org.autojs.autojs.runtime.api.Events",
        "com.stardust.autojs.runtime.api.Files" to "org.autojs.autojs.runtime.api.Files",
        "com.stardust.autojs.runtime.api.Images" to "org.autojs.autojs.runtime.api.Images",
        "com.stardust.autojs.runtime.api.Threads" to "org.autojs.autojs.runtime.api.Threads",
        "com.stardust.autojs.runtime.api.Timers" to "org.autojs.autojs.runtime.api.Timers",
        "com.stardust.autojs.core.util.Shell" to "org.autojs.autojs.runtime.api.Shell",
        "com.stardust.autojs.core.image.Colors" to "org.autojs.autojs.core.image.Colors",
        "com.stardust.pio.PFiles" to "org.autojs.autojs.pio.PFiles",
        "com.stardust.pio.PFile" to "org.autojs.autojs.pio.PFile",
        "com.stardust.pio.PWritableTextFile" to "org.autojs.autojs.pio.PWritableTextFile",
        "com.stardust.pio.PReadableTextFile" to "org.autojs.autojs.pio.PReadableTextFile",
    )

    /**
     * The host class name for [name] when it is an aliased `com.stardust` class or a nested class of one,
     * otherwise null. zh-CN: [name] 是别名表中的类或其嵌套类时返回宿主类名, 否则返回 null.
     */
    @JvmStatic
    fun resolve(name: String): String? {
        TABLE[name]?.let { return it }
        val nested = name.indexOf('$')
        if (nested > 0) {
            TABLE[name.substring(0, nested)]?.let { return it + name.substring(nested) }
        }
        return null
    }

}
