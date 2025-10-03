package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.*

class ProcessStreamTest : FunSpec({
    
    test("测试进程流式读取") {
        val command = listOf(
            "C:\\Users\\lin17\\sdk\\cangjie-sdk-windows-x64-1.0.1\\cangjie\\bin\\cjc.exe",
            "-j10",
            "--import-path=D:\\code\\cangjie\\bson\\target\\release",
            "--no-sub-pkg",
            "-p",
            "D:\\code\\cangjie\\bson\\src\\a",
            "--output-type=staticlib"
        )
        
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(java.io.File("D:\\code\\cangjie\\bson\\src\\a"))
        
        val env = processBuilder.environment()
        env["TERM"] = "xterm-256color"
        env["FORCE_COLOR"] = "1"
        
        println("执行命令: ${command.joinToString(" ")}")
        val process = processBuilder.start()
        
        val stdoutLines = mutableListOf<String>()
        val stderrLines = mutableListOf<String>()
        
        val exitCode = coroutineScope {
            val stdoutJob = launch(Dispatchers.IO) {
                try {
                    val reader = process.inputStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            println("[STDOUT] $it")
                            stdoutLines.add(it)
                        }
                    }
                    println("stdout 读取完成，共 ${stdoutLines.size} 行")
                } catch (e: Exception) {
                    println("stdout 读取异常: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            val stderrJob = launch(Dispatchers.IO) {
                try {
                    val reader = process.errorStream.bufferedReader()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            println("[STDERR] $it")
                            stderrLines.add(it)
                        }
                    }
                    println("stderr 读取完成，共 ${stderrLines.size} 行")
                } catch (e: Exception) {
                    println("stderr 读取异常: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            val exitCode = process.waitFor()
            println("进程退出，退出码: $exitCode")
            stdoutJob.join()
            stderrJob.join()
            exitCode
        }
        
        println("=== 总结 ===")
        println("退出码: $exitCode")
        println("stdout 行数: ${stdoutLines.size}")
        println("stderr 行数: ${stderrLines.size}")
        
        if (stderrLines.isNotEmpty()) {
            println("\n=== STDERR 内容 ===")
            stderrLines.forEach { println(it) }
        }
    }
})