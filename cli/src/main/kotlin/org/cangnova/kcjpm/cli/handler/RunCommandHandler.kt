package org.cangnova.kcjpm.cli.handler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cangnova.kcjpm.build.CompilationTarget
import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.OutputType
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.*

class RunCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Run) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        return try {
            output.startProgress(Messages.get("run.loadingConfig"))
            val config = ConfigLoader.loadFromProjectRoot(projectPath).getOrThrow()
            output.completeProgress(Messages.get("run.configLoaded"))
            
            // 检查是否为可执行项目
            if (config.`package`?.outputType != OutputType.EXECUTABLE) {
                return handleError(Messages.get("run.notExecutable"))
            }
            
            val projectName = config.`package`?.name ?: return handleError(Messages.get("run.notFound"))
            val outputDir = config.build?.outputDir ?: "target"
            
            // 默认使用 release 模式运行，除非没有 release 构建
            val debugOutputDir = projectPath.resolve(outputDir).resolve("debug")
            val releaseOutputDir = projectPath.resolve(outputDir).resolve("release")
            
            // 优先选择 release，如果不存在则选择 debug
            val (profile, buildOutputDir) = when {
                releaseOutputDir.exists() -> "release" to releaseOutputDir
                debugOutputDir.exists() -> "debug" to debugOutputDir
                else -> "release" to releaseOutputDir  // 默认尝试 release，如果需要会触发构建
            }
            
            // 根据平台确定可执行文件名
            val executableName = when (CompilationTarget.current()) {
                CompilationTarget.WINDOWS_X64 -> if (projectName.endsWith(".exe")) projectName else "$projectName.exe"
                else -> projectName
            }
            
            val executablePath = buildOutputDir.resolve(executableName)
            
            // 检查是否需要重新构建
            val needsRebuild = !executablePath.exists() || isSourceNewerThanExecutable(projectPath, executablePath, config)
            
            if (needsRebuild) {
                if (executablePath.exists()) {
                    output.info("检测到源文件变更，重新构建...")
                } else {
                    output.warning(Messages.get("run.executableNotFound"))
                    output.info("尝试构建项目...")
                }
                output.newline()
                
                // 根据选择的 profile 决定构建参数
                val buildCommand = if (profile == "release") {
                    Command.Build(path = command.path, release = true)
                } else {
                    Command.Build(path = command.path, release = false)
                }
                
                val buildResult = BuildCommandHandler(output).handle(buildCommand, options)
                
                if (buildResult != 0) {
                    return handleError(Messages.get("run.buildFailed"))
                }
                
                // 构建后再次检查
                if (!executablePath.exists()) {
                    // 如果选择的 profile 构建后仍不存在，尝试另一个 profile
                    val alternativeProfile = if (profile == "release") "debug" else "release"
                    val alternativeDir = projectPath.resolve(outputDir).resolve(alternativeProfile)
                    val alternativeExecutable = alternativeDir.resolve(executableName)
                    
                    if (alternativeExecutable.exists()) {
                        output.info("使用 $alternativeProfile 构建版本")
                        return runExecutable(alternativeExecutable, command, projectPath, options)
                    }
                    
                    return handleError("构建完成但找不到可执行文件: $executablePath")
                }
                
                output.newline()
            }
            
            return runExecutable(executablePath, command, projectPath, options)
        } catch (e: Exception) {
            handleError(Messages.get("run.failed", e.message ?: "Unknown error"), e)
        }
    }
    
    private suspend fun runExecutable(
        executablePath: Path,
        command: Command.Run,
        projectPath: Path,
        options: GlobalOptions
    ): Int {
        output.info(Messages.get("run.running", executablePath))
        output.newline()
        
        val processBuilder = ProcessBuilder(
            listOf(executablePath.toString()) + command.args
        )
        processBuilder.directory(projectPath.toFile())
        processBuilder.inheritIO()
        
        val exitCode = withContext(Dispatchers.IO) {
            val process = processBuilder.start()
            process.waitFor()
        }
        
        output.newline()
        if (exitCode == 0) {
            output.success(Messages.get("run.complete"))
        } else {
            output.warning(Messages.get("run.exitCode", exitCode))
        }
        
        return exitCode
    }
    
    /**
     * 检查源文件是否比可执行文件更新
     */
    private fun isSourceNewerThanExecutable(
        projectPath: Path,
        executablePath: Path,
        config: org.cangnova.kcjpm.config.CjpmConfig
    ): Boolean {
        if (!executablePath.exists()) {
            return true
        }
        
        try {
            val executableTime = executablePath.getLastModifiedTime()
            
            // 检查配置文件
            val configFile = projectPath.resolve("cjpm.toml")
            if (configFile.exists() && configFile.getLastModifiedTime() > executableTime) {
                return true
            }
            
            // 检查源文件目录
            val sourceDir = config.build?.sourceDir ?: "src"
            val sourcePath = projectPath.resolve(sourceDir)
            
            if (!sourcePath.exists()) {
                return false
            }
            
            // 递归检查所有 .cj 源文件
            return sourcePath.walk()
                .filter { it.isRegularFile() && it.extension == "cj" }
                .any { sourceFile ->
                    sourceFile.getLastModifiedTime() > executableTime
                }
        } catch (e: Exception) {
            // 如果检查失败，为了安全起见，假设需要重新构建
            return true
        }
    }
}