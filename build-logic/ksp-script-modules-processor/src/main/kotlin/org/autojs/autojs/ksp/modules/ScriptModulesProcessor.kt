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
import com.google.devtools.ksp.validate

class ScriptModulesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        ScriptModulesProcessor(environment.codeGenerator, environment.logger, environment.options)
}

/**
 * Generates one `<Module>Binding` per @ScriptModule class (isolating output, it depends only on that class's file)
 * and, at the end of the compilation, `autojs6/script-modules.json` over all modules (aggregating output).
 *
 * Option `scriptModules.projectDir`: source locations in the metadata are written relative to it.
 *
 * zh-CN: 为每个 @ScriptModule 类生成一个 `<模块>Binding` (隔离输出, 只依赖该类所在文件), 并在编译结束时生成覆盖全部模块的
 * `autojs6/script-modules.json` (聚合输出). 选项 `scriptModules.projectDir`: 元数据中的源码位置相对于此目录.
 */
class ScriptModulesProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String>,
) : SymbolProcessor {

    private val analyzer = ScriptModuleAnalyzer(logger, options[OPTION_PROJECT_DIR])
    private val modules = mutableListOf<ModuleModel>()
    private val sources = linkedSetOf<KSFile>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
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
        codeGenerator.createNewFileByPath(
            dependencies = Dependencies(true, *sources.toTypedArray()),
            path = METADATA_PATH,
            extensionName = "json",
        ).use { it.write(MetadataWriter.write(modules).toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        const val OPTION_PROJECT_DIR = "scriptModules.projectDir"
        const val METADATA_PATH = "autojs6/script-modules"
    }

}
