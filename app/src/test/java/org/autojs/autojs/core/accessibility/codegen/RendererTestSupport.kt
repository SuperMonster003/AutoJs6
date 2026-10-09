package org.autojs.autojs.core.accessibility.codegen

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Cardinality
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan.Family
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertFalse
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser
import org.mozilla.javascript.ast.AstRoot

/** Executable API doubles. Selection order and the target/receiver identities are independent of
 * the renderer output, and missing API names fail in Rhino just as they do in a script.
 * zh-CN: 可执行的 API 替身. 选择顺序与目标/接收者身份独立于渲染输出, 错误 API 名称会在 Rhino 中报错. */
internal object RendererTestSupport {

    val textArgument = "a\"b\\c\n\r\t\u2028\u2029');throw Error('injected');('😀"
    val modes = listOf(Mode.WAIT, Mode.WAIT_TIMEOUT(3_000), Mode.CHECK)
    val actions = listOf(Action.NONE, Action.CLICK, Action.LONG_CLICK, Action.SET_TEXT(textArgument), Action.SCROLL_FORWARD, Action.SCROLL_BACKWARD)
    val receivers = listOf(PlanReceiver.Self, PlanReceiver.Up(1))

    private val item = SelectorSpec(Condition("id", "example:id/item"))
    val plans = listOf(
        LocatePlan(Family.SELF, SelectorSpec(Condition("id", "example:id/item"), Condition("text", "target"))),
        LocatePlan(Family.SELF, item, cardinality = Cardinality.ALL),
        LocatePlan(Family.SELF_INDEX, item, listOf(PlanStep.Index(-1))),
        LocatePlan(Family.ANCHOR_PATH, SelectorSpec(Condition("id", "example:id/label")), listOf(PlanStep.Up(1), PlanStep.Child(-1))),
        LocatePlan(Family.ANCHOR_SEARCH, SelectorSpec(Condition("id", "example:id/row2")), listOf(PlanStep.Search(item))),
        LocatePlan(Family.CONTAINER_ITEMS, SelectorSpec(Condition("id", "example:id/root")), listOf(PlanStep.Search(item)), Cardinality.ALL),
        LocatePlan(Family.SELF_RELATIVE, item, listOf(PlanStep.Sibling(-1), PlanStep.Match(SelectorSpec(Condition("id", "example:id/label"))))),
        LocatePlan(Family.SELF_RELATIVE, item, listOf(PlanStep.Up(1), PlanStep.Match(item)), Cardinality.ALL),
    )

    fun withReceiver(plan: LocatePlan, receiver: PlanReceiver) = LocatePlan(plan.family, plan.selector, plan.steps, plan.cardinality, receiver)

    fun request(plan: LocatePlan, mode: Mode, action: Action = Action.NONE, style: CodeGenRequest.Style = CodeGenRequest.Style.CLASSIC): CodeGenRequest<FakeUiNode> {
        val root = FakeUiNode.root {
            child("other") { editable = true }
            child("target") { editable = true }
        }
        return CodeGenRequest(
            if (plan.cardinality == Cardinality.ALL) listOf(root.find("other"), root.find("target")) else listOf(root.find("target")),
            scope = if (plan.cardinality == Cardinality.ALL) CodeGenRequest.Scope.COLLECTION else CodeGenRequest.Scope.SINGLE,
            mode = mode,
            action = action,
            style = style,
        )
    }

    fun parse(source: String): AstRoot {
        assertFalse(source, source.contains("waitFor("))
        assertFalse(source, Regex("\\bfindOne\\s*\\(\\s*\\)").containsMatchIn(source))
        assertFalse(source, Regex("\\bconst\\s").containsMatchIn(source))
        return Parser(CompilerEnvirons().apply { languageVersion = Context.VERSION_ES6 }).parse(source, "generated.js", 1)
    }

    fun expectedActions(plan: LocatePlan, action: Action): String {
        if (action == Action.NONE) return ""
        val targets = if (plan.cardinality == Cardinality.ALL) listOf("other", "target") else listOf("target")
        val names = when (plan.receiver) {
            PlanReceiver.Self -> targets
            is PlanReceiver.Up -> targets.map { if (it == "other") "row1" else "row2" }
        }
        val method = when (action) {
            Action.NONE -> error("No action")
            Action.CLICK -> "click"
            Action.LONG_CLICK -> "longClick"
            is Action.SET_TEXT -> "setText"
            Action.SCROLL_FORWARD -> "scrollForward"
            Action.SCROLL_BACKWARD -> "scrollBackward"
        }
        return names.joinToString(",") { "$it:$method" }
    }

    fun evaluate(source: String, setup: String = "", expression: String = "calls.map(x => x[0] + ':' + x[1]).join(',')"): Any? {
        val cx = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        return try {
            val scope = cx.initStandardObjects()
            cx.evaluateString(scope, API_STUBS + "\n" + setup + "\n" + source + "\n" + expression + ";", "generated.js", 1, null)
                .let { if (it is CharSequence) it.toString() else it }
        } finally {
            Context.exit()
        }
    }

    const val RESULT = "describe(typeof found !== 'undefined' ? found : typeof ws !== 'undefined' ? ws : typeof w !== 'undefined' ? w : null)"

    private val API_STUBS = """
        var calls = [], observedTimeout = null, observedResultType = null, observedCompass = null;
        var hiddenTargets = false, hiddenAnchors = false, missingReceiver = false, failActions = false;
        var methods = ['click', 'longClick', 'setText', 'scrollForward', 'scrollBackward'];
        function collection(nodes) {
            nodes.nonEmpty = function () { return this.length > 0; };
            nodes.each = function (fn) { this.forEach(fn); return this; };
            methods.forEach(function (method) {
                nodes[method + 'Each'] = function (arg) { return this.map(n => n[method](arg)); };
            });
            return nodes;
        }
        function node(name, resource, parentNode) {
            var n = { name: name, id: 'example:id/' + resource, text: name, parentNode: parentNode, children: [] };
            if (parentNode) parentNode.children.push(n);
            n.parent = function (levels) {
                if (missingReceiver && this.id === 'example:id/item') return null;
                var p = this;
                for (var i = 0; i < levels && p; i++) p = p.parentNode;
                return p;
            };
            n.compass = function (path) {
                var parts = path.match(/p-?[0-9]+|c-?[0-9]+|s[<>][0-9]+/g), p = this;
                for (var i = 0; i < parts.length && p; i++) {
                    if (parts[i][0] === 's') {
                        var siblings = p.parentNode ? p.parentNode.children : [], at = siblings.indexOf(p) + Number(parts[i].slice(2)) * (parts[i][1] === '>' ? 1 : -1);
                        p = at >= 0 && at < siblings.length ? siblings[at] : null;
                        continue;
                    }
                    var index = Number(parts[i].slice(1));
                    if (parts[i][0] === 'p') {
                        // A compass walk reads the tree directly; only action receivers go through parent().
                        for (var k = 0; k < index && p; k++) p = p.parentNode || null;
                        continue;
                    }
                    p = p.children[index < 0 ? p.children.length + index : index] || null;
                }
                return p;
            };
            n.find = function (sel) { return sel.search(this); };
            n.findOne = function (sel) { return sel.search(this)[0] || null; };
            methods.forEach(function (method) {
                n[method] = function (arg) { calls.push([this.name, method, arg]); return !failActions; };
            });
            return n;
        }
        var rootNode = node('root', 'root', null);
        var row1 = node('row1', 'row1', rootNode), otherNode = node('other', 'item', row1);
        var row2 = node('row2', 'row2', rootNode), labelNode = node('label', 'label', row2), targetNode = node('target', 'item', row2);
        function selector() {
            var s = { predicates: [] };
            ['id', 'text', 'desc', 'className'].forEach(function (attr) {
                s[attr] = function (value) { this.predicates.push([attr, value]); return this; };
            });
            s.filter = function (fn) { this.predicates.push(['filter', fn]); return this; };
            s.search = function (root) {
                var found = [], sel = this;
                function walk(n) {
                    var hidden = hiddenTargets && n.id === 'example:id/item' || hiddenAnchors && n.id !== 'example:id/item';
                    if (!hidden && sel.predicates.every(p => p[0] === 'filter' ? p[1](n) === true : n[p[0]] === p[1])) found.push(n);
                    n.children.forEach(walk);
                }
                walk(root);
                return collection(found);
            };
            s.find = function () { return this.search(rootNode); };
            s.findOnce = function (i) { var nodes = this.find(); i = i || 0; return nodes[i < 0 ? nodes.length + i : i] || null; };
            s.findOne = function (timeout) { if (timeout === undefined) throw Error('deprecated findOne'); observedTimeout = timeout; return this.findOnce(); };
            s.untilFindOne = function () { var n = this.findOnce(); if (!n) throw Error('still waiting'); return n; };
            s.untilFind = function () { var ns = this.find(); if (!ns.nonEmpty()) throw Error('still waiting'); return ns; };
            s.exists = function () { return this.findOnce() !== null; };
            return s;
        }
        function id(value) { return selector().id(value); }
        function text(value) { return selector().text(value); }
        function desc(value) { return selector().desc(value); }
        function className(value) { return selector().className(value); }
        function satisfied(value) { return value != null && value !== false && !(typeof value === 'number' && isNaN(value)); }
        function wait(cond, timeout, interval, callbacks) {
            observedTimeout = timeout;
            var value = cond();
            if (!satisfied(value)) { if (timeout === Infinity) throw Error('still waiting'); return false; }
            var result = callbacks.then(value);
            return result == null ? true : result;
        }
        function probe(cond, options) {
            observedTimeout = options.timeout; observedResultType = options.resultType; observedCompass = options.compass;
            if (typeof cond === 'function') { var value = cond(); return satisfied(value) ? value : null; }
            var result = options.resultType === 'widgets' ? cond.find() : cond.findOnce();
            if (Array.isArray(result) && result.length === 0) return null;
            // The compass option names the action receiver, so it goes through parent() like the classic receiver.
            var receiver = options.compass && options.compass.match(/^p([0-9]+)$/);
            return result && options.compass ? (receiver ? result.parent(Number(receiver[1])) : result.compass(options.compass)) : result;
        }
        function waitAsync(cond, options) {
            var value = probe(cond, options), chain = {};
            if (value == null) throw Error('still waiting');
            methods.forEach(function (method) {
                chain[method] = function (arg) {
                    if (Array.isArray(value)) value.forEach(n => n[method](arg)); else value[method](arg);
                    return chain;
                };
            });
            chain.then = function (fn) { fn(value); return chain; };
            chain.sync = function () { return value; };
            return chain;
        }
        function waitThenClick(cond, options) { return waitAsync(cond, options).click(); }
        var flow = { whenPresent: function (cond, handler, options) {
            var value = probe(cond, options);
            if (value != null) handler(value);
            return { sync: function () {} };
        } };
        function describe(value) {
            if (value == null) return 'null';
            if (Array.isArray(value)) return 'collection:' + value.map(x => x.name).join(',');
            if (typeof value === 'boolean') return String(value);
            return 'node:' + value.name;
        }
    """.trimIndent()
}
