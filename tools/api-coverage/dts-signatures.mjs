// Argument-count ranges of the call and construct signatures of a d.ts type (api-coverage P0.3).
// Each range is {min, max}; max null means a rest parameter without a fixed tuple length.
// A rest parameter of tuple type counts its elements (`...args: [a, b?]` is 1-2).

function range(ts, checker, signature) {
    const parameters = signature.getParameters();
    let min = signature.minArgumentCount ?? parameters.length;
    let max = parameters.length;
    if (ts.signatureHasRestParameter(signature)) {
        const rest = checker.getTypeOfSymbol(parameters.at(-1));
        if (checker.isTupleType(rest)) {
            // minArgumentCount counts the required parameters before the rest parameter only.
            const target = rest.target;
            if (min >= parameters.length - 1) min = parameters.length - 1 + target.minLength;
            max = target.hasRestElement ? null : parameters.length - 1 + target.fixedLength;
        } else {
            min = Math.min(min, parameters.length - 1);
            max = null;
        }
    }
    return {min, max};
}

export function signatureArities(ts, checker, type) {
    const target = checker.getNonNullableType(type);
    const ranges = kind => checker.getSignaturesOfType(target, kind).map(signature => range(ts, checker, signature));
    const call = ranges(ts.SignatureKind.Call), construct = ranges(ts.SignatureKind.Construct);
    return call.length || construct.length ? {signatures: {call, construct}} : {};
}
