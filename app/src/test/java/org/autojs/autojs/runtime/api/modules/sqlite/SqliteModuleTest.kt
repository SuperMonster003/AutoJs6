package org.autojs.autojs.runtime.api.modules.sqlite

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P3.5: `sqlite` on the new script module layer. Opening a database needs Android's SQLite, so the JVM checks
 * the surface (against the obsolete `__sqlite__.js`, whose only member is `open`, and against the current
 * `augment/sqlite/SQLite`) and the argument errors, which are raised before anything is opened (D10); the cases that
 * open databases run on the device (`build/script-modules-device/SqliteModuleTest.json`). The obsolete module's
 * `sqlite(...)` returned undefined (the function had an empty body); 6.6.0 made it open a database as documented.
 *
 * zh-CN: P3.5: 新层的 sqlite. 打开数据库需要 Android 的 SQLite, 因此 JVM 只检查表面 (对照旧 __sqlite__.js, 其唯一成员是 open, 以及现有
 * augment/sqlite/SQLite) 与参数错误 (在打开之前抛出, D10); 打开数据库的用例在真机运行. 旧模块的 sqlite(...) 返回 undefined (函数体为空), 6.6.0 起按文档
 * 打开数据库.
 */
class SqliteModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        rhino.mountNewModules("sqlite")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val obsolete = ScriptModuleExpectations.obsoleteSurface("surface: sqlite") {
            SurfaceDiff.surface(ObsoleteModuleHost(rhino.context, rhino.jsScope).load("sqlite"), rhino.jsScope.globalThis)
        }
        val report = SurfaceDiff.compare(obsolete, SurfaceDiff.surface(rhino.evalKt("sqlite"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
    }

    @Test
    fun surfaceMatchesTheCurrentModule() {
        HostModuleTestSupport.assertSameSurface(rhino, "sqlite") { SQLite(it.ktRuntime).augment(it.ktScope.globalThis as ScriptableObject) }
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    /**
     * The argument errors against the current module (P3.6): the shape is the same, every error is a known difference of
     * appendix E (P3.5) (the current module threw IllegalArgumentException, handed the value to Android, or opened a
     * database named by blanks); the comparison fails if the current module ever gives the new result.
     */
    @Test
    fun argumentErrorsDifferFromTheCurrentModuleAsKnown() {
        val errors = ERRORS.drop(1)
        val known = errors.associate { (expression, expected) -> expression to (expected to "appendix E P3.5 sqlite (D10, Q7)") }
        val expressions = listOf("[typeof sqlite, typeof sqlite.open]") + errors.map { it.first }
        val compared = HostModuleTestSupport.assertSameResults(rhino, expressions, known) { SQLite(it.ktRuntime).augment(it.ktScope.globalThis as ScriptableObject) }
        assertEquals(expressions.size, compared)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("SqliteModuleTest", "new script module layer (P3.5)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val ERRORS = listOf(
            "[typeof sqlite, typeof sqlite.open, sqlite.length, sqlite.open.length]" to "[\"function\",\"function\",1,1]",
            "sqlite()" to TYPE,
            "sqlite(null)" to TYPE,
            "sqlite('')" to RANGE,
            "sqlite.open('  ')" to RANGE,
            "sqlite.open()" to TYPE,
            "sqlite('a.db', 5)" to TYPE,
            "sqlite('a.db', [])" to TYPE,
            "sqlite('a.db', { version: 'x' })" to TYPE,
            "sqlite('a.db', { version: 0 })" to RANGE,
            "sqlite('a.db', { version: 0.5 })" to RANGE,
            "sqlite('a.db', { version: Infinity })" to RANGE,
            "sqlite('a.db', { version: 2147483648 })" to RANGE,
            "sqlite('a.db', null, 5)" to TYPE,
            "sqlite('a.db', null, 'callback')" to TYPE,
            "sqlite.open('a.db', {}, null, 1)" to TYPE,
        )

        /** A database in the script's working directory, closed after [body] (which sees it as `db`). */
        private fun withDatabase(open: String, body: String) = "(function () { var db = $open; try { return $body; } finally { db.close(); } })()"

        private val DEVICE_ONLY = listOf(
            withDatabase(
                "sqlite('aj6-p35-sqlite.db', { version: 2 })",
                "(db.execSQL('DROP TABLE IF EXISTS t'), db.execSQL('CREATE TABLE t (a INTEGER, b TEXT, c REAL)'), db.insert('t', { a: 1, b: 'x', c: 1.5 }), " +
                    "(function (rows) { return [db.getVersion(), rows.length, Number(rows[0].a), String(rows[0].b), Number(rows[0].c)]; })(db.query('t', null, null, null, null, null, null).all()))",
            ) to "[2,1,1,\"x\",1.5]",
            withDatabase("sqlite.open('aj6-p35-sqlite.db', { version: 2 })", "[db.isOpen(), String(new java.io.File(db.getPath()).getName())]") to "[true,\"aj6-p35-sqlite.db\"]",
            "(function () { var events = []; var db = sqlite.open('aj6-p35-callback.db', null, { onCreate: function () { events.push('create'); }, onOpen: function () { events.push('open'); } }); db.close(); return events; })()" to "[\"create\",\"open\"]",
            "(function () { var events = []; var db = sqlite.open('aj6-p35-callback.db', undefined, { onOpen: function () { events.push('open'); } }); db.close(); return events; })()" to "[\"open\"]",
            "(function () { var events = []; var db = sqlite.open('aj6-p35-callback.db', { version: 3 }, { onUpgrade: function (d, from, to) { events.push(from + '->' + to); } }); db.close(); return events; })()" to "[\"1->3\"]",
            withDatabase("sqlite('aj6-p35-sqlite.db', { version: 2, readOnly: true })", "typeof db.isReadOnly()") to "\"boolean\"",
            // A lower version keeps the data (db-downgrade D6; before, Android's default onDowngrade threw SQLiteException).
            withDatabase("sqlite('aj6-p35-sqlite.db', { version: 1 })", "[db.getVersion(), db.query('t', null, null, null, null, null, null).all().length]") to "[1,1]",
            "(function () { var events = []; sqlite.open('aj6-p35-downgrade.db', { version: 3 }).close(); " +
                "var db = sqlite.open('aj6-p35-downgrade.db', { version: 2 }, { onDowngrade: function (d, from, to) { events.push(from + '->' + to, d.getVersion()); }, onUpgrade: function () { events.push('upgrade'); }, onOpen: function () { events.push('open'); } }); " +
                "events.push(db.getVersion()); db.close(); return events; })()" to "[\"3->2\",3,\"open\",2]",
            "(function () { sqlite.open('aj6-p35-downgrade.db', { version: 3 }).close(); var error = null; " +
                "try { sqlite.open('aj6-p35-downgrade.db', { version: 2 }, { onDowngrade: function () { throw new Error('kept'); } }).close(); } catch (e) { error = /kept/.test(String(e)); } " +
                "var db = sqlite.open('aj6-p35-downgrade.db', { version: 3 }); try { return [error, db.getVersion()]; } finally { db.close(); } })()" to "[true,3]",
        )

    }

}
