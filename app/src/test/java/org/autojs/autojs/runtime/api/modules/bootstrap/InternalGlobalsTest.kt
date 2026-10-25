package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino.Side
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * Roadmap P2.5: `__exitIfError__` and `__asGlobal__` in Kotlin ([InternalGlobals]) against the module
 * `assets/modules/internal.js` they replace, assigned to the global object as `initPrologue` did.
 */
class InternalGlobalsTest {

    private lateinit var sides: BootstrapSides

    @Before
    fun setUp() {
        sides = BootstrapSides()
        sides.both(
            """
            var exits = [];
            var runtime = {
                exit: function (e) {
                    if (arguments.length === 0) return exits.push('exit()');
                    if (e instanceof java.lang.Throwable) {
                        var detail = e instanceof org.mozilla.javascript.RhinoException ? e.details() : e.getMessage();
                        var line = e instanceof org.mozilla.javascript.RhinoException ? e.lineNumber() : -1;
                        return exits.push(e.getClass().getSimpleName() + ': ' + detail + ' @' + line);
                    }
                    exits.push('exit(' + typeof e + ')');
                },
            };
            var isObject = function (o) { return o !== null && typeof o === 'object'; };
            var EvaluatorException = org.mozilla.javascript.EvaluatorException;
            var before = Object.keys(global).length;
            """.trimIndent()
        )
        val exports = sides.module(sides.jsScope, sides.original("internal.js")) as ScriptableObject
        exports.ids.forEach { id -> ScriptableObject.putProperty(sides.jsGlobal, id as String, exports.get(id, exports)) }
        InternalGlobals.install(sides.ktScope, sides.ktGlobal)
    }

    @After
    fun tearDown() = sides.close()

    @Test
    fun surfaceIsTheModules() = sides.assertSame(
        listOf(
            "Object.keys(global).slice(before).join()",
            "[typeof __exitIfError__, __exitIfError__.name, __exitIfError__.length, __exitIfError__.arity]",
            "[typeof __asGlobal__, __asGlobal__.name, __asGlobal__.length, __asGlobal__.arity]",
            "['__exitIfError__', '__asGlobal__'].map(function (k) { var d = Object.getOwnPropertyDescriptor(global, k); return [d.enumerable, d.writable, d.configurable]; })",
            "[__exitIfError__.hasOwnProperty('prototype'), __asGlobal__.hasOwnProperty('prototype')]",
            "Object.getOwnPropertyNames(__exitIfError__).sort().join()",
            "Object.getPrototypeOf(__asGlobal__) === Function.prototype",
        )
    )

    @Test
    fun exitIfErrorReturnsTheResultOrExits() = sides.assertSame(
        listOf(
            "__exitIfError__(function () { return 42; })",
            "__exitIfError__(function () { return this === global; })",
            "[__exitIfError__(function () { throw new Error('boom'); }), exits.join(' | ')]",
            "[__exitIfError__(function () { throw new TypeError('bad'); }), exits.join(' | ')]",
            "[__exitIfError__(function () { undefined.x; }), exits.join(' | ').replace(/@\\d+/, '@N')]",
            "[__exitIfError__(function () { java.lang.Integer.parseInt('x'); }), exits.join(' | ')]",
            "[__exitIfError__(function () { throw new java.lang.IllegalStateException('java'); }), exits.join(' | ')]",
            "[__exitIfError__(function () { throw 'text'; }), exits.join(' | ')]",
            "[__exitIfError__(function () { throw { name: 'Custom', message: 'm' }; }), exits.join(' | ')]",
            "[__exitIfError__(function () { var e = new Error('props'); e.fileName = 'f.js'; e.lineNumber = 7; throw e; }), exits.join(' | ')]",
            "(function () { runtime.exit = function () { throw new RangeError('from exit'); }; return __exitIfError__(function () { throw 1; }); })()",
        )
    )

    @Test
    fun exitIfErrorRejectsANonFunctionAction() {
        // The TypeError message names the call site, which differs; the error type and the exit are the same.
        // zh-CN: TypeError 的消息含调用处, 两侧不同; 错误类型与退出相同.
        sides.assertSame(listOf("[__exitIfError__(5), exits.join(' | ').split(':').slice(0, 2).join(':')]"))
    }

    @Test
    fun asGlobalBindsOnTheGlobalObjectOrTheGivenScope() = sides.assertSame(
        listOf(
            "(function () { var o = { v: 1, a: function () { return this.v; }, b: function () { return 2; } }; __asGlobal__(o, ['a', { bb: 'b' }]); return [a(), bb(), a === o.a, Object.keys(global).slice(-2).join()]; })()",
            "(function () { var o = { v: 3, a: function () { return this.v; } }; var t = {}; var r = __asGlobal__(o, ['a'], t); return [r, t.a(), typeof global.a]; })()",
            "(function () { var o = { a: function () { return 4; } }; __asGlobal__(o, ['a'], 'not an object'); return a(); })()",
            "(function () { var o = { a: function () { return 5; } }; __asGlobal__(o, [{}]); return 'no throw'; })()",
            "__asGlobal__({ a: 1 }, ['a'])",
            "__asGlobal__({}, ['missing'])",
            "__asGlobal__({}, [1])",
            "__asGlobal__({}, [null])",
            "__asGlobal__({}, 'a')",
            "__asGlobal__(undefined, ['a'])",
            "__asGlobal__({ a: function () {} }, [])",
        )
    )

    @Test
    fun errorMessagesAreTheModules() = sides.assertRenders(
        Side.KT,
        listOf(
            "Object.keys(global).slice(before).join()" to "\"__exitIfError__,__asGlobal__\"",
            "[__exitIfError__.name, __exitIfError__.length, __asGlobal__.length, __asGlobal__.hasOwnProperty('prototype')]" to "[\"__exitIfError__\",1,3,false]",
            "[__exitIfError__(function () { throw new Error('boom'); }), exits.join(' | ').replace(/ @\\d+${'$'}/, '')]" to "[null,\"EvaluatorException: Error: boom\"]",
            "__asGlobal__({}, ['missing'])" to "throws ReferenceError [missing doesn't exist on object: [object Object]]",
            "__asGlobal__({}, [true])" to "throws TypeError [Unknown type of name (true) in functions for __asGlobal__]",
        )
    )

}
