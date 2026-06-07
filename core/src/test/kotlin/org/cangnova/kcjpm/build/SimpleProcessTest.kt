package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class SimpleProcessTest : FunSpec({

    test("测试简单的进程输出读取") {
        val process = ProcessBuilder(javaCommand(), "-version").start()

        val stderr = process.errorStream.bufferedReader().readText()
        val stdout = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(30, TimeUnit.SECONDS)

        completed shouldBe true
        process.exitValue() shouldBe 0
        (stdout + stderr).contains("version", ignoreCase = true) shouldBe true
    }

    test("测试使用 readText 读取进程输出") {
        val process = ProcessBuilder(javaCommand(), "-XshowSettings:properties", "-version").start()

        val stderr = process.errorStream.bufferedReader().readText()
        val stdout = process.inputStream.bufferedReader().readText()
        val completed = process.waitFor(30, TimeUnit.SECONDS)

        completed shouldBe true
        process.exitValue() shouldBe 0
        (stdout + stderr).contains("java.home", ignoreCase = true) shouldBe true
    }
})

private fun javaCommand(): String {
    val executable = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
        "java.exe"
    } else {
        "java"
    }
    return Path.of(System.getProperty("java.home"), "bin", executable).toString()
}
