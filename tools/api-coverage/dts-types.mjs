// Comparison of d.ts parameter / return types with the Kotlin types of script-modules.json (api-coverage P2.4).
// kotlin_types.py maps the Kotlin types to descriptors (type-map.json); typeExpressions lists the TypeScript texts they
// need, which the caller declares in its query file, and compareTypes classifies every position (README "Types"):
// match / refined / coerced are consistent, narrow / wide / error differ, unmapped is not compared.

// Texts the descriptors need as types: TypeScript texts and qualified Java class names.
export function typeExpressions(rows) {
    const texts = new Set();
    const add = type => {
        if (type?.ts) for (const key of ["nominal", "accepted", "returns"]) if (type.ts[key]) texts.add(type.ts[key]);
        if (type?.java) texts.add(type.java);
    };
    for (const row of rows) {
        for (const overload of row.overloads || []) {
            overload.params.forEach(param => add(param.type));
            add(overload.returns.type);
        }
        add(row.value?.type);
    }
    return [...texts].sort();
}

// The query file text declaring each type text as `declare const __type_<n>: <text>;` (a module, so nothing leaks
// into the global names the surface checks enumerate).
export function declarationText(texts) {
    return texts.map((text, index) => `declare const __type_${index}: ${text};`).join("\n") + "\nexport {};\n";
}

/**
 * ts, checker, source: the TypeScript module, checker and query source file; typeOf(path): the d.ts type of an API
 * path (verify-surfaces.mjs typeOf, null when it does not resolve); texts: the array given to declarationText;
 * awaited(row): compare the awaited return type (asynchronous runtime members, verify-runtime-types.mjs).
 */
export function compareTypes({ts, checker, source, typeOf, texts, awaited = () => false}, rows) {
    const byText = new Map();
    for (const statement of source.statements) {
        if (!ts.isVariableStatement(statement)) continue;
        for (const declaration of statement.declarationList.declarations) {
            const match = /^__type_(\d+)$/.exec(declaration.name.text);
            if (match) byText.set(texts[Number(match[1])], checker.getTypeAtLocation(declaration.name));
        }
    }
    const undefinedType = checker.getUndefinedType(), nullType = checker.getNullType();
    const union = types => checker.getUnionType(types);
    const parts = type => type.isUnion() ? type.types : [type];
    const has = (type, flag) => parts(type).some(part => part.flags & flag);
    const without = (type, flag) => union(parts(type).filter(part => !(part.flags & flag)));
    const assignable = (a, b) => checker.isTypeAssignableTo(a, b);
    const isError = type => Boolean(type.flags & ts.TypeFlags.Any) && type.intrinsicName === "error";
    // d.ts any / unknown: nothing to compare with. Type parameters and the types built on them (Awaited<T>, T[K]) are
    // replaced by their constraints, unknown when they have none; one that stays generic is not compared either.
    const vague = type => parts(type).some(part => part.flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown | ts.TypeFlags.InstantiableNonPrimitive));
    const constrained = type => union(parts(type).map(part => part.flags & ts.TypeFlags.InstantiableNonPrimitive
        ? checker.getBaseConstraintOfType(part) || checker.getUnknownType() : part));
    const widened = type => union(parts(type).map(part => checker.getBaseTypeOfLiteralType(part)));
    // Some value belongs to both types: a part of one is inside the other (`${number}` inside string).
    const overlaps = (a, b) => parts(a).some(part => assignable(part, b)) || parts(b).some(part => assignable(part, a));
    const arrayLike = type => parts(type).every(part => checker.isArrayType(part) || checker.isTupleType(part));
    const voidLike = type => parts(type).every(part => part.flags & (ts.TypeFlags.Void | ts.TypeFlags.Undefined));
    const probes = new Map();

    // {type, open} of a descriptor for "nominal" / "accepted" (parameters) or "returns"; {reason} when unmapped.
    function implementation(descriptor, key) {
        let type, open = false;
        if (descriptor.unmapped) return {reason: descriptor.unmapped};
        if (descriptor.ts) {
            type = byText.get(descriptor.ts[key] || descriptor.ts.nominal);
            open = descriptor.ts.open;
        } else if (descriptor.object) {
            if (!probes.has(descriptor.probe)) probes.set(descriptor.probe, typeOf(descriptor.probe));
            type = probes.get(descriptor.probe);
            if (!type || vague(type)) return {reason: `probe ${descriptor.probe} has no d.ts type`};
            type = checker.getNonNullableType(type);
            open = true;
        } else if (descriptor.java) {
            type = byText.get(descriptor.java);
            if (!type || isError(type)) return {reason: "java-undeclared"};
            open = true;
        }
        if (!type || isError(type)) return {reason: "type text does not resolve"};
        const extra = [];
        if (descriptor.nullable) extra.push(nullType);
        if (descriptor.nullable && key !== "returns") extra.push(undefinedType);
        return {type: extra.length ? union([type, ...extra]) : type, open};
    }

    function parameterLevel(D, N, A) {
        if (vague(D)) return {level: "unmapped", reason: "dts-any"};
        if (assignable(D, N.type)) {
            if (assignable(N.type, D)) return {level: "match"};
            return {level: has(N.type, ts.TypeFlags.Null) && !has(D, ts.TypeFlags.Null) ? "narrow" : "refined"};
        }
        if (assignable(D, A.type)) return {level: "coerced"};
        return {level: overlaps(D, A.type) ? "wide" : "error"};
    }

    function returnLevel(declared, K) {
        if (vague(declared)) return {level: "unmapped", reason: "dts-any"};
        // A void result is undefined to the script.
        const D = union(parts(declared).map(part => part.flags & ts.TypeFlags.Void ? undefinedType : part));
        if (voidLike(K.type) && voidLike(D)) return {level: "match"};
        const down = assignable(D, K.type), up = assignable(K.type, D);
        if (down && up) return {level: "match"};
        if (down) {
            if (has(K.type, ts.TypeFlags.Null) && !has(D, ts.TypeFlags.Null)) return {level: "narrow"};
            const known = without(K.type, ts.TypeFlags.Null);
            const refined = K.open || assignable(known, widened(without(D, ts.TypeFlags.Null)))
                || (arrayLike(known) && arrayLike(without(D, ts.TypeFlags.Null)));
            return {level: refined ? "refined" : "narrow"};
        }
        if (up) return {level: "wide"};
        return {level: overlaps(D, K.type) ? "narrow" : "error"};
    }

    // d.ts parameter types at a position over all signatures; undefined from `?` / an initializer, or any undefined
    // when the Kotlin parameter has a default value, is dropped (the arity comparison covers omitted arguments).
    function declaredAt(signatures, index, optional) {
        const types = [];
        for (const signature of signatures) {
            const parameters = signature.getParameters();
            let symbol = parameters[index];
            const restIndex = ts.signatureHasRestParameter(signature) ? parameters.length - 1 : -1;
            if (!symbol && restIndex >= 0) symbol = parameters[restIndex];
            if (!symbol) continue;
            const declaration = symbol.valueDeclaration;
            let type = checker.getTypeOfSymbolAtLocation(symbol, source);
            if (parameters.indexOf(symbol) === restIndex) {
                if (checker.isTupleType(type)) {
                    type = checker.getTypeArguments(type)[index - restIndex];
                    if (!type) continue;
                } else type = checker.getIndexTypeOfType(type, ts.IndexKind.Number) || type;
            }
            if (optional || (declaration && ts.isParameter(declaration) && (declaration.questionToken || declaration.initializer))) type = without(type, ts.TypeFlags.Undefined);
            types.push(type);
        }
        return types.length ? constrained(union(types)) : null;
    }

    const show = type => checker.typeToString(type, undefined, ts.TypeFormatFlags.NoTruncation);
    const result = [];
    for (const row of rows) {
        const out = {api: row.api, module: row.module, kind: row.kind, params: [], returns: null};
        let type = typeOf(row.query);
        if (!type || vague(type)) {
            result.push({...out, status: "no-dts", reason: type ? "d.ts any" : "no d.ts type"});
            continue;
        }
        if (row.kind === "accessor") {
            if (awaited(row)) type = checker.getAwaitedType(type) || type;
            const K = implementation(row.value.type, "returns");
            out.returns = K.reason ? {level: "unmapped", reason: K.reason, kotlin: row.value.kotlin}
                : {...returnLevel(type, K), kotlin: row.value.kotlin, dts: show(type)};
        } else {
            const signatures = checker.getSignaturesOfType(checker.getNonNullableType(type), row.signature === "construct" ? ts.SignatureKind.Construct : ts.SignatureKind.Call);
            if (!signatures.length) {
                result.push({...out, status: "no-dts", reason: "not callable"});
                continue;
            }
            const positions = Math.max(0, ...row.overloads.map(overload => overload.params.length));
            for (let index = 0; index < positions; index++) {
                const taken = row.overloads.map(overload => overload.params[index] || (overload.params.at(-1)?.vararg ? overload.params.at(-1) : null)).filter(Boolean);
                const kotlin = [...new Set(taken.map(param => param.kotlin))].join(" | ");
                const unmapped = taken.find(param => param.type.unmapped);
                if (unmapped) {
                    out.params.push({index, level: "unmapped", reason: unmapped.type.unmapped, kotlin});
                    continue;
                }
                const optional = taken.some(param => param.optional);
                const nominal = taken.map(param => implementation(param.type, "nominal"));
                const accepted = taken.map(param => implementation(param.type, "accepted"));
                const missing = nominal.find(item => item.reason);
                if (missing) {
                    out.params.push({index, level: "unmapped", reason: missing.reason, kotlin});
                    continue;
                }
                const D = declaredAt(signatures, index, optional);
                if (!D) {
                    out.params.push({index, level: "unmapped", reason: "no d.ts parameter", kotlin});
                    continue;
                }
                const N = {type: union(nominal.map(item => item.type))}, A = {type: union(accepted.map(item => item.type))};
                out.params.push({index, ...parameterLevel(D, N, A), kotlin, dts: show(D)});
            }
            const kotlin = [...new Set(row.overloads.map(overload => overload.returns.kotlin))].join(" | ");
            const returned = row.overloads.map(overload => implementation(overload.returns.type, "returns"));
            const missing = returned.find(item => item.reason);
            if (missing) out.returns = {level: "unmapped", reason: missing.reason, kotlin};
            else {
                let D = constrained(union(signatures.map(signature => checker.getReturnTypeOfSignature(signature))));
                if (awaited(row)) D = checker.getAwaitedType(D) || D;
                // A constructor's d.ts return type is the instance type, which `new` never makes null.
                if (row.signature === "construct") D = checker.getNonNullableType(D);
                const K = {type: union(returned.map(item => item.type)), open: returned.every(item => item.open)};
                out.returns = {...returnLevel(D, K), kotlin, dts: show(D)};
            }
        }
        const levels = [...out.params, ...(out.returns ? [out.returns] : [])].map(item => item.level);
        out.status = levels.some(level => ["narrow", "wide", "error"].includes(level)) ? "differs"
            : levels.some(level => level !== "unmapped") ? "typed" : "unmapped";
        result.push(out);
    }
    return result;
}
