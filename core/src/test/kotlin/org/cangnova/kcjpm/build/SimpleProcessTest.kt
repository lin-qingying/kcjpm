package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.Path

class SimpleProcessTest : FunSpec({
    
    test("测试简单的进程输出读取") {
        val command = listOf(
            "C:\\Users\\lin17\\sdk\\cangjie-sdk-windows-x64-1.0.1\\cangjie\\bin\\cjc.exe",
            "--help"
        )
        
        val processBuilder = ProcessBuilder(command)
        val process = processBuilder.start()
        
        // 使用最简单的方式读取
        val stderr = process.errorStream.bufferedReader().readText()
        val stdout = process.inputStream.bufferedReader().readText()
        
        val exitCode = process.waitFor()
        
        println("退出码: $exitCode")
        println("STDOUT 长度: ${stdout.length}")
        println("STDERR 长度: ${stderr.length}")
        
        if (stdout.isNotEmpty()) {
            println("STDOUT 前100字符: ${stdout.take(100)}")
        }
        if (stderr.isNotEmpty()) {
            println("STDERR 前100字符: ${stderr.take(100)}")
        }
        
        // 应该有帮助信息输出
        (stdout.length + stderr.length > 0) shouldBe true
    }
    
    test("测试使用 readText 读取警告") {
        // 先创建目录
        val targetDir = Path("D:\\code\\cangjie\\bson\\target\\test_simple")
        val libsDir = targetDir.resolve("libs")
        libsDir.toFile().mkdirs()
        
        val command = listOf(
            "C:\\Users\\lin17\\sdk\\cangjie-sdk-windows-x64-1.0.1\\cangjie\\bin\\cjc.exe",
            "-j10",
            "--import-path=${targetDir}",
            "--no-sub-pkg",
            "-p",
            "D:\\code\\cangjie\\bson\\src\\a",
            "--output-type=staticlib",
            "--output-dir=${libsDir}",
            "-o=libbson.a.a",
            "-O2",
            "--target",
            "x86_64-w64-mingw32"
        )
        
        println("执行命令: ${command.joinToString(" ")}")
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(java.io.File("D:\\code\\cangjie\\bson\\src\\a"))
        val process = processBuilder.start()
        
        // 使用 readText 一次性读取所有内容
        val stderr = process.errorStream.bufferedReader().readText()
        val stdout = process.inputStream.bufferedReader().readText()
        
        val exitCode = process.waitFor()
        
        println("退出码: $exitCode")
        println("STDOUT 内容:")
        println(stdout)
        println("STDERR 内容:")
        println(stderr)
        
        // 应该包含警告
        stderr.contains("warning") shouldBe true
        stderr.contains("unused function") shouldBe true
    }
})