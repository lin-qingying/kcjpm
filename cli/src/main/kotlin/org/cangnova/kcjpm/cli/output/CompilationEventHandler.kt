package org.cangnova.kcjpm.cli.output

import org.cangnova.kcjpm.build.*
import java.nio.file.Path
import kotlin.math.roundToInt

class CompilationEventHandler(
    private val output: OutputAdapter,
    private val verbose: Boolean = false
) : CompilationEventListener {
    
    private var currentStage: String? = null
    private var currentStageIndex: Int = 0
    private var totalStages: Int = 0
    private var currentPackage: String? = null
    
    override fun onEvent(event: CompilationEvent) {
        when (event) {
            is PipelineStartedEvent -> handlePipelineStarted(event)
            is PipelineCompletedEvent -> handlePipelineCompleted(event)
            is StageStartedEvent -> handleStageStarted(event)
            is StageCompletedEvent -> handleStageCompleted(event)
            is ValidationEvent -> handleValidation(event)
            is DependencyResolutionEvent -> handleDependencyResolution(event)
            is PackageDiscoveryEvent -> handlePackageDiscovery(event)
            is PackageCompilationStartedEvent -> handlePackageCompilationStarted(event)
            is PackageCompilationCommandEvent -> handlePackageCompilationCommand(event)
            is CompilerOutputEvent -> handleCompilerOutput(event)
            is PackageCompilationCompletedEvent -> handlePackageCompilationCompleted(event)
            is IncrementalCacheEvent -> handleIncrementalCache(event)
            is ChangeDetectionEvent -> handleChangeDetection(event)
        }
    }
    
    private fun handlePipelineStarted(event: PipelineStartedEvent) {
        totalStages = event.totalStages
        if (verbose) {
            output.info("开始编译流水线 (${event.totalStages} 个阶段)")
            output.newline()
        }
    }
    
    private fun handlePipelineCompleted(event: PipelineCompletedEvent) {
        if (verbose) {
            output.newline()
            if (event.success) {
                output.success("编译流水线完成")
            } else {
                output.error("编译流水线失败")
            }
        }
    }
    
    private fun handleStageStarted(event: StageStartedEvent) {
        currentStage = event.stageName
        currentStageIndex = event.stageIndex
        totalStages = event.totalStages
        
        if (verbose) {
            val progress = if (totalStages > 0) {
                " [${event.stageIndex + 1}/${event.totalStages}]"
            } else ""
            
            output.startProgress("${event.stageName}$progress")
        }
    }
    
    private fun handleStageCompleted(event: StageCompletedEvent) {
        if (verbose) {
            val progress = if (totalStages > 0) {
                " [${event.stageIndex + 1}/${event.totalStages}]"
            } else ""
            
            output.completeProgress("${event.stageName}$progress")
        }
        currentStage = null
    }
    
    private fun handleValidation(event: ValidationEvent) {
        if (verbose) {
            val pathInfo = event.path?.let { " (${it.fileName})" } ?: ""
            output.debug("验证: ${event.message}$pathInfo")
        }
    }
    
    private fun handleDependencyResolution(event: DependencyResolutionEvent) {
        when {
            event.dependencyName != null && event.dependencyType != null -> {
                if (verbose) {
                    output.debug("依赖解析: ${event.dependencyName} (${event.dependencyType})")
                } else {
                    output.updateProgress("解析依赖 ${event.dependencyName}")
                }
            }
            verbose -> {
                output.debug("依赖解析: ${event.message}")
            }
        }
    }
    
    private fun handlePackageDiscovery(event: PackageDiscoveryEvent) {
        if (event.totalPackages > 0) {
            if (verbose) {
                // 使用output显示包发现信息，OutputAdapter会自动处理进度行清理
                output.info("发现 ${event.totalPackages} 个包:")
                event.packages.forEach { pkg ->
                    val fileCount = if (pkg.sourceFileCount > 0) " (${pkg.sourceFileCount} 个源文件)" else ""
                    output.info("  - ${pkg.name}$fileCount")
                }
                output.newline()
            } else {
                // 非详细模式下只显示总数，通过更新进度显示
                output.updateProgress("发现 ${event.totalPackages} 个包")
            }
        }
    }
    
    private fun handlePackageCompilationStarted(event: PackageCompilationStartedEvent) {
        currentPackage = event.packageName
        if (verbose) {
            output.startProgress("编译包 ${event.packageName}")
        }
    }
    
    private fun handlePackageCompilationCommand(event: PackageCompilationCommandEvent) {
        if (verbose) {
            output.debug("执行命令: ${event.command.joinToString(" ")}")
        }
    }
    
    private fun handleCompilerOutput(event: CompilerOutputEvent) {
        if (verbose) {
            val prefix = if (event.isStderr) "[STDERR]" else "[STDOUT]"
            output.debug("$prefix ${event.packageName}: ${event.line}")
        }
    }
    
    private fun handlePackageCompilationCompleted(event: PackageCompilationCompletedEvent) {
        currentPackage = null
        
        if (verbose) {
            if (event.success) {
                val outputInfo = event.outputPath?.let { " -> ${it.fileName}" } ?: ""
                output.completeProgress("编译包 ${event.packageName}$outputInfo")
                
                if (event.warnings.isNotEmpty()) {
                    output.warning("${event.packageName}: ${event.warnings.size} 个警告")
                    event.warnings.take(3).forEach { warning ->
                        output.warning("  ${warning.file}:${warning.line}:${warning.column}: ${warning.message}")
                    }
                    if (event.warnings.size > 3) {
                        output.warning("  ... 还有 ${event.warnings.size - 3} 个警告")
                    }
                }
            } else {
                output.error("编译包 ${event.packageName} 失败")
                
                if (event.errors.isNotEmpty()) {
                    output.error("错误详情:")
                    event.errors.take(5).forEach { error ->
                        output.error("  ${error.file}:${error.line}:${error.column}: ${error.message}")
                    }
                    if (event.errors.size > 5) {
                        output.error("  ... 还有 ${event.errors.size - 5} 个错误")
                    }
                }
            }
        } else {
            // 非详细模式下，只有编译失败时才显示错误信息
            if (!event.success && event.errors.isNotEmpty()) {
                output.error("编译包 ${event.packageName} 失败")
                output.error("错误详情:")
                event.errors.take(5).forEach { error ->
                    output.error("  ${error.file}:${error.line}:${error.column}: ${error.message}")
                }
                if (event.errors.size > 5) {
                    output.error("  ... 还有 ${event.errors.size - 5} 个错误")
                }
            }
        }
    }
    
    private fun handleIncrementalCache(event: IncrementalCacheEvent) {
        if (verbose) {
            val cacheInfo = event.cacheDir?.let { " (${it})" } ?: ""
            output.debug("增量缓存: ${event.message}$cacheInfo")
        }
    }
    
    private fun handleChangeDetection(event: ChangeDetectionEvent) {
        if (verbose) {
            val details = event.details?.let { " - $it" } ?: ""
            output.debug("变更检测: ${event.packageName} ${event.changeType}$details")
        }
    }
}