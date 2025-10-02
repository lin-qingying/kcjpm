package org.cangnova.kcjpm.build

import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * 默认编译流水线实现
 * 
 * 包含四个标准编译阶段：验证、依赖解析、包编译、链接
 * 支持动态添加、插入和移除阶段
 */
class DefaultCompilationPipeline(
    private val mutableStages: MutableList<CompilationStage> = mutableListOf(
        ValidationStage(),
        DependencyResolutionStage(), 
        PackageCompilationStage(),
        LinkingStage()
    )
) : CompilationPipeline {
    
    override val stages: List<CompilationStage>
        get() = mutableStages.toList()
    
    /**
     * 顺序执行所有编译阶段
     * 
     * 任何阶段失败时立即返回错误，成功则继续执行下一阶段
     */
    override suspend fun compile(context: CompilationContext): Result<CompilationResult> = runCatching {
        var currentContext = context
        
        for (stage in stages) {
            val result = stage.execute(currentContext)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                return Result.failure(
                    RuntimeException("编译阶段 '${stage.name}' 失败: ${error?.message}", error)
                )
            }
            currentContext = result.getOrThrow()
        }
        
        CompilationResult.Success(
            outputPath = currentContext.outputPath.toString(),
            artifacts = listOf(currentContext.outputPath.toString())
        )
    }
    
    override fun addStage(stage: CompilationStage): CompilationPipeline {
        return DefaultCompilationPipeline((mutableStages + stage).toMutableList())
    }
    
    override fun insertStageAfter(afterStage: String, stage: CompilationStage): Result<CompilationPipeline> {
        val index = mutableStages.indexOfFirst { it.name == afterStage }
        
        return if (index >= 0) {
            val newStages = mutableStages.toMutableList()
            newStages.add(index + 1, stage)
            Result.success(DefaultCompilationPipeline(newStages))
        } else {
            Result.failure(IllegalArgumentException("找不到编译阶段: $afterStage"))
        }
    }
    
    override fun removeStage(stageName: String): CompilationPipeline {
        val newStages = mutableStages.filterNot { it.name == stageName }.toMutableList()
        return DefaultCompilationPipeline(newStages)
    }
}

/**
 * 验证阶段：检查项目根目录、源文件和输出路径的有效性
 */
class ValidationStage : CompilationStage {
    override val name: String = "validation"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        if (!context.projectRoot.exists() || !context.projectRoot.isDirectory()) {
            throw IllegalArgumentException("项目根目录不存在或不是目录: ${context.projectRoot}")
        }
        
        if (context.sourceFiles.isEmpty()) {
            throw IllegalArgumentException("没有指定源文件")
        }
        
        context.sourceFiles.forEach { sourceFile ->
            if (!sourceFile.exists()) {
                throw IllegalArgumentException("源文件不存在: $sourceFile")
            }
            if (!sourceFile.toString().endsWith(".cj")) {
                throw IllegalArgumentException("不是有效的仓颉源文件: $sourceFile")
            }
        }
        
        val outputParent = context.outputPath.parent
        if (outputParent != null && !outputParent.exists()) {
            outputParent.toFile().mkdirs()
        }
        
        context
    }
}

/**
 * 依赖解析阶段：解析和下载项目依赖（当前为占位实现）
 */
class DependencyResolutionStage : CompilationStage {
    override val name: String = "dependency-resolution"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        context
    }
}

/**
 * 包编译阶段：发现项目中的包并并行编译为 .cjo 库文件
 */
class PackageCompilationStage : CompilationStage {
    override val name: String = "package-compilation"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        // 发现项目中的所有包
        val packages = with(context) { PackageDiscovery.discoverPackages() }
        
        // 并行编译所有包
        val compiledLibraries = coroutineScope {
            packages.map { packageInfo ->
                async {
                    compilePackage(packageInfo, context)
                }
            }.toList().awaitAll()
        }
        
        context
    }
    
    /**
     * 编译单个包为 .cjo 库文件
     * 
     * @param packageInfo 包信息（名称、根目录、源文件列表）
     * @param context 编译上下文
     * @return 编译生成的库文件路径
     */
    private suspend fun compilePackage(packageInfo: PackageInfo, context: CompilationContext): Path = withContext(Dispatchers.IO) {
        val outputDir = packageInfo.packageRoot.resolve("target")
        outputDir.toFile().mkdirs()
        
        val libraryPath = outputDir.resolve("${packageInfo.name}.cjo")
        
        val command = with(context) {
            CompilationCommandBuilder().buildPackageCommand(
                packageDir = packageInfo.packageRoot,
                outputPath = libraryPath,
                moduleName = packageInfo.name
            )
        }
        
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(packageInfo.packageRoot.toFile())
        
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val errorOutput = process.errorStream.bufferedReader().readText()
            throw RuntimeException("包编译失败: ${packageInfo.name}, 错误: $errorOutput")
        }
        
        libraryPath
    }
}

/**
 * 链接阶段：查找 main 函数并链接所有库文件生成可执行文件
 */
class LinkingStage : CompilationStage {
    override val name: String = "linking"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        // 查找包含 main() 函数的源文件
        val mainFile = findMainFile(context.sourceFiles)
            ?: throw IllegalArgumentException("找不到包含 main 函数的源文件")
        
        // 收集所有需要链接的库文件（.cjo, .so, .dylib, .dll）
        val libraryFiles = with(context) { DependencyCollector.collectLibraryFiles() }
        
        val command = with(context) {
            CompilationCommandBuilder().buildExecutableCommand(
                mainFile = mainFile,
                libraryFiles = libraryFiles,
                outputPath = outputPath
            )
        }
        
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(context.projectRoot.toFile())
        
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val errorOutput = process.errorStream.bufferedReader().readText()
            throw RuntimeException("链接失败: $errorOutput")
        }
        
        context
    }
    
    /**
     * 在源文件中查找包含 main() {} 函数的文件
     * 
     * @param sourceFiles 源文件列表
     * @return 包含 main 函数的源文件，未找到则返回 null
     */
    private fun findMainFile(sourceFiles: List<Path>): Path? {
        return sourceFiles.find { sourceFile ->
            try {
                val content = sourceFile.toFile().readText()
                content.contains(Regex("""main\s*\(\s*\)\s*\{"""))
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * 编译管理器：提供编译流水线的统一入口
 */
class CompilationManager {
    private val pipeline: CompilationPipeline = DefaultCompilationPipeline()
    
    /**
     * 执行完整的编译流程（验证 -> 依赖解析 -> 包编译 -> 链接）
     */
    suspend fun compile(context: CompilationContext): Result<CompilationResult> {
        return pipeline.compile(context)
    }
    
    /**
     * 仅编译包，不执行链接（用于库项目）
     */
    suspend fun compilePackagesOnly(context: CompilationContext): Result<List<Path>> = runCatching {
        val validationStage = ValidationStage()
        val packageStage = PackageCompilationStage()
        
        var currentContext = context
        currentContext = validationStage.execute(currentContext).getOrThrow()
        packageStage.execute(currentContext).getOrThrow()
        
        emptyList()
    }
    
    fun withCustomPipeline(stages: List<CompilationStage>): CompilationManager {
        return CompilationManager()
    }
}