// noinspection JSUnusedGlobalSymbols

(function () {
    const File = java.io.File;
    const FileInputStream = java.io.FileInputStream;
    const FileOutputStream = java.io.FileOutputStream;
    const ByteArrayOutputStream = java.io.ByteArrayOutputStream;
    const JArray = java.lang.reflect.Array;
    const ByteType = java.lang.Byte.TYPE;
    const Buffer = require('buffer').Buffer;
    const path = require('path');

    function resolvePath(filePath) {
        const value = String(filePath);
        const cwd = typeof process !== 'undefined' && process.cwd ? process.cwd() : runtime.files.cwd();
        return path.isAbsolute(value) ? value : path.resolve(cwd || '.', value);
    }

    function ensureParent(file) {
        const parent = file.getParentFile();
        if (parent && !parent.exists()) {
            parent.mkdirs();
        }
    }

    function readAllBytes(file) {
        const input = new FileInputStream(file);
        const output = new ByteArrayOutputStream();
        const chunk = JArray.newInstance(ByteType, 8192);
        try {
            let count;
            while ((count = input.read(chunk)) !== -1) {
                output.write(chunk, 0, count);
            }
            return output.toByteArray();
        } finally {
            input.close();
            output.close();
        }
    }

    function optionEncoding(options) {
        if (options == null) {
            return null;
        }
        if (typeof options === 'string') {
            return options;
        }
        if (typeof options === 'object' && options.encoding) {
            return String(options.encoding);
        }
        return null;
    }

    function javaArrayToArray(array) {
        const result = [];
        for (let i = 0; i < array.length; i += 1) {
            result.push(array[i]);
        }
        return result;
    }

    function toJavaBytes(data, encoding) {
        if (Buffer.isBuffer(data)) {
            return Buffer._toByteArray(data);
        }
        return Buffer._toByteArray(Buffer.from(String(data), encoding));
    }

    function statFor(file) {
        const stat = {
            size: file.length(),
            mtimeMs: file.lastModified(),
            ctimeMs: file.lastModified(),
            atimeMs: file.lastModified(),
            birthtimeMs: file.lastModified(),
            isFile() {
                return file.isFile();
            },
            isDirectory() {
                return file.isDirectory();
            },
            isSymbolicLink() {
                return false;
            },
        };
        stat.mtime = new Date(stat.mtimeMs);
        stat.ctime = new Date(stat.ctimeMs);
        stat.atime = new Date(stat.atimeMs);
        stat.birthtime = new Date(stat.birthtimeMs);
        return stat;
    }

    function readFileSync(filePath, options) {
        const file = new File(resolvePath(filePath));
        const encoding = optionEncoding(options);
        const bytes = readAllBytes(file);
        const buffer = Buffer.from(bytes);
        return encoding ? buffer.toString(encoding) : buffer;
    }

    function writeFileSync(filePath, data, options) {
        const file = new File(resolvePath(filePath));
        ensureParent(file);
        const encoding = optionEncoding(options);
        const output = new FileOutputStream(file, false);
        try {
            output.write(toJavaBytes(data, encoding));
        } finally {
            output.close();
        }
    }

    function appendFileSync(filePath, data, options) {
        const file = new File(resolvePath(filePath));
        ensureParent(file);
        const encoding = optionEncoding(options);
        const output = new FileOutputStream(file, true);
        try {
            output.write(toJavaBytes(data, encoding));
        } finally {
            output.close();
        }
    }

    function existsSync(filePath) {
        return new File(resolvePath(filePath)).exists();
    }

    function statSync(filePath) {
        const file = new File(resolvePath(filePath));
        if (!file.exists()) {
            throw new Error(`ENOENT: no such file or directory, stat '${filePath}'`);
        }
        return statFor(file);
    }

    function readdirSync(filePath, options) {
        const file = new File(resolvePath(filePath || '.'));
        const names = file.list();
        if (names == null) {
            throw new Error(`ENOTDIR: not a directory, scandir '${filePath}'`);
        }
        if (options && options.withFileTypes) {
            return javaArrayToArray(names).map((name) => {
                const child = new File(file, name);
                return {
                    name,
                    isFile() {
                        return child.isFile();
                    },
                    isDirectory() {
                        return child.isDirectory();
                    },
                    isSymbolicLink() {
                        return false;
                    },
                };
            });
        }
        return javaArrayToArray(names);
    }

    function mkdirSync(filePath, options) {
        const file = new File(resolvePath(filePath));
        const recursive = !!(options && options.recursive);
        const ok = recursive ? file.mkdirs() : file.mkdir();
        if (!ok && !file.isDirectory()) {
            throw new Error(`EEXIST: file already exists, mkdir '${filePath}'`);
        }
        return undefined;
    }

    function unlinkSync(filePath) {
        const file = new File(resolvePath(filePath));
        if (!file.delete()) {
            throw new Error(`ENOENT: no such file or directory, unlink '${filePath}'`);
        }
    }

    function rmSync(filePath, options) {
        const file = new File(resolvePath(filePath));
        if (!file.exists()) {
            if (options && options.force) {
                return;
            }
            throw new Error(`ENOENT: no such file or directory, rm '${filePath}'`);
        }
        if (file.isDirectory()) {
            const children = file.listFiles();
            if (children && children.length && !(options && options.recursive)) {
                throw new Error(`ENOTEMPTY: directory not empty, rm '${filePath}'`);
            }
            if (children) {
                javaArrayToArray(children).forEach((child) => rmSync(child.getPath(), { recursive: true, force: true }));
            }
        }
        if (!file.delete() && !(options && options.force)) {
            throw new Error(`EACCES: permission denied, rm '${filePath}'`);
        }
    }

    function renameSync(oldPath, newPath) {
        const source = new File(resolvePath(oldPath));
        const target = new File(resolvePath(newPath));
        ensureParent(target);
        if (!source.renameTo(target)) {
            throw new Error(`EACCES: permission denied, rename '${oldPath}' -> '${newPath}'`);
        }
    }

    function copyFileSync(sourcePath, targetPath) {
        writeFileSync(targetPath, readFileSync(sourcePath));
    }

    module.exports = {
        readFileSync,
        writeFileSync,
        appendFileSync,
        existsSync,
        statSync,
        lstatSync: statSync,
        readdirSync,
        mkdirSync,
        unlinkSync,
        rmSync,
        rmdirSync: rmSync,
        renameSync,
        copyFileSync,
        constants: {
            F_OK: 0,
            R_OK: 4,
            W_OK: 2,
            X_OK: 1,
        },
        accessSync(filePath) {
            if (!existsSync(filePath)) {
                throw new Error(`ENOENT: no such file or directory, access '${filePath}'`);
            }
        },
    };
})();
