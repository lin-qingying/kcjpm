package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path

class KotlinStyleCompilationTest : BaseTest() {
    
    init {
        test("使用DSL构建编译上下文") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", """
                package main
                main() { println("Hello") }
            """.trimIndent())
            
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig {
                    target(CompilationTarget.current())
                    optimizationLevel(OptimizationLevel.DEBUG)
                    debugInfo(true)
                    parallel(true, 4)
                    incremental(false)
                    verbose(false)
                }
                sourceFile(sourceFile)
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            context.projectRoot shouldBe testProject.root
            context.buildConfig.target shouldBe CompilationTarget.current()
            context.buildConfig.optimizationLevel shouldBe OptimizationLevel.DEBUG
            context.buildConfig.debugInfo shouldBe true
            context.buildConfig.parallel shouldBe true
            context.buildConfig.maxParallelSize shouldBe 4
            context.sourceFiles shouldHaveSize 1
        }
        
        test("使用上下文接收器构建命令") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig {
                    target(CompilationTarget.LINUX_X64)
                    optimizationLevel(OptimizationLevel.RELEASE)
                }
                sourceFile(sourceFile)
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            with(context) {
                val command = buildCompilationCommand()
                
                command shouldContain "cjc"
                command shouldContain "--output-type=exe"
                command shouldContain "-O2"
                command shouldContain "--target"
                command shouldContain "x86_64-unknown-linux-gnu"
            }
        }
        
        test("上下文接收器扩展属性") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val debugContext = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig {
                    target(CompilationTarget.current())
                    optimizationLevel(OptimizationLevel.DEBUG)
                }
                sourceFile(sourceFile)
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            val releaseContext = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig {
                    target(CompilationTarget.current())
                    optimizationLevel(OptimizationLevel.RELEASE)
                    parallel(true)
                }
                sourceFile(sourceFile)
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            with(debugContext) {
                isDebugBuild shouldBe true
                sourceFileCount shouldBe 1
            }
            
            with(releaseContext) {
                isDebugBuild shouldBe false
                isParallelEnabled shouldBe true
                sourceFileCount shouldBe 1
            }
        }
        
        test("使用DSL构建编译流水线") {
            val pipeline = buildCompilationPipeline {
                stage("validation") { context ->
                    Result.success(context)
                }
                stage("compilation") { context ->
                    Result.success(context)
                }
                stage("linking") { context ->
                    Result.success(context)
                }
                
                onError { error ->
                    println("编译错误: ${error.message}")
                }
                
                onProgress { stageName, current, total ->
                    println("执行阶段: $stageName ($current/$total)")
                }
            }
            
            pipeline.stages shouldHaveSize 3
            pipeline.stages[0].name shouldBe "validation"
            pipeline.stages[1].name shouldBe "compilation"
            pipeline.stages[2].name shouldBe "linking"
        }
        
        test("编译流水线进度流") {
            val pipeline = buildCompilationPipeline {
                stage("stage1") { Result.success(it) }
                stage("stage2") { Result.success(it) }
                stage("stage3") { Result.success(it) }
            }
            
            runBlocking {
                val progress = pipeline.progressFlow().toList()
                
                progress shouldHaveSize 3
                progress[0].stageName shouldBe "stage1"
                progress[0].current shouldBe 1
                progress[0].total shouldBe 3
                progress[0].percentage.shouldBeWithinPercentageOf(33.33, 0.1)
                
                progress[2].stageName shouldBe "stage3"
                progress[2].current shouldBe 3
                progress[2].total shouldBe 3
                progress[2].percentage shouldBe 100.0
            }
        }
        
        test("并行执行编译阶段") {
            val testProject = createTestProject()
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            val stage1 = object : CompilationStage {
                override val name = "stage1"
                override suspend fun execute(context: CompilationContext) = Result.success(context)
            }
            
            val stage2 = object : CompilationStage {
                override val name = "stage2"
                override suspend fun execute(context: CompilationContext) = Result.success(context)
            }
            
            runBlocking {
                val results = executeStagesInParallel(context, stage1, stage2)
                
                results shouldHaveSize 2
                results.all { it.isSuccess } shouldBe true
            }
        }
        
        test("带重试机制的编译阶段") {
            val testProject = createTestProject()
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            var attemptCount = 0
            val flakyStage = object : CompilationStage {
                override val name = "flaky-stage"
                override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
                    attemptCount++
                    return if (attemptCount < 3) {
                        Result.failure(RuntimeException("模拟失败"))
                    } else {
                        Result.success(context)
                    }
                }
            }
            
            runBlocking {
                val result = flakyStage.executeWithRetry(context, maxRetries = 3, delayMs = 10)
                
                result.isSuccess shouldBe true
                attemptCount shouldBe 3
            }
        }
        
        test("包发现器使用序列操作") {
            val testProject = createTestProject()
            
            // 创建包结构
            val pkg1Dir = testProject.root.resolve("pkg1")
            pkg1Dir.toFile().mkdirs()
            val pkg1File1 = pkg1Dir.resolve("module1.cj")
            pkg1File1.toFile().writeText("package pkg1\nfunc hello() {}")
            val pkg1File2 = pkg1Dir.resolve("module2.cj")
            pkg1File2.toFile().writeText("package pkg1\nfunc world() {}")
            
            val pkg2Dir = testProject.root.resolve("pkg2")
            pkg2Dir.toFile().mkdirs()
            val pkg2File = pkg2Dir.resolve("main.cj")
            pkg2File.toFile().writeText("package pkg2\nfun main() {}")
            
            val sourceFiles = listOf(pkg1File1, pkg1File2, pkg2File)
            val packages = PackageDiscovery.discoverPackages(sourceFiles, testProject.root).toList()
            
            packages shouldHaveSize 2
            packages.any { it.name == "pkg1" } shouldBe true
            packages.any { it.name == "pkg2" } shouldBe true
            
            val pkg1 = packages.find { it.name == "pkg1" }!!
            pkg1.sourceFiles shouldHaveSize 2
            pkg1.isMainPackage shouldBe false
            
            val pkg2 = packages.find { it.name == "pkg2" }!!
            pkg2.hasMainFunction shouldBe true
        }
        
        test("命令DSL构建器") {
            val command = buildCommand {
                command("cjc")
                option("--package", "src")
                option("--output-type", "exe")
                flag("-g")
                arguments("--target", "x86_64-unknown-linux-gnu")
                argument("main.cj")
            }
            
            command shouldContain "cjc"
            command shouldContain "--package"
            command shouldContain "src"
            command shouldContain "--output-type"
            command shouldContain "exe"
            command shouldContain "-g"
            command shouldContain "--target"
            command shouldContain "x86_64-unknown-linux-gnu"
            command shouldContain "main.cj"
        }
        
        test("依赖收集器函数式风格") {
            val testProject = createTestProject()
            
            // 创建模拟库文件
            val targetDir = testProject.root.resolve("target")
            targetDir.toFile().mkdirs()
            val libFile = targetDir.resolve("libtest.a")
            libFile.toFile().createNewFile()
            
            val pathDep = Dependency.PathDependency(
                name = "testlib",
                path = libFile
            )
            
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                dependency(pathDep)
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            val libraryFiles = DependencyCollector.collectLibraryFiles(context)
            
            libraryFiles.any { it.toString().endsWith("libtest.a") } shouldBe true
        }
        
        test("编译上下文验证") {
            val testProject = createTestProject()
            
            // 创建有效上下文
            val validContext = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            validContext.validate().isSuccess shouldBe true
            
            // 创建无效上下文（非存在的项目根目录）
            val invalidRootContext = buildCompilationContext {
                projectRoot(testProject.root.resolve("nonexistent"))
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            invalidRootContext.validate().isFailure shouldBe true
        }
        
        test("流水线错误处理和回调") {
            val testProject = createTestProject()
            val context = buildCompilationContext {
                projectRoot(testProject.root)
                buildConfig { target(CompilationTarget.current()) }
                sourceFile(testProject.createSourceFile("main.cj", "main() {}"))
                outputPath(testProject.root.resolve("main.exe"))
            }
            
            var errorCaught = false
            var completionCalled = false
            
            val failingStage = object : CompilationStage {
                override val name = "failing-stage"
                override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
                    return Result.failure(RuntimeException("模拟失败"))
                }
                
                override suspend fun onFailure(context: CompilationContext, error: Throwable) {
                    errorCaught = true
                }
                
                override suspend fun onComplete(context: CompilationContext) {
                    completionCalled = true
                }
            }
            
            val pipeline = buildCompilationPipeline {
                stage(failingStage)
            }
            
            runBlocking {
                val result = pipeline.compile(context)
                
                result.isFailure shouldBe true
                errorCaught shouldBe true
                completionCalled shouldBe false // 失败时不应调用 onComplete
            }
        }
    }
}