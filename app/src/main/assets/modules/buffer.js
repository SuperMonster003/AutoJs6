// noinspection JSUnusedGlobalSymbols

(function () {
    const JArray = java.lang.reflect.Array;
    const JString = java.lang.String;
    const ByteType = java.lang.Byte.TYPE;

    const hasTypedArray = typeof Uint8Array === 'function';

    function normalizeEncoding(encoding) {
        const enc = String(encoding || 'utf8').toLowerCase().replace(/[-_]/g, '');
        switch (enc) {
            case 'utf8':
            case 'utf':
                return 'UTF-8';
            case 'utf16le':
            case 'ucs2':
                return 'UTF-16LE';
            case 'ascii':
                return 'US-ASCII';
            case 'latin1':
            case 'binary':
                return 'ISO-8859-1';
            case 'base64':
            case 'hex':
                throw new Error(`Buffer encoding ${encoding} is not supported in Rhino compatibility mode`);
            default:
                return encoding || 'UTF-8';
        }
    }

    function makeArray(length) {
        const size = Math.max(0, Number(length) || 0) >>> 0;
        if (hasTypedArray) {
            return new Uint8Array(size);
        }
        const array = new Array(size);
        for (let i = 0; i < size; i += 1) {
            array[i] = 0;
        }
        return array;
    }

    function mark(buffer) {
        try {
            Object.setPrototypeOf(buffer, Buffer.prototype);
        } catch (_) {
            Object.getOwnPropertyNames(Buffer.prototype).forEach((name) => {
                if (name !== 'constructor' && typeof Buffer.prototype[name] === 'function') {
                    buffer[name] = Buffer.prototype[name];
                }
            });
        }
        try {
            Object.defineProperty(buffer, '_isBuffer', { value: true, enumerable: false });
        } catch (_) {
            buffer._isBuffer = true;
        }
        return buffer;
    }

    function signedByte(value) {
        const byte = Number(value) & 0xff;
        return byte > 127 ? byte - 256 : byte;
    }

    function toJavaByteArray(value, start, end) {
        const from = Math.max(0, Number(start) || 0);
        const to = Math.min(value.length >>> 0, end == null ? value.length : Number(end));
        const bytes = JArray.newInstance(ByteType, Math.max(0, to - from));
        for (let i = from; i < to; i += 1) {
            JArray.setByte(bytes, i - from, signedByte(value[i]));
        }
        return bytes;
    }

    function fromJavaBytes(bytes) {
        const length = bytes.length || 0;
        const out = makeArray(length);
        for (let i = 0; i < length; i += 1) {
            out[i] = Number(bytes[i]) & 0xff;
        }
        return mark(out);
    }

    function fromArrayLike(value) {
        const length = Math.max(0, Number(value.length) || 0) >>> 0;
        const out = makeArray(length);
        for (let i = 0; i < length; i += 1) {
            out[i] = Number(value[i]) & 0xff;
        }
        return mark(out);
    }

    function Buffer(value, encoding) {
        return Buffer.from(value, encoding);
    }

    Buffer.from = function (value, encoding) {
        if (value == null) {
            throw new TypeError('The first argument must not be null or undefined');
        }
        if (Buffer.isBuffer(value)) {
            return fromArrayLike(value);
        }
        if (typeof value === 'string') {
            return fromJavaBytes(new JString(String(value)).getBytes(normalizeEncoding(encoding)));
        }
        if (hasTypedArray && value instanceof ArrayBuffer) {
            return fromArrayLike(new Uint8Array(value));
        }
        if (Array.isArray(value) || typeof value.length === 'number') {
            return fromArrayLike(value);
        }
        if (value.bytes && typeof value.bytes.length === 'number') {
            return fromArrayLike(value.bytes);
        }
        return Buffer.from(String(value), encoding);
    };

    Buffer.alloc = function (size, fill, encoding) {
        const out = mark(makeArray(size));
        if (fill !== undefined) {
            const source = typeof fill === 'number'
                ? [ Number(fill) & 0xff ]
                : Buffer.from(String(fill), encoding);
            for (let i = 0; i < out.length; i += 1) {
                out[i] = source[i % source.length] & 0xff;
            }
        }
        return out;
    };

    Buffer.allocUnsafe = function (size) {
        return mark(makeArray(size));
    };

    Buffer.isBuffer = function (value) {
        return !!(value && value._isBuffer === true);
    };

    Buffer.byteLength = function (value, encoding) {
        return Buffer.isBuffer(value) ? value.length : Buffer.from(String(value), encoding).length;
    };

    Buffer.concat = function (list, totalLength) {
        if (!Array.isArray(list)) {
            throw new TypeError('The "list" argument must be an Array');
        }
        const length = totalLength == null
            ? list.reduce((sum, item) => sum + Buffer.from(item).length, 0)
            : Number(totalLength) >>> 0;
        const out = Buffer.allocUnsafe(length);
        let offset = 0;
        list.forEach((item) => {
            const buffer = Buffer.from(item);
            for (let i = 0; i < buffer.length && offset < out.length; i += 1) {
                out[offset++] = buffer[i];
            }
        });
        return out;
    };

    Buffer._toByteArray = toJavaByteArray;

    Buffer.prototype = Object.create(hasTypedArray ? Uint8Array.prototype : Array.prototype);
    Buffer.prototype.constructor = Buffer;
    Buffer.prototype.toString = function (encoding, start, end) {
        return String(new JString(toJavaByteArray(this, start, end), normalizeEncoding(encoding)));
    };
    Buffer.prototype.toJSON = function () {
        return { type: 'Buffer', data: Array.prototype.slice.call(this, 0) };
    };
    Buffer.prototype.slice = function (start, end) {
        const length = this.length >>> 0;
        let from = start == null ? 0 : Number(start);
        let to = end == null ? length : Number(end);
        if (from < 0) from = Math.max(length + from, 0);
        if (to < 0) to = Math.max(length + to, 0);
        to = Math.min(to, length);
        const out = makeArray(Math.max(0, to - from));
        for (let i = from; i < to; i += 1) {
            out[i - from] = this[i] & 0xff;
        }
        return mark(out);
    };
    Buffer.prototype.write = function (string, offset, length, encoding) {
        const start = Math.max(0, Number(offset) || 0);
        const source = Buffer.from(String(string), encoding);
        const limit = Math.min(length == null ? source.length : Number(length), this.length - start);
        for (let i = 0; i < limit; i += 1) {
            this[start + i] = source[i];
        }
        return limit;
    };
    Buffer.prototype.readUInt8 = function (offset) {
        return this[Number(offset) || 0] & 0xff;
    };
    Buffer.prototype.readInt8 = function (offset) {
        const value = this.readUInt8(offset);
        return value & 0x80 ? value - 0x100 : value;
    };
    Buffer.prototype.writeUInt8 = function (value, offset) {
        this[Number(offset) || 0] = Number(value) & 0xff;
        return (Number(offset) || 0) + 1;
    };
    Buffer.prototype.writeInt8 = Buffer.prototype.writeUInt8;
    Buffer.prototype.readUInt16BE = function (offset) {
        const i = Number(offset) || 0;
        return ((this[i] & 0xff) << 8) | (this[i + 1] & 0xff);
    };
    Buffer.prototype.readUInt16LE = function (offset) {
        const i = Number(offset) || 0;
        return (this[i] & 0xff) | ((this[i + 1] & 0xff) << 8);
    };
    Buffer.prototype.writeUInt16BE = function (value, offset) {
        const i = Number(offset) || 0;
        this[i] = (Number(value) >>> 8) & 0xff;
        this[i + 1] = Number(value) & 0xff;
        return i + 2;
    };
    Buffer.prototype.writeUInt16LE = function (value, offset) {
        const i = Number(offset) || 0;
        this[i] = Number(value) & 0xff;
        this[i + 1] = (Number(value) >>> 8) & 0xff;
        return i + 2;
    };
    Buffer.prototype.readUInt32BE = function (offset) {
        const i = Number(offset) || 0;
        return ((this[i] & 0xff) * 0x1000000) + (((this[i + 1] & 0xff) << 16) | ((this[i + 2] & 0xff) << 8) | (this[i + 3] & 0xff));
    };
    Buffer.prototype.readUInt32LE = function (offset) {
        const i = Number(offset) || 0;
        return (this[i] & 0xff) | ((this[i + 1] & 0xff) << 8) | ((this[i + 2] & 0xff) << 16) | ((this[i + 3] & 0xff) * 0x1000000);
    };
    Buffer.prototype.writeUInt32BE = function (value, offset) {
        const i = Number(offset) || 0;
        const n = Number(value) >>> 0;
        this[i] = (n >>> 24) & 0xff;
        this[i + 1] = (n >>> 16) & 0xff;
        this[i + 2] = (n >>> 8) & 0xff;
        this[i + 3] = n & 0xff;
        return i + 4;
    };
    Buffer.prototype.writeUInt32LE = function (value, offset) {
        const i = Number(offset) || 0;
        const n = Number(value) >>> 0;
        this[i] = n & 0xff;
        this[i + 1] = (n >>> 8) & 0xff;
        this[i + 2] = (n >>> 16) & 0xff;
        this[i + 3] = (n >>> 24) & 0xff;
        return i + 4;
    };

    Buffer.prototype.readInt16BE = Buffer.prototype.readUInt16BE;
    Buffer.prototype.readInt16LE = Buffer.prototype.readUInt16LE;
    Buffer.prototype.readInt32BE = Buffer.prototype.readUInt32BE;
    Buffer.prototype.readInt32LE = Buffer.prototype.readUInt32LE;
    Buffer.prototype.writeInt16BE = Buffer.prototype.writeUInt16BE;
    Buffer.prototype.writeInt16LE = Buffer.prototype.writeUInt16LE;
    Buffer.prototype.writeInt32BE = Buffer.prototype.writeUInt32BE;
    Buffer.prototype.writeInt32LE = Buffer.prototype.writeUInt32LE;

    module.exports = {
        Buffer,
    };
})();
