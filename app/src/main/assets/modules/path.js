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
        let result = normalizeArray(path.split(/[\\/]+/), !absolute).join(sep);
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
        resolved = normalizeArray(resolved.split(/[\\/]+/), !absolute).join(sep);
        return (absolute ? sep : '') + resolved || '.';
    }

    function dirname(path) {
        assertPath(path);
        if (path.length === 0) {
            return '.';
        }
        const normalized = normalize(path);
        if (normalized === sep) {
            return sep;
        }
        const index = normalized.lastIndexOf(sep);
        if (index < 0) {
            return '.';
        }
        if (index === 0) {
            return sep;
        }
        return normalized.slice(0, index);
    }

    function basename(path, ext) {
        assertPath(path);
        let base = normalize(path).split(sep).pop();
        if (ext && base.endsWith(ext)) {
            base = base.slice(0, -ext.length);
        }
        return base;
    }

    function extname(path) {
        assertPath(path);
        const base = basename(path);
        const index = base.lastIndexOf('.');
        return index <= 0 ? '' : base.slice(index);
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
        const dir = dirname(path);
        const base = basename(path);
        const ext = extname(base);
        return {
            root: isAbsolute(path) ? sep : '',
            dir: dir === '.' ? '' : dir,
            base,
            ext,
            name: ext ? base.slice(0, -ext.length) : base,
        };
    }

    function format(pathObject) {
        if (!pathObject || typeof pathObject !== 'object') {
            throw new TypeError('Parameter "pathObject" must be an object');
        }
        const dir = pathObject.dir || pathObject.root || '';
        const base = pathObject.base || ((pathObject.name || '') + (pathObject.ext || ''));
        return dir ? join(dir, base) : base;
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
