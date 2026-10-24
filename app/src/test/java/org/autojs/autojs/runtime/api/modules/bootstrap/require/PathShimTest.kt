package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapSides
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ScriptableObject

/**
 * The Node `path` module of `assets/modules/path.js`, which both states load for `require('path')` / `require('node:path')`
 * (P3.7; the Bundled Libraries plugin ships the same file, appendix C Q6), against `path.posix` of Node.js 24.15: the
 * expected values are Node's results rendered as `DifferentialRhino.render` does. With the Rhino jar before `0e4f95bb2` a
 * const in a loop body of a function kept the value of the first iteration, so `path.join('a', 'b', 'c')` gave "a/a/a" in
 * both states; dirname, basename, extname, parse and format followed Node only for a normalized path without a trailing
 * separator, format normalized its result, and a backslash was a separator (appendix C Q131). The device cases run the
 * same expressions through `require('path')` (`PathShimTest.json`).
 *
 * zh-CN: assets/modules/path.js 中的 Node path 模块, 两态的 require('path') / require('node:path') 都加载它 (P3.7; Bundled Libraries 插件带同一文件,
 * 附录 C Q6), 对照 Node.js 24.15 的 path.posix: 期望值是 Node 的结果按 DifferentialRhino.render 的规则渲染. 0e4f95bb2 之前的 Rhino jar 中, 函数内循环体里的
 * const 保持首轮的值, 因此两态的 path.join('a', 'b', 'c') 都是 "a/a/a"; dirname, basename, extname, parse 与 format 只在路径已规范化且没有末尾分隔符时与 Node
 * 一致, format 会规范化结果, 反斜杠被当作分隔符 (附录 C Q131). 设备用例经 require('path') 运行同一组表达式 (PathShimTest.json).
 */
class PathShimTest {

    private lateinit var sides: BootstrapSides

    @Before
    fun setUp() {
        sides = BootstrapSides()
        ScriptableObject.putProperty(sides.ktGlobal, "path", sides.module(sides.ktScope, sides.asset("path.js")))
    }

    @After
    fun tearDown() = sides.close()

    @Test
    fun followsNodePathPosix() = HostModuleTestSupport.check(sides.rhino, CASES)

    // What normalizeArray relies on: a const in a loop body of a function is bound again on every iteration (0e4f95bb2).
    @Test
    fun constInLoopBodiesOfFunctionsIsBoundEveryIteration() = HostModuleTestSupport.check(
        sides.rhino,
        listOf(
            "(function (a) { var r = []; for (let i = 0; i < a.length; i += 1) { const x = a[i]; r.push(x); } return r.join(); })(['a', 'b', 'c'])" to "\"a,b,c\"",
            "(function (a) { var r = [], i = 0; while (i < a.length) { const x = a[i]; r.push(x); i += 1; } return r.join(); })(['a', 'b', 'c'])" to "\"a,b,c\"",
            "(function (a) { var r = []; for (const x of a) { r.push(x); } return r.join(); })(['a', 'b', 'c'])" to "\"a,b,c\"",
        ),
    )

    @Test
    fun deviceCases() {
        DEVICE.add(CASES.map { (expression, expected) -> "(function (path) { return $expression; })(require('path'))" to expected })
    }

    companion object {

        private val DEVICE = DeviceCases("PathShimTest", "assets/modules/path.js against Node path.posix (augment-misc, appendix C Q131)")

        // Expressions with `path` the module and their rendering on Node 24.15 (a backslash is shown as "|").
        private val CASES = listOf(
            """path.join('a', 'b', 'c')""" to """"a/b/c"""",
            """path.join('/a', 'b', '../c')""" to """"/a/c"""",
            """path.join('a', '', 'b')""" to """"a/b"""",
            """path.join()""" to """"."""",
            """path.join('')""" to """"."""",
            """path.join('a/', '/b/')""" to """"a/b/"""",
            """path.join('./a', './b')""" to """"a/b"""",
            """path.join('..', 'a')""" to """"../a"""",
            """path.join('/', '..')""" to """"/"""",
            """path.join('a', 'b/')""" to """"a/b/"""",
            """path.join('a//b', 'c')""" to """"a/b/c"""",
            """path.join('foo', 'bar', 'baz/asdf', 'quux', '..')""" to """"foo/bar/baz/asdf"""",
            """path.join('a', 1)""" to """throws TypeError""",
            """path.join('a' + String.fromCharCode(92) + 'b', 'c').split(String.fromCharCode(92)).join('|')""" to """"a|b/c"""",
            """path.normalize('/foo/bar//baz/asdf/quux/..')""" to """"/foo/bar/baz/asdf"""",
            """path.normalize('')""" to """"."""",
            """path.normalize('./')""" to """"./"""",
            """path.normalize('/')""" to """"/"""",
            """path.normalize('a/../..')""" to """".."""",
            """path.normalize('//a')""" to """"/a"""",
            """path.normalize('a/./b/../c/')""" to """"a/c/"""",
            """path.normalize('/a' + String.fromCharCode(92) + '..' + String.fromCharCode(92) + 'b').split(String.fromCharCode(92)).join('|')""" to """"/a|..|b"""",
            """path.resolve('/foo/bar', './baz')""" to """"/foo/bar/baz"""",
            """path.resolve('/foo/bar', '/tmp/file/')""" to """"/tmp/file"""",
            """path.resolve('/a', 'b', '..', 'c')""" to """"/a/c"""",
            """path.resolve('/')""" to """"/"""",
            """path.resolve('/a/b', '', 'c')""" to """"/a/b/c"""",
            """path.dirname('/a/b/c')""" to """"/a/b"""",
            """path.dirname('/a/b/')""" to """"/a"""",
            """path.dirname('a')""" to """"."""",
            """path.dirname('/a')""" to """"/"""",
            """path.dirname('/')""" to """"/"""",
            """path.dirname('a/b//')""" to """"a"""",
            """path.dirname('a/../b')""" to """"a/.."""",
            """path.dirname('')""" to """"."""",
            """path.dirname('a//b')""" to """"a/"""",
            """path.dirname('//a')""" to """"//"""",
            """path.dirname('a/')""" to """"."""",
            """path.dirname(1)""" to """throws TypeError""",
            """path.basename('/a/b/c.txt')""" to """"c.txt"""",
            """path.basename('/a/b/')""" to """"b"""",
            """path.basename('c.txt', '.txt')""" to """"c"""",
            """path.basename('/')""" to """""""",
            """path.basename('a/../b')""" to """"b"""",
            """path.basename('.txt', '.txt')""" to """""""",
            """path.basename('')""" to """""""",
            """path.basename('/a/b.txt/', '.txt')""" to """"b"""",
            """path.basename('aaa', 'aaa')""" to """""""",
            """path.basename('/a/b', 'b')""" to """"b"""",
            """path.basename('a.txt', 'txt')""" to """"a."""",
            """path.basename('a', 1)""" to """throws TypeError""",
            """path.basename('a' + String.fromCharCode(92) + 'b').split(String.fromCharCode(92)).join('|')""" to """"a|b"""",
            """path.extname('index.html')""" to """".html"""",
            """path.extname('index.coffee.md')""" to """".md"""",
            """path.extname('index.')""" to """"."""",
            """path.extname('index')""" to """""""",
            """path.extname('.index')""" to """""""",
            """path.extname('a/b.c/')""" to """".c"""",
            """path.extname('..')""" to """""""",
            """path.extname('...')""" to """"."""",
            """path.extname('a.b.')""" to """"."""",
            """path.extname('.a.b')""" to """".b"""",
            """path.isAbsolute('/a')""" to """true""",
            """path.isAbsolute('a')""" to """false""",
            """path.isAbsolute('')""" to """false""",
            """path.relative('/data/orandea/test/aaa', '/data/orandea/impl/bbb')""" to """"../../impl/bbb"""",
            """path.relative('/a/b', '/a/b')""" to """""""",
            """path.relative('/a/b', '/a/b/c/d')""" to """"c/d"""",
            """path.parse('/home/user/dir/file.txt')""" to """{"root":"/","dir":"/home/user/dir","base":"file.txt","ext":".txt","name":"file"}""",
            """path.parse('file.txt')""" to """{"root":"","dir":"","base":"file.txt","ext":".txt","name":"file"}""",
            """path.parse('/')""" to """{"root":"/","dir":"/","base":"","ext":"","name":""}""",
            """path.parse('a/b/')""" to """{"root":"","dir":"a","base":"b","ext":"","name":"b"}""",
            """path.parse('')""" to """{"root":"","dir":"","base":"","ext":"","name":""}""",
            """path.parse('//a//b.c//')""" to """{"root":"/","dir":"//a/","base":"b.c","ext":".c","name":"b"}""",
            """path.parse('.bashrc')""" to """{"root":"","dir":"","base":".bashrc","ext":"","name":".bashrc"}""",
            """path.parse('/a/.x.y')""" to """{"root":"/","dir":"/a","base":".x.y","ext":".y","name":".x"}""",
            """path.parse('./a')""" to """{"root":"","dir":".","base":"a","ext":"","name":"a"}""",
            """path.format({"root":"/","dir":"/home/user/dir","base":"file.txt"})""" to """"/home/user/dir/file.txt"""",
            """path.format({"root":"/","base":"file.txt"})""" to """"/file.txt"""",
            """path.format({"name":"file","ext":".txt"})""" to """"file.txt"""",
            """path.format({"dir":"a","name":"b","ext":"c"})""" to """"a/b.c"""",
            """path.format({"root":"/","dir":"/","base":"x"})""" to """"/x"""",
            """path.format({"root":"/r/","name":"x"})""" to """"/r/x"""",
            """path.format({"dir":"a/","base":"b"})""" to """"a//b"""",
            """path.format({})""" to """""""",
            """path.format(null)""" to """throws TypeError""",
            """path.sep""" to """"/"""",
            """path.delimiter""" to """":"""",
            """path.posix === path""" to """true""",
        )

    }

}
