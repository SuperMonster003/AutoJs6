'use strict';

// Rhino mishandles the nested lookbehind in (?<!NAME_SUFFIX). Test the same
// suffix against the matched prefix in the forward direction instead. In both
// affected patterns, the final character cannot start another match, so keeping
// a rejected match unchanged also preserves the upstream scanning behavior.
module.exports = function adaptRhino(source) {
    function replaceOnce(before, after) {
        if (source.split(before).length !== 2) {
            throw new Error(`Upstream pangu source changed around: ${before}`);
        }
        source = source.replace(before, () => after);
    }

    const assertion = '(?<!${NAME_SUFFIX})';
    if (source.split(assertion).length !== 3) {
        throw new Error('Expected exactly two nested name-suffix assertions.');
    }
    source = source.split(assertion).join('');
    replaceOnce(
        "newText = newText.replace(ANS_OPERATOR_CJK, '$1 $2 $3');",
        `newText = newText.replace(ANS_OPERATOR_CJK, (match, left, operator, right, offset, input) => {
      const prefix = input.slice(0, offset + left.length + operator.length);
      return NAME_SUFFIX_AT_END.test(prefix) ? match : left + ' ' + operator + ' ' + right;
    });`,
    );
    replaceOnce(
        "newText = newText.replace(CJK_ANS, '$1 $2');",
        `newText = newText.replace(CJK_ANS, (match, left, right, offset, input) => {
      const prefix = input.slice(0, offset + match.length);
      return NAME_SUFFIX_AT_END.test(prefix) ? match : left + ' ' + right;
    });`,
    );
    return source;
};
