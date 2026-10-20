package org.autojs.autojs.ksp.modules

/**
 * Writes `autojs6/script-modules.json`: names, aliases, placement, attributes, parameter specs, return types and source
 * locations of every module of the compilation (roadmap T2 / T10: the single metadata source for the d.ts and
 * documentation tools). Keys are written in a fixed order so that the file diffs cleanly.
 *
 * zh-CN: 写出 `autojs6/script-modules.json`: 本次编译全部模块的名称, 别名, 挂载位置, 属性特性, 参数规格, 返回类型与源码位置
 * (T2 / T10: d.ts 与文档工具的唯一元数据来源). 键按固定顺序写出, 便于 diff.
 */
internal object MetadataWriter {

    const val FORMAT_VERSION = 1

    fun write(modules: List<ModuleModel>): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT_VERSION,
            "generator" to "ksp-script-modules-processor",
            "modules" to modules.sortedWith(compareBy({ it.parent ?: "" }, { it.name })).map(::module),
        )
        return StringBuilder().also { render(it, root, 0) }.append('\n').toString()
    }

    private fun module(m: ModuleModel): Map<String, Any?> = linkedMapOf(
        "name" to m.name,
        "aliases" to m.aliases,
        "names" to m.names,
        "dollarPrefix" to m.dollarPrefix,
        "eager" to m.eager,
        "parent" to m.parent,
        "attributes" to m.attributes,
        "emitter" to m.emitter,
        "builtIn" to m.builtIn,
        "class" to m.qualifiedName,
        "binding" to "${BindingGenerator.PACKAGE}.${m.bindingName}",
        "source" to m.source,
        "invoke" to m.invoke?.let(::overload),
        "construct" to m.construct?.let(::overload),
        "prototype" to m.prototype?.kotlinName,
        "functions" to m.functions.map { g ->
            linkedMapOf(
                "id" to g.id,
                "name" to g.name,
                "aliases" to g.aliases,
                "global" to g.placement.label,
                "attributes" to g.attributes,
                "toStringRole" to g.toStringRole,
                "builtIn" to g.builtIn,
                "builtInOnly" to g.builtInOnly,
                "length" to g.length,
                "overloads" to g.overloads.map(::overload),
            )
        },
        "accessors" to m.accessors.map { a ->
            linkedMapOf(
                "id" to a.id,
                "name" to a.name,
                "kind" to if (a.isGetter) "getter" else "property",
                "writable" to a.mutable,
                "type" to a.type,
                "global" to a.placement.label,
                "attributes" to a.attributes,
                "builtIn" to a.builtIn,
                "source" to a.source,
            )
        },
        "javaClasses" to m.javaClasses.map { j ->
            linkedMapOf("name" to j.name, "type" to j.type, "global" to j.placement.label, "attributes" to j.attributes)
        },
    )

    private fun overload(o: Overload): Map<String, Any?> = linkedMapOf(
        "kotlinName" to o.kotlinName,
        "params" to o.scriptParams.map { p ->
            linkedMapOf(
                "name" to p.name,
                "type" to p.type,
                "kind" to p.kind.name,
                "optional" to p.optional,
                "vararg" to p.vararg,
            )
        },
        "injected" to o.params.mapNotNull { it.kind.injected },
        "returns" to o.returnType,
        "source" to o.source,
    )

    private fun render(out: StringBuilder, value: Any?, indent: Int) {
        val pad = "  ".repeat(indent)
        when (value) {
            null -> out.append("null")
            is String -> out.append(BindingGenerator.lit(value).replace("\\$", "$"))
            is Number, is Boolean -> out.append(value.toString())
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    out.append("{}"); return
                }
                out.append("{\n")
                value.entries.forEachIndexed { i, (k, v) ->
                    out.append(pad).append("  ").append(BindingGenerator.lit(k.toString())).append(": ")
                    render(out, v, indent + 1)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(pad).append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) {
                    out.append("[]"); return
                }
                if (value.all { it is String || it is Number || it is Boolean }) {
                    out.append(value.joinToString(", ", "[", "]") { v -> if (v is String) BindingGenerator.lit(v).replace("\\$", "$") else v.toString() })
                    return
                }
                out.append("[\n")
                value.forEachIndexed { i, v ->
                    out.append(pad).append("  ")
                    render(out, v, indent + 1)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(pad).append(']')
            }
            else -> out.append(BindingGenerator.lit(value.toString()).replace("\\$", "$"))
        }
    }

}
