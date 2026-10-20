package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.InMemorySettingsStore
import org.autojs.autojs.core.pref.SettingsTestResources
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.After
import org.junit.Before

/**
 * Base of the domain tests of docs/settings/ROADMAP.md P2: every store is an [InMemorySettingsStore] and key names and
 * resource defaults come from `res/values` ([SettingsTestResources]), so a test reads and writes declared keys the way
 * the app does, without Android (R3).
 *
 * zh-CN: ROADMAP P2 领域测试的基类: 每个存储都是 [InMemorySettingsStore], 键名与资源默认值取自 `res/values`, 测试像应用一样
 * 读写键声明, 不依赖 Android (R3).
 */
abstract class DomainKeysTestBase {

    private lateinit var resources: AutoCloseable

    private val stores = LinkedHashMap<StoreId, InMemorySettingsStore>()

    /** The default store of the test. */
    protected val store: InMemorySettingsStore
        get() = store(StoreId.DEFAULT)

    protected fun store(id: StoreId): InMemorySettingsStore = SettingsStores[id] as InMemorySettingsStore

    @Before
    fun installStores() {
        resources = SettingsTestResources.install()
        SettingsStores.install { id -> stores.getOrPut(id) { InMemorySettingsStore(id.name) } }
    }

    @After
    fun resetStores() {
        SettingsStores.reset()
        resources.close()
    }

    /** The key string of `R.string.<name>`. */
    protected fun key(name: String): String = SettingsTestResources.value("string", name)
}
