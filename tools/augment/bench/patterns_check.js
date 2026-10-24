// Q70 (2): the pattern check of the new module layer against the engine of this device (tools/augment/device_patterns.py).
// Every pattern the check refuses must not compile with JsRegexUtils.compileJsOrPlainRegex (java.util.regex.Pattern, ICU
// on Android). Patterns: the corpus pushed beside this script (patterns.json), random strings of regex characters and
// random patterns built from the syntax, half of them mutated. Prints AJ6PAT lines only.
// zh-CN: Q70 (2): 新模块层的正则检查与本设备引擎对照. 被检查拒绝的正则不得能被 JsRegexUtils.compileJsOrPlainRegex 编译.

var dir = files.cwd();
var args = JSON.parse(files.read(dir + '/patterns_args.json'));
var check = org.autojs.autojs.runtime.api.modules.selector.SelectorPatterns.INSTANCE;
var regex = org.autojs.autojs.core.automator.filter.JsRegexUtils.INSTANCE;

var seed = args.seed >>> 0;
function random() {
    // mulberry32
    seed = (seed + 0x6D2B79F5) >>> 0;
    var t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
function int(n) { return Math.floor(random() * n); }
function pick(a) { return a[int(a.length)]; }

var BS = String.fromCharCode(92);
var STRING_ALPHABET = '[]()*+?|' + BS + 'a{}^-:=!<>.,0123QEcpdxkbuUN/&#';
var MUTATION_ALPHABET = '[]()*+?|' + BS + '{}-,0&:<>^';
function e(s) { return s.replace(/~/g, BS); }
var ESCAPES = ['~t', '~n', '~e', '~a', '~f', '~x41', '~x7a', '~x{41}', '~x{1F600}', '~x{7}', '~u0041', '~u007a', '~uD835~uDFD8',
    '~cA', '~cz', '~0101', '~07', '~00', '~p{L}', '~p{Lu}', '~P{Nd}', '~pL', '~p{Greek}', '~p{IsLatin}', '~p{InGreek}', '~p{Any}',
    '~N{LATIN SMALL LETTER A}', '~~', '~-', '~]', '~[', '~.', '~{', '~}', '~(', '~)', '~*', '~/', '~ ', '~#', '~k<a>', '~k<n1>',
    '~Qa(~E', '~g', '~i', '~y', '~C', '~E', '~U', '~x', '~u', '~k', '~N', '~p', '~0', '~c', '~x4', '~U0001F600', '~p{Han}'].map(e);
var ATOMS = ['a', 'b', 'z', '.', '-', '#', ' ', '}', ']', '\u00e9', '\ud835\udfd8', '^', '$', ','];
var ANCHORS = ['~b', '~B', '~A', '~z', '~Z', '~G', '~d', '~W', '~R', '~X', '~h', '~1', '~2', '~s'].map(e);
var MEMBERS = ['a', 'b', 'z', '0', '9', '-', '^', '&', '(', ')', '{', '*', '|', ' ', '\u00e9', '\ud835\udfd8', '!', '~'];
var CLASS_EXTRAS = ['[:alpha:]', '--', e('~d'), e('~p{L}'), e('~b')];
var GROUPS = ['(', '(', '(?:', '(?=', '(?!', '(?<=', '(?<!', '(?>', '(?<a>', '(?<b>', '(?<n1>', '(?i)', '(?i:', '(?-m:', '(?#c)', '(?x)', '(?U)', '(?w)'];
var QUANTIFIERS = ['*', '+', '?', '{2}', '{0,}', '{1,3}', '{3,1}', '{,2}', '{x}', '{', '{2147483648}', '{1, 2}'];

function built() {
    var sb = [];
    var depth = 0;
    function alternation() {
        sequence();
        while (int(5) === 0) { sb.push('|'); sequence(); }
    }
    function sequence() {
        var k = int(4);
        for (var j = 0; j < k; j++) {
            atom();
            if (int(3) === 0) { sb.push(pick(QUANTIFIERS)); if (int(3) === 0) sb.push(pick(['?', '+', '*', '{2}'])); }
        }
    }
    function atom() {
        var r = int(depth > 2 ? 6 : 9);
        if (r < 2) sb.push(pick(ATOMS));
        else if (r === 2) sb.push(pick(ESCAPES));
        else if (r === 3) sb.push(pick(ANCHORS));
        else if (r < 6) klass();
        else group();
    }
    function member() { return int(4) === 0 ? pick(ESCAPES) : pick(MEMBERS); }
    function klass() {
        sb.push('[');
        if (int(4) === 0) sb.push('^');
        if (int(8) === 0) sb.push(']');
        var k = int(4);
        for (var j = 0; j < k; j++) {
            var r = int(8);
            if (r === 0) sb.push(member() + '-' + member());
            else if (r === 1) sb.push('&&');
            else if (r === 2) { if (depth < 3) { depth++; klass(); depth--; } }
            else if (r === 3) sb.push(pick(CLASS_EXTRAS));
            else sb.push(member());
        }
        if (int(12) !== 0) sb.push(']');
    }
    function group() {
        depth++;
        var g = pick(GROUPS);
        sb.push(g);
        if (g.charAt(g.length - 1) !== ')') {
            alternation();
            if (int(12) !== 0) sb.push(')');
        }
        depth--;
    }
    alternation();
    return sb.join('');
}

function randomString() {
    var k = 1 + int(10), s = '';
    for (var j = 0; j < k; j++) s += STRING_ALPHABET.charAt(int(STRING_ALPHABET.length));
    return s;
}

function mutate(p) {
    var a = p.split('');
    var k = 1 + int(2);
    for (var j = 0; j < k; j++) {
        var at = a.length === 0 ? 0 : int(a.length + 1);
        var r = int(3);
        if (r === 0) { if (at < a.length) a.splice(at, 1); }
        else if (r === 1) a.splice(at, 0, MUTATION_ALPHABET.charAt(int(MUTATION_ALPHABET.length)));
        else if (at + 1 < a.length) { var c = a[at]; a[at] = a[at + 1]; a[at + 1] = c; }
    }
    return a.join('');
}

function Tally(name) {
    this.name = name;
    this.total = 0; this.failing = 0; this.refused = 0; this.wrong = [];
    this.regexpOnly = 0; this.patternOnly = 0;
}
Tally.prototype.check = function (s, verdicts) {
    this.total++;
    var compiles = true;
    try { regex.compileJsOrPlainRegex(s); } catch (err) { compiles = false; }
    var refused = check.refusal(s) !== null;
    var regexp = true;
    try { new RegExp(s); } catch (err) { regexp = false; }
    if (!compiles) this.failing++;
    if (refused) { this.refused++; if (compiles) this.wrong.push(s); }
    if (regexp && !compiles) this.regexpOnly++;
    if (compiles && !regexp) this.patternOnly++;
    if (verdicts) verdicts.push((compiles ? '1' : '0') + (refused ? '1' : '0') + (regexp ? '1' : '0'));
};
Tally.prototype.print = function () {
    console.log('AJ6PAT ' + this.name + ' total ' + this.total + ' failing ' + this.failing + ' refused ' + this.refused +
        ' wrong ' + this.wrong.length + ' left ' + (this.failing - this.refused) + ' regexp-only ' + this.regexpOnly + ' pattern-only ' + this.patternOnly);
    for (var j = 0; j < Math.min(20, this.wrong.length); j++) console.log('AJ6PAT wrong ' + JSON.stringify(this.wrong[j]));
};

var started = Date.now();
var corpus = JSON.parse(files.read(dir + '/patterns.json'));
var t = new Tally('corpus'), verdicts = [];
corpus.forEach(function (s) { t.check(s, verdicts); });
files.write(dir + '/patterns_verdicts.txt', verdicts.join('\n') + '\n');
t.print();
t = new Tally('strings');
for (var j = 0; j < args.samples; j++) t.check(randomString());
t.print();
t = new Tally('built');
for (j = 0; j < args.samples / 2; j++) { var b = built(); t.check(b); t.check(mutate(b)); }
t.print();
console.log('AJ6PAT done ' + Math.round((Date.now() - started) / 1000) + ' s');
