package org.cangnova.kcjpm.build

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

private val logger = KotlinLogging.logger {}

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

    override suspend fun compile(context: CompilationContext): Result<CompilationResult> = runCatching {
        var currentContext = context

        for (stage in stages) {
            val result = with(currentContext) { stage.execute() }
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

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        context
    }
}

/**
 * 包编译阶段：发现项目中的包并并行编译为 .cjo 库文件
 */
class PackageCompilationStage : CompilationStage {
    override val name: String = "package-compilation"

    private val reportBuilder = CompilationReportBuilder()

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        val packages = PackageDiscovery.discoverPackages()

        val compiledLibraries = coroutineScope {
            packages.map { packageInfo ->
                async {
                    compilePackage(packageInfo, context)
                }
            }.toList().awaitAll()
        }

        context
    }

    fun getReport(): CompilationReport = reportBuilder.build()

    private suspend fun compilePackage(packageInfo: PackageInfo, context: CompilationContext): Path =
        withContext(Dispatchers.IO) {
            context.outputPath.toFile().mkdirs()

            val outputDir = context.outputPath.resolve("libs")
            outputDir.toFile().mkdirs()



            val isMainPackage = with(context) { packageInfo.isMainPackage() }
            val libraryFileName = getPackageOutputFileName(packageInfo.name, context.outputType, isMainPackage)
            val outputType = getPackageOutputType(context.outputType, isMainPackage)
            val libraryPath = outputDir.resolve(libraryFileName)

            val importPaths = listOf(context.outputPath)

            val command = with(context) {
                CompilationCommandBuilder().buildPackageCommand(
                    packageDir = packageInfo.packageRoot,
                    outputDir = outputDir,
                    outputFileName = libraryFileName,
                    outputType = outputType,
                    importPaths = importPaths,
                    hasSubPackages = packageInfo.hasSubPackages
                )
            }
            val command1 = listOf(
                "C:\\Users\\lin17\\sdk\\cangjie-sdk-windows-x64-1.0.1\\cangjie\\bin\\cjc.exe",
                "-j10",
                "--import-path=D:\\code\\cangjie\\bson\\target\\release",
"--output-dir=D:\\code\\cangjie\\bson\\target\\release\\libs",
                "-p",
                "D:\\code\\cangjie\\bson\\src\\a",
                "--no-sub-pkg",
                "--output-type=staticlib",
                "-o=libbson.a.a",
                "-O2"
            )
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
                                logger.debug { "[STDOUT] $line" }

                                stdoutParser.parseLine(line, false)?.let { event ->
                                    when (event) {
                                        is CjcOutputEvent.CompilationProgress -> {
                                            logger.debug { event.message }
                                        }
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
                        logger.error(e) { "[SYSTEM_ERROR] 读取 stdout 失败" }
                    }
                }

                val stderrJob = launch(Dispatchers.IO) {
                    val stderrParser = CjcOutputParser()
                    try {
                        process.errorStream.bufferedReader().use { reader ->
                            reader.lines().forEach { line ->
                                stderrLines.add(line)
                                logger.debug { "[STDERR] $line" }

                                stderrParser.parseLine(line, true)?.let { event ->
                                    when (event) {
                                        is CjcOutputEvent.CompilationError -> {
                                            synchronized(errors) { errors.add(event.error) }
                                            logger.debug { "[COMPILER_ERROR] ${event.error.file}:${event.error.line}:${event.error.column}: ${event.error.message}" }
                                        }
                                        is CjcOutputEvent.CompilationWarning -> {
                                            synchronized(warnings) { warnings.add(event.warning) }
                                            logger.debug { "[COMPILER_WARNING] ${event.warning.file}:${event.warning.line}:${event.warning.column}: ${event.warning.message}" }
                                        }
                                        is CjcOutputEvent.CompilationProgress -> {
                                            logger.debug { event.message }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "[SYSTEM_ERROR] 读取 stderr 失败" }
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
 * 链接阶段：查找 main 函数并链接所有库文件生成可执行文件
 */
class LinkingStage : CompilationStage {
    override val name: String = "linking"

    private var linkingReport: LinkingReport? = null

    context(context: CompilationContext)
    override suspend fun execute(): Result<CompilationContext> = runCatching {
        val mainFile = findMainFile(context.sourceFiles)
            ?: throw IllegalArgumentException("找不到包含 main 函数的源文件")

        val libraryFiles = DependencyCollector.collectLibraryFiles()

        val command = CompilationCommandBuilder().buildExecutableCommand(
            mainFile = mainFile,
            libraryFiles = libraryFiles,
            outputPath = context.outputPath
        )

        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(context.projectRoot.toFile())

        val process = processBuilder.start()

        val errors = mutableListOf<CjcDiagnostic>()
        val warnings = mutableListOf<CjcDiagnostic>()

        val stdoutReader = process.inputStream.bufferedReader()
        val stderrReader = process.errorStream.bufferedReader()

        val exitCode = coroutineScope {
            val stdoutJob = launch(Dispatchers.IO) {
                val stdoutParser = CjcOutputParser()
                try {
                    stdoutReader.useLines { lines ->
                        lines.forEach { line ->
                            stdoutParser.parseLine(line, false)?.let { event ->
                                when (event) {
                                    is CjcOutputEvent.CompilationProgress -> logger.debug { event.message }
                                    is CjcOutputEvent.CompilationError -> synchronized(errors) { errors.add(event.error) }
                                    is CjcOutputEvent.CompilationWarning -> synchronized(warnings) { warnings.add(event.warning) }
                                    else -> {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }

            val stderrJob = launch(Dispatchers.IO) {
                val stderrParser = CjcOutputParser()
                try {
                    stderrReader.useLines { lines ->
                        lines.forEach { line ->
                            stderrParser.parseLine(line, true)?.let { event ->
                                when (event) {
                                    is CjcOutputEvent.CompilationError -> {
                                        synchronized(errors) { errors.add(event.error) }
                                        logger.debug { "[COMPILER_ERROR] ${event.error.file}:${event.error.line}:${event.error.column}: ${event.error.message}" }
                                    }
                                    is CjcOutputEvent.CompilationWarning -> {
                                        synchronized(warnings) { warnings.add(event.warning) }
                                        logger.debug { "[COMPILER_WARNING] ${event.warning.file}:${event.warning.line}:${event.warning.column}: ${event.warning.message}" }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }

            val exitCode = process.waitFor()
            stdoutJob.join()
            stderrJob.join()
            exitCode
        }

        if (exitCode != 0) {
            linkingReport = LinkingReport(
                success = false,
                errors = errors,
                warnings = warnings,
                outputPath = null,
                stdoutLog = null,
                stderrLog = null,
                exception = RuntimeException("链接失败，退出码: $exitCode")
            )

            val errorMsg = if (errors.isNotEmpty()) {
                errors.joinToString("\n") { "${it.file}:${it.line}:${it.column}: ${it.message}" }
            } else {
                "未知链接错误"
            }
            throw RuntimeException("链接失败\n$errorMsg")
        }

        linkingReport = LinkingReport(
            success = true,
            errors = emptyList(),
            warnings = warnings,
            outputPath = context.outputPath,
            stdoutLog = null,
            stderrLog = null,
            exception = null
        )

        context
    }

    fun getReport(): LinkingReport? = linkingReport

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
    private var compilationReport: CompilationReport? = null

    context(context: CompilationContext)
    suspend fun compile(): Result<CompilationResult> {
        val result = pipeline.execute()

        val packageStage = (pipeline as? DefaultCompilationPipeline)
            ?.stages
            ?.find { it is PackageCompilationStage } as? PackageCompilationStage

        val linkingStage = (pipeline as? DefaultCompilationPipeline)
            ?.stages
            ?.find { it is LinkingStage } as? LinkingStage

        val reportBuilder = CompilationReportBuilder()

        packageStage?.getReport()?.packages?.forEach { pkgReport ->
            reportBuilder.addPackageReport(pkgReport)
        }

        linkingStage?.getReport()?.let { linkReport ->
            reportBuilder.setLinkingReport(linkReport)
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
    when {
        outputType == org.cangnova.kcjpm.config.OutputType.EXECUTABLE && isMainPackage -> "exe"
        outputType == org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY -> "dylib"
        else -> "staticlib"
    }

private fun getPackageOutputFileName(
    name: String, 
    outputType: org.cangnova.kcjpm.config.OutputType, 
    isMainPackage: Boolean
): String =
    when {
        outputType == org.cangnova.kcjpm.config.OutputType.EXECUTABLE && isMainPackage -> name
        outputType == org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY -> "lib$name.b.dll"
        else -> "lib$name.a"
    }