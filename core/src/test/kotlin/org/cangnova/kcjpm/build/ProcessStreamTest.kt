package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.nio.file.Path

class ProcessStreamTest : FunSpec({

    test("测试进程流式读取") {
        val process = ProcessBuilder(streamTestJavaCommand(), "-version").start()
        val stdoutLines = mutableListOf<String>()
        val stderrLines = mutableListOf<String>()

        val exitCode = coroutineScope {
            val stdout = async(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    stdoutLines.addAll(lines)
                }
            }
            val stderr = async(Dispatchers.IO) {
                process.errorStream.bufferedReader().useLines { lines ->
                    stderrLines.addAll(lines)
                }
            }

            val code = process.waitFor()
            stdout.await()
            stderr.await()
            code
        }

        exitCode shouldBe 0
        (stdoutLines + stderrLines).any { it.contains("version", ignoreCase = true) } shouldBe true
    }
})

private fun streamTestJavaCommand(): String {
    val executable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "java.exe"
    } else {
        "java"
    }
    return Path.of(System.getProperty("java.home"), "bin", executable).toString()
}
