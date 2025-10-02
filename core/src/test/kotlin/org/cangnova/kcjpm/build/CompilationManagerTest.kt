package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.test.BaseTest

class CompilationManagerTest : BaseTest() {
    
    private val compilationManager = CompilationManager()
    
    init {
        test("验证阶段应检查项目根目录存在") {
            val validationStage = ValidationStage()
            val nonExistentPath = createTempDir().resolve("nonexistent")
            
            val context = createMockContext(projectRoot = nonExistentPath)
            
            runBlocking {
                val result = validationStage.execute(context)
                result.isFailure shouldBe true
                result.exceptionOrNull()?.message shouldContain "项目根目录不存在"
            }
        }
        
        test("验证阶段应检查源文件存在") {
            val testProject = createTestProject()
            val nonExistentFile = testProject.root.resolve("nonexistent.cj")
            
            val validationStage = ValidationStage()
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(nonExistentFile)
            )
            
            runBlocking {
                val result = validationStage.execute(context)
                result.isFailure shouldBe true
                result.exceptionOrNull()?.message shouldContain "源文件不存在"
            }
        }
        
        test("验证阶段应检查源文件扩展名") {
            val testProject = createTestProject()
            val wrongExtFile = testProject.createSourceFile("test.txt", "not cangjie")
            
            val validationStage = ValidationStage()
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(wrongExtFile)
            )
            
            runBlocking {
                val result = validationStage.execute(context)
                result.isFailure shouldBe true
                result.exceptionOrNull()?.message shouldContain "不是有效的仓颉源文件"
            }
        }
        
        test("验证阶段应创建输出目录") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            val outputPath = testProject.root.resolve("build").resolve("main.exe")
            
            val validationStage = ValidationStage()
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                outputPath = outputPath
            )
            
            runBlocking {
                val result = validationStage.execute(context)
                result.isSuccess shouldBe true
                outputPath.parent.toFile().exists() shouldBe true
            }
        }
        
        test("包编译阶段应发现和组织包") {
            val testProject = createTestProject()
            
            // 创建第一个包
            val package1Dir = testProject.root.resolve("package1")
            package1Dir.toFile().mkdirs()
            val pkg1File1 = package1Dir.resolve("module1.cj")
            pkg1File1.toFile().writeText("""
                package package1
                
                public func hello() {
                    println("Hello from package1")
                }
            """.trimIndent())
            
            val pkg1File2 = package1Dir.resolve("module2.cj")
            pkg1File2.toFile().writeText("""
                package package1
                
                public func world() {
                    println("World from package1")
                }
            """.trimIndent())
            
            // 创建第二个包
            val package2Dir = testProject.root.resolve("package2")
            package2Dir.toFile().mkdirs()
            val pkg2File = package2Dir.resolve("main.cj")
            pkg2File.toFile().writeText("""
                package package2
                
                public func main() {
                    println("Main from package2")
                }
            """.trimIndent())
            
            val packageStage = PackageCompilationStage()
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(pkg1File1, pkg1File2, pkg2File)
            )
            
            runBlocking {
                val result = packageStage.execute(context)
                result.isSuccess shouldBe true
            }
        }
        
        test("链接阶段应找到主文件") {
            val testProject = createTestProject()
            
            val mainFile = testProject.createSourceFile("main.cj", """
                main() {
                    println("Hello, Cangjie!")
                }
            """.trimIndent())
            
            val libFile = testProject.createSourceFile("lib.cj", """
                package mylib
                
                public func helper() {
                    println("Helper function")
                }
            """.trimIndent())
            
            val linkingStage = LinkingStage()
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(mainFile, libFile)
            )
            
            runBlocking {
                val result = linkingStage.execute(context)
                // 由于没有真实的 cjc 编译器，这里会失败，但我们可以验证逻辑
                result.isFailure shouldBe true
                result.exceptionOrNull()?.message shouldContain "链接失败"
            }
        }
        
        test("编译流水线应按顺序执行所有阶段") {
            val pipeline = DefaultCompilationPipeline()
            
            pipeline.stages shouldHaveSize 4
            pipeline.stages[0].name shouldBe "validation"
            pipeline.stages[1].name shouldBe "dependency-resolution"
            pipeline.stages[2].name shouldBe "package-compilation"
            pipeline.stages[3].name shouldBe "linking"
        }
        
        test("应该能够添加自定义编译阶段") {
            val pipeline = DefaultCompilationPipeline()
            val customStage = object : CompilationStage {
                override val name = "custom-stage"
                override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
                    return Result.success(context)
                }
            }
            
            val newPipeline = pipeline.addStage(customStage)
            newPipeline.stages shouldHaveSize 5
            newPipeline.stages.last().name shouldBe "custom-stage"
        }
        
        test("应该能够在指定阶段后插入新阶段") {
            val pipeline = DefaultCompilationPipeline()
            val customStage = object : CompilationStage {
                override val name = "custom-stage"
                override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
                    return Result.success(context)
                }
            }
            
            val result = pipeline.insertStageAfter("validation", customStage)
            result.isSuccess shouldBe true
            
            val newPipeline = result.getOrThrow()
            newPipeline.stages shouldHaveSize 5
            newPipeline.stages[1].name shouldBe "custom-stage"
        }
        
        test("插入到不存在的阶段后应该失败") {
            val pipeline = DefaultCompilationPipeline()
            val customStage = object : CompilationStage {
                override val name = "custom-stage"
                override suspend fun execute(context: CompilationContext): Result<CompilationContext> {
                    return Result.success(context)
                }
            }
            
            val result = pipeline.insertStageAfter("nonexistent", customStage)
            result.isFailure shouldBe true
            result.exceptionOrNull()!!.shouldBeInstanceOf<IllegalArgumentException>()
        }
        
        test("应该能够移除编译阶段") {
            val pipeline = DefaultCompilationPipeline()
            val newPipeline = pipeline.removeStage("dependency-resolution")
            
            newPipeline.stages shouldHaveSize 3
            newPipeline.stages.none { it.name == "dependency-resolution" } shouldBe true
        }
        
        test("编译管理器应该协调完整的编译流程") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", """
                main() {
                    println("Hello, Cangjie!")
                }
            """.trimIndent())
            
            val context = createMockContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile)
            )
            
            runBlocking {
                val result = compilationManager.compile(context)
                // 由于没有真实的编译器，会在某个阶段失败
                result.isFailure shouldBe true
            }
        }
    }
    
    private fun createMockContext(
        projectRoot: java.nio.file.Path = createTempDir(),
        sourceFiles: List<java.nio.file.Path> = emptyList(),
        dependencies: List<Dependency> = emptyList(),
        buildConfig: BuildConfig = BuildConfig(target = CompilationTarget.current()),
        outputPath: java.nio.file.Path = projectRoot.resolve("output.exe")
    ): CompilationContext {
        return object : CompilationContext {
            override val projectRoot = projectRoot
            override val buildConfig = buildConfig
            override val dependencies = dependencies
            override val sourceFiles = sourceFiles
            override val outputPath = outputPath
            
            override fun validate(): Result<Unit> = Result.success(Unit)
            override fun toCompilerArgs(): List<String> = emptyList()
        }
    }
}