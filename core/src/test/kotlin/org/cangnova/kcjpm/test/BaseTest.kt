package org.cangnova.kcjpm.test

import io.kotest.core.spec.style.FunSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * 测试基类，提供通用的测试工具和辅助方法。
 *
 * 使用 Kotest 的 FunSpec 风格测试。
 */
abstract class BaseTest : FunSpec() {
    
    /**
     * 创建临时测试目录。
     *
     * @param prefix 目录前缀
     * @return 临时目录路径
     */
    protected fun createTempDir(prefix: String = "kcjpm-test"): Path {
        return createTempDirectory(prefix)
    }
    
    /**
     * 在临时目录中创建文件。
     *
     * @param dir 目录路径
     * @param relativePath 相对路径
     * @param content 文件内容
     * @return 创建的文件路径
     */
    protected fun createFile(dir: Path, relativePath: String, content: String): Path {
        val file = dir.resolve(relativePath)
        Files.createDirectories(file.parent)
        file.writeText(content)
        return file
    }
    
    /**
     * 创建测试用的 cjpm.toml 配置文件。
     *
     * @param dir 项目目录
     * @param content TOML 配置内容
     * @return 配置文件路径
     */
    protected fun createCjpmToml(dir: Path, content: String): Path {
        return createFile(dir, "cjpm.toml", content)
    }
    
    /**
     * 创建标准的测试项目结构。
     *
     * @param projectName 项目名称
     * @return 项目根目录路径
     */
    protected fun createTestProject(projectName: String = "test-project"): TestProject {
        val root = createTempDir("kcjpm-$projectName")
        
        // 创建基本目录结构
        Files.createDirectories(root.resolve("src"))
        Files.createDirectories(root.resolve("tests"))
        
        return TestProject(root)
    }
    
    /**
     * 创建简单的 TOML 配置。
     *
     * @param packageName 包名称
     * @param version 版本
     * @param outputType 输出类型
     * @return TOML 配置字符串
     */
    protected fun simpleToml(
        packageName: String = "test-package",
        version: String = "0.1.0",
        outputType: String = "executable"
    ): String = """
        [package]
        name = "$packageName"
        version = "$version"
        cjc-version = "1.0.0"
        output-type = "$outputType"
    """.trimIndent()
    
    /**
     * 创建带依赖的 TOML 配置。
     *
     * @param packageName 包名称
     * @param dependencies 依赖配置
     * @return TOML 配置字符串
     */
    protected fun tomlWithDependencies(
        packageName: String = "test-package",
        dependencies: String
    ): String = """
        [package]
        name = "$packageName"
        version = "0.1.0"
        cjc-version = "1.0.0"
        output-type = "executable"
        
        [dependencies]
        $dependencies
    """.trimIndent()
}

/**
 * 测试项目封装类。
 *
 * @property root 项目根目录
 */
data class TestProject(val root: Path) {
    /**
     * 创建源文件。
     *
     * @param relativePath 相对路径
     * @param content 文件内容
     * @return 文件路径
     */
    fun createSourceFile(relativePath: String, content: String = ""): Path {
        val file = root.resolve("src").resolve(relativePath)
        Files.createDirectories(file.parent)
        file.writeText(content)
        return file
    }
    
    /**
     * 创建配置文件。
     *
     * @param content TOML 配置内容
     * @return 配置文件路径
     */
    fun createConfig(content: String): Path {
        val configFile = root.resolve("cjpm.toml")
        configFile.writeText(content)
        return configFile
    }
    
    /**
     * 创建依赖目录。
     *
     * @param name 依赖名称
     * @return 依赖目录路径
     */
    fun createDependency(name: String): Path {
        val depDir = root.resolve("../$name")
        Files.createDirectories(depDir)
        return depDir
    }
}

/**
 * 测试依赖目录封装类。
 *
 * @property path 依赖目录路径
 */
fun Path.writeConfig(config: org.cangnova.kcjpm.config.CjpmConfig) {
    val content = buildString {
        appendLine("[package]")
        appendLine("name = \"${config.`package`.name}\"")
        appendLine("version = \"${config.`package`.version}\"")
        appendLine("cjc-version = \"${config.`package`.cjcVersion}\"")
        appendLine("output-type = \"${config.`package`.outputType.name.lowercase().replace('_', '-')}\"")
        if (config.dependencies.isNotEmpty()) {
            appendLine()
            appendLine("[dependencies]")
            config.dependencies.forEach { (name, dep) ->
                when {
                    dep.path != null -> appendLine("$name = { path = \"${dep.path}\" }")
                    dep.git != null && dep.tag != null -> appendLine("$name = { git = \"${dep.git}\", tag = \"${dep.tag}\" }")
                    dep.git != null && dep.branch != null -> appendLine("$name = { git = \"${dep.git}\", branch = \"${dep.branch}\" }")
                    dep.version != null -> appendLine("$name = { version = \"${dep.version}\" }")
                }
            }
        }
    }
    this.resolve("cjpm.toml").writeText(content)
}