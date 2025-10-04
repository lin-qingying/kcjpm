package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Paths

class CompilationCommandBuilderTest : BaseTest() {
    
    private val commandBuilder = CompilationCommandBuilder()
    
    init {
        test("构建基本编译命令") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", """
                package main
                
                main() {
                    println("Hello, Cangjie!")
                }
            """.trimIndent())
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                outputPath = testProject.root.resolve("main.exe")
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "cjc"
            command shouldContain "--output-type=exe"
            command shouldContain "--output"
            command shouldContain testProject.root.resolve("main.exe").toString()
            command shouldContain "-O2"  // 默认 RELEASE 模式
        }
        
        test("构建包编译命令") {
            val packageDir = createTempDir().resolve("mypackage")
            packageDir.toFile().mkdirs()
            
            val outputPath = packageDir.resolve("libmypackage.a")
            val buildConfig = BuildConfig(
                target = CompilationTarget.LINUX_X64,
                optimizationLevel = OptimizationLevel.DEBUG,
                debugInfo = true
            )
            
            val context = createCompilationContext(
                projectRoot = packageDir.parent,
                sourceFiles = emptyList(),
                buildConfig = buildConfig
            )
            
            val command = with(context) {
                commandBuilder.buildPackageCommand(
                    packageDir = packageDir,
                    outputDir = outputPath.parent ?: outputPath,
                    outputFileName = "libmypackage.a"
                )
            }
            
            command shouldContain "cjc"
            command shouldContain "-p"
            command shouldContain packageDir.toString()
            command shouldContain "--output-type=staticlib"
            command shouldContain "-O0"
            command shouldContain "-g"
        }
        
        test("构建可执行文件链接命令") {
            val testProject = createTestProject()
            val mainFile = testProject.createSourceFile("main.cj", """
                import mylib.*
                
                main() {
                    mylib.hello()
                }
            """.trimIndent())
            
            val libFile = testProject.root.resolve("libmylib.a")
            libFile.toFile().createNewFile()
            
            val outputPath = testProject.root.resolve("main.exe")
            val buildConfig = BuildConfig(target = CompilationTarget.WINDOWS_X64)
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(mainFile),
                buildConfig = buildConfig,
                outputPath = outputPath
            )
            
            val command = with(context) {
                commandBuilder.buildExecutableCommand(
                    mainFile = mainFile,
                    libraryFiles = listOf(libFile),
                    outputPath = outputPath
                )
            }
            
            command shouldContain "cjc"
            command shouldContain mainFile.toString()
            command shouldContain libFile.toString()
            command shouldContain "--output-type=exe"
            command shouldContain "--target"
            command shouldContain "x86_64-w64-mingw32"
        }
        
        test("处理路径依赖") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val libPath = testProject.root.resolve("lib").resolve("mylib.a")
            libPath.parent.toFile().mkdirs()
            libPath.toFile().createNewFile()
            
            val pathDep = Dependency.PathDependency(
                name = "mylib",
                path = libPath
            )
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                dependencies = listOf(pathDep)
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain libPath.toString()
        }
        
        test("处理Git依赖") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val localPath = testProject.root.resolve("deps").resolve("gitlib")
            localPath.toFile().mkdirs()
            val libFile = localPath.resolve("libgitlib.a")
            libFile.toFile().createNewFile()
            
            val gitDep = Dependency.GitDependency(
                name = "gitlib",
                url = "https://example.com/gitlib.git",
                reference = Dependency.GitReference.Tag("v1.0.0"),
                localPath = localPath
            )
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                dependencies = listOf(gitDep)
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "--library-path"
            command shouldContain localPath.toString()
            command shouldContain libFile.toString()
        }
        
        test("处理优化级别") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            // 测试不同优化级别
            val optimizationLevels = mapOf(
                OptimizationLevel.DEBUG to "-O0",
                OptimizationLevel.RELEASE to "-O2", 
                OptimizationLevel.SIZE to "-Os",
                OptimizationLevel.SPEED to "-O3"
            )
            
            optimizationLevels.forEach { (level, expectedFlag) ->
                val context = createCompilationContext(
                    projectRoot = testProject.root,
                    sourceFiles = listOf(sourceFile),
                    buildConfig = BuildConfig(
                        target = CompilationTarget.current(),
                        optimizationLevel = level
                    )
                )
                
                val command = with(context) {
                    commandBuilder.buildCommand()
                }
                command shouldContain expectedFlag
            }
        }
        
        test("处理并行编译选项") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                buildConfig = BuildConfig(
                    target = CompilationTarget.current(),
                    parallel = true,
                    maxParallelSize = 8
                )
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "--jobs"
            command shouldContain "8"
        }
        
        test("处理增量编译选项") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                buildConfig = BuildConfig(
                    target = CompilationTarget.current(),
                    incremental = true
                )
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "--experimental"
            command shouldContain "--incremental-compile"
        }
        
        test("处理详细输出选项") {
            val testProject = createTestProject()
            val sourceFile = testProject.createSourceFile("main.cj", "main() {}")
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile),
                buildConfig = BuildConfig(
                    target = CompilationTarget.current(),
                    verbose = true
                )
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "--verbose"
        }

        test("动态库输出文件名使用.b.dll扩展名") {
            val packageDir = createTempDir().resolve("mypackage")
            packageDir.toFile().mkdirs()

            val context = createCompilationContext(
                projectRoot = packageDir.parent,
                sourceFiles = emptyList(),
                outputType = org.cangnova.kcjpm.config.OutputType.DYNAMIC_LIBRARY
            )

            val command = with(context) {
                commandBuilder.buildPackageCommand(
                    packageDir = packageDir,
                    outputDir = packageDir.parent,
                    outputFileName = "libmypackage.b.dll"
                )
            }

            command shouldContain "-o=libmypackage.b.dll"
            command shouldContain "--output-type=dylib"
        }

        test("静态库输出文件名使用.a扩展名") {
            val packageDir = createTempDir().resolve("mypackage")
            packageDir.toFile().mkdirs()

            val context = createCompilationContext(
                projectRoot = packageDir.parent,
                sourceFiles = emptyList(),
                outputType = org.cangnova.kcjpm.config.OutputType.STATIC_LIBRARY
            )

            val command = with(context) {
                commandBuilder.buildPackageCommand(
                    packageDir = packageDir,
                    outputDir = packageDir.parent,
                    outputFileName = "libmypackage.a"
                )
            }

            command shouldContain "-o=libmypackage.a"
            command shouldContain "--output-type=staticlib"
        }
        
        test("查找包目录") {
            val testProject = createTestProject()
            val packageDir = testProject.root.resolve("mypackage")
            packageDir.toFile().mkdirs()
            
            // 创建包中的多个源文件
            val sourceFile1 = packageDir.resolve("module1.cj")
            sourceFile1.toFile().writeText("""
                package mypackage
                
                public func hello() {
                    println("Hello from module1")
                }
            """.trimIndent())
            
            val sourceFile2 = packageDir.resolve("module2.cj")
            sourceFile2.toFile().writeText("""
                package mypackage
                
                public func world() {
                    println("World from module2")
                }
            """.trimIndent())
            
            val context = createCompilationContext(
                projectRoot = testProject.root,
                sourceFiles = listOf(sourceFile1, sourceFile2)
            )
            
            val command = with(context) {
                commandBuilder.buildCommand()
            }
            
            command shouldContain "--package"
            command shouldContain packageDir.toString()
        }
    }
    
    private fun createCompilationContext(
        projectRoot: java.nio.file.Path,
        sourceFiles: List<java.nio.file.Path>,
        dependencies: List<Dependency> = emptyList(),
        buildConfig: BuildConfig = BuildConfig(target = CompilationTarget.current()),
        outputPath: java.nio.file.Path = projectRoot.resolve("output"),
        outputType: org.cangnova.kcjpm.config.OutputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
    ): CompilationContext {
        return object : CompilationContext {
            override val projectRoot = projectRoot
            override val buildConfig = buildConfig
            override val dependencies = dependencies
            override val sourceFiles = sourceFiles
            override val outputPath = outputPath
            override val outputType = outputType
            
            override fun validate(): Result<Unit> = Result.success(Unit)
        }
    }
}