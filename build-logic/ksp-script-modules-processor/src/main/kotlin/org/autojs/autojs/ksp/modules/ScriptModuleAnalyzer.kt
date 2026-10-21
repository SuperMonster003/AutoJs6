package org.autojs.autojs.ksp.modules

import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptProxyGet
import org.autojs.autojs.runtime.api.modules.ScriptProxySet

/**
 * Turns a @ScriptModule class into a [ModuleModel], reporting every unsupported declaration as a compile error on the
 * offending symbol (roadmap D5: signature mistakes fail the build). Returns null when an error was reported.
 *
 * zh-CN: 把 @ScriptModule 类转换为 [ModuleModel]; 不支持的声明在对应符号上报告为编译错误 (D5: 签名错误编译期报错), 有错误时返回 null.
 */
internal class ScriptModuleAnalyzer(private val logger: KSPLogger, private val projectDir: String?) {

    private var failed = false

    fun analyze(declaration: KSClassDeclaration): ModuleModel? {
        failed = false
        val moduleAnnotation = declaration.annotation(ScriptModuleNames.MODULE) ?: return null
        val qualifiedName = declaration.qualifiedName?.asString() ?: return error(declaration, "A @ScriptModule class must have a qualified name")
        val instance = instanceOf(declaration) ?: return null

        val functions = mutableListOf<Pair<KSFunctionDeclaration, KSAnnotation>>()
        var invoke: Overload? = null
        var construct: Overload? = null
        val accessors = mutableListOf<Accessor>()
        var prototype: Member? = null
        var install: Member? = null
        var proxyGet: Member? = null
        var proxySet: Member? = null
        val scriptName = "${moduleAnnotation.string("name")}"
        val builtIn = moduleAnnotation.string("builtIn").ifEmpty { null }
        val assign = moduleAnnotation.boolean("assign", false)
        val proxy = moduleAnnotation.boolean("proxy", false)

        declaration.declarations.forEach { member ->
            when (member) {
                is KSFunctionDeclaration -> {
                    member.annotation(ScriptModuleNames.FUNCTION)?.let { functions += member to it }
                    member.annotation(ScriptModuleNames.INVOKE)?.let {
                        if (invoke != null) error(member, "At most one @ScriptInvoke function per module")
                        invoke = overloadOf(member, "$scriptName()")
                    }
                    member.annotation(ScriptModuleNames.CONSTRUCT)?.let {
                        if (construct != null) error(member, "At most one @ScriptConstruct function per module")
                        construct = overloadOf(member, "new $scriptName()")
                    }
                    member.annotation(ScriptModuleNames.GETTER)?.let { accessors += accessorOf(member, it, accessors.size, isGetter = true) ?: return@let }
                    member.annotation(ScriptModuleNames.PROTOTYPE)?.let {
                        if (member.parameters.isNotEmpty()) error(member, "A @ScriptPrototype function takes no parameters")
                        prototype = Member(member.simpleName.asString(), isFunction = true)
                    }
                    member.annotation(ScriptModuleNames.INSTALL)?.let {
                        if (install != null) error(member, "At most one @ScriptInstall function per module")
                        install = hookOf(member, "@ScriptInstall", listOf(INSTALLATION), returnsUnit = true)
                    }
                    member.annotation(ScriptModuleNames.PROXY_GET)?.let {
                        if (proxyGet != null) error(member, "At most one @ScriptProxyGet function per module")
                        proxyGet = hookOf(member, "@ScriptProxyGet", listOf(SCRIPTABLE, "kotlin.String"), returnsUnit = false)
                    }
                    member.annotation(ScriptModuleNames.PROXY_SET)?.let {
                        if (proxySet != null) error(member, "At most one @ScriptProxySet function per module")
                        proxySet = hookOf(member, "@ScriptProxySet", listOf(SCRIPTABLE, "kotlin.String", "kotlin.Any"), returnsUnit = true)
                    }
                }
                is KSPropertyDeclaration -> {
                    member.annotation(ScriptModuleNames.GETTER)?.let { accessors += accessorOf(member, it, accessors.size, isGetter = true) ?: return@let }
                    member.annotation(ScriptModuleNames.PROPERTY)?.let { accessors += accessorOf(member, it, accessors.size, isGetter = false) ?: return@let }
                    member.annotation(ScriptModuleNames.PROTOTYPE)?.let {
                        prototype = Member(member.simpleName.asString(), isFunction = false)
                    }
                }
                else -> Unit
            }
        }

        val declaredGroups = functions
            .groupBy { (function, annotation) -> annotation.string("name").ifEmpty { function.simpleName.asString() } }
            .entries
            .mapIndexed { id, (name, members) -> groupOf(id, name, scriptName, members, builtIn) }
            .filterNotNull()
        accessors.filter { it.builtIn.isNotEmpty() }.forEach { a ->
            if (builtIn == null) error(declaration, "$scriptName.${a.name}: builtIn needs @ScriptModule(builtIn) on the module")
            if (!a.placement.onModule || a.isGetter) error(declaration, "$scriptName.${a.name}: builtIn applies to a @ScriptProperty bound on the module object")
            if ("PROTOTYPE" in a.builtIn) error(declaration, "$scriptName.${a.name}: a property cannot extend the prototype of a built-in object")
        }

        val declaredJavaClasses = declaration.annotations(ScriptModuleNames.JAVA_CLASS).mapNotNull { annotation ->
            val type = annotation.type("type") ?: return@mapNotNull null.also { error(declaration, "@ScriptJavaClass needs a type") }
            JavaClassBinding(
                name = annotation.string("name"),
                type = type.declaration.qualifiedName!!.asString(),
                placement = annotation.placement(),
                attributes = annotation.int("attributes", PERMANENT),
            )
        }.toList()

        // The members of an assign module all go on the global object; they keep the default placement in the source.
        val onGlobal = Placement(onModule = false, onGlobal = true)
        if (assign) {
            val placed = declaredGroups.map { it.name to it.placement } + accessors.map { it.name to it.placement } + declaredJavaClasses.map { it.name to it.placement }
            placed.filter { (_, placement) -> placement.onGlobal }.forEach { (name, _) ->
                error(declaration, "$scriptName.$name: every member of an assign module goes on the global object; leave global = NONE")
            }
        }
        val groups = if (assign) declaredGroups.map { it.copy(placement = onGlobal) } else declaredGroups
        val placedAccessors = if (assign) accessors.map { it.copy(placement = onGlobal) } else accessors
        val javaClasses = if (assign) declaredJavaClasses.map { it.copy(placement = onGlobal) } else declaredJavaClasses

        checkDuplicateNames(declaration, groups, placedAccessors, javaClasses)
        checkDuplicateBuiltInNames(declaration, groups, placedAccessors)

        val parent = moduleAnnotation.type("parent")?.declaration?.qualifiedName?.asString()?.takeUnless { it == "kotlin.Unit" }
        if (parent == qualifiedName) error(declaration, "A script module cannot be its own parent")
        if (parent != null && moduleAnnotation.boolean("eager", false)) error(declaration, "A sub-module is built with its parent; eager applies to top-level modules only")
        val parentDeclaration = moduleAnnotation.type("parent")?.declaration as? KSClassDeclaration
        if (parent != null && parentDeclaration?.annotation(ScriptModuleNames.MODULE)?.boolean("assign", false) == true) {
            error(declaration, "An assign module has no module object to hold the sub-module $scriptName")
        }
        if (assign) {
            if (moduleAnnotation.strings("aliases").isNotEmpty()) error(declaration, "An assign module has no module object, so no aliases")
            if (invoke != null || construct != null) error(declaration, "An assign module has no module object to invoke or construct")
            if (parent != null) error(declaration, "An assign module is a top-level module")
            if (moduleAnnotation.boolean("emitter", false)) error(declaration, "An assign module has no module object to carry an emitter")
            if (builtIn != null) error(declaration, "An assign module cannot extend a built-in object")
            if (proxy) error(declaration, "A module cannot be both assign and proxy")
        }
        if (proxy && (invoke != null || construct != null)) error(declaration, "A proxy module object cannot be invoked or constructed")
        if (!proxy && (proxyGet != null || proxySet != null)) error(declaration, "@ScriptProxyGet / @ScriptProxySet need @ScriptModule(proxy = true)")

        return ModuleModel(
            qualifiedName = qualifiedName,
            simpleName = declaration.simpleName.asString(),
            name = scriptName,
            path = pathOf(declaration),
            aliases = moduleAnnotation.strings("aliases"),
            dollarPrefix = moduleAnnotation.boolean("dollarPrefix", true),
            eager = moduleAnnotation.boolean("eager", false),
            parent = parent,
            attributes = moduleAnnotation.int("attributes", PERMANENT),
            emitter = moduleAnnotation.boolean("emitter", false),
            instance = instance,
            functions = groups,
            invoke = invoke,
            construct = construct,
            accessors = placedAccessors,
            javaClasses = javaClasses,
            prototype = prototype,
            builtIn = builtIn,
            source = sourceOf(declaration),
            assign = assign,
            proxy = proxy,
            install = install,
            proxyGet = proxyGet,
            proxySet = proxySet,
        ).takeUnless { failed }
    }

    /**
     * A hook function (`@ScriptInstall`, `@ScriptProxyGet`, `@ScriptProxySet`): public, not suspend or generic, with
     * exactly the parameter types [params] (qualified names; `kotlin.Any` stands for `Any?`) and a Unit result when
     * [returnsUnit].
     */
    private fun hookOf(function: KSFunctionDeclaration, annotation: String, params: List<String>, returnsUnit: Boolean): Member? {
        val display = "$annotation ${function.simpleName.asString()}"
        when {
            !function.isPublic() -> return error(function, "$display must be public")
            Modifier.SUSPEND in function.modifiers -> return error(function, "$display cannot be suspend")
            function.extensionReceiver != null -> return error(function, "$display cannot be an extension")
            function.typeParameters.isNotEmpty() -> return error(function, "$display cannot be generic")
        }
        val actual = function.parameters.map { it.type.resolve().declaration.qualifiedName?.asString() }
        if (actual != params || function.parameters.any { it.hasDefault || it.isVararg }) {
            return error(function, "$display must take (${params.joinToString { it.substringAfterLast('.') }})")
        }
        val returnType = function.returnType?.resolve()?.declaration?.qualifiedName?.asString() ?: "kotlin.Unit"
        if (returnsUnit && returnType != "kotlin.Unit") return error(function, "$display must return Unit")
        return Member(function.simpleName.asString(), isFunction = true)
    }

    /**
     * The script path of a module, as scripts reach it (`util.inspect`, `cvt.bytes`): the names along its `parent` chain
     * (appendix C Q19). A cycle or a parent without `@ScriptModule` ends the chain; both are reported elsewhere.
     */
    private fun pathOf(declaration: KSClassDeclaration, seen: Set<String> = emptySet()): String {
        val annotation = declaration.annotation(ScriptModuleNames.MODULE) ?: return declaration.simpleName.asString()
        val name = annotation.string("name")
        val self = declaration.qualifiedName?.asString() ?: return name
        val parent = annotation.type("parent")?.declaration as? KSClassDeclaration ?: return name
        val parentName = parent.qualifiedName?.asString()
        if (parentName == null || parentName == "kotlin.Unit" || parentName == self || parentName in seen) return name
        if (parent.annotation(ScriptModuleNames.MODULE) == null) return name
        return pathOf(parent, seen + self) + "." + name
    }

    private fun instanceOf(declaration: KSClassDeclaration): ModuleInstance? {
        if (declaration.classKind == ClassKind.OBJECT) return ModuleInstance.OBJECT
        if (declaration.classKind != ClassKind.CLASS || Modifier.ABSTRACT in declaration.modifiers) {
            return error(declaration, "A @ScriptModule must be an object or a concrete class")
        }
        val constructors = declaration.getConstructors().filter { it.isPublic() }.toList()
        constructors.firstOrNull { it.parameters.size == 1 && it.parameters[0].type.qualifiedName() == SCRIPT_RUNTIME }?.let { return ModuleInstance.RUNTIME }
        constructors.firstOrNull { c -> c.parameters.all { it.hasDefault } }?.let { return ModuleInstance.NO_ARG }
        return error(declaration, "A @ScriptModule class needs a public constructor taking nothing or one $SCRIPT_RUNTIME")
    }

    private fun groupOf(id: Int, name: String, moduleName: String, members: List<Pair<KSFunctionDeclaration, KSAnnotation>>, moduleBuiltIn: String?): FunctionGroup? {
        val (first, firstAnnotation) = members.first()
        val builtIn = firstAnnotation.enumEntries("builtIn")
        val builtInOnly = firstAnnotation.boolean("builtInOnly", false)
        val placement = if (builtInOnly) Placement(onModule = false, onGlobal = false) else firstAnnotation.placement()
        val attributes = firstAnnotation.int("attributes", PERMANENT)
        val aliases = firstAnnotation.strings("aliases")
        val toStringRole = firstAnnotation.enumEntry("toStringRole", "NONE")
        members.drop(1).forEach { (function, annotation) ->
            if (annotation.placement() != firstAnnotation.placement() || annotation.int("attributes", PERMANENT) != attributes || annotation.strings("aliases") != aliases ||
                annotation.enumEntry("toStringRole", "NONE") != toStringRole || annotation.enumEntries("builtIn") != builtIn || annotation.boolean("builtInOnly", false) != builtInOnly
            ) {
                error(function, "Overloads of script function \"$name\" must declare the same aliases, global placement, attributes, toStringRole and built-in placement as ${first.simpleName.asString()}")
            }
        }
        if (builtIn.isNotEmpty() && moduleBuiltIn == null) error(first, "$moduleName.$name: builtIn needs @ScriptModule(builtIn) on the module")
        if (builtInOnly && builtIn.isEmpty()) error(first, "$moduleName.$name: builtInOnly needs at least one builtIn placement")
        if (builtInOnly && firstAnnotation.enumEntry("global", "NONE") != "NONE") error(first, "$moduleName.$name: a builtInOnly function cannot be global")
        if ("PROTOTYPE" in builtIn && aliases.isNotEmpty()) error(first, "$moduleName.$name: a function extending a built-in prototype cannot have aliases")
        if (toStringRole != "NONE" && "toString" !in listOf(name) + aliases) {
            error(first, "$moduleName.$name: toStringRole applies to a script function named or aliased \"toString\" only")
        }
        if (toStringRole == "FUNCTIONAL" && !placement.onModule) {
            error(first, "$moduleName.$name: a FUNCTIONAL toString must be bound on the module object")
        }
        val overloads = members.mapNotNull { (function, _) -> overloadOf(function, "$moduleName.$name") }
        if (overloads.size != members.size) return null
        if ("PROTOTYPE" in builtIn && overloads.any { it.required < 1 }) {
            error(first, "$moduleName.$name: a function extending a built-in prototype takes the receiver as its first, required parameter")
        }
        checkAmbiguity(first, name, overloads)
        return FunctionGroup(id, name, aliases, placement, attributes, toStringRole, overloads, builtIn, builtInOnly)
    }

    private fun overloadOf(function: KSFunctionDeclaration, display: String): Overload? {
        when {
            !function.isPublic() -> return error(function, "$display: a script function must be public")
            Modifier.SUSPEND in function.modifiers -> return error(function, "$display: a script function cannot be suspend")
            function.extensionReceiver != null -> return error(function, "$display: a script function cannot be an extension")
            function.typeParameters.isNotEmpty() -> return error(function, "$display: a script function cannot be generic")
        }
        val params = function.parameters.map { paramOf(it, display) ?: return null }
        var seenScriptParam = false
        var seenOptional = false
        params.forEachIndexed { index, p ->
            if (p.kind.injected != null) {
                if (seenScriptParam) return error(function, "$display: injected parameter ${p.name} must come before the script parameters")
                return@forEachIndexed
            }
            seenScriptParam = true
            if (p.vararg && index != params.lastIndex) return error(function, "$display: the vararg parameter must be the last one")
            if (p.optional) seenOptional = true
            else if (seenOptional && !p.vararg) return error(function, "$display: parameter ${p.name} without a default value follows an optional one")
        }
        val returnType = function.returnType?.resolve()
        val returnsUnit = returnType == null || returnType.declaration.qualifiedName?.asString() == "kotlin.Unit"
        return Overload(
            kotlinName = function.simpleName.asString(),
            params = params,
            returnType = returnType?.let(::render) ?: "kotlin.Unit",
            returnsUnit = returnsUnit,
            source = sourceOf(function),
        )
    }

    private fun paramOf(parameter: KSValueParameter, display: String): Param? {
        val name = parameter.name?.asString() ?: return error(parameter, "$display: unnamed parameter")
        val type = parameter.type.resolve()
        val qualified = type.declaration.qualifiedName?.asString() ?: return error(parameter, "$display: cannot resolve the type of $name")
        val kind = when (qualified) {
            "kotlin.Any" -> ParamKind.RAW
            "kotlin.String", "kotlin.CharSequence" -> ParamKind.STRING
            "kotlin.Boolean" -> ParamKind.BOOLEAN
            "kotlin.Double", "kotlin.Number" -> ParamKind.DOUBLE
            "kotlin.Float" -> ParamKind.FLOAT
            "kotlin.Int" -> ParamKind.INT
            "kotlin.Long" -> ParamKind.LONG
            "kotlin.ByteArray" -> ParamKind.BYTES
            "kotlin.collections.List", "kotlin.collections.Collection", "kotlin.collections.Iterable" -> ParamKind.LIST
            "kotlin.collections.Map" -> ParamKind.MAP
            CONTEXT -> ParamKind.CONTEXT
            VAR_SCOPE -> ParamKind.SCOPE
            else -> ParamKind.TYPED
        }
        if (kind == ParamKind.TYPED && type.arguments.any { it.variance != Variance.STAR }) {
            return error(parameter, "$display: parameter $name of generic type ${render(type)} is not supported; use a star projection or Any?")
        }
        if (kind == ParamKind.TYPED && qualified in UNSUPPORTED_KOTLIN) {
            return error(parameter, "$display: parameter type $qualified is not supported")
        }
        if (kind.injected != null && (parameter.hasDefault || parameter.isVararg || type.isMarkedNullable)) {
            return error(parameter, "$display: injected parameter $name cannot be nullable, optional or vararg")
        }
        if (parameter.isVararg && kind.varargConverter == null) {
            return error(parameter, "$display: vararg of $qualified is not supported (use Any?, String, Double or Int)")
        }
        return Param(
            name = name,
            type = render(type),
            rawClass = qualified,
            kind = kind,
            nullable = type.isMarkedNullable,
            optional = parameter.hasDefault,
            vararg = parameter.isVararg,
        )
    }

    private fun accessorOf(member: KSDeclaration, annotation: KSAnnotation, id: Int, isGetter: Boolean): Accessor? {
        val kotlinName = member.simpleName.asString()
        val name = annotation.string("name").ifEmpty { kotlinName }
        if (!member.isPublic()) return error(member, "Script accessor $name must be public")
        return when (member) {
            is KSFunctionDeclaration -> {
                if (member.parameters.isNotEmpty()) return error(member, "A @ScriptGetter function takes no parameters")
                Accessor(id, name, kotlinName, isFunction = true, isGetter = true, mutable = false, member.returnType?.resolve()?.let(::render) ?: "kotlin.Unit", null, annotation.placement(), annotation.int("attributes", PERMANENT), sourceOf(member))
            }
            is KSPropertyDeclaration -> {
                val mutable = isGetter && member.isMutable && member.setter?.modifiers?.contains(Modifier.PRIVATE) != true
                val setterParam = if (mutable) {
                    val type = member.type.resolve()
                    val qualified = type.declaration.qualifiedName?.asString() ?: return error(member, "Cannot resolve the type of $kotlinName")
                    val kind = SIMPLE_KINDS[qualified] ?: ParamKind.TYPED
                    Param("value", render(type), qualified, kind, type.isMarkedNullable, optional = false, vararg = false)
                } else null
                val builtIn = if (isGetter) emptyList() else annotation.enumEntries("builtIn")
                Accessor(id, name, kotlinName, isFunction = false, isGetter = isGetter, mutable = mutable, render(member.type.resolve()), setterParam, annotation.placement(), annotation.int("attributes", PERMANENT), sourceOf(member), builtIn)
            }
            else -> error(member, "Unsupported accessor declaration")
        }
    }

    private fun checkAmbiguity(symbol: KSNode, name: String, overloads: List<Overload>) {
        if (overloads.size < 2) return
        val limit = overloads.maxOf { it.max ?: (it.required + 1) }
        for (count in 0..limit) {
            val candidates = overloads.filter { o -> count >= o.required && (o.max == null || count <= o.max!!) }
            val keys = candidates.map { o -> (0 until count).map { i -> guardKey(o, i) } }
            if (keys.size != keys.toSet().size) {
                error(symbol, "Overloads of script function \"$name\" cannot be told apart for $count argument(s): ${candidates.joinToString(" | ") { it.signature }}")
                return
            }
        }
    }

    private fun guardKey(overload: Overload, index: Int): String {
        val params = overload.scriptParams
        val p = if (index < params.size) params[index] else params.last()
        // Overloads are told apart by the runtime guards, so all numeric kinds share one key.
        return if (p.kind == ParamKind.TYPED) p.rawClass else p.kind.guard ?: "any"
    }

    private fun checkDuplicateNames(declaration: KSClassDeclaration, groups: List<FunctionGroup>, accessors: List<Accessor>, javaClasses: List<JavaClassBinding>) {
        val onModule = mutableMapOf<String, Int>()
        val onGlobal = mutableMapOf<String, Int>()
        fun count(names: List<String>, placement: Placement) {
            names.forEach { n ->
                if (placement.onModule) onModule[n] = (onModule[n] ?: 0) + 1
                if (placement.onGlobal) onGlobal[n] = (onGlobal[n] ?: 0) + 1
            }
        }
        groups.forEach { count(listOf(it.name) + it.aliases, it.placement) }
        accessors.forEach { count(listOf(it.name), it.placement) }
        javaClasses.forEach { count(listOf(it.name), it.placement) }
        val duplicates = (onModule.filterValues { it > 1 }.keys + onGlobal.filterValues { it > 1 }.keys).sorted()
        if (duplicates.isNotEmpty()) error(declaration, "Duplicate script member names: ${duplicates.joinToString()}")
    }

    /** Names bound by the built-in extension are unique per target (the built-in object, its prototype, the global object). */
    private fun checkDuplicateBuiltInNames(declaration: KSClassDeclaration, groups: List<FunctionGroup>, accessors: List<Accessor>) {
        val names = mutableMapOf<String, MutableList<String>>()
        groups.forEach { g -> g.builtIn.forEach { target -> (listOf(g.name) + g.aliases).forEach { names.getOrPut(target) { mutableListOf() } += it } } }
        accessors.forEach { a -> a.builtIn.forEach { target -> names.getOrPut(target) { mutableListOf() } += a.name } }
        val duplicates = names.flatMap { (target, list) -> list.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.map { "$target $it" } }.sorted()
        if (duplicates.isNotEmpty()) error(declaration, "Duplicate built-in extension names: ${duplicates.joinToString()}")
    }

    private fun sourceOf(node: KSNode): String {
        val location = node.location as? FileLocation ?: return ""
        val path = location.filePath.replace('\\', '/')
        val relative = projectDir?.replace('\\', '/')?.trimEnd('/')?.let { root -> path.removePrefix("$root/") } ?: path
        return "$relative:${location.lineNumber}"
    }

    private fun error(node: KSNode, message: String): Nothing? {
        logger.error(message, node)
        failed = true
        return null
    }

    companion object {

        const val PERMANENT = ScriptAttributes.PERMANENT
        private const val SCRIPT_RUNTIME = "org.autojs.autojs.runtime.ScriptRuntime"
        private const val CONTEXT = "org.mozilla.javascript.Context"
        private const val VAR_SCOPE = "org.mozilla.javascript.VarScope"
        private const val SCRIPTABLE = "org.mozilla.javascript.Scriptable"
        private const val INSTALLATION = "org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation"

        private val UNSUPPORTED_KOTLIN = setOf("kotlin.Short", "kotlin.Byte", "kotlin.Char", "kotlin.Unit", "kotlin.Nothing", "kotlin.Array")

        private val SIMPLE_KINDS = mapOf(
            "kotlin.Any" to ParamKind.RAW,
            "kotlin.String" to ParamKind.STRING,
            "kotlin.Boolean" to ParamKind.BOOLEAN,
            "kotlin.Double" to ParamKind.DOUBLE,
            "kotlin.Float" to ParamKind.FLOAT,
            "kotlin.Int" to ParamKind.INT,
            "kotlin.Long" to ParamKind.LONG,
        )

        /** Source text of a resolved type: fully qualified, with type arguments and nullability. */
        fun render(type: KSType): String = buildString {
            append(type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString())
            if (type.arguments.isNotEmpty()) {
                append(type.arguments.joinToString(", ", "<", ">") { argument ->
                    val argType = argument.type?.resolve()
                    when {
                        argument.variance == Variance.STAR || argType == null -> "*"
                        argument.variance == Variance.COVARIANT -> "out ${render(argType)}"
                        argument.variance == Variance.CONTRAVARIANT -> "in ${render(argType)}"
                        else -> render(argType)
                    }
                })
            }
            if (type.isMarkedNullable) append('?')
        }

        private fun KSTypeReference.qualifiedName(): String? = resolve().declaration.qualifiedName?.asString()

        private fun KSAnnotated.annotations(qualifiedName: String): Sequence<KSAnnotation> =
            annotations.filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }

        fun KSAnnotated.annotation(qualifiedName: String): KSAnnotation? = annotations(qualifiedName).firstOrNull()

        private fun KSAnnotation.value(name: String): Any? =
            arguments.firstOrNull { it.name?.asString() == name }?.value ?: defaultArguments.firstOrNull { it.name?.asString() == name }?.value

        fun KSAnnotation.string(name: String): String = value(name) as? String ?: ""

        fun KSAnnotation.boolean(name: String, default: Boolean): Boolean = value(name) as? Boolean ?: default

        fun KSAnnotation.int(name: String, default: Int): Int = (value(name) as? Number)?.toInt() ?: default

        fun KSAnnotation.strings(name: String): List<String> = (value(name) as? List<*>)?.map { it.toString() } ?: emptyList()

        fun KSAnnotation.type(name: String): KSType? = value(name) as? KSType

        /** Entries of an enum array argument by simple name, in the order written, without repetition. */
        fun KSAnnotation.enumEntries(name: String): List<String> = (value(name) as? List<*>)?.map { raw ->
            when (raw) {
                is KSType -> raw.declaration.simpleName.asString()
                is KSDeclaration -> raw.simpleName.asString()
                else -> raw.toString().substringAfterLast('.')
            }
        }?.distinct() ?: emptyList()

        /** Enum arguments arrive as the entry's declaration (KSP2) or as its type (KSP1). */
        fun KSAnnotation.enumEntry(name: String, default: String): String = when (val raw = value(name)) {
            is KSType -> raw.declaration.simpleName.asString()
            is KSDeclaration -> raw.simpleName.asString()
            null -> default
            else -> raw.toString().substringAfterLast('.')
        }

        fun KSAnnotation.placement(): Placement {
            return when (enumEntry("global", "NONE")) {
                "ALSO" -> Placement(onModule = true, onGlobal = true)
                "ONLY" -> Placement(onModule = false, onGlobal = true)
                else -> Placement(onModule = true, onGlobal = false)
            }
        }

    }

}

/** Qualified names of the annotations, taken from the shared annotations module so that the two cannot drift apart. */
internal object ScriptModuleNames {
    val MODULE = ScriptModule::class.qualifiedName!!
    val FUNCTION = ScriptFunction::class.qualifiedName!!
    val GETTER = ScriptGetter::class.qualifiedName!!
    val PROPERTY = ScriptProperty::class.qualifiedName!!
    val JAVA_CLASS = ScriptJavaClass::class.qualifiedName!!
    val INVOKE = ScriptInvoke::class.qualifiedName!!
    val CONSTRUCT = ScriptConstruct::class.qualifiedName!!
    val PROTOTYPE = ScriptPrototype::class.qualifiedName!!
    val INSTALL = ScriptInstall::class.qualifiedName!!
    val PROXY_GET = ScriptProxyGet::class.qualifiedName!!
    val PROXY_SET = ScriptProxySet::class.qualifiedName!!
}
