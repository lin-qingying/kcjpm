package org.cangnova.kcjpm.build

import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * 默认编译流水线实现
 *
 * 包含三个标准编译阶段：验证、依赖解析、包编译
 * cjc 编译器会自动处理链接过程，因此不需要单独的链接阶段
 * 支持动态添加、插入和移除阶段
 */
class DefaultCompilationPipeline(
    private val mutableStages: MutableList<CompilationStage> = mutableListOf(
        ValidationStage(),
        DependencyResolutionStage(),
        PackageCompilationStage()
        // 移除 LinkingStage，因为 cjc 编译器会自动链接
    )
) : CompilationPipeline {

    override val stages: List<CompilationStage>
        get() = mutableStages.toList()

    override suspend fun compile(context: CompilationContext): Result<CompilationResult> = runCatching {
        var currentContext = context

        with(currentContext) {
            emit(PipelineStartedEvent(totalStages = stages.size))
        }

        for ((index, stage) in stages.withIndex()) {
            with(currentContext) {
                emit(StageStartedEvent(
                    stageName = stage.name,
                    stageIndex = index,
                    totalStages = stages.size
                ))
            }
            
            val result = with(currentContext) { stage.execute() }
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                return Result.failure(
                    RuntimeException("编译阶段 '${stage.name}' 失败: ${error?.message}", error)
                )
            }
            currentContext = result.getOrThrow()
            
            with(currentContext) {
                emit(StageCompletedEvent(
                    stageName = stage.name,
                    stageIndex = index,
                    totalStages = stages.size
                ))
            }
        }

        with(currentContext) {
            emit(PipelineCompletedEvent(success = true))
        }

        CompilationResult.Success(
            outputPath = currentContext.outputPath.toString(),
            artifacts = listOf(currentContext.outputPath.toString())
        )
    }

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationResult> = compile(context)

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

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        emit(ValidationEvent("验证项目根目录: ${context.projectRoot}"))
        
        if (!context.projectRoot.exists() || !context.projectRoot.isDirectory()) {
            throw IllegalArgumentException("Project root does not exist or is not a directory: ${context.projectRoot}")
        }

        emit(ValidationEvent("验证源文件 (${context.sourceFiles.size} 个文件)"))
        
        if (context.sourceFiles.isEmpty()) {
            throw IllegalArgumentException("No source files specified")
        }

        context.sourceFiles.forEach { sourceFile ->
            if (!sourceFile.exists()) {
                throw IllegalArgumentException("Source file does not exist: $sourceFile")
            }
            if (!sourceFile.toString().endsWith(".cj")) {
                throw IllegalArgumentException("Not a valid Cangjie source file: $sourceFile")
            }
            emit(ValidationEvent("验证源文件: $sourceFile", sourceFile))
        }

        emit(ValidationEvent("验证输出路径: ${context.outputPath}"))
        
        val outputParent = context.outputPath.parent
        if (outputParent != null && !outputParent.exists()) {
            emit(ValidationEvent("创建输出目录: $outputParent", outputParent))
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

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        emit(DependencyResolutionEvent(
            message = "开始解析依赖 (${context.dependencies.size} 个依赖)"
        ))
        
        context.dependencies.forEach { dependency ->
            emit(DependencyResolutionEvent(
                message = "依赖: ${dependency.name}",
                dependencyName = dependency.name,
                dependencyType = dependency.javaClass.simpleName
            ))
        }
        
        // TODO: 实际的依赖解析逻辑
        
        emit(DependencyResolutionEvent(
            message = "依赖解析完成"
        ))
        
        context
    }
}

/**
 * 包编译阶段：发现项目中的包并并行编译为 .cjo 库文件
 */
class PackageCompilationStage : CompilationStage {
    override val name: String = "package-compilation"

    private val reportBuilder = CompilationReportBuilder()
    private var cacheManager: IncrementalCacheManager? = null
    private val changeDetector = FileChangeDetector()

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        val packages = PackageDiscovery.discoverPackages()
        val packageList = packages.toList()

        emit(PackageDiscoveryEvent(
            totalPackages = packageList.size,
            packages = packageList.map { packageInfo ->
                PackageDiscoveryInfo(
                    name = packageInfo.name,
                    path = packageInfo.packageRoot,
                    sourceFileCount = packageInfo.sourceFiles.size,
                    sourceFiles = packageInfo.sourceFiles
                )
            }
        ))

        if (context.buildConfig.incremental) {
            val cacheDir = context.outputPath.resolve(".kcjpm-cache")
            cacheManager = IncrementalCacheManager(cacheDir)
            emit(IncrementalCacheEvent(
                message = "启用增量编译",
                cacheDir = cacheDir
            ))
        }

        var cache = cacheManager?.loadCache() ?: CompilationCache()
        val buildConfigHash = computeBuildConfigHash(context.buildConfig)

        val compiledLibraries = coroutineScope {
            packageList.map { packageInfo ->
                async {
                    val result = compilePackageWithCache(packageInfo, context, cache, buildConfigHash)
                    synchronized(this@PackageCompilationStage) {
                        cache = cacheManager?.updatePackageCache(
                            cache,
                            packageInfo,
                            result,
                            buildConfigHash
                        ) ?: cache
                    }
                    result
                }
            }.toList().awaitAll()
        }

        cacheManager?.saveCache(cache)

        if (context.buildConfig.verbose) {
            emit(ValidationEvent("所有包编译完成，生成了 ${compiledLibraries.size} 个库文件"))
        }

        context
    }

    fun getReport(): CompilationReport = reportBuilder.build()

    private suspend fun compilePackageWithCache(
        packageInfo: PackageInfo,
        context: CompilationContext,
        cache: CompilationCache,
        buildConfigHash: String
    ): Path = withContext(Dispatchers.IO) {
        val cachedEntry = cache.packages[packageInfo.name]
        val changeResult = changeDetector.detectChanges(packageInfo, cachedEntry, buildConfigHash)

        if (changeResult is ChangeDetectionResult.NoChanges) {
            val cachedOutputPath = Path.of(cachedEntry!!.outputPath)
            with(context) {
                emit(ChangeDetectionEvent(
                    packageName = packageInfo.name,
                    changeType = "NoChanges",
                    details = "无变更，跳过编译"
                ))
            }
            return@withContext cachedOutputPath
        }

        with(context) {
            when (changeResult) {
                is ChangeDetectionResult.NoCacheFound -> 
                    emit(ChangeDetectionEvent(packageInfo.name, "NoCacheFound", "首次编译"))
                is ChangeDetectionResult.BuildConfigChanged -> 
                    emit(ChangeDetectionEvent(packageInfo.name, "BuildConfigChanged", "构建配置变更"))
                is ChangeDetectionResult.OutputMissing -> 
                    emit(ChangeDetectionEvent(packageInfo.name, "OutputMissing", "输出文件缺失"))
                is ChangeDetectionResult.FilesChanged -> {
                    if (changeResult.added.isNotEmpty()) 
                        emit(ChangeDetectionEvent(packageInfo.name, "FilesAdded", "新增文件 ${changeResult.added.size} 个"))
                    if (changeResult.removed.isNotEmpty()) 
                        emit(ChangeDetectionEvent(packageInfo.name, "FilesRemoved", "删除文件 ${changeResult.removed.size} 个"))
                    if (changeResult.modified.isNotEmpty()) 
                        emit(ChangeDetectionEvent(packageInfo.name, "FilesModified", "修改文件 ${changeResult.modified.size} 个"))
                }
                else -> {}
            }
        }

        compilePackage(packageInfo, context)
    }

    private suspend fun compilePackage(packageInfo: PackageInfo, context: CompilationContext): Path =
        withContext(Dispatchers.IO) {
            context.outputPath.toFile().mkdirs()

            val libsDir = context.outputPath.resolve("libs")
            libsDir.toFile().mkdirs()

            val isMainPackage = with(context) { packageInfo.isMainPackage() }
            val libraryFileName = getPackageOutputFileName(packageInfo.name, context.outputType, isMainPackage, context.buildConfig.target)
            val outputType = getPackageOutputType(context.outputType, isMainPackage)
            
            // 根据输出类型决定输出目录：可执行文件输出到上级目录，库文件输出到 libs 目录
            val actualOutputDir = if (outputType == "exe") context.outputPath else libsDir
            val libraryPath = actualOutputDir.resolve(libraryFileName)

            val importPaths = listOf(context.outputPath)

            val command = with(context) {
                CompilationCommandBuilder().buildPackageCommand(
                    packageDir = packageInfo.packageRoot,
                    outputDir = actualOutputDir,
                    outputFileName = libraryFileName,
                    outputType = outputType,
                    importPaths = importPaths,
                    hasSubPackages = packageInfo.hasSubPackages
                )
            }

            with(context) {
                emit(PackageCompilationStartedEvent(
                    packageName = packageInfo.name,
                    packagePath = packageInfo.packageRoot
                ))
                emit(PackageCompilationCommandEvent(
                    packageName = packageInfo.name,
                    command = command
                ))
            }

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(packageInfo.packageRoot.toFile())

            // 重定向错误流到标准输出，或者分别处理
            // processBuilder.redirectErrorStream(true) // 可选：合并流

            val process = processBuilder.start()

            val errors = mutableListOf<CjcDiagnostic>()
            val warnings = mutableListOf<CjcDiagnostic>()
            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            val exitCode = coroutineScope {
                val stdoutJob = launch(Dispatchers.IO) {
                    val stdoutParser = CjcOutputParser()
                    try {
                        process.inputStream.bufferedReader().use { reader ->
                            reader.lines().forEach { line ->
                                stdoutLines.add(line)
                                with(context) {
                                    emit(CompilerOutputEvent(
                                        packageName = packageInfo.name,
                                        line = line,
                                        isStderr = false
                                    ))
                                }

                                stdoutParser.parseLine(line, false)?.let { event ->
                                    when (event) {
                                        is CjcOutputEvent.CompilationProgress -> {}
                                        is CjcOutputEvent.CompilationError -> {
                                            synchronized(errors) { errors.add(event.error) }
                                        }
                                        is CjcOutputEvent.CompilationWarning -> {
                                            synchronized(warnings) { warnings.add(event.warning) }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 读取 stdout 失败
                    }
                }

                val stderrJob = launch(Dispatchers.IO) {
                    val stderrParser = CjcOutputParser()
                    try {
                        process.errorStream.bufferedReader().use { reader ->
                            reader.lines().forEach { line ->
                                stderrLines.add(line)
                                with(context) {
                                    emit(CompilerOutputEvent(
                                        packageName = packageInfo.name,
                                        line = line,
                                        isStderr = true
                                    ))
                                }

                                stderrParser.parseLine(line, true)?.let { event ->
                                    when (event) {
                                        is CjcOutputEvent.CompilationError -> {
                                            synchronized(errors) { errors.add(event.error) }
                                        }
                                        is CjcOutputEvent.CompilationWarning -> {
                                            synchronized(warnings) { warnings.add(event.warning) }
                                        }
                                        is CjcOutputEvent.CompilationProgress -> {}
                                        else -> {}
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 读取 stderr 失败
                    }
                }

                // 等待读取任务完成或进程结束
                val exitCodeDeferred = async { process.waitFor() }

                // 确保读取任务有机会运行
                joinAll(stdoutJob, stderrJob)

                // 获取退出码
                exitCodeDeferred.await()
            }

            // 创建详细的报告
            val report = PackageCompilationReport(
                packageName = packageInfo.name,
                packageRoot = packageInfo.packageRoot,
                success = exitCode == 0,
                errors = errors,
                warnings = warnings,
                outputPath = if (exitCode == 0) libraryPath else null,
//                stdoutLog = stdoutLines.joinToString("\n"),  // 保存完整日志
//                stderrLog = stderrLines.joinToString("\n"),  // 保存完整日志
                exception = if (exitCode != 0) {
                    RuntimeException("编译失败，退出码: $exitCode")
                } else null
            )
            reportBuilder.addPackageReport(report)

            with(context) {
                emit(PackageCompilationCompletedEvent(
                    packageName = packageInfo.name,
                    success = exitCode == 0,
                    outputPath = if (exitCode == 0) libraryPath else null,
                    errors = errors,
                    warnings = warnings
                ))
            }

            if (exitCode != 0) {
                val errorMsg = buildString {
                    appendLine("包编译失败: ${packageInfo.name}")
                    appendLine("退出码: $exitCode")
                    if (errors.isNotEmpty()) {
                        appendLine("错误:")
                        errors.forEach { error ->
                            appendLine("  ${error.file}:${error.line}:${error.column}: ${error.message}")
                        }
                    }
                    if (stderrLines.isNotEmpty()) {
                        appendLine("标准错误输出:")
                        stderrLines.forEach { appendLine("  $it") }
                    }
                }
                throw RuntimeException(errorMsg)
            }

            libraryPath
        }
}

/**
 * 编译管理器：提供编译流水线的统一入口
 */
class CompilationManager {
    private val pipeline: CompilationPipeline = DefaultCompilationPipeline()
    private var compilationReport: CompilationReport? = null

    context(context: CompilationContext)
    suspend fun compile(): Result<CompilationResult> {
        val result = pipeline.execute()

        val packageStage = (pipeline as? DefaultCompilationPipeline)
            ?.stages
            ?.find { it is PackageCompilationStage } as? PackageCompilationStage

        val reportBuilder = CompilationReportBuilder()

        packageStage?.getReport()?.packages?.forEach { pkgReport ->
            reportBuilder.addPackageReport(pkgReport)
        }

        compilationReport = reportBuilder.build()

        return result
    }

    fun getReport(): CompilationReport? = compilationReport

    context(context: CompilationContext)
    suspend fun compilePackagesOnly(): Result<List<Path>> = runCatching {
        val validationStage = ValidationStage()
        val packageStage = PackageCompilationStage()

        var currentContext = context
        currentContext = with(currentContext) { validationStage.execute() }.getOrThrow()
        with(currentContext) { packageStage.execute() }.getOrThrow()

        compilationReport = packageStage.getReport()

        emptyList()
    }

    fun withCustomPipeline(stages: List<CompilationStage>): CompilationManager {
        return CompilationManager()
    }
}

private fun getPackageOutputType(outputType: org.cangnova.kcjpm.config.OutputType, isMainPackage: Boolean): String =
    when (outputType) {
        org.cangnova.kcjpm.config.OutputType.EXECUTABLE if isMainPackage -> "exe"
        org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY -> "dylib"
        else -> "staticlib"
    }

private fun getPackageOutputFileName(
    name: String, 
    outputType: org.cangnova.kcjpm.config.OutputType, 
    isMainPackage: Boolean,
    target: CompilationTarget?
): String =
    when (outputType) {
        org.cangnova.kcjpm.config.OutputType.EXECUTABLE if isMainPackage -> {
            if (name.endsWith(".exe")) name
            else when (target ?: CompilationTarget.current()) {
                CompilationTarget.WINDOWS_X64 -> "$name.exe"
                else -> name
            }
        }
        org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY -> "lib$name.b.dll"
        else -> "lib$name.a"
    }