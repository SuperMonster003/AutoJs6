import io
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

import language_runtimes as runtimes

HERE = Path(__file__).resolve().parent

LUA = '''
int install_autojs_module(lua_State* state) {
    lua_getglobal(state, LUA_MATHLIBNAME);
    lua_getfield(state, -1, "randomseed");
    lua_pushcclosure(state, controlled_math_randomseed, 1);
    lua_setfield(state, -2, "randomseed");
    lua_pop(state, 1);
    lua_pushcfunction(state, autojs_console_print);
    lua_setglobal(state, "print");
    lua_newtable(state);
    lua_newtable(state);
    lua_pushcfunction(state, autojs_console_log);
    lua_setfield(state, -2, "log");
    lua_setfield(state, -2, "console");
    lua_pushcfunction(state, autojs_now);
    lua_setfield(state, -2, "now");
    if (!push_native_arguments(state, arguments)) {
        return luaL_error(state, "invalid");
    }
    lua_setfield(state, -2, "arguments");
    lua_pushvalue(state, -1);
    lua_newtable(state);
    lua_newtable(state);
    lua_pushcclosure(state, restricted_require, 3);
    lua_setglobal(state, "require");
    lua_pop(state, 1);
    return 0;
}
'''


def class_file(name, methods, access=0x601):
    """A class file declaring `methods` [(name, descriptor, access, thrown classes)]."""
    pool, index = [], {}

    def utf8(text):
        if ("u", text) not in index:
            pool.append(b"\x01" + struct.pack(">H", len(text)) + text.encode())
            index[("u", text)] = len(pool)
        return index[("u", text)]

    def cls(text):
        if ("c", text) not in index:
            target = utf8(text)
            pool.append(b"\x07" + struct.pack(">H", target))
            index[("c", text)] = len(pool)
        return index[("c", text)]

    this, parent, body = cls(name), cls("java/lang/Object"), b""
    for method, descriptor, flags, thrown in methods:
        attributes = b""
        if thrown:
            table = struct.pack(f">H{len(thrown)}H", len(thrown), *map(cls, thrown))
            attributes = struct.pack(">HHI", 1, utf8("Exceptions"), len(table)) + table
        body += struct.pack(">HHH", flags, utf8(method), utf8(descriptor)) + (attributes or b"\0\0")
    return (b"\xca\xfe\xba\xbe\0\0\0\x34" + struct.pack(">H", len(pool) + 1) + b"".join(pool)
            + struct.pack(">HHHHH", access, this, parent, 0, 0) + struct.pack(">H", len(methods)) + body + b"\0\0")


class ReadersTest(unittest.TestCase):
    def test_python_package(self):
        with tempfile.TemporaryDirectory() as temp:
            package = Path(temp, "pkg")
            package.mkdir()
            (package / "__init__.py").write_text('from . import mod\nfrom .errors import Boom\n\n\ndef toast(text: str) -> None:\n    pass\n\n\n'
                                                 '__all__ = ("mod", "toast", "Boom")\n', encoding="utf-8")
            (package / "mod.py").write_text('__all__ = ["LIMIT", "run"]\nLIMIT = 3\n\n\ndef run(path: str, *, limit: int = LIMIT) -> bool:\n    pass\n\n\n'
                                            'def hidden():\n    pass\n', encoding="utf-8")
            (package / "errors.py").write_text("__all__ = ['Boom']\n\n\nclass Boom(RuntimeError):\n    pass\n", encoding="utf-8")
            names = runtimes.read_python(Path(temp), {"source": "pkg"})
        self.assertEqual(names, {"functions": {"mod.run": "(path: str, *, limit: int=LIMIT) -> bool", "toast": "(text: str) -> None"},
                                 "constants": ["mod.LIMIT"], "classes": ["Boom"]})

    def test_lua_module_table(self):
        with tempfile.TemporaryDirectory() as temp:
            Path(temp, "jni.cpp").write_text(LUA, encoding="utf-8")
            names = runtimes.read_lua(Path(temp), {"source": "jni.cpp", "function": "install_autojs_module"})
        self.assertEqual(names, {"functions": {"console.log": None, "now": None}, "values": ["arguments"]})

    def test_jvm_reachable_types(self):
        api = "org/demo/api/"
        classes = {
            api + "Entry": class_file(api + "Entry", [("run", f"(L{api}Context;)Ljava/lang/Object;", 0x401, ["java/lang/Exception"])]),
            api + "Context": class_file(api + "Context", [("sleep", "(J)V", 0x401, [api + "Cancelled", "java/lang/InterruptedException"]),
                                                          ("console", f"()L{api}Console;", 0x401, []),
                                                          ("access$sleep$jd", f"(L{api}Context;)V", 0x1009, [])]),
            api + "Console": class_file(api + "Console", [("log", "(Ljava/lang/String;[I)Z", 0x401, [])]),
            api + "Cancelled": class_file(api + "Cancelled", [("<init>", "()V", 0x1, [])], access=0x31),
            api + "Unused": class_file(api + "Unused", [("call", "()V", 0x401, [])]),
        }
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, "w") as archive:
            for name, data in classes.items():
                archive.writestr(name + ".class", data)
        with tempfile.TemporaryDirectory() as temp:
            with zipfile.ZipFile(Path(temp, "api.aar"), "w") as aar:
                aar.writestr("classes.jar", jar.getvalue())
            names = runtimes.read_jvm(Path(temp), {"source": "api.aar", "entry": api + "Entry"})
        self.assertEqual(names, {"functions": {"Entry.run": "(Context) -> Object", "Context.sleep": "(long) -> void",
                                               "Context.console": "() -> Console", "Console.log": "(String, int[]) -> boolean"},
                                 "types": ["Entry", "Context", "Cancelled", "Console"]})


class CheckPageTest(unittest.TestCase):
    PYTHON = {"kind": "python-package", "functions": {"mod.run": "(path: str, *, limit: int=LIMIT) -> bool", "toast": "(text: str) -> None"},
              "constants": ["mod.LIMIT"], "classes": ["Boom"]}
    PAGE = ("# Python\n\n## pkg.mod\n\n### pkg.mod.run(path, *, limit=pkg.mod.LIMIT)\n\n### pkg.toast(text)\n\n"
            "| 常量 | 值 |\n| --- | --- |\n| `pkg.mod.LIMIT` | `3` |\n| `pkg.Boom` | RuntimeError |\n| `OTHER_CODE` | x |\n")

    def test_python_page_passes(self):
        self.assertEqual(runtimes.check_page(self.PAGE, "pkg.", self.PYTHON), [])

    def test_missing_extra_and_parameters(self):
        page = self.PAGE.replace("limit=pkg.mod.LIMIT", "limit").replace("### pkg.toast(text)", "### pkg.notice(text)")
        self.assertEqual(runtimes.check_page(page, "pkg.", self.PYTHON),
                         ["missing pkg.toast", "extra pkg.notice", "pkg.mod.run(path, *, limit) differs from (path, *, limit=)"])

    def test_function_needs_a_heading(self):
        page = self.PAGE.replace("### pkg.toast(text)", "### pkg.toast")
        self.assertEqual(runtimes.check_page(page, "pkg.", self.PYTHON), ["pkg.toast needs a heading with its parameters"])

    def test_jvm_parameter_count(self):
        listed = {"kind": "jvm-entry-aar", "functions": {"Console.log": "(String, int[]) -> boolean", "Context.app": "() -> App"}, "types": ["Console"]}
        page = "### Console.log(message)\n\n### Context.app()\n\n| `Console` | interface |\n"
        self.assertEqual(runtimes.check_page(page, "", listed), ["Console.log(message) differs from (String, int[]) -> boolean"])


class StubsTest(unittest.TestCase):
    def test_python_stub_package(self):
        with tempfile.TemporaryDirectory() as temp:
            package = Path(temp)
            (package / "__init__.pyi").write_text("from . import mod as mod\nfrom .errors import Boom as Boom\nimport os\n\n\n"
                                                  "def toast(text: str) -> None: ...\n\n\ndef _hidden() -> None: ...\n", encoding="utf-8")
            (package / "mod.pyi").write_text("import builtins\nfrom typing import Final\n\nLIMIT: Final[int]\n\n\n"
                                             "def run(path: str, *, limit: int = ...) -> builtins.list[str]: ...\n", encoding="utf-8")
            (package / "errors.pyi").write_text("class Boom(RuntimeError): ...\n\n\nclass Other(RuntimeError): ...\n", encoding="utf-8")
            names = runtimes.read_python_stubs(package)
        self.assertEqual(names, {"functions": {"mod.run": "(path: str, *, limit: int=...) -> list[str]", "toast": "(text: str) -> None"},
                                 "constants": ["mod.LIMIT"], "classes": ["Boom"]})
        listed = {"functions": {"mod.run": "(path: str, *, limit: int=LIMIT) -> list[str]", "toast": "(text: str) -> None"},
                  "constants": ["mod.LIMIT"], "classes": ["Boom"]}
        view = runtimes.stub_view(names, package), runtimes.stub_view(listed, package)
        self.assertEqual(runtimes.differences(*view), [])
        listed["functions"]["mod.run"] = "(path: str, limit: int=LIMIT) -> list[str]"
        self.assertEqual(runtimes.differences(runtimes.stub_view(names, package), runtimes.stub_view(listed, package)), ["~mod.run"])

    def test_lua_meta_definitions(self):
        with tempfile.TemporaryDirectory() as temp:
            Path(temp, "autojs.lua").write_text("---@meta autojs\nlocal autojs = {}\n---@type table\nautojs.arguments = {}\n"
                                                "autojs.console = {}\nfunction autojs.console.log(text) end\n"
                                                "function autojs.now() end\nreturn autojs\n", encoding="utf-8")
            names = runtimes.read_lua_meta(Path(temp, "autojs.lua"), "autojs.")
        self.assertEqual(names, {"functions": {"console.log": None, "now": None}, "values": ["arguments"]})

    def test_jvm_language_index(self):
        index = {"globals": [{"name": "JvmAppApi", "type": "class"}, {"name": "View", "type": "class"}],
                 "modules": {"JvmAppApi": [{"name": "launch", "signature": "JvmAppApi.launch(packageName: String): boolean"}],
                             "View": [{"name": "post", "signature": "View.post(action: Runnable): boolean"}]}}
        with tempfile.TemporaryDirectory() as temp:
            Path(temp, "java.js").write_text('(function(global) {\n    registry["java"] = ' + runtimes.json.dumps(index) + ';\n})(window);\n',
                                             encoding="utf-8")
            names = runtimes.read_jvm_index(Path(temp, "java.js"))
        self.assertEqual(names, {"functions": {"JvmAppApi.launch": 1}, "types": ["JvmAppApi"]})
        listed = {"functions": {"JvmAppApi.launch": "(String) -> boolean"}, "types": ["JvmAppApi"]}
        self.assertEqual(runtimes.differences(names["functions"], runtimes.stub_view(listed, Path("java.js"))), ["-JvmAppApi"])

    def test_host_entry_api(self):
        with tempfile.TemporaryDirectory() as temp:
            Path(temp, "JvmEntryApi.kt").write_text("fun interface AutoJsJvmEntry {\n    @Throws(Exception::class)\n"
                                                    "    fun run(context: JvmScriptContext): Any?\n}\n\ninterface JvmScriptContext {\n"
                                                    "    fun args(): Map<String, Any?>\n    fun sleep(millis: Long)\n"
                                                    "    fun isCancellationRequested(): Boolean = true\n}\n\n"
                                                    "class JvmCancellationException @JvmOverloads constructor(\n    message: String = \"x\",\n) : Exception(message)\n",
                                                    encoding="utf-8")
            names = runtimes.read_host_entry_api(Path(temp, "JvmEntryApi.kt"))
        self.assertEqual(names, {"AutoJsJvmEntry": None, "AutoJsJvmEntry.run": 1, "JvmScriptContext": None, "JvmScriptContext.args": 0,
                                 "JvmScriptContext.sleep": 1, "JvmScriptContext.isCancellationRequested": 0, "JvmCancellationException": None})


class MainTest(unittest.TestCase):
    def test_plugin_methods_must_exist_in_the_host_entry_api(self):
        api = "org/autojs/plugin/jvmsource/api/"
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr(api + "AutoJsJvmEntry.class", class_file(api + "AutoJsJvmEntry", [("run", f"(L{api}JvmScriptContext;)Ljava/lang/Object;", 0x401, [])]))
            archive.writestr(api + "JvmScriptContext.class", class_file(api + "JvmScriptContext", [("sleep", "(J)V", 0x401, []), ("extra", "()V", 0x401, [])]))
        with tempfile.TemporaryDirectory() as temp:
            Path(temp, "plugin", "protocol").mkdir(parents=True)
            with zipfile.ZipFile(Path(temp, "plugin", "protocol", "jvm-source-api.aar"), "w") as aar:
                aar.writestr("classes.jar", jar.getvalue())
            entry_api = Path(temp, "host", "plugin-api/jvm-source-api/src/main/java/org/autojs/plugin/jvmsource/api/JvmEntryApi.kt")
            entry_api.parent.mkdir(parents=True)
            entry_api.write_text("fun interface AutoJsJvmEntry {\n    fun run(context: JvmScriptContext): Any?\n}\n\n"
                                 "interface JvmScriptContext {\n    fun sleep(millis: Long)\n    fun toast(message: String)\n}\n", encoding="utf-8")
            plugins = [f"--plugin={name}={temp}/none" for name in ("python", "kotlin", "lua")] + [f"--plugin=java={temp}/plugin"]
            result = subprocess.run([sys.executable, str(HERE / "language_runtimes.py"), "--host", f"{temp}/host", "--documentation", temp,
                                     "--ace", f"{temp}/none", *plugins], capture_output=True, text=True)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("host FAIL: JvmScriptContext.extra not in JvmEntryApi.kt", result.stdout)

    def test_without_checkouts_everything_is_skipped(self):
        with tempfile.TemporaryDirectory() as temp:
            plugins = [f"--plugin={name}={temp}/none" for name in ("python", "java", "kotlin", "lua")]
            result = subprocess.run([sys.executable, str(HERE / "language_runtimes.py"), "--documentation", temp, "--ace", f"{temp}/none",
                                     *plugins], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout.count("source skipped"), 4)
        self.assertEqual(result.stdout.count("doc skipped"), 4)
        self.assertEqual(result.stdout.count("stubs skipped"), 7)


if __name__ == "__main__":
    unittest.main()
