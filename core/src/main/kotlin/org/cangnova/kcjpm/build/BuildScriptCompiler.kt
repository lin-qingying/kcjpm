package org.cangnova.kcjpm.build

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

interface BuildScriptCompiler {
    fun detectBuildScript(projectRoot: Path): Path?
    
    fun compile(
        buildScriptPath: Path,
        outputDir: Path,
        context: BuildScriptContext
    ): Result<Path>
}

class DefaultBuildScriptCompiler(
    private val compilerExecutable: String = "cjc"
) : BuildScriptCompiler {
    
    override fun detectBuildScript(projectRoot: Path): Path? {
        val buildScript = projectRoot.resolve("build.cj")
        return if (buildScript.exists() && buildScript.isRegularFile()) {
            buildScript
        } else {
            null
        }
    }
    
    override fun compile(
        buildScriptPath: Path,
        outputDir: Path,
        context: BuildScriptContext
    ): Result<Path> = runCatching {
        Files.createDirectories(outputDir)
        
        val outputName = when {
            System.getProperty("os.name").lowercase().contains("windows") -> "build.exe"
            else -> "build"
        }
        val outputPath = outputDir.resolve(outputName)
        
        val command = buildList {
            add(compilerExecutable)
            add(buildScriptPath.toString())
            add("-o")
            add(outputPath.toString())
            add("--output-type")
            add("executable")
        }
        
        val process = ProcessBuilder(command)
            .directory(context.projectRoot.toFile())
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw BuildScriptCompilationException(
                "构建脚本编译失败 (退出码: $exitCode):\n$output"
            )
        }
        
        outputPath
    }
}

class BuildScriptCompilationException(message: String) : Exception(message)