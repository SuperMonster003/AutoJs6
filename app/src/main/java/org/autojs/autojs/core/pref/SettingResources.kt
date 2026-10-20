package org.autojs.autojs.core.pref

import org.autojs.autojs.app.GlobalAppContext

/**
 * Reads the Android resource values that computed defaults come from (docs/settings/ROADMAP.md T1, R2): `R.bool`,
 * `R.integer` and `R.string`, the same calls the old readers made (`resources.getBoolean(...)` and so on). Reads go
 * through [reader], so JVM tests can install one that reads `res/values` instead (R3).
 *
 * zh-CN: 读取计算型默认值所依据的 Android 资源值 (ROADMAP T1, R2): `R.bool`, `R.integer` 与 `R.string`, 调用与旧读取方相同
 * (`resources.getBoolean(...)` 等). 读取经 [reader], JVM 测试可以安装读取 `res/values` 的实现 (R3).
 */
object SettingResources {

    interface Reader {
        fun bool(id: Int): Boolean
        fun int(id: Int): Int
        fun string(id: Int): String
    }

    /** The resources of the application context. */
    object AndroidReader : Reader {
        override fun bool(id: Int): Boolean = GlobalAppContext.get().resources.getBoolean(id)
        override fun int(id: Int): Int = GlobalAppContext.get().resources.getInteger(id)
        override fun string(id: Int): String = GlobalAppContext.get().getString(id)
    }

    @JvmStatic
    @Volatile
    var reader: Reader = AndroidReader

    @JvmStatic
    fun bool(id: Int): Boolean = reader.bool(id)

    @JvmStatic
    fun int(id: Int): Int = reader.int(id)

    @JvmStatic
    fun string(id: Int): String = reader.string(id)
}
