package org.autojs.autojs.runtime.api.augment.differential

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Describes the own members of a module object (name, `typeof`, function length, attributes) and compares two
 * such descriptions. Names are the compatibility contract; attribute differences between the obsolete JS objects
 * (plain writable properties) and the Kotlin augmentables (PERMANENT) are expected and only reported.
 *
 * zh-CN: 描述模块对象的自有成员 (名称, typeof, 函数参数个数, 属性特性) 并比对两份描述. 名称是兼容契约; 旧 JS 对象的
 * 普通可写属性与 Kotlin 的 PERMANENT 属性之间的特性差异是预期内的, 只报告不判失败.
 */
internal object SurfaceDiff {

    data class Member(
        val name: String,
        val type: String,
        val length: Int?,
        val enumerable: Boolean,
        val writable: Boolean,
        val configurable: Boolean,
    ) {
        val summary: String
            get() = buildString {
                append(type)
                if (length != null) append("(length=").append(length).append(")")
                append(" ")
                append(if (enumerable) "E" else "-")
                append(if (writable) "W" else "-")
                append(if (configurable) "C" else "-")
            }
    }

    data class Report(
        val onlyInFirst: Set<String>,
        val onlyInSecond: Set<String>,
        val typeMismatches: Map<String, Pair<String, String>>,
        val attributeMismatches: Map<String, Pair<String, String>>,
    ) {
        val namesMatch: Boolean get() = onlyInFirst.isEmpty() && onlyInSecond.isEmpty() && typeMismatches.isEmpty()
    }

    /**
     * Members of [target] and of its prototype chain below `Object.prototype` / `Function.prototype` (the
     * obsolete modules keep their methods on a constructor prototype, the Kotlin ones on the object itself).
     * Function intrinsics (`length`, `name`, `arguments`, `caller`, `prototype`), `constructor` and internal
     * `__xxx__` names are left out; a member that cannot be read (also a getter whose Android class cannot be initialized
     * on the JVM) is typed `inaccessible`. A Java object in the chain
     * (util and mime use one as prototype) contributes its public members; it has no attributes, so they are
     * recorded as enumerable, writable and permanent.
     */
    fun surface(target: Any?, scope: Scriptable): Map<String, Member> {
        require(target is ScriptableObject) { "Surface requires a ScriptableObject, got ${target?.javaClass?.name}" }
        val terminals = setOf(ScriptableObject.getObjectPrototype(scope), ScriptableObject.getFunctionPrototype(scope))
        val result = linkedMapOf<String, Member>()
        var current: Scriptable? = target
        var depth = 0
        while (current != null && depth < 8 && current !in terminals) {
            val owner = current
            val ids = if (owner is ScriptableObject) owner.allIds else owner.ids
            ids.filterIsInstance<String>()
                .filterNot { it in SKIPPED || (it.startsWith("__") && it.endsWith("__")) }
                .forEach { name ->
                    if (name in result) return@forEach
                    val value = try {
                        owner.get(name, owner)
                    } catch (e: RuntimeException) {
                        INACCESSIBLE
                    } catch (e: LinkageError) {
                        // An Android class behind a getter cannot be initialized on the JVM (NoClassDefFoundError).
                        INACCESSIBLE
                    }
                    val attributes = (owner as? ScriptableObject)?.getAttributes(name) ?: ScriptableObject.PERMANENT
                    result[name] = Member(
                        name = name,
                        type = if (value === INACCESSIBLE) "inaccessible" else RhinoScriptRuntime.`typeof`(value),
                        length = (value as? BaseFunction)?.length,
                        enumerable = (attributes and ScriptableObject.DONTENUM) == 0,
                        writable = (attributes and ScriptableObject.READONLY) == 0,
                        configurable = (attributes and ScriptableObject.PERMANENT) == 0,
                    )
                }
            current = owner.prototype
            depth++
        }
        return result
    }

    /** `arity` is Rhino's non-standard alias of `length` on function objects. */
    private val SKIPPED = setOf("constructor", "length", "name", "arity", "arguments", "caller", "prototype")
    private val INACCESSIBLE = Any()

    fun compare(first: Map<String, Member>, second: Map<String, Member>): Report {
        val common = first.keys intersect second.keys
        return Report(
            onlyInFirst = first.keys - second.keys,
            onlyInSecond = second.keys - first.keys,
            typeMismatches = common.mapNotNull { name ->
                val a = first.getValue(name).type
                val b = second.getValue(name).type
                if (a != b) name to (a to b) else null
            }.toMap(),
            attributeMismatches = common.mapNotNull { name ->
                val a = first.getValue(name)
                val b = second.getValue(name)
                if (a.summary != b.summary) name to (a.summary to b.summary) else null
            }.toMap(),
        )
    }

    /** [surface] as text, one member per line (`name`, `type`, `length` or `-`, the attributes), for a recording. */
    fun encode(surface: Map<String, Member>): String = surface.values.joinToString("\n") { member ->
        listOf(member.name, member.type, member.length?.toString() ?: "-", member.summary.substringAfterLast(' ')).joinToString("\t")
    }

    /** The surface [encode] wrote. */
    fun decode(text: String): Map<String, Member> = text.lines().filter { it.isNotEmpty() }.associate { line ->
        val (name, type, length, attributes) = line.split('\t')
        name to Member(name, type, length.toIntOrNull(), attributes[0] == 'E', attributes[1] == 'W', attributes[2] == 'C')
    }

    fun describe(report: Report, firstLabel: String, secondLabel: String): String = buildString {
        if (report.onlyInFirst.isNotEmpty()) appendLine("only in $firstLabel: ${report.onlyInFirst.sorted().joinToString()}")
        if (report.onlyInSecond.isNotEmpty()) appendLine("only in $secondLabel: ${report.onlyInSecond.sorted().joinToString()}")
        report.typeMismatches.toSortedMap().forEach { (name, pair) ->
            appendLine("type mismatch $name: $firstLabel=${pair.first} $secondLabel=${pair.second}")
        }
        report.attributeMismatches.toSortedMap().forEach { (name, pair) ->
            appendLine("attributes $name: $firstLabel=${pair.first} $secondLabel=${pair.second}")
        }
    }

}
