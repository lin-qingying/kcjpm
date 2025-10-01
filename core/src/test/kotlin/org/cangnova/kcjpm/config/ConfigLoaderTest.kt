package org.cangnova.kcjpm.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.build.CompilationTarget
import org.cangnova.kcjpm.config.toml.TomlConfigParser
import org.cangnova.kcjpm.test.BaseTest

class ConfigLoaderTest : BaseTest() {
    init {
    ConfigLoader.setParser(TomlConfigParser())
    
    test("应该从文件加载最小配置") {
        val project = createTestProject()
        val content = simpleToml("test-pkg", "1.0.0", "executable")
        project.createConfig(content)
        
        val result = ConfigLoader.loadFromProjectRoot(project.root)
        
        result.isSuccess shouldBe true
        val config = result.getOrThrow()
        config.`package`.name shouldBe "test-pkg"
        config.`package`.version shouldBe "1.0.0"
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
    }
}