package org.cangnova.kcjpm.init

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.readText

class
ProjectInitializerTest : BaseTest() {
    
    init {
        test("应该初始化可执行程序项目") {
            val targetDir = createTempDir("init-exe-project")
            val initializer = DefaultProjectInitializer()
            
            val template = BuiltinTemplates.getExecutableTemplate()
            val options = InitOptions(
                projectName = "my-app",
                version = "0.1.0",
                authors = listOf("Test Author"),
                description = "A test application"
            )
            
            val result = runBlocking {
                initializer.initProject(targetDir, template, options)
            }
            
            result.isSuccess shouldBe true
            val initResult = result.getOrThrow()
            
            initResult.createdFiles shouldHaveSize 4
            
            val configFile = targetDir.resolve("kcjpm.toml")
            configFile.toFile().exists() shouldBe true
            
            val config = configFile.readText()
            config shouldContain "name = \"my-app\""
            config shouldContain "version = \"0.1.0\""
            config shouldContain "output-type = \"executable\""
            config shouldContain "authors = [\"Test Author\"]"
            config shouldContain "description = \"A test application\""
            
            val mainFile = targetDir.resolve("src/main.cj")
            mainFile.toFile().exists() shouldBe true
            val mainContent = mainFile.readText()
            mainContent shouldContain "Hello, my-app!"
        }
        
        test("应该初始化库项目") {
            val targetDir = createTempDir("init-lib-project")
            val initializer = DefaultProjectInitializer()
            
            val template = BuiltinTemplates.getLibraryTemplate()
            val options = InitOptions(
                projectName = "my-lib",
                version = "1.0.0",
                license = "MIT"
            )
            
            val result = runBlocking {
                initializer.initProject(targetDir, template, options)
            }
            
            result.isSuccess shouldBe true
            
            val configFile = targetDir.resolve("kcjpm.toml")
            val config = configFile.readText()
            config shouldContain "output-type = \"library\""
            config shouldContain "license = \"MIT\""
            
            val libFile = targetDir.resolve("src/lib.cj")
            libFile.toFile().exists() shouldBe true
            val libContent = libFile.readText()
            libContent shouldContain "class MyLib"
        }
        
        test("应该初始化工作空间") {
            val targetDir = createTempDir("init-workspace")
            val initializer = DefaultProjectInitializer()
            
            val template = BuiltinTemplates.getWorkspaceTemplate()
            val options = InitOptions(
                projectName = "my-workspace",
                description = "A test workspace"
            )
            
            val result = runBlocking {
                initializer.initProject(targetDir, template, options)
            }
            
            result.isSuccess shouldBe true
            
            val configFile = targetDir.resolve("kcjpm.toml")
            val config = configFile.readText()
            config shouldContain "[workspace]"
            config shouldContain "members = [\"packages/*\"]"
            
            val packagesDir = targetDir.resolve("packages")
            packagesDir.toFile().exists() shouldBe true
        }
        
        test("应该列出所有内置模板") {
            val initializer = DefaultProjectInitializer()
            
            val result = runBlocking {
                initializer.listTemplates()
            }
            
            result.isSuccess shouldBe true
            val templates = result.getOrThrow()
            
            templates shouldHaveSize 3
            templates.map { it.name } shouldContain "executable"
            templates.map { it.name } shouldContain "library"
            templates.map { it.name } shouldContain "workspace"
        }
        
        test("应该根据名称获取模板") {
            val initializer = DefaultProjectInitializer()
            
            val exeResult = runBlocking { initializer.getTemplate("executable") }
            exeResult.isSuccess shouldBe true
            exeResult.getOrThrow().info.name shouldBe "executable"
            
            val libResult = runBlocking { initializer.getTemplate("library") }
            libResult.isSuccess shouldBe true
            libResult.getOrThrow().info.name shouldBe "library"
            
            val wsResult = runBlocking { initializer.getTemplate("workspace") }
            wsResult.isSuccess shouldBe true
            wsResult.getOrThrow().info.name shouldBe "workspace"
        }
        
        test("模板别名应该正常工作") {
            val initializer = DefaultProjectInitializer()
            
            val exeResult = runBlocking { initializer.getTemplate("exe") }
            exeResult.isSuccess shouldBe true
            exeResult.getOrThrow().info.name shouldBe "executable"
            
            val libResult = runBlocking { initializer.getTemplate("lib") }
            libResult.isSuccess shouldBe true
            libResult.getOrThrow().info.name shouldBe "library"
        }
        
        test("不存在的目标路径应该被创建") {
            val baseDir = createTempDir("base")
            val targetDir = baseDir.resolve("new-project")
            
            targetDir.toFile().exists() shouldBe false
            
            val initializer = DefaultProjectInitializer()
            val template = BuiltinTemplates.getExecutableTemplate()
            val options = InitOptions(projectName = "test")
            
            val result = runBlocking {
                initializer.initProject(targetDir, template, options)
            }
            
            result.isSuccess shouldBe true
            targetDir.toFile().exists() shouldBe true
        }
    }
}