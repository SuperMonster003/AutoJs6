package org.autojs.autojs.ksp.modules

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate

class ScriptModulesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ScriptModulesProcessor(environment.codeGenerator, environment.logger, environment.options)
}

/**
 * Generates one `<Module>Binding` per @ScriptModule class (isolating output, it depends only on that class's file)
 * and, at the end of the compilation, the registry `<registry>` listing every binding of the compilation unit and
 * `autojs6/script-modules.json` over all modules (both aggregating outputs).
 *
 * Options: `scriptModules.projectDir`: source locations in the metadata are written relative to it.
 * `scriptModules.registry`: simple name of the registry object (default [DEFAULT_REGISTRY]); a compilation unit that
 * sees another unit's classes (the unit tests see main) must use its own name, and a generated class that already
 * exists on the classpath is a compile error.
 *
 * zh-CN: 为每个 @ScriptModule 类生成一个 `<模块>Binding` (隔离输出, 只依赖该类所在文件), 并在编译结束时生成列出本编译单元全部绑定的
 * 注册表与覆盖全部模块的 `autojs6/script-modules.json` (均为聚合输出). 选项 `scriptModules.projectDir`: 元数据中的源码位置相对于此目录;
 * `scriptModules.registry`: 注册表对象的简单名 (默认 ScriptModules), 能看到其他编译单元类的编译单元 (单元测试能看到主源码) 须使用不同名称,
 * 生成的类已存在于 classpath 时编译期报错.
 */
class ScriptModulesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor {

    private val analyzer = ScriptModuleAnalyzer(logger, options[OPTION_PROJECT_DIR])
    private val registryName = options[OPTION_REGISTRY]?.takeIf { it.isNotBlank() } ?: DEFAULT_REGISTRY
    private val modules = mutableListOf<ModuleModel>()
    private val sources = linkedSetOf<KSFile>()
    private var registryOnClasspath = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        registryOnClasspath = registryOnClasspath || resolver.existsOnClasspath("${BindingGenerator.PACKAGE}.$registryName")
        val symbols = resolver.getSymbolsWithAnnotation(ScriptModuleNames.MODULE).toList()
        val (ready, deferred) = symbols.partition { it.validate(enableNewFeatures = true) }
        ready.forEach { symbol ->
            if (symbol !is KSClassDeclaration) {
                logger.error("@ScriptModule applies to classes and objects only", symbol)
                return@forEach
            }
            val model = analyzer.analyze(symbol) ?: return@forEach
            modules.firstOrNull { it.bindingName == model.bindingName }?.let { other ->
                logger.error("Script module classes ${other.qualifiedName} and ${model.qualifiedName} would both generate ${model.bindingName}; rename one", symbol)
                return@forEach
            }
            modules.firstOrNull { it.parent == model.parent && it.names.intersect(model.names.toSet()).isNotEmpty() }?.let { other ->
                logger.error("Script modules ${other.qualifiedName} and ${model.qualifiedName} share a name on the same target", symbol)
                return@forEach
            }
            if (resolver.existsOnClasspath("${BindingGenerator.PACKAGE}.${model.bindingName}")) {
                logger.error("${BindingGenerator.PACKAGE}.${model.bindingName} already exists on the classpath (another compilation unit has a module class named ${model.simpleName}); rename one", symbol)
                return@forEach
            }
            val file = symbol.containingFile
            codeGenerator.createNewFile(
                dependencies = Dependencies(false, *listOfNotNull(file).toTypedArray()),
                packageName = BindingGenerator.PACKAGE,
                fileName = model.bindingName,
            ).use { it.write(BindingGenerator.generate(model).toByteArray(Charsets.UTF_8)) }
            modules += model
            file?.let(sources::add)
        }
        return deferred
    }

    override fun finish() {
        if (modules.isEmpty()) return
        if (registryOnClasspath) {
            logger.error("${BindingGenerator.PACKAGE}.$registryName already exists on the classpath; give this compilation unit its own registry name with the KSP option $OPTION_REGISTRY")
            return
        }
        modules.filter { it.parent != null && modules.none { parent -> parent.qualifiedName == it.parent } }.forEach {
            logger.error("Script module ${it.qualifiedName}: parent ${it.parent} is not a @ScriptModule of the same compilation unit")
        }
        codeGenerator.createNewFile(
            dependencies = Dependencies(true, *sources.toTypedArray()),
            packageName = BindingGenerator.PACKAGE,
            fileName = registryName,
        ).use { it.write(BindingGenerator.generateRegistry(registryName, modules).toByteArray(Charsets.UTF_8)) }
        codeGenerator.createNewFileByPath(
            dependencies = Dependencies(true, *sources.toTypedArray()),
            path = METADATA_PATH,
            extensionName = "json",
        ).use { it.write(MetadataWriter.write(modules).toByteArray(Charsets.UTF_8)) }
    }

    /** A class of that name compiled in another unit (a library or class directory on the classpath). */
    private fun Resolver.existsOnClasspath(qualifiedName: String): Boolean {
        val declaration = getClassDeclarationByName(getKSNameFromString(qualifiedName)) ?: return false
        return declaration.origin == Origin.KOTLIN_LIB || declaration.origin == Origin.JAVA_LIB
    }

    companion object {
        const val OPTION_PROJECT_DIR = "scriptModules.projectDir"
        const val OPTION_REGISTRY = "scriptModules.registry"
        const val DEFAULT_REGISTRY = "ScriptModules"
        const val METADATA_PATH = "autojs6/script-modules"
    }

}
