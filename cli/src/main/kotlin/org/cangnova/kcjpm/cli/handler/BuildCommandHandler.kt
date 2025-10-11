package org.cangnova.kcjpm.cli.handler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cangnova.kcjpm.build.*
import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.CompilationEventHandler
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.ConfigToContextConverter
import org.cangnova.kcjpm.dependency.DefaultDependencyManager
import org.cangnova.kcjpm.dependency.DependencyManagerWithLock
import org.cangnova.kcjpm.workspace.DefaultWorkspaceManager
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

class BuildCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Build) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        output.info(Messages.get("build.starting", projectPath))
        output.newline()
        
        val workspaceManager = DefaultWorkspaceManager()
        
        return if (workspaceManager.isWorkspaceRoot(projectPath)) {
            buildWorkspace(projectPath, command, options)
        } else {
            buildSingleProject(projectPath, command, options)
        }
    }
    
    private suspend fun buildWorkspace(
        projectPath: Path,
        command: Command.Build,
        options: GlobalOptions
    ): Int {
        return try {
            output.startProgress(Messages.get("workspace.loading"))
            val workspaceManager = DefaultWorkspaceManager()
            val workspace = workspaceManager.loadWorkspace(projectPath).getOrThrow()
            output.completeProgress(Messages.get("workspace.loaded"))
            
            output.info(Messages.get("workspace.members", workspace.members.joinToString(", ") { it.name }))
            output.newline()
            
            val coordinator = org.cangnova.kcjpm.workspace.WorkspaceCompilationCoordinator(workspace)
            
            val targetPlatform = command.target?.let { 
                CompilationTarget.valueOf(it.uppercase())
            }
            
            val parallel = workspace.rootConfig.build?.parallel ?: true
            
            output.startProgress(Messages.get("workspace.building"))
            val result = if (workspace.rootConfig.workspace?.defaultMembers.isNullOrEmpty()) {
                coordinator.buildAll(parallel, targetPlatform)
            } else {
                coordinator.buildDefaultMembers(parallel, targetPlatform)
            }
            
            result.fold(
                onSuccess = { buildResults ->
                    output.completeProgress(Messages.get("workspace.buildComplete"))
                    output.newline()
                    
                    buildResults.forEach { (memberName, memberResult) ->
                        when (memberResult) {
                            is org.cangnova.kcjpm.workspace.MemberBuildResult.Success -> {
                                output.success(Messages.get("workspace.memberSuccess", memberName))
                                output.info(Messages.get("build.output", memberResult.outputPath))
                            }
                            is org.cangnova.kcjpm.workspace.MemberBuildResult.Failure -> {
                                output.error(Messages.get("workspace.memberFailed", memberName, memberResult.error.message ?: "Unknown error"))
                            }
                            is org.cangnova.kcjpm.workspace.MemberBuildResult.Skipped -> {
                                output.info(Messages.get("workspace.memberSkipped", memberName, memberResult.reason))
                            }
                        }
                    }
                    
                    handleSuccess(Messages.get("workspace.buildComplete"))
                },
                onFailure = { error ->
                    handleError(Messages.get("workspace.buildFailed", error.message ?: "Unknown error"), error)
                }
            )
        } catch (e: Exception) {
            handleError(Messages.get("workspace.buildFailed", e.message ?: "Unknown error"), e)
        }
    }
    
    private suspend fun buildSingleProject(
        projectPath: Path,
        command: Command.Build,
        options: GlobalOptions
    ): Int {
        return try {
            val eventBus = CompilationEventBus()
            val eventHandler = CompilationEventHandler(output, options.verbose)
            eventBus.addListener(eventHandler)
            
            output.startProgress(Messages.get("build.loadingConfig"))
            val config = ConfigLoader.loadFromProjectRoot(projectPath).getOrThrow()
            output.completeProgress(Messages.get("build.configLoaded"))
            
            output.info(Messages.get("build.project", config.`package`?.name ?: "未命名"))
            output.info(Messages.get("build.version", config.`package`?.version ?: "未指定"))
            output.newline()
            
            if (options.verbose) {
                output.startProgress(Messages.get("build.resolvingDeps"))
            }
            val cacheDir = Path(System.getProperty("user.home")).resolve(".kcjpm").resolve("cache")
            val dependencyManager = DependencyManagerWithLock(
                DefaultDependencyManager(cacheDir)
            )
            
            val (dependencies, lockFile) = dependencyManager.installWithLock(config, projectPath).getOrThrow()
            if (options.verbose) {
                output.completeProgress(Messages.get("build.depsResolved", dependencies.size))
                
                if (dependencies.isNotEmpty()) {
                    dependencies.forEach { dep ->
                        output.info("  - ${dep.name} ${dep.version ?: ""}")
                    }
                    output.newline()
                }
                
                // 显示锁文件信息
                output.info("生成锁文件: kcjpm.lock (${lockFile.packages.size} 个包)")
            }
            
            val profileName = when {
                command.profile != null -> command.profile
                command.release -> "release"
                else -> "debug"
            }
            
            val context = withContext(Dispatchers.IO) {
                val targetPlatform = command.target?.let { 
                    CompilationTarget.valueOf(it.uppercase())
                }
                ConfigToContextConverter.convert(config, projectPath, targetPlatform, profileName, eventBus).getOrThrow()
            }
            
            val compilationManager = CompilationManager()
            val result = with(context) {
                compilationManager.compile()
            }
            
            result.fold(
                onSuccess = { compResult ->
                    when (compResult) {
                        is CompilationResult.Success -> {
                            output.success(Messages.get("build.success"))
                            output.info(Messages.get("build.output", compResult.outputPath))
                            
                            val report = compilationManager.getReport()
                            if (report != null && options.verbose) {
                                printCompilationReport(report)
                            }
                            
                            0
                        }
                        is CompilationResult.Failure -> {
                            handleError(Messages.get("build.compileFailed", compResult.errors.firstOrNull()?.message ?: "未知错误"))
                        }
                    }
                },
                onFailure = { error ->
                    handleError(Messages.get("build.compileFailed", error.message ?: "Unknown error"), error)
                }
            )
        } catch (e: Exception) {
            handleError(Messages.get("build.failed", e.message ?: "Unknown error"), e)
        }
    }
    
    private fun printCompilationReport(report: CompilationReport) {
        output.newline()
        output.info(Messages.get("build.report"))
        
        report.packages.forEach { pkgReport ->
            if (pkgReport.success) {
                output.success("  包 ${pkgReport.packageName}: 成功")
            } else {
                output.error("  包 ${pkgReport.packageName}: 失败")
                
                if (pkgReport.errors.isNotEmpty()) {
                    output.error("    错误:")
                    pkgReport.errors.take(5).forEach { error ->
                        output.error("      ${error.file}:${error.line}:${error.column}: ${error.message}")
                    }
                    if (pkgReport.errors.size > 5) {
                        output.error("      ... 还有 ${pkgReport.errors.size - 5} 个错误")
                    }
                }
            }
            
            if (pkgReport.warnings.isNotEmpty()) {
                output.warning("    警告: ${pkgReport.warnings.size} 个")
            }
        }
        
        report.linking?.let { linkReport ->
            if (linkReport.success) {
                output.success("  链接: 成功")
            } else {
                output.error("  链接: 失败")
                linkReport.errors.forEach { error ->
                    output.error("    ${error.file}:${error.line}:${error.column}: ${error.message}")
                }
            }
        }
    }
}
