// Writes a JSON description of the script API surface: every own property of `global`, and for objects and
// functions two more levels of own properties, each with its attributes (enumerable / writable / configurable /
// accessor), value kind (function name and length, object tag, Java class name, primitive value) and prototype
// chain. Accessor properties are recorded without being evaluated; data properties are read as they are.
// Driven by tools/augment/device_surface_snapshot.py; the output path is fixed.
let OUT = "/sdcard/Download/aj6-augment-bench/surface.json";
let MAX_DEPTH = 2;
let toStr = Object.prototype.toString;
let JAVA_TAGS = { "[object JavaObject]": 1, "[object JavaClass]": 1, "[object JavaPackage]": 1, "[object JavaArray]": 1 };

function tagOf(v) {
    try {
        return toStr.call(v);
    } catch (e) {
        return "[error " + e + "]";
    }
}

function javaClassOf(v) {
    try {
        if (v instanceof java.lang.Object) return String(v.getClass().getName());
    } catch (e) {
        // Not a Java object or the access failed; the tag already says which.
    }
    return undefined;
}

function protoChain(o) {
    let chain = [];
    let p;
    try {
        p = Object.getPrototypeOf(o);
    } catch (e) {
        return ["[error " + e + "]"];
    }
    let guard = 0;
    while (p !== null && p !== undefined && guard++ < 12) {
        let tag = tagOf(p);
        let cls = javaClassOf(p);
        let ctor;
        try {
            ctor = Object.prototype.hasOwnProperty.call(p, "constructor") && typeof p.constructor === "function" ? p.constructor.name : undefined;
        } catch (e) {
            ctor = undefined;
        }
        chain.push(tag + (cls ? " " + cls : "") + (ctor ? " ctor=" + ctor : "") + (p === Object.prototype ? " Object.prototype" : p === Function.prototype ? " Function.prototype" : p === Array.prototype ? " Array.prototype" : ""));
        if (p === Object.prototype || p === Function.prototype) break;
        try {
            p = Object.getPrototypeOf(p);
        } catch (e) {
            chain.push("[error " + e + "]");
            break;
        }
    }
    return chain;
}

function describeValue(v, depth) {
    if (v === null) return { type: "null" };
    let t = typeof v;
    let d = { type: t };
    if (t === "undefined") return d;
    if (t === "function") {
        d.name = v.name;
        d.length = v.length;
        d.tag = tagOf(v);
        if (depth < MAX_DEPTH) d.props = describeOwn(v, depth + 1);
        return d;
    }
    if (t === "object") {
        d.tag = tagOf(v);
        if (JAVA_TAGS[d.tag]) {
            let cls = javaClassOf(v);
            if (cls) d.javaClass = cls;
            return d;
        }
        if (Array.isArray(v)) {
            d.length = v.length;
            return d;
        }
        d.proto = protoChain(v);
        if (depth < MAX_DEPTH) d.props = describeOwn(v, depth + 1);
        return d;
    }
    if (t === "string") d.value = v.length > 80 ? v.slice(0, 80) + "..." : v;
    else if (t === "number" || t === "boolean" || t === "bigint") d.value = String(v);
    else if (t === "symbol") d.value = String(v);
    return d;
}

function describeOwn(o, depth) {
    let names;
    try {
        names = Object.getOwnPropertyNames(o);
    } catch (e) {
        return { "[error]": { error: String(e) } };
    }
    let result = {};
    names.forEach(name => {
        let desc;
        try {
            desc = Object.getOwnPropertyDescriptor(o, name);
        } catch (e) {
            result[name] = { error: String(e) };
            return;
        }
        if (!desc) {
            result[name] = { missing: true };
            return;
        }
        let entry = { enumerable: !!desc.enumerable, configurable: !!desc.configurable };
        if ("get" in desc || "set" in desc) {
            entry.accessor = true;
            entry.hasGetter = typeof desc.get === "function";
            entry.hasSetter = typeof desc.set === "function";
        } else {
            entry.writable = !!desc.writable;
            try {
                entry.value = describeValue(desc.value, depth);
            } catch (e) {
                entry.value = { error: String(e) };
            }
        }
        result[name] = entry;
    });
    return result;
}

let snapshot = {
    versionName: String(autojs.versionName),
    versionCode: Number(autojs.versionCode),
    device: String(device.brand) + " " + String(device.model) + " API " + String(device.sdkInt),
    keysOrder: Object.keys(global),
    aliases: {},
    global: describeOwn(global, 0),
};
Object.getOwnPropertyNames(global).forEach(name => {
    if (name.charAt(0) === "$" && name.length > 1) {
        let base = name.slice(1);
        try {
            if (Object.prototype.hasOwnProperty.call(global, base)) snapshot.aliases[name] = global[name] === global[base] ? "same" : "different";
        } catch (e) {
            snapshot.aliases[name] = "error " + e;
        }
    }
});
files.write(OUT, JSON.stringify(snapshot));
console.log("AJ6SURFACE written " + OUT + " keys=" + snapshot.keysOrder.length);
