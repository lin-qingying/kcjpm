package org.cangnova.kcjpm.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.build.CompilationTarget
import org.cangnova.kcjpm.config.toml.TomlConfigParser
import org.cangnova.kcjpm.test.BaseTest

class ConfigLoaderTest : BaseTest() {
    init {
        test("应该从文件加载最小配置") {
            val project = createTestProject()
            val content = simpleToml("test-pkg", "1.0.0", "executable")
            project.createConfig(content)

            val result = ConfigLoader.loadFromProjectRoot(project.root)

            result.isSuccess shouldBe true
            val config = result.getOrThrow()
            config.`package`?.name shouldBe "test-pkg"
            config.`package`?.version shouldBe "1.0.0"
        }

        test("当配置文件未找到时应该失败") {
            val project = createTestProject()

            val result = ConfigLoader.loadFromProjectRoot(project.root)

            result.isFailure shouldBe true
        }

        test("当 TOML 语法无效时应该失败") {
            val project = createTestProject()
            project.createConfig("invalid toml content {]}")

            val result = ConfigLoader.loadFromProjectRoot(project.root)

            result.isFailure shouldBe true
        }

        test("应该加载带有依赖的配置") {
            val project = createTestProject()
            val content = tomlWithDependencies(
                "my-app",
                """
            std-http = "1.2.0"
            local-lib = { path = "../local-lib" }
            """.trimIndent()
            )
            project.createConfig(content)

            val result = ConfigLoader.loadFromProjectRoot(project.root)

            result.isSuccess shouldBe true
            val config = result.getOrThrow()
            config.dependencies.size shouldBe 2
            config.dependencies["std-http"]?.version shouldBe "1.2.0"
            config.dependencies["local-lib"]?.path shouldBe "../local-lib"
        }

        test("应该加载并转换为编译上下文") {
            val project = createTestProject()
            val content = simpleToml("my-app")
            project.createConfig(content)
            project.createSourceFile("main.cj", "// main file")

            val result = ConfigLoader.loadAndConvert(
                project.root,
                CompilationTarget.LINUX_X64,
                "release"
            )

            result.isSuccess shouldBe true
            val context = result.getOrThrow()
            context.projectRoot shouldBe project.root
            context.buildConfig.target shouldBe CompilationTarget.LINUX_X64
            context.sourceFiles.size shouldBe 1
        }

        test("未指定时应该使用默认配置文件") {
            val project = createTestProject()
            project.createConfig(simpleToml())
            project.createSourceFile("main.cj")

            val result = ConfigLoader.loadAndConvert(project.root)

            result.isSuccess shouldBe true
            val context = result.getOrThrow()
            context.buildConfig.debugInfo shouldBe false // release profile default
        }

        test("指定时应该使用自定义配置文件") {
            val project = createTestProject()
            val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [profile.debug]
            optimization-level = 0
            debug-info = true
            lto = false
        """.trimIndent()
            project.createConfig(content)
            project.createSourceFile("main.cj")

            val result = ConfigLoader.loadAndConvert(
                project.root,
                profileName = "debug"
            )

            result.isSuccess shouldBe true
            val context = result.getOrThrow()
            context.buildConfig.debugInfo shouldBe true
        }

        test("应该解析真实的官方格式配置文件") {
            val bsonProjectRoot = java.nio.file.Paths.get("D:\\code\\cangjie\\bson")

            if (!bsonProjectRoot.toFile().exists()) {
                println("跳过测试: bson 项目不存在")
                return@test
            }

            val result = ConfigLoader.loadFromProjectRoot(bsonProjectRoot)

            result.isSuccess shouldBe true
            val config = result.getOrThrow()
            config.`package`?.name shouldBe "bson"
            config.`package`?.version shouldBe "1.0.0"
            config.`package`?.cjcVersion shouldBe "0.53.13"
            config.`package`?.outputType shouldBe org.cangnova.kcjpm.config.OutputType.EXECUTABLE
            config.`package`?.description shouldBe "nothing here111"
        }

        test("应该完成从配置到编译命令的完整流程") {
            val bsonProjectRoot = java.nio.file.Paths.get("D:\\code\\cangjie\\bson")

            if (!bsonProjectRoot.toFile().exists()) {
                println("跳过测试: bson 项目不存在")
                return@test
            }

            val contextResult = ConfigLoader.loadAndConvert(
                bsonProjectRoot,
                org.cangnova.kcjpm.build.CompilationTarget.WINDOWS_X64,
                "release"
            )

            contextResult.isSuccess shouldBe true
            val context = contextResult.getOrThrow()

            context.projectRoot shouldBe bsonProjectRoot
            context.buildConfig.target shouldBe org.cangnova.kcjpm.build.CompilationTarget.WINDOWS_X64
            context.sourceFiles.size shouldNotBe 0

            val commandBuilder = org.cangnova.kcjpm.build.CompilationCommandBuilder()
            
            val packageCommands = with(context) {
                commandBuilder.buildPackageCommands()
            }
            
            println("发现 ${packageCommands.size} 个包:")
            packageCommands.forEachIndexed { index, command ->
                println("包 ${index + 1}: ${command.joinToString(" ")}")
            }
            
            packageCommands.size shouldBe 3
            
            packageCommands.forEach { command ->
                command shouldContain "cjc"
                command shouldContain "--package"
                command shouldContain "--output-type=staticlib"
                command shouldContain "-O2"
                command shouldContain "--target"
                command shouldContain "x86_64-w64-mingw32"
            }
        }
    }
}