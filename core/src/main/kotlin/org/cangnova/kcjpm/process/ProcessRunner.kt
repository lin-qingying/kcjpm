package org.cangnova.kcjpm.process

import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ProcessResult(
    val exitCode: Int,
    val output: String
)

/**
 * 外部进程执行器。
 *
 * Core 中所有短生命周期工具进程都应通过这里执行，统一处理输出读取、超时和进程清理。
 */
object ProcessRunner {
    fun run(
        command: List<String>,
        workingDirectory: Path? = null,
        timeout: Duration = Duration.ofSeconds(60)
    ): Result<ProcessResult> = runCatching {
        val processBuilder = ProcessBuilder(command)
            .redirectErrorStream(true)

        workingDirectory?.let { processBuilder.directory(it.toFile()) }

        val process = processBuilder.start()
        val outputReader = Executors.newSingleThreadExecutor()
        val outputFuture = outputReader.submit<String> {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        try {
            val finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
                throw RuntimeException("外部进程执行超时: ${command.joinToString(" ")}")
            }

            val output = outputFuture.get(5, TimeUnit.SECONDS)
            ProcessResult(process.exitValue(), output)
        } finally {
            outputReader.shutdownNow()
        }
    }
}
