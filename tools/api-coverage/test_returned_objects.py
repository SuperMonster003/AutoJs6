import json
from pathlib import Path
import tempfile
import unittest

from returned_objects import Sources, attach, class_members, prototype_members

PACKAGE = 'app/src/main/java/org/autojs/autojs/sample'

KOTLIN = '''package org.autojs.autojs.sample

import android.view.View
import org.autojs.autojs.sample.base.Base

open class Widget(val id: String, private val secret: Int, @JvmField var count: Int = 0) : Base<Widget>(), Shape, View.OnClickListener {

    val width: Int get() = 1
    var isEnabled = true
    var label: String = ""
        private set
    internal val hidden = 0
    private fun helper() = 0
    protected fun guarded() = 0

    fun area(scale: Double = 1.0): Double {
        val local = 2
        fun inner() = local
        return inner() * scale
    }

    @JvmOverloads
    fun move(x: Int, y: Int = 0) = Unit

    fun all(vararg items: Any?) = items.size

    fun area(a: Int, b: Int, c: Int) = a + b + c

    override fun toString() = id

    companion object {
        fun create() = Widget("x", 0)
    }

    class Nested {
        fun nestedOnly() = 0
    }
}

interface Shape {
    fun sides(): Int
}
'''

BASE = '''package org.autojs.autojs.sample.base

abstract class Base<T> {
    fun baseName() = "base"
    abstract fun area(scale: Double): Double
}
'''

JAVA = '''package org.autojs.autojs.sample;

public class Legacy extends java.lang.Object implements java.io.Closeable {
    public int code;
    private String secret;
    public static final String TAG = "legacy";
    public Legacy() {}
    public String read(int size) { return ""; }
    public String read() { return ""; }
    String packageOnly() { return ""; }
    public void log(String... parts) {}
    public void close() {}

    public record Options(int width, int height) {}
}
'''

NATIVE = '''package org.autojs.autojs.sample

class Handle(runtime: Any) : NativeObject() {
    init {
        defineProperty("pid", { 1 }, null, READONLY)
        defineProperty("mode", { 1 }, { _ -> }, READONLY)
        defineProperty("path", "x", READONLY)
        defineProperty("toString", newBaseFunction(scope, "toString", { "" }, NOT_CONSTRUCTABLE), READONLY)
        function("kill") { true }
        method("hide", "dismiss") { true }
        defineOp(this, "send", 1..2) { a -> a }
        Actions.NAMES.keys.forEach { name -> method(name) { true } }
        for (event in listOf("on", "off")) function(event) { true }
        defineProperty(StringReadable.KEY, newBaseFunction(scope, "x", { "" }, NOT_CONSTRUCTABLE), READONLY)
        defineProperty(computeName(), 1, READONLY)
        RhinoUtils.defineFunctionProperties(this, mFunctionNames, javaClass, PERMANENT)
    }

    private val mFunctionNames = arrayOf(::blockedGet.name, "setValue")

    private fun function(name: String, body: () -> Any?) = defineProperty(name, newBaseFunction(scope, name, body), READONLY)

    private fun folder(path: String) = NativeObject().also { target -> target.defineProperty("folderOnly", path, READONLY) }

    class Inner : NativeObject() {
        init { defineProperty("innerOnly", 1, READONLY) }
    }
}
'''

ACTIONS = '''package org.autojs.autojs.sample

object Actions {
    const val BACK = "back"
    val NAMES = mapOf("click" to 1, Actions.BACK to 2)
}
'''

READABLE = '''package org.autojs.autojs.sample

interface StringReadable {
    companion object {
        val KEY = StringReadable::toStringReadable.name
    }
    fun toStringReadable(): String
}
'''


def write(root, relative, text):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def host():
    directory = tempfile.TemporaryDirectory()
    root = Path(directory.name)
    write(root, f'{PACKAGE}/Widget.kt', KOTLIN)
    write(root, f'{PACKAGE}/base/Base.kt', BASE)
    write(root, f'{PACKAGE}/Legacy.java', JAVA)
    write(root, f'{PACKAGE}/Handle.kt', NATIVE)
    write(root, f'{PACKAGE}/Actions.kt', ACTIONS)
    write(root, f'{PACKAGE}/StringReadable.kt', READABLE)
    return directory, root


class KotlinClassTests(unittest.TestCase):
    def test_public_members_properties_overloads_and_host_supertypes(self):
        directory, root = host()
        with directory:
            members, external, scriptable, source = class_members(Sources(root), 'org.autojs.autojs.sample.Widget')
            by_name = {m['name']: m for m in members}
            self.assertEqual(f'{PACKAGE}/Widget.kt', source)
            self.assertFalse(scriptable)
            self.assertEqual(['android.view.View.OnClickListener'], external)
            self.assertEqual({'id', 'count', 'width', 'isEnabled', 'label', 'area', 'move', 'all', 'toString', 'sides', 'baseName'}, set(by_name))
            self.assertEqual(('Getters', ['getId']), (by_name['id']['kind'], by_name['id']['aliases']))
            self.assertEqual('Properties', by_name['count']['kind'])
            self.assertEqual(['isEnabled', 'setEnabled', 'enabled'], by_name['isEnabled']['aliases'])
            self.assertEqual('Getters', by_name['label']['kind'])
            self.assertEqual([{'min': 1, 'max': 1, 'basis': 'params'}, {'min': 3, 'max': 3, 'basis': 'params'}], by_name['area']['arity']['overloads'])
            self.assertEqual([{'min': 1, 'max': 2, 'basis': 'params'}], by_name['move']['arity']['overloads'])
            self.assertEqual([{'min': 0, 'max': None, 'basis': 'params'}], by_name['all']['arity']['overloads'])
            self.assertEqual('org.autojs.autojs.sample.Shape', by_name['sides']['inheritedFrom'])
            self.assertEqual('org.autojs.autojs.sample.base.Base', by_name['baseName']['inheritedFrom'])

    def test_java_public_members_varargs_and_records(self):
        directory, root = host()
        with directory:
            sources = Sources(root)
            members, external, _, _ = class_members(sources, 'org.autojs.autojs.sample.Legacy')
            by_name = {m['name']: m for m in members}
            self.assertEqual({'code', 'TAG', 'read', 'log', 'close'}, set(by_name))
            self.assertEqual(['java.lang.Object', 'java.io.Closeable'], external)
            self.assertEqual([{'min': 1, 'max': 1, 'basis': 'params'}, {'min': 0, 'max': 0, 'basis': 'params'}], by_name['read']['arity']['overloads'])
            self.assertEqual([{'min': 0, 'max': None, 'basis': 'params'}], by_name['log']['arity']['overloads'])
            record, _, _, _ = class_members(sources, 'org.autojs.autojs.sample.Legacy.Options')
            self.assertEqual(['width', 'height'], [m['name'] for m in record])


class PrototypeTests(unittest.TestCase):
    def test_literal_registrations_aliases_collections_and_leftovers(self):
        directory, root = host()
        with directory:
            members, dynamic, source = prototype_members(Sources(root), {'class': 'org.autojs.autojs.sample.Handle', 'skipFunctions': ['folder'],
                                                                          'helperSuffixes': {'defineOp': ['Async']}})
            by_name = {m['name']: m for m in members}
            self.assertEqual({'pid', 'mode', 'path', 'toString', 'kill', 'hide', 'dismiss', 'send', 'sendAsync', 'click', 'back', 'on', 'off',
                              'toStringReadable', 'blockedGet', 'setValue'}, set(by_name))
            self.assertEqual(('Getters', 'GettersAndSetters', 'Properties', 'Functions'),
                             tuple(by_name[n]['kind'] for n in ('pid', 'mode', 'path', 'toString')))
            self.assertEqual('hide', by_name['dismiss']['aliasOf'])
            self.assertEqual([{'min': 1, 'max': 2, 'basis': 'defineOp'}], by_name['send']['arity']['overloads'])
            self.assertEqual('send', by_name['sendAsync']['aliasOf'])
            self.assertEqual(1, len(dynamic))
            self.assertIn('defineProperty(computeName()', dynamic[0])


class AttachTests(unittest.TestCase):
    def test_producers_from_return_types_listed_rows_and_problems(self):
        directory, root = host()
        with directory:
            metadata = root / 'script-modules.json'
            metadata.write_text(json.dumps({'format': 1, 'modules': [{
                'class': 'org.autojs.autojs.sample.SampleModule', 'name': 'sample', 'accessors': [{'name': 'current', 'type': 'org.autojs.autojs.sample.Widget?'}],
                'functions': [{'name': 'make', 'aliases': ['build'], 'overloads': [{'returns': 'org.autojs.autojs.sample.Widget'}]},
                              {'name': 'uri', 'aliases': [], 'overloads': [{'returns': 'android.net.Uri'}]}]}]}), encoding='utf-8')
            config = root / 'returned-objects.json'
            config.write_text(json.dumps({'objects': [
                {'object': 'Widget', 'class': 'org.autojs.autojs.sample.Widget', 'memberSource': 'kotlin-class', 'producers': ['sample.()', 'sample.missing'],
                 'exclude': ['all'], 'probe': 'sample.make.()', 'documentation': ['sample']},
                {'object': 'Handle', 'class': 'org.autojs.autojs.sample.Handle', 'memberSource': 'kotlin-class'},
                {'object': 'Listed', 'class': 'none.Listed', 'memberSource': 'policy', 'members': ['a', 'b']},
            ]}), encoding='utf-8')
            result = {'modules': [{'module': 'sample', 'class': 'org.autojs.autojs.sample.SampleModule', 'metadata': 'ksp'}],
                      'records': [{'api': f'sample.{n}', 'ownerModule': 'sample', 'member': n} for n in ('make', 'build', 'current', 'uri')]}
            attach(root, result, metadata, config)
            objects = {o['object']: o for o in result['returnedObjects']}
            self.assertEqual(['sample.()', 'sample.build', 'sample.current', 'sample.make'], objects['Widget']['producers'])
            self.assertNotIn('all', [m['name'] for m in objects['Widget']['members']])
            self.assertEqual(['a', 'b'], [m['name'] for m in objects['Listed']['members']])
            stats = result['returnedObjectsStats']
            self.assertEqual(['Widget: producer sample.missing is not an export', 'Handle: org.autojs.autojs.sample.Handle is a Scriptable; use augment-prototype'], stats['problems'])
            self.assertEqual({'android.net.Uri': ['sample.uri']}, stats['externalTypes'])


if __name__ == '__main__':
    unittest.main()
