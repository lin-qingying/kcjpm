package org.cangnova.kcjpm.platform

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class JvmProcessExecutor : ProcessExecutor {
    override fun execute(
        command: List<String>,
        workingDirectory: KPath?,
        environment: Map<String, String>,
        redirectOutput: Boolean
    ): Result<ProcessResult> = runCatching {
        val processBuilder = ProcessBuilder(command)
        
        workingDirectory?.let {
            processBuilder.directory(java.io.File(it.path))
        }
        
        if (environment.isNotEmpty()) {
            processBuilder.environment().putAll(environment)
        }
        
        if (redirectOutput) {
            processBuilder.redirectErrorStream(false)
        }
        
        val process = processBuilder.start()
        
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        
        val exitCode = process.waitFor()
        
        ProcessResult(exitCode, stdout, stderr)
    }
    
    override fun executeAsync(
        command: List<String>,
        workingDirectory: KPath?,
        environment: Map<String, String>,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): Result<ProcessHandle> = runCatching {
        val processBuilder = ProcessBuilder(command)
        
        workingDirectory?.let {
            processBuilder.directory(java.io.File(it.path))
        }
        
        if (environment.isNotEmpty()) {
            processBuilder.environment().putAll(environment)
        }
        
        val process = processBuilder.start()
        
        Thread {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    onOutput(line)
                }
            }
        }.start()
        
        Thread {
            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    onError(line)
                }
            }
        }.start()
        
        JvmProcessHandle(process)
    }
}

class JvmProcessHandle(private val process: Process) : ProcessHandle {
    override fun waitFor(): Result<Int> = runCatching {
        process.waitFor()
    }
    
    override fun isAlive(): Boolean = process.isAlive
    
    override fun destroy() {
        process.destroy()
    }
}

actual fun getProcessExecutor(): ProcessExecutor = JvmProcessExecutor()