package org.autojs.autojs.runtime.api.modules.app

import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.files.BatchRename
import org.autojs.autojs.runtime.api.augment.files.Files
import org.autojs.autojs.runtime.api.augment.storages.Storages
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    private companion object {
        const val EXPORT = "AppModulesTest"
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
