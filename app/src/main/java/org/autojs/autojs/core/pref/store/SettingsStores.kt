package org.autojs.autojs.core.pref.store

import org.autojs.autojs.app.GlobalAppContext
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Entry point to the settings stores (docs/settings/ROADMAP.md T3): opens each [StoreId] once, keeps the list of
 * registered named stores (filled from P3 on; guard (b) of T5 checks the `getSharedPreferences(` file names against
 * it) and fans the changes of every opened store out to [addChangeListener] (used by the change backups of T20, P9.3).
 *
 * In the app, stores are opened by [AndroidProvider] ([SharedPreferencesStore] for SharedPreferences ids,
 * [JsonFileStore] under `filesDir` for JSON file ids); JVM tests [install] their own provider (for example of
 * `InMemorySettingsStore`) and [reset] afterwards. Change listeners attach to a store only once someone listens, so
 * a store nobody listens to gets no extra SharedPreferences listener.
 *
 * zh-CN: 设置存储的入口 (ROADMAP T3): 每个 [StoreId] 只打开一次, 维护已登记的命名存储清单 (P3 起填充; T5 守卫 (b) 以它核对
 * `getSharedPreferences(` 的文件名), 并把所有已打开存储的变化转发给 [addChangeListener] (供 T20 的变更后备份, P9.3). 应用内由
 * [AndroidProvider] 打开存储; JVM 测试 [install] 自己的提供方并在结束后 [reset]. 有人监听时才挂到存储上, 因此无人监听的存储不会
 * 多一个 SharedPreferences 监听器.
 */
object SettingsStores {

    fun interface Provider {
        fun open(id: StoreId): SettingsStore
    }

    /** A change of [name] (null after a clear) in the store [store]. */
    fun interface ChangeListener {
        fun onChanged(store: StoreId, name: String?)
    }

    /** Opens stores of the app: SharedPreferences ids with [SharedPreferencesStore], JSON file ids under `filesDir`. */
    object AndroidProvider : Provider {
        override fun open(id: StoreId): SettingsStore {
            val context = GlobalAppContext.get()
            return when (id.backend) {
                StoreId.Backend.SHARED_PREFERENCES -> SharedPreferencesStore.open(context, id)
                StoreId.Backend.JSON_FILE -> JsonFileStore(File(context.filesDir, id.name), id)
            }
        }
    }

    private val lock = Any()

    @Volatile
    private var provider: Provider = AndroidProvider

    private val opened = LinkedHashMap<StoreId, SettingsStore>()
    private val registered = LinkedHashSet<StoreId>().apply { add(StoreId.DEFAULT) }
    private val changeListeners = CopyOnWriteArrayList<ChangeListener>()
    private val subscriptions = LinkedHashMap<StoreId, AutoCloseable>()

    /** The default SharedPreferences (`Pref` reads and writes through it from P1.4 on). */
    @JvmStatic
    val default: SettingsStore
        get() = get(StoreId.DEFAULT)

    @JvmStatic
    operator fun get(id: StoreId): SettingsStore = synchronized(lock) {
        opened[id] ?: provider.open(id).also { store ->
            require(store.id == id) { "Provider opened ${store.id} for $id" }
            opened[id] = store
            if (changeListeners.isNotEmpty()) subscribe(store)
        }
    }

    /**
     * Registers a named store declaration (P3) and returns it; registering the same id twice is allowed.
     * zh-CN: 登记命名存储声明 (P3) 并返回; 允许重复登记同一个 id.
     */
    @JvmStatic
    fun register(id: StoreId): StoreId = synchronized(lock) { id.also { registered.add(it) } }

    /** Registered store ids in registration order ([StoreId.DEFAULT] first). */
    @JvmStatic
    val registeredIds: Set<StoreId>
        get() = synchronized(lock) { LinkedHashSet(registered) }

    /**
     * Adds a listener for the changes of every opened store (and of stores opened later); closing the result removes
     * it. Store listeners run on the store's thread (the main thread for SharedPreferences).
     * zh-CN: 添加全部已打开存储 (含之后打开的) 的变化监听, 关闭返回值即移除; 回调线程由存储决定.
     */
    @JvmStatic
    fun addChangeListener(listener: ChangeListener): AutoCloseable = synchronized(lock) {
        changeListeners.add(listener)
        if (changeListeners.size == 1) opened.values.forEach(::subscribe)
        AutoCloseable {
            synchronized(lock) {
                if (changeListeners.remove(listener) && changeListeners.isEmpty()) {
                    subscriptions.values.forEach(AutoCloseable::close)
                    subscriptions.clear()
                }
            }
        }
    }

    /**
     * Replaces the provider and forgets the opened stores (JVM tests); [reset] restores [AndroidProvider].
     * zh-CN: 替换提供方并丢弃已打开的存储 (JVM 测试); [reset] 恢复 [AndroidProvider].
     */
    @JvmStatic
    fun install(provider: Provider) = synchronized(lock) {
        subscriptions.values.forEach(AutoCloseable::close)
        subscriptions.clear()
        opened.clear()
        this.provider = provider
    }

    /** Restores [AndroidProvider] and forgets the opened stores and the change listeners (registrations stay). */
    @JvmStatic
    fun reset() = synchronized(lock) {
        install(AndroidProvider)
        changeListeners.clear()
    }

    private fun subscribe(store: SettingsStore) {
        if (store.id in subscriptions) return
        subscriptions[store.id] = store.listen { name -> changeListeners.forEach { it.onChanged(store.id, name) } }
    }
}
