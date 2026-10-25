package org.autojs.autojs.runtime.api.modules.app

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.files.BatchRename
import org.autojs.autojs.runtime.api.augment.files.Files
import org.autojs.autojs.runtime.api.modules.storages.Storages
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.util.RhinoUtils

/**
 * Roadmap P3.4, second group: `app`, `autojs` (with `autojs.version`), `device`, `files` (with `files.batchRename`) and
 * `storages` on the new script module layer. The surfaces of app / files / storages are compared with the current augment
 * calls in the same JVM ([RuntimeModulesFixture]; `length` left out, appendix C Q9). The current `autojs` module reads
 * `Context.getString` when constructed and `device` needs `runtime.device`, whose class cannot even be loaded on the JVM,
 * so their surfaces are compared on the device only and their argument rules run here on the new modules (device without
 * prototype). Cases that need Android go to `build/script-modules-device/AppModulesTest.json`.
 *
 * zh-CN: P3.4 第二组: 新层上的 app, autojs (含 autojs.version), device, files (含 files.batchRename) 与 storages. app / files / storages 的表面在同一 JVM
 * 中与现有 augment 调用比较 (不含 length). 现有 autojs 模块构造时读取 Context.getString, device 依赖的 runtime.device 在 JVM 上无法加载其类, 二者表面只在真机比较,
 * 参数规则在此对新模块检查 (device 无原型). 需要 Android 的用例写入设备用例.
 */
class AppModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT, cases)
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, PATHS) }
        assertEquals(
            // Documented: the function under `apply` is named after its script name (formerly the Kotlin name applyPlan).
            expected.replace("files.batchRename.apply EW- function applyPlan", "files.batchRename.apply EW- function apply"),
            actual,
        )
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        (GLOBALS + listOf("autojs", "\$autojs", "device", "\$device")).forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun appArguments() = check(
        listOf(
            "app.autojs === autojs" to "true",
            "app.versionCode === autojs.versionCode && app.versionName === autojs.versionName" to "true",
            "launchSettings === app.launchAppDetailsSettings && openDualAppSettings === app.launchDualAppDetailsSettings" to "true",
            "app.intent()" to "throws TypeError",
            "app.intent(1)" to "throws TypeError",
            "app.intent('x')" to "throws TypeError",
            "app.startActivity()" to "throws TypeError",
            "startActivity(1)" to "throws TypeError",
            "startActivity(null)" to "throws TypeError",
            "app.startActivity('settings', {})" to "throws TypeError",
            "app.startActivity({}, 1)" to "throws TypeError",
            "app.startActivity({}, {}, 1)" to "throws TypeError",
            "startDualActivity([])" to "throws TypeError",
            "app.intentToShell(1)" to "throws TypeError",
            "app.startService('x')" to "throws TypeError",
            "sendEmail()" to "throws TypeError",
            "app.sendBroadcast('no_such_form')" to "throws RangeError",
            "app.sendBroadcast(1)" to "throws TypeError",
            "app.sendLocalBroadcastSync(1)" to "throws TypeError",
            "sendLocalBroadcastSync(null, 2)" to "throws TypeError",
            "app.openUrl()" to "throws TypeError",
            "app.openUrl(null)" to "throws TypeError",
            "app.openDualUrl(undefined)" to "throws TypeError",
            "app.getInstalledApps(1)" to "throws TypeError",
            "app.getInstalledPackages('meta_data')" to "throws TypeError",
            "app.getApkInfo()" to "throws TypeError",
            "app.getApkInfo('a.apk', 1)" to "throws TypeError",
            "app.viewFile(1)" to "throws TypeError",
            "app.editFile({})" to "throws TypeError",
            "launch()" to "throws TypeError",
            "launch(1, 2)" to "throws TypeError",
            "app.kill()" to "throws TypeError",
        )
    )

    @Test
    fun autojsArguments() = check(
        listOf(
            "typeof R" to "\"object\"",
            "typeof autojs.R.string" to "\"object\"",
            "autojs.setRootMode()" to "throws TypeError",
            "autojs.setRootMode({})" to "throws TypeError",
            "autojs.setRootMode(null)" to "throws TypeError",
            "autojs.setRootMode(2)" to "throws RangeError",
            "autojs.setRootMode('x')" to "throws RangeError",
            "autojs.setRootMode(1, true, 1)" to "throws TypeError",
            "autojs.restart(1)" to "throws TypeError",
            "autojs.exit({})" to "throws TypeError",
            "autojs.isRootAvailable(1)" to "throws TypeError",
            "autojs.getLanguageTag(1)" to "throws TypeError",
            "autojs.version.code === autojs.versionCode && autojs.version.name === autojs.versionName" to "true",
            "autojs.version.isAtLeast('1.0.0')" to "true",
            "autojs.version.isAtLeast(autojs.version.name, true)" to "true",
            "autojs.version.isHigherThan('99.0.0')" to "false",
            "autojs.version.isLowerThan('99.0.0')" to "true",
            "autojs.version.isEqual(autojs.version.name)" to "true",
            "autojs.version.isAtLeast(new Packages.io.github.g00fy2.versioncompare.Version('1.0.0'))" to "true",
            "autojs.version.isAtLeast()" to "throws TypeError",
            "autojs.version.isAtLeast(null)" to "throws TypeError",
            "autojs.version.isAtLeast('6.0.0', 1)" to "throws TypeError",
            "autojs.version.isEqual('1', '2')" to "throws TypeError",
        )
    )

    @Test
    fun deviceArguments() = check(
        listOf(
            "Object.keys(device).slice(0, 4).join()" to "\"summary,digest,vibrate,isScreenOff\"",
            "device.vibrate()" to "throws TypeError",
            "device.vibrate({})" to "throws TypeError",
            "device.vibrate(null)" to "throws TypeError",
            "device.vibrate([100, 'x'])" to "throws TypeError",
            "device.vibrate(100, 'x')" to "throws TypeError",
            "device.vibrate([1], [2])" to "throws TypeError",
            "device.vibrate(1, 2, 3)" to "throws TypeError",
            "device.getIpAddress(1)" to "throws TypeError",
            "device.setPointerLocation()" to "throws TypeError",
            "device.setPointerLocation(1)" to "throws TypeError",
            "device.summary(1)" to "throws TypeError",
            "device.isScreenOff(1)" to "throws TypeError",
        )
    )

    @Test
    fun filesAndStoragesArguments() = check(
        listOf(
            "files.join('a', 'b', 'c') === String(new java.io.File(new java.io.File('a', 'b'), 'c').getPath())" to "true",
            "files.open()" to "throws TypeError",
            "open(null)" to "throws TypeError",
            "files.open('a', 'r', 'utf-8', 'x')" to "throws TypeError",
            "files.open('a', 'r', 'utf-8', 1, 2)" to "throws TypeError",
            "files.join()" to "throws TypeError",
            "files.join('a', null)" to "throws TypeError",
            "files.toFile()" to "throws TypeError",
            "files.path(1, 2)" to "throws TypeError",
            "typeof files.batchRename" to "\"function\"",
            "files.batchRename.name" to "\"batchRename\"",
            "typeof files.batchRename.BatchRenameError" to "\"function\"",
            "new files.batchRename.BatchRenameError('m', 'INVALID_OPTIONS').toString()" to "\"BatchRenameError [INVALID_OPTIONS]: m\"",
            "storages.create()" to "throws TypeError",
            "storages.create(null)" to "throws TypeError",
            "storages.remove(undefined)" to "throws TypeError",
            "storages.removeSync()" to "throws TypeError",
            "storages.all(1)" to "throws TypeError",
            "storages.names(1)" to "throws TypeError",
        )
    )

    /**
     * The members of the obsolete `__device__.js` and `__storages__.js` (and of the storage objects the obsolete `create`
     * returned) exist on the new modules. The obsolete device has `runtime.device` as the prototype of its prototype; the
     * class cannot be loaded on the JVM (nor is it the prototype of the new module here), so a plain object stands in for it
     * and the members of `runtime.device` are checked on the device ([deviceOnlyCases]). The obsolete storages open their
     * storages with the global `context`, an in-memory store here ([StoreContext]).
     */
    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        host.setRuntimeProperty("device", rhino.evalJs("({})"))
        ScriptableObject.putProperty(host.global, "context", Context.javaToJS(StoreContext.install(), rhino.jsScope))
        val obsolete = listOf("device", "storages").associateWith { host.load(it) }
        ScriptableObject.putProperty(host.global, "obsoleteStorages", obsolete["storages"])
        listOf(
            "device" to (obsolete["device"] to "device"),
            "storages" to (obsolete["storages"] to "storages"),
            // `_storage` is the LocalStorage the obsolete proxy kept as a property; the new storage object keeps it in Kotlin.
            "storage" to (rhino.evalJs("obsoleteStorages.create('aj6-p36-obsolete')") to "storages.create('aj6-p36-obsolete')"),
        ).forEach { (name, sides) ->
            val (js, kt) = sides
            val report = SurfaceDiff.compare(SurfaceDiff.surface(js, rhino.jsScope.globalThis).filterKeys { it != "_storage" }, SurfaceDiff.surface(rhino.evalKt(kt), rhino.ktScope.globalThis))
            assertTrue(name + ": " + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        }
    }

    /**
     * Storages on the new layer and on the current module (P3.4) give the same results on an in-memory store
     * ([StoreContext], a fresh one per side): JSON round trips, defaults, contains / remove, clearing by name, the commit
     * result of removeSync and the name of a storage created with a number. Writing `undefined` fails on both sides (the
     * storage object is the current one).
     */
    @Test
    fun storagesGiveTheSameResultsAsTheCurrentModule() {
        StoreContext.install()
        val compared = HostModuleTestSupport.assertSameResults(rhino, STORAGE_RESULTS.map { it.first }, emptyMap(), prepare = {
            RuntimeModulesFixture.prepareHost(it.ktRuntime, RuntimeModulesFixture.CORE_FIELDS + "app")
            StoreContext.install()
        }) { Storages.augmentWithRuntime(it.ktScope.globalThis as ScriptableObject, it.ktRuntime) }
        assertEquals(STORAGE_RESULTS.size, compared)
        StoreContext.install()
        RuntimeModulesFixture.check(rhino, STORAGE_RESULTS)
    }

    /** Behaviour that needs Android: written for the device only. */
    @Test
    fun deviceOnlyCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT,
        listOf(
            "app.isInstalled('org.autojs.autojs6')" to "true",
            "isInstalled('no.such.package.aj6p34')" to "false",
            "app.launch(null)" to "false",
            "app.getPackageName(null)" to "null",
            "typeof app.getAppName('org.autojs.autojs6')" to "\"string\"",
            "String(app.intent({ action: 'VIEW', data: 'https://example.com' }).getAction())" to "\"android.intent.action.VIEW\"",
            "app.intentToShell({ action: 'VIEW', data: 'x' })" to "\"-a 'android.intent.action.VIEW' -d x\"",
            "String(app.parseUri('https://example.com/a'))" to "\"https://example.com/a\"",
            "app.parseUri(1)" to "null",
            "app.getAppByAlias('no-such-alias-aj6p34')" to "null",
            "Array.isArray(app.getInstalledApps()) && app.getInstalledApps().length > 0" to "true",
            "app.getApkInfo('/sdcard/no-such-file-aj6p34.apk')" to "null",
            "app.viewFile(null)" to "false",
            "app.viewFile('/sdcard/no-such-file-aj6p34.txt')" to "throws JavaException Error",
            "app.sendLocalBroadcastSync(null)" to "undefined",
            "app.autojs === autojs" to "true",
            "autojs.getLanguageTag().length > 0" to "true",
            "typeof autojs.isRootAvailable()" to "\"boolean\"",
            "autojs.setRootMode(autojs.getRootMode())" to "undefined",
            "autojs.setRootMode(' AUTO ')" to "undefined",
            "[typeof autojs.canModifySystemSettings(), typeof autojs.canWriteSecureSettings(), typeof autojs.canDisplayOverOtherApps()].join()" to "\"boolean,boolean,boolean\"",
            "typeof autojs.themeColor" to "\"object\"",
            "[0, 1, 2, 3].indexOf(autojs.rotation) >= 0 && typeof autojs.orientation === 'number'" to "true",
            "autojs.isScreenPortrait() !== autojs.isScreenLandscape()" to "true",
            "typeof R.string.app_name" to "\"number\"",
            "R.string.app_name === autojs.R.strings.app_name" to "true",
            "autojs.name.length > 0 && autojs.packageName === 'org.autojs.autojs6'" to "true",
            "device.width > 0 && device.height > 0 && device.density > 0" to "true",
            "device.pageSize === 4096 || device.pageSize === 16384" to "true",
            "(function () { 'use strict'; try { device.pageSize = 1; return 'written'; } catch (e) { return e.name; } })()" to "\"TypeError\"",
            "typeof device.summary()" to "\"string\"",
            "device.digest().split(' / ').length" to "3",
            "typeof device.getIpAddress(true)" to "\"string\"",
            "[typeof device.isWifiAvailable(), typeof device.isConnectedOrConnecting(), typeof device.isActiveNetworkMetered()].join()" to "\"boolean,boolean,boolean\"",
            "typeof device.isPointerLocationEnabled() && device.isPointerLocationEnabled() !== device.isPointerLocationDisabled()" to "true",
            "device.isScreenOff() === !device.isScreenOn()" to "true",
            "typeof device.brand" to "\"string\"",
            "device.vibrate(1)" to "undefined",
            "device.vibrate([0, 1])" to "undefined",
            "device.vibrate(0, 1)" to "undefined",
            "device.vibrate([1], 0)" to "undefined",
            "files.join('a', 'b', 'c')" to "\"a/b/c\"",
            "files.path()" to "null",
            "files.path('a.txt').endsWith('/a.txt')" to "true",
            "String(files.toFile('a.txt').getName())" to "\"a.txt\"",
            "(function () { var p = files.join(files.cwd(), 'aj6-p34-open.txt'); var f = open(p, 'w'); f.write('x'); f.close(); var r = files.read(p); files.remove(p); return r; })()" to "\"x\"",
            "(function () { var p = files.join(files.cwd(), 'aj6-p34-open.txt'); files.write(p, 'yz'); var f = files.open(p, undefined, undefined, 1024); var r = f.read(); f.close(); files.remove(p); return r; })()" to "\"yz\"",
            "typeof files.batchRename.isAvailable()" to "\"boolean\"",
            "(function () { try { files.batchRename.plan(); } catch (e) { return e instanceof files.batchRename.BatchRenameError && e.name; } })()" to "\"BatchRenameError\"",
            "(function () { var s = storages.create('aj6-p34'); s.put('k', { a: 1 }); var v = s.get('k'); storages.remove('aj6-p34'); return v; })()" to "{\"a\":1}",
            "Array.isArray(storages.all()) && Array.isArray(storages.names())" to "true",
            "storages.removeSync('aj6-p34-sync')" to "true",
            // The obsolete __device__.js had runtime.device as a prototype; its members are reachable on the new module.
            "(function () { var p = runtime.device, missing = []; for (var k in p) { if (typeof p[k] === 'function' && typeof device[k] !== 'function') missing.push(k); } return missing.join(); })()" to "\"\"",
        )
    )

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        RuntimeModulesFixture.prepareHost(side.ktRuntime, RuntimeModulesFixture.CORE_FIELDS + "app")
        RuntimeModulesFixture.installAppContext()
        var app: ScriptableObject? = null
        RuntimeModulesFixture.mount(side, if (newLayer) MODULES + listOf("autojs", "device") else MODULES, newLayer) { name, global, runtime ->
            when (name) {
                "app" -> app = App(runtime).augment(global, runtime.app)
                "files" -> Files(runtime).augment(global, runtime.files).also { BatchRename(runtime).augment(it) }
                "storages" -> Storages.augmentWithRuntime(global, runtime)
                else -> throw AssertionError(name)
            }
        }
        // As ScriptRuntime.augment() does after mounting every module with the new layer off; the current autojs module
        // reads Context.getString when constructed, which the JVM android stubs refuse, so a plain object stands in for it.
        if (!newLayer) app!!.defineProp("autojs", RhinoUtils.newNativeObject(side.ktScope))
    }

    /**
     * An in-memory SharedPreferences (the JVM android stubs have none); [StoreContext] hands out one per name. Edits are
     * applied as Android applies them: `clear` first, then the removals and the values in order.
     */
    private class MemoryPreferences : SharedPreferences {

        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(values)

        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = key in values

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {

            private val changes = mutableListOf<Pair<String, Any?>>()
            private var clearing = false

            private fun change(key: String?, value: Any?): SharedPreferences.Editor = also { changes += key!! to value }

            override fun putString(key: String?, value: String?) = change(key, value)

            override fun putStringSet(key: String?, values: MutableSet<String>?) = change(key, values)

            override fun putInt(key: String?, value: Int) = change(key, value)

            override fun putLong(key: String?, value: Long) = change(key, value)

            override fun putFloat(key: String?, value: Float) = change(key, value)

            override fun putBoolean(key: String?, value: Boolean) = change(key, value)

            override fun remove(key: String?) = change(key, null)

            override fun clear(): SharedPreferences.Editor = also { clearing = true }

            override fun commit(): Boolean {
                if (clearing) values.clear()
                changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }

            override fun apply() {
                commit()
            }

        }

    }

    /** The application context of the storages cases: the stores by name, emptied by [install]. */
    private class StoreContext : ContextWrapper(null) {

        override fun getPackageName() = "org.autojs.autojs.test"

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = STORES.getOrPut(name!!) { MemoryPreferences() }

        companion object {

            private val STORES = linkedMapOf<String, MemoryPreferences>()

            /** Installs a context without stores as the application context (each DifferentialRhino restores the previous one on close). */
            fun install(): StoreContext {
                STORES.clear()
                return DifferentialRhino.allocateWithoutConstructor(StoreContext::class.java).also {
                    GlobalAppContext::class.java.getDeclaredField("sApplicationContext").apply { isAccessible = true }.set(null, it)
                }
            }

        }

    }

    private companion object {
        const val EXPORT = "AppModulesTest"
        val STORAGE_RESULTS = listOf(
            "(function () { var s = storages.create('aj6-p36-a'); s.put('k', { a: [1, 'x', null], b: true }); return s.get('k'); })()" to "{\"a\":[1,\"x\",null],\"b\":true}",
            "(function () { var s = storages.create('aj6-p36-b'); s.put('n', 1.5).put('t', 'x'); return [s.get('n'), s.get('t'), s.get('none') === undefined, s.get('none', 7), s.size]; })()" to "[1.5,\"x\",true,7,2]",
            "(function () { var s = storages.create('aj6-p36-c'); s.put('k', 1); var before = s.contains('k'); s.remove('k'); return [before, s.contains('k'), s.get('k', 'gone')]; })()" to "[true,false,\"gone\"]",
            "(function () { storages.create('aj6-p36-d').put('k', 1); storages.remove('aj6-p36-d'); var s = storages.create('aj6-p36-d'); return [s.contains('k'), s.size]; })()" to "[false,0]",
            "(function () { var s = storages.create('aj6-p36-e'); s.putSync('k', 'v'); return [storages.removeSync('aj6-p36-e'), s.size]; })()" to "[true,0]",
            "(function () { var s = storages.create('aj6-p36-f'); s.put('a', 1).put('b', 2); s.clear(); return s.size; })()" to "0",
            "(function () { var s = storages.create('aj6-p36-g'); return [s.put('k', 1) === s, s.remove('k') === s, s.name]; })()" to "[true,true,\"aj6-p36-g\"]",
            "(function () { storages.create('aj6-p36-h').put('k', 'v'); return [storages.create('aj6-p36-h').get('k'), storages.create('aj6-p36-i').contains('k')]; })()" to "[\"v\",false]",
            "(function () { var s = storages.create('aj6-p36-j'); s.put('d', new Date(0)); s.put('s', ''); return [s.get('d'), s.get('s')]; })()" to "[\"1970-01-01T00:00:00.000Z\",\"\"]",
            "storages.create(1).name" to "\"1\"",
            "storages.create('aj6-p36-k').put('k', undefined)" to "throws JavaException IllegalArgumentException",
        )
        val MODULES = listOf("app", "files", "storages")
        val PATHS = listOf("app", "files", "files.batchRename", "storages")
        val GLOBALS = listOf(
            "startActivity", "startDualActivity", "startService", "sendEmail", "sendBroadcast", "sendLocalBroadcastSync",
            "launch", "launchDual", "launchPackage", "launchDualPackage", "launchApp", "launchDualApp", "getPackageName", "getAppName",
            "launchAppDetailsSettings", "launchSettings", "openAppSetting", "openAppSettings",
            "launchDualAppDetailsSettings", "launchDualSettings", "openDualAppSetting", "openDualAppSettings",
            "isInstalled", "isDualInstalled", "uninstall", "uninstallDual", "kill", "killDual", "app", "\$app",
            "open", "files", "\$files", "storages", "\$storages",
        )
    }

}
