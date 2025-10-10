package org.cangnova.kcjpm.workspace

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.config.ConfigFormat
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.official.OfficialConfigParser
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.writeText

class WorkspaceManagerTest : BaseTest() {
    
    init {
     
        
        test("加载纯工作空间") {
            val root = createTempDir("workspace-root")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a", "pkg-b"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "pkg-a", "pkg-a", "0.1.0")
            createMember(root, "pkg-b", "pkg-b", "0.2.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.isVirtual shouldBe true
            workspace.members shouldHaveSize 2
            workspace.members.map { it.name } shouldContain "pkg-a"
            workspace.members.map { it.name } shouldContain "pkg-b"
        }
        
        test("加载混合工作空间") {
            val root = createTempDir("mixed-workspace")
            
            val workspaceToml = """
                [package]
                name = "root-app"
                version = "1.0.0"
                cjc-version = "1.0.0"
                output-type = "executable"
                
                [workspace]
                members = [".", "libs/core", "libs/utils"]
                default-members = ["."]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "libs/core", "core", "0.1.0")
            createMember(root, "libs/utils", "utils", "0.1.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.isMixed shouldBe true
            workspace.rootConfig.`package` shouldNotBe null
            workspace.rootConfig.`package`?.name shouldBe "root-app"
            workspace.members shouldHaveSize 3
            workspace.members.map { it.name } shouldContain "root-app"
            workspace.members.map { it.name } shouldContain "core"
            workspace.members.map { it.name } shouldContain "utils"
        }
        
        test("加载工作空间使用通配符") {
            val root = createTempDir("wildcard-workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["packages/*"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "packages/pkg-a", "pkg-a", "0.1.0")
            createMember(root, "packages/pkg-b", "pkg-b", "0.2.0")
            createMember(root, "packages/pkg-c", "pkg-c", "0.3.0")
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isSuccess shouldBe true
            val workspace = result.getOrThrow()
            
            workspace.members shouldHaveSize 3
            workspace.members.map { it.name } shouldContain "pkg-a"
            workspace.members.map { it.name } shouldContain "pkg-b"
            workspace.members.map { it.name } shouldContain "pkg-c"
        }
        
        test("非工作空间根目录加载失败") {
            val root = createTempDir("not-workspace")
            
            val normalToml = """
                [package]
                name = "normal-package"
                version = "1.0.0"
                cjc-version = "1.0.0"
                output-type = "executable"
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(normalToml)
            
            val manager = DefaultWorkspaceManager()
            val result = runBlocking { manager.loadWorkspace(root) }
            
            result.isFailure shouldBe true
        }
        
        test("检测工作空间根目录") {
            val workspaceRoot = createTempDir("workspace")
            val workspaceToml = """
                [workspace]
                members = ["pkg-a"]
            """.trimIndent()
            workspaceRoot.resolve("cjpm.toml").writeText(workspaceToml)
            createMember(workspaceRoot, "pkg-a", "pkg-a", "1.0.0")
            
            val normalRoot = createTempDir("normal")
            val normalToml = """
                [package]
                name = "normal"
                version = "1.0.0"
                cjc-version = "1.0.0"
                output-type = "executable"
            """.trimIndent()
            normalRoot.resolve("cjpm.toml").writeText(normalToml)
            
            val manager = DefaultWorkspaceManager()
            
            manager.isWorkspaceRoot(workspaceRoot) shouldBe true
            manager.isWorkspaceRoot(normalRoot) shouldBe false
        }
        
        test("查找工作空间成员") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a", "pkg-b"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "pkg-a", "pkg-a", "0.1.0")
            createMember(root, "pkg-b", "pkg-b", "0.2.0")
            
            val manager = DefaultWorkspaceManager()
            val workspace = runBlocking { manager.loadWorkspace(root) }.getOrThrow()
            
            workspace.findMember("pkg-a") shouldNotBe null
            workspace.findMember("pkg-b") shouldNotBe null
            workspace.findMember("pkg-c") shouldBe null
            
            workspace.getMember("pkg-a").name shouldBe "pkg-a"
            shouldThrow<IllegalArgumentException> {
                workspace.getMember("pkg-c")
            }
        }
    }
    
    private fun createMember(workspaceRoot: java.nio.file.Path, relativePath: String, name: String, version: String) {
        val memberDir = workspaceRoot.resolve(relativePath)
        java.nio.file.Files.createDirectories(memberDir)
        
        val memberToml = """
            [package]
            name = "$name"
            version = "$version"
            cjc-version = "1.0.0"
            output-type = "library"
        """.trimIndent()
        memberDir.resolve("cjpm.toml").writeText(memberToml)
        
        val srcDir = memberDir.resolve("src")
        java.nio.file.Files.createDirectories(srcDir)
    }
}