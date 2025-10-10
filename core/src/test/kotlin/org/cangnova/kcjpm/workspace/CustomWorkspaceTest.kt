package org.cangnova.kcjpm.workspace

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.writeText

class CustomWorkspaceTest : BaseTest() {
    
    init {
        test("加载自定义配置格式的纯工作空间") {
            val root = createTempDir("custom-workspace-root")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a", "pkg-b"]
            """.trimIndent()
            root.resolve("kcjpm.toml").writeText(workspaceToml)
            
            createCustomMember(root, "pkg-a", "pkg-a", "0.1.0")
            createCustomMember(root, "pkg-b", "pkg-b", "0.2.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.isVirtual shouldBe true
            workspace.members shouldHaveSize 2
            workspace.members.map { it.name } shouldContain "pkg-a"
            workspace.members.map { it.name } shouldContain "pkg-b"
        }
        
        test("加载自定义配置格式的混合工作空间") {
            val root = createTempDir("custom-mixed-workspace")
            
            val workspaceToml = """
                [package]
                name = "root-app"
                version = "1.0.0"
                cjc-version = "1.0.0"
                output-type = "executable"
                authors = ["Test Author"]
                license = "MIT"
                
                [workspace]
                members = [".", "libs/core", "libs/utils"]
                default-members = ["."]
            """.trimIndent()
            root.resolve("kcjpm.toml").writeText(workspaceToml)
            
            createCustomMember(root, "libs/core", "core", "0.1.0")
            createCustomMember(root, "libs/utils", "utils", "0.1.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.isMixed shouldBe true
            workspace.rootConfig.`package` shouldNotBe null
            workspace.rootConfig.`package`?.name shouldBe "root-app"
            workspace.rootConfig.`package`?.authors?.shouldContain("Test Author")
            workspace.members shouldHaveSize 3
        }
        
        test("加载自定义配置格式工作空间使用通配符") {
            val root = createTempDir("custom-wildcard-workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["packages/*"]
            """.trimIndent()
            root.resolve("kcjpm.toml").writeText(workspaceToml)
            
            createCustomMember(root, "packages/pkg-a", "pkg-a", "0.1.0")
            createCustomMember(root, "packages/pkg-b", "pkg-b", "0.2.0")
            createCustomMember(root, "packages/pkg-c", "pkg-c", "0.3.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.members shouldHaveSize 3
            workspace.members.map { it.name } shouldContain "pkg-a"
            workspace.members.map { it.name } shouldContain "pkg-b"
            workspace.members.map { it.name } shouldContain "pkg-c"
        }
        
        test("自定义配置工作空间成员依赖分析") {
            val root = createTempDir("custom-workspace-deps")
            
            val workspaceToml = """
                [workspace]
                members = ["core", "utils", "app"]
            """.trimIndent()
            root.resolve("kcjpm.toml").writeText(workspaceToml)
            
            createCustomMember(root, "core", "core", "0.1.0")
            createCustomMemberWithDeps(root, "utils", "utils", "0.1.0", """
                core = { path = "../core" }
            """.trimIndent())
            createCustomMemberWithDeps(root, "app", "app", "1.0.0", """
                core = { path = "../core" }
                utils = { path = "../utils" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = runBlocking { manager.loadWorkspace(root) }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            val sorted = graph.topologicalSort().getOrThrow()
            
            sorted shouldHaveSize 3
            
            val coreIndex = sorted.indexOfFirst { it.name == "core" }
            val utilsIndex = sorted.indexOfFirst { it.name == "utils" }
            val appIndex = sorted.indexOfFirst { it.name == "app" }
            
            coreIndex shouldBe 0
            (utilsIndex > coreIndex) shouldBe true
            (appIndex > utilsIndex) shouldBe true
        }
        
        test("自定义配置工作空间带registry配置") {
            val root = createTempDir("custom-workspace-registry")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a"]
                
                [registry]
                default = "https://custom-registry.example.com"
                mirrors = ["https://mirror1.example.com"]
            """.trimIndent()
            root.resolve("kcjpm.toml").writeText(workspaceToml)
            
            createCustomMember(root, "pkg-a", "pkg-a", "1.0.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.isVirtual shouldBe true
            workspace.rootConfig.registry shouldNotBe null
            workspace.rootConfig.registry?.default shouldBe "https://custom-registry.example.com"
        }
    }
    
    private fun createCustomMember(
        workspaceRoot: java.nio.file.Path,
        relativePath: String,
        name: String,
        version: String
    ) {
        val memberDir = workspaceRoot.resolve(relativePath)
        java.nio.file.Files.createDirectories(memberDir)
        
        val memberToml = """
            [package]
            name = "$name"
            version = "$version"
            cjc-version = "1.0.0"
            output-type = "library"
            authors = ["Test Author"]
            license = "MIT"
        """.trimIndent()
        memberDir.resolve("kcjpm.toml").writeText(memberToml)
        
        val srcDir = memberDir.resolve("src")
        java.nio.file.Files.createDirectories(srcDir)
    }
    
    private fun createCustomMemberWithDeps(
        workspaceRoot: java.nio.file.Path,
        relativePath: String,
        name: String,
        version: String,
        dependencies: String
    ) {
        val memberDir = workspaceRoot.resolve(relativePath)
        java.nio.file.Files.createDirectories(memberDir)
        
        val memberToml = """
            [package]
            name = "$name"
            version = "$version"
            cjc-version = "1.0.0"
            output-type = "library"
            
            [dependencies]
            $dependencies
        """.trimIndent()
        memberDir.resolve("kcjpm.toml").writeText(memberToml)
        
        val srcDir = memberDir.resolve("src")
        java.nio.file.Files.createDirectories(srcDir)
    }
}