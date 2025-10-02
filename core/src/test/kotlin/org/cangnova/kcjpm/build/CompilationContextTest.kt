package org.cangnova.kcjpm.build

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path

class CompilationContextTest : BaseTest() {
    init {
        test("应该使用构建器创建上下文") {
            val project = createTestProject()
            val sourceFile = project.createSourceFile("main.cj", "// main")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .buildConfig(BuildConfig(CompilationTarget.LINUX_X64))
                .addSourceFile(sourceFile)
                .outputPath(project.root.resolve("target"))
                .build()
            
            context.projectRoot shouldBe project.root
            context.buildConfig.target shouldBe CompilationTarget.LINUX_X64
            context.sourceFiles shouldHaveSize 1
            context.dependencies shouldHaveSize 0
        }
        
        test("当项目根目录不存在时应该验证失败") {
            val nonExistent = Path.of("/non/existent/path")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(nonExistent)
                .addSourceFile(Path.of("main.cj"))
                .build()
            
            val result = context.validate()
            result.isFailure shouldBe true
        }
        
        test("当没有源文件时应该验证失败") {
            val project = createTestProject()
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .build()
            
            val result = context.validate()
            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldContain "No source files"
        }
        
        test("当源文件不存在时应该验证失败") {
            val project = createTestProject()
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .addSourceFile(project.root.resolve("missing.cj"))
                .build()
            
            val result = context.validate()
            result.isFailure shouldBe true
        }
        
        test("应该验证路径依赖") {
            val project = createTestProject()
            val sourceFile = project.createSourceFile("main.cj")
            val depDir = project.createDependency("lib")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .addSourceFile(sourceFile)
                .addDependency(Dependency.PathDependency("lib", "1.0.0", depDir))
                .build()
            
            val result = context.validate()
            result.isSuccess shouldBe true
        }
        
        test("不存在的路径依赖应该验证失败") {
            val project = createTestProject()
            val sourceFile = project.createSourceFile("main.cj")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .addSourceFile(sourceFile)
                .addDependency(Dependency.PathDependency("lib", null, Path.of("/non/existent")))
                .build()
            
            val result = context.validate()
            result.isFailure shouldBe true
        }
        
        test("应该验证 Git 依赖") {
            val project = createTestProject()
            val sourceFile = project.createSourceFile("main.cj")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .addSourceFile(sourceFile)
                .addDependency(
                    Dependency.GitDependency(
                        "http-client",
                        "1.0.0",
                        "https://github.com/example/repo",
                        Dependency.GitReference.Tag("v1.0.0")
                    )
                )
                .build()
            
            val result = context.validate()
            result.isSuccess shouldBe true
        }
        
        test("应该验证仓库依赖") {
            val project = createTestProject()
            val sourceFile = project.createSourceFile("main.cj")
            
            val context = DefaultCompilationContext.builder()
                .projectRoot(project.root)
                .addSourceFile(sourceFile)
                .addDependency(
                    Dependency.RegistryDependency(
                        "std-http",
                        "1.2.0",
                        "https://repo.cangjie-lang.cn"
                    )
                )
                .build()
            
            val result = context.validate()
            result.isSuccess shouldBe true
        }
        
        
        
        
        
        
        test("应该检测当前平台") {
            val target = CompilationTarget.current()
            
            target shouldBe when {
                System.getProperty("os.name").lowercase().contains("win") -> CompilationTarget.WINDOWS_X64
                System.getProperty("os.name").lowercase().contains("mac") -> {
                    if (System.getProperty("os.arch").lowercase().contains("aarch64")) 
                        CompilationTarget.MACOS_ARM64 
                    else 
                        CompilationTarget.MACOS_X64
                }
                else -> {
                    if (System.getProperty("os.arch").lowercase().contains("aarch64"))
                        CompilationTarget.LINUX_ARM64
                    else
                        CompilationTarget.LINUX_X64
                }
            }
        }
    }
}