package org.cangnova.kcjpm.build

import java.nio.file.Files
import java.nio.file.Path

class BuildScriptStage(
    private val compiler: BuildScriptCompiler = DefaultBuildScriptCompiler(),
    private val executor: BuildScriptExecutor = DefaultBuildScriptExecutor()
) : CompilationStage {
    
    override val name: String = "build-script"
    
    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        val buildScriptPath = compiler.detectBuildScript(context.projectRoot)
        
        if (buildScriptPath == null) {
            return@runCatching context
        }
        
        val buildScriptOutDir = context.projectRoot
            .resolve(".kcjpm")
            .resolve("build-script")
        
        Files.createDirectories(buildScriptOutDir)
        
        val buildConfig = context.buildConfig
        
        val config = org.cangnova.kcjpm.config.ConfigLoader.loadFromProjectRoot(context.projectRoot).getOrNull()
        val packageName = config?.`package`?.name ?: "unknown"
        val packageVersion = config?.`package`?.version ?: "0.0.0"
        
        val scriptContext = BuildScriptContext(
            projectRoot = context.projectRoot,
            outDir = buildScriptOutDir,
            target = buildConfig.target ?: CompilationTarget.current(),
            profile = when (buildConfig.optimizationLevel) {
                OptimizationLevel.DEBUG -> "debug"
                else -> "release"
            },
            packageName = packageName,
            packageVersion = packageVersion
        )
        
        val executablePath = compiler.compile(
            buildScriptPath,
            buildScriptOutDir,
            scriptContext
        ).getOrThrow()
        
        val scriptResult = executor.execute(executablePath, scriptContext).getOrThrow()
        
        if (scriptResult.hasErrors) {
            throw BuildScriptExecutionException(
                "构建脚本返回错误:\n${scriptResult.errors.joinToString("\n")}"
            )
        }
        
        context.withBuildScriptResult(scriptResult)
    }
    
    context(context: CompilationContext)
    override suspend fun onComplete() {
    }
    
    context(context: CompilationContext)
    override suspend fun onFailure(error: Throwable) {
    }
}

private fun CompilationContext.withBuildScriptResult(result: BuildScriptResult): CompilationContext {
    val builder = when (this) {
        is DefaultCompilationContext -> this.toBuilder()
        else -> throw UnsupportedOperationException("不支持的 CompilationContext 实现")
    }
    
    result.linkLibraries.forEach { lib ->
        builder.addLinkLibrary(lib)
    }
    
    result.includeDirs.forEach { dir ->
        builder.addIncludeDir(dir)
    }
    
    return builder.build()
}