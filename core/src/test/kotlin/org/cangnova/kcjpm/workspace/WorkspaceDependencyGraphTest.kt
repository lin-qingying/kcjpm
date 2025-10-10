package org.cangnova.kcjpm.workspace

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.config.ConfigFormat
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.official.OfficialConfigParser
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.writeText

class WorkspaceDependencyGraphTest : BaseTest() {
    
    init {

        
        test("拓扑排序无依赖成员") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a", "pkg-b", "pkg-c"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "pkg-a", "pkg-a", "0.1.0")
            createMember(root, "pkg-b", "pkg-b", "0.2.0")
            createMember(root, "pkg-c", "pkg-c", "0.3.0")
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            val sorted = graph.topologicalSort().getOrThrow()
            
            sorted shouldHaveSize 3
        }
        
        test("拓扑排序线性依赖链") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["core", "utils", "app"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "core", "core", "0.1.0")
            createMemberWithDeps(root, "utils", "utils", "0.1.0", """
                core = { path = "../core" }
            """.trimIndent())
            createMemberWithDeps(root, "app", "app", "1.0.0", """
                core = { path = "../core" }
                utils = { path = "../utils" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
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
        
        test("拓扑排序菱形依赖") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["core", "lib-a", "lib-b", "app"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "core", "core", "0.1.0")
            createMemberWithDeps(root, "lib-a", "lib-a", "0.1.0", """
                core = { path = "../core" }
            """.trimIndent())
            createMemberWithDeps(root, "lib-b", "lib-b", "0.1.0", """
                core = { path = "../core" }
            """.trimIndent())
            createMemberWithDeps(root, "app", "app", "1.0.0", """
                lib-a = { path = "../lib-a" }
                lib-b = { path = "../lib-b" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            val sorted = graph.topologicalSort().getOrThrow()
            
            sorted shouldHaveSize 4
            
            val coreIndex = sorted.indexOfFirst { it.name == "core" }
            val libAIndex = sorted.indexOfFirst { it.name == "lib-a" }
            val libBIndex = sorted.indexOfFirst { it.name == "lib-b" }
            val appIndex = sorted.indexOfFirst { it.name == "app" }
            
            coreIndex shouldBe 0
            (libAIndex > coreIndex) shouldBe true
            (libBIndex > coreIndex) shouldBe true
            (appIndex > libAIndex) shouldBe true
            (appIndex > libBIndex) shouldBe true
        }
        
        test("检测循环依赖") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["pkg-a", "pkg-b"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMemberWithDeps(root, "pkg-a", "pkg-a", "0.1.0", """
                pkg-b = { path = "../pkg-b" }
            """.trimIndent())
            createMemberWithDeps(root, "pkg-b", "pkg-b", "0.2.0", """
                pkg-a = { path = "../pkg-a" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            val result = graph.topologicalSort()
            
            result.isFailure shouldBe true
            
            val cycles = graph.detectCycles()
            cycles.isNotEmpty() shouldBe true
        }
        
        test("获取独立成员") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["core", "standalone", "app"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "core", "core", "0.1.0")
            createMember(root, "standalone", "standalone", "0.1.0")
            createMemberWithDeps(root, "app", "app", "1.0.0", """
                core = { path = "../core" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            val independent = graph.getIndependentMembers()
            
            independent shouldHaveSize 2
            independent.map { it.name }.contains("core") shouldBe true
            independent.map { it.name }.contains("standalone") shouldBe true
        }
        
        test("获取成员依赖和依赖者") {
            val root = createTempDir("workspace")
            
            val workspaceToml = """
                [workspace]
                members = ["core", "utils", "app"]
            """.trimIndent()
            root.resolve("cjpm.toml").writeText(workspaceToml)
            
            createMember(root, "core", "core", "0.1.0")
            createMemberWithDeps(root, "utils", "utils", "0.1.0", """
                core = { path = "../core" }
            """.trimIndent())
            createMemberWithDeps(root, "app", "app", "1.0.0", """
                core = { path = "../core" }
                utils = { path = "../utils" }
            """.trimIndent())
            
            val manager = DefaultWorkspaceManager()
            val workspace = kotlinx.coroutines.runBlocking { 
                manager.loadWorkspace(root) 
            }.getOrThrow()
            
            val graph = WorkspaceDependencyGraph(workspace)
            
            val coreDependents = graph.getDependents("core")
            coreDependents shouldHaveSize 2
            coreDependents.contains("utils") shouldBe true
            coreDependents.contains("app") shouldBe true
            
            val appDeps = graph.getDependencies("app")
            appDeps shouldHaveSize 2
            appDeps.contains("core") shouldBe true
            appDeps.contains("utils") shouldBe true
        }
    }
    
    private fun createMember(
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
        """.trimIndent()
        memberDir.resolve("cjpm.toml").writeText(memberToml)
        
        val srcDir = memberDir.resolve("src")
        java.nio.file.Files.createDirectories(srcDir)
    }
    
    private fun createMemberWithDeps(
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
        memberDir.resolve("cjpm.toml").writeText(memberToml)
        
        val srcDir = memberDir.resolve("src")
        java.nio.file.Files.createDirectories(srcDir)
    }
}