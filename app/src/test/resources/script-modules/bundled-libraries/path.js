// noinspection JSUnusedGlobalSymbols

(function () {
    const sep = '/';
    const delimiter = ':';

    function assertPath(path) {
        if (typeof path !== 'string') {
            throw new TypeError('Path must be a string');
        }
    }

    function normalizeArray(parts, allowAboveRoot) {
        const res = [];
        for (let i = 0; i < parts.length; i += 1) {
            const part = parts[i];
            if (!part || part === '.') {
                continue;
            }
            if (part === '..') {
                if (res.length && res[res.length - 1] !== '..') {
                    res.pop();
                } else if (allowAboveRoot) {
                    res.push('..');
                }
            } else {
                res.push(part);
            }
        }
        return res;
    }

    function normalize(path) {
        assertPath(path);
        if (path.length === 0) {
            return '.';
        }
        const absolute = path.charAt(0) === sep;
        const trailing = path.endsWith(sep);
        let result = normalizeArray(path.split(/\/+/), !absolute).join(sep);
        if (!result && !absolute) {
            result = '.';
        }
        if (result && trailing) {
            result += sep;
        }
        return (absolute ? sep : '') + result;
    }

    function join() {
        if (arguments.length === 0) {
            return '.';
        }
        const joined = Array.prototype.slice.call(arguments).filter((part) => {
            assertPath(part);
            return part.length > 0;
        }).join(sep);
        return normalize(joined || '.');
    }

    function resolve() {
        let resolved = '';
        let absolute = false;
        for (let i = arguments.length - 1; i >= -1 && !absolute; i -= 1) {
            const path = i >= 0 ? arguments[i] : (typeof process !== 'undefined' && process.cwd ? process.cwd() : runtime.files.cwd());
            assertPath(path);
            if (!path) {
                continue;
            }
            resolved = path + sep + resolved;
            absolute = path.charAt(0) === sep;
        }
        resolved = normalizeArray(resolved.split(/\/+/), !absolute).join(sep);
        return (absolute ? sep : '') + resolved || '.';
    }

    // Where the last segment starts and ends, trailing separators left out (both 0 when there are only separators).
    // The functions below take the path as it is, without normalizing it, as path.posix of Node does.
    function lastSegment(path) {
        let end = path.length;
        while (end > 0 && path.charAt(end - 1) === sep) {
            end -= 1;
        }
        return { start: end === 0 ? 0 : path.lastIndexOf(sep, end - 1) + 1, end };
    }

    function extensionOf(base) {
        const index = base.lastIndexOf('.');
        return index <= 0 || base === '..' ? '' : base.slice(index);
    }

    function dirname(path) {
        assertPath(path);
        if (path.length === 0) {
            return '.';
        }
        const { start, end } = lastSegment(path);
        if (end === 0) {
            return sep;
        }
        if (start === 0) {
            return '.';
        }
        if (start === 1) {
            return sep;
        }
        // Only the separator before the last segment is dropped: "a//b" gives "a/", "//a" gives "//".
        return path.slice(0, start === 2 && path.charAt(0) === sep ? 2 : start - 1);
    }

    function basename(path, ext) {
        assertPath(path);
        if (ext !== undefined) {
            assertPath(ext);
        }
        const { start, end } = lastSegment(path);
        const base = path.slice(start, end);
        if (ext && ext !== base && base.endsWith(ext)) {
            return base.slice(0, -ext.length);
        }
        return ext && ext === path ? '' : base;
    }

    function extname(path) {
        assertPath(path);
        const { start, end } = lastSegment(path);
        return extensionOf(path.slice(start, end));
    }

    function isAbsolute(path) {
        assertPath(path);
        return path.charAt(0) === sep;
    }

    function relative(from, to) {
        assertPath(from);
        assertPath(to);
        const fromParts = resolve(from).slice(1).split(sep).filter(Boolean);
        const toParts = resolve(to).slice(1).split(sep).filter(Boolean);
        while (fromParts.length && toParts.length && fromParts[0] === toParts[0]) {
            fromParts.shift();
            toParts.shift();
        }
        return fromParts.map(() => '..').concat(toParts).join(sep) || '';
    }

    function parse(path) {
        assertPath(path);
        const root = isAbsolute(path) ? sep : '';
        const { start, end } = lastSegment(path);
        const base = path.slice(start, end);
        const ext = extensionOf(base);
        return {
            root,
            dir: start > 1 ? path.slice(0, start - 1) : root,
            base,
            ext,
            name: ext ? base.slice(0, -ext.length) : base,
        };
    }

    function format(pathObject) {
        if (!pathObject || typeof pathObject !== 'object') {
            throw new TypeError('Parameter "pathObject" must be an object');
        }
        const dir = pathObject.dir || pathObject.root;
        const ext = pathObject.ext ? (String(pathObject.ext).charAt(0) === '.' ? '' : '.') + pathObject.ext : '';
        const base = pathObject.base || (pathObject.name || '') + ext;
        if (!dir) {
            return base;
        }
        return dir === pathObject.root ? dir + base : dir + sep + base;
    }

    const path = {
        sep,
        delimiter,
        normalize,
        join,
        resolve,
        dirname,
        basename,
        extname,
        isAbsolute,
        relative,
        parse,
        format,
    };

    path.posix = path;
    module.exports = path;
})();
