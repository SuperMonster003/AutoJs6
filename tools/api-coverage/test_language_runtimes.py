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


class MainTest(unittest.TestCase):
    def test_without_checkouts_everything_is_skipped(self):
        with tempfile.TemporaryDirectory() as temp:
            plugins = [f"--plugin={name}={temp}/none" for name in ("python", "java", "kotlin", "lua")]
            result = subprocess.run([sys.executable, str(HERE / "language_runtimes.py"), "--documentation", temp, *plugins],
                                    capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout.count("source skipped"), 4)
        self.assertEqual(result.stdout.count("doc skipped"), 4)


if __name__ == "__main__":
    unittest.main()
