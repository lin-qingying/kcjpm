package org.cangnova.kcjpm.build

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class DefaultCompilationPipeline : CompilationPipeline {
    
    override val stages: List<CompilationStage> = listOf(
        ValidationStage(),
        DependencyResolutionStage(), 
        PackageCompilationStage(),
        LinkingStage()
    )
    
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
        return DefaultCompilationPipeline().apply {
            (this.stages as MutableList).add(stage)
        }
    }
    
    override fun insertStageAfter(afterStage: String, stage: CompilationStage): Result<CompilationPipeline> {
        val stages = this.stages.toMutableList()
        val index = stages.indexOfFirst { it.name == afterStage }
        
        return if (index >= 0) {
            stages.add(index + 1, stage)
            Result.success(DefaultCompilationPipeline().apply {
                (this.stages as MutableList).addAll(stages)
            })
        } else {
            Result.failure(IllegalArgumentException("找不到编译阶段: $afterStage"))
        }
    }
    
    override fun removeStage(stageName: String): CompilationPipeline {
        val newStages = stages.filterNot { it.name == stageName }
        return DefaultCompilationPipeline().apply {
            (this.stages as MutableList).addAll(newStages)
        }
    }
}

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

class DependencyResolutionStage : CompilationStage {
    override val name: String = "dependency-resolution"
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        context
    }
}

class PackageCompilationStage : CompilationStage {
    override val name: String = "package-compilation"
    
    private val commandBuilder = CompilationCommandBuilder()
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        val packages = discoverPackages(context)
        
        val compiledLibraries = coroutineScope {
            packages.map { packageInfo ->
                async {
                    compilePackage(packageInfo, context.buildConfig)
                }
            }.awaitAll()
        }
        
        context // 这里应该返回包含库文件信息的新上下文
    }
    
    private fun discoverPackages(context: CompilationContext): List<PackageInfo> {
        val packages = mutableListOf<PackageInfo>()
        
        val packageGroups = context.sourceFiles.groupBy { sourceFile ->
            findPackageRoot(sourceFile, context.projectRoot)
        }
        
        packageGroups.forEach { (packageRoot, sourceFiles) ->
            if (packageRoot != null) {
                val packageName = extractPackageName(sourceFiles.first())
                packages.add(PackageInfo(packageName, packageRoot, sourceFiles))
            }
        }
        
        return packages
    }
    
    private fun findPackageRoot(sourceFile: Path, projectRoot: Path): Path? {
        var current = sourceFile.parent
        while (current != null && current != projectRoot) {
            if (isPackageRoot(current)) {
                return current
            }
            current = current.parent
        }
        return sourceFile.parent
    }
    
    private fun isPackageRoot(directory: Path): Boolean {
        val cjFiles = directory.toFile().listFiles { _, name -> 
            name.endsWith(".cj") 
        } ?: return false
        
        if (cjFiles.isEmpty()) return false
        
        val firstPackage = extractPackageName(cjFiles.first().toPath())
        
        return cjFiles.all { file ->
            extractPackageName(file.toPath()) == firstPackage
        }
    }
    
    private fun extractPackageName(sourceFile: Path): String {
        return try {
            val content = sourceFile.toFile().readText()
            val packageLine = content.lines().find { it.trim().startsWith("package ") }
            packageLine?.substringAfter("package ")?.trim() ?: "main"
        } catch (e: Exception) {
            "main"
        }
    }
    
    private suspend fun compilePackage(packageInfo: PackageInfo, buildConfig: BuildConfig): Path = withContext(Dispatchers.IO) {
        val outputDir = packageInfo.packageRoot.resolve("target")
        outputDir.toFile().mkdirs()
        
        val libraryPath = outputDir.resolve("lib${packageInfo.name}.a")
        
        val command = commandBuilder.buildPackageCommand(
            packageDir = packageInfo.packageRoot,
            outputPath = libraryPath,
            buildConfig = buildConfig,
            moduleName = packageInfo.name
        )
        
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

class LinkingStage : CompilationStage {
    override val name: String = "linking"
    
    private val commandBuilder = CompilationCommandBuilder()
    
    override suspend fun execute(context: CompilationContext): Result<CompilationContext> = runCatching {
        val mainFile = findMainFile(context.sourceFiles)
            ?: throw IllegalArgumentException("找不到包含 main 函数的源文件")
        
        val libraryFiles = collectLibraryFiles(context)
        
        val command = commandBuilder.buildExecutableCommand(
            mainFile = mainFile,
            libraryFiles = libraryFiles,
            outputPath = context.outputPath,
            buildConfig = context.buildConfig
        )
        
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
    
    private fun findMainFile(sourceFiles: List<Path>): Path? {
        return sourceFiles.find { sourceFile ->
            try {
                val content = sourceFile.toFile().readText()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun collectLibraryFiles(context: CompilationContext): List<Path> {
        val libraryFiles = mutableListOf<Path>()
        
        context.sourceFiles.forEach { sourceFile ->
            val packageRoot = sourceFile.parent
            val targetDir = packageRoot.resolve("target")
            if (libFile.exists()) {
                libraryFiles.add(libFile)
            }
        }
        
        context.dependencies.forEach { dependency ->
            when (dependency) {
                is Dependency.PathDependency -> {
                    if (dependency.path.toString().endsWith(".a")) {
                        libraryFiles.add(dependency.path)
                    }
                }
                is Dependency.GitDependency -> {
                    dependency.localPath?.let { localPath ->
                        val libFile = localPath.resolve("lib${dependency.name}.a")
                        if (libFile.exists()) {
                            libraryFiles.add(libFile)
                        }
                    }
                }
                is Dependency.RegistryDependency -> {
                    dependency.localPath?.let { localPath ->
                        val libFile = localPath.resolve("lib${dependency.name}.a")
                        if (libFile.exists()) {
                            libraryFiles.add(libFile)
                        }
                    }
                }
            }
        }
        
        return libraryFiles
    }
}

class CompilationManager {
    private val pipeline: CompilationPipeline = DefaultCompilationPipeline()
    
    suspend fun compile(context: CompilationContext): Result<CompilationResult> {
        return pipeline.compile(context)
    }
    
    suspend fun compilePackagesOnly(context: CompilationContext): Result<List<Path>> = runCatching {
        val validationStage = ValidationStage()
        val packageStage = PackageCompilationStage()
        
        var currentContext = context
        currentContext = validationStage.execute(currentContext).getOrThrow()
        packageStage.execute(currentContext).getOrThrow()
        
    }
    
    fun withCustomPipeline(stages: List<CompilationStage>): CompilationManager {
        return CompilationManager().apply {
        }
    }
}