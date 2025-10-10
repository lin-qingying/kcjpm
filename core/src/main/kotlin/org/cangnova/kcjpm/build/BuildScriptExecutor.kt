package org.cangnova.kcjpm.build

import java.nio.file.Path

interface BuildScriptExecutor {
    fun execute(
        executablePath: Path,
        context: BuildScriptContext
    ): Result<BuildScriptResult>
}

class DefaultBuildScriptExecutor : BuildScriptExecutor {
    
    override fun execute(
        executablePath: Path,
        context: BuildScriptContext
    ): Result<BuildScriptResult> = runCatching {
        val process = ProcessBuilder(executablePath.toString())
            .directory(context.projectRoot.toFile())
            .apply {
                environment().putAll(context.toEnvironmentVariables())
            }
            .redirectErrorStream(false)
            .start()
        
        val stdout = process.inputStream.bufferedReader().readLines()
        val stderr = process.errorStream.bufferedReader().readLines()
        
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            throw BuildScriptExecutionException(
                "构建脚本执行失败 (退出码: $exitCode):\n${stderr.joinToString("\n")}"
            )
        }
        
        parseOutput(stdout, stderr, context.projectRoot)
    }
    
    private fun parseOutput(
        stdout: List<String>,
        stderr: List<String>,
        projectRoot: Path
    ): BuildScriptResult {
        val linkLibraries = mutableListOf<String>()
        val includeDirs = mutableListOf<Path>()
        val rerunIfChanged = mutableListOf<Path>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val customFlags = mutableMapOf<String, String>()
        
        for (line in stdout) {
            val instruction = BuildScriptInstruction.parse(line, projectRoot) ?: continue
            
            when (instruction) {
                is BuildScriptInstruction.LinkLibrary -> linkLibraries.add(instruction.library)
                is BuildScriptInstruction.IncludeDir -> includeDirs.add(instruction.path)
                is BuildScriptInstruction.RerunIfChanged -> rerunIfChanged.add(instruction.path)
                is BuildScriptInstruction.Warning -> warnings.add(instruction.message)
                is BuildScriptInstruction.Error -> errors.add(instruction.message)
                is BuildScriptInstruction.CustomFlag -> customFlags[instruction.key] = instruction.value
            }
        }
        
        stderr.forEach { line ->
            if (line.isNotBlank()) {
                warnings.add(line)
            }
        }
        
        return BuildScriptResult(
            linkLibraries = linkLibraries,
            includeDirs = includeDirs,
            rerunIfChanged = rerunIfChanged,
            warnings = warnings,
            errors = errors,
            customFlags = customFlags
        )
    }
}

class BuildScriptExecutionException(message: String) : Exception(message)