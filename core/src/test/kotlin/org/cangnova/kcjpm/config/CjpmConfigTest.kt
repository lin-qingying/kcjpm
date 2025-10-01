package org.cangnova.kcjpm.config

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import net.peanuuutz.tomlkt.Toml
import org.cangnova.kcjpm.test.BaseTest

class CjpmConfigTest : BaseTest() {
    init {
    val toml = Toml { ignoreUnknownKeys = true }
    
    test("应该解析最小配置") {
        val content = """
            [package]
            name = "test-package"
            version = "0.1.0"
            cjc-version = "1.0.0"
            output-type = "executable"
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.`package`.name shouldBe "test-package"
        config.`package`.version shouldBe "0.1.0"
        config.`package`.cjcVersion shouldBe "1.0.0"
        config.`package`.outputType shouldBe OutputType.EXECUTABLE
    }
    
    test("应该解析带有仓库的配置") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "library"
            
            [registry]
            default = "https://repo.cangjie-lang.cn"
            mirrors = ["https://mirror1.com", "https://mirror2.com"]
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.registry shouldNotBe null
        config.registry?.default shouldBe "https://repo.cangjie-lang.cn"
        config.registry?.mirrors?.size shouldBe 2
        config.registry?.mirrors?.shouldContain("https://mirror1.com")
    }
    
    test("应该解析远程依赖") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [dependencies]
            std-http = "1.2.0"
            std-json = { version = "2.0.0", registry = "default" }
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.dependencies.size shouldBe 2
        config.dependencies["std-http"]?.version shouldBe "1.2.0"
        config.dependencies["std-json"]?.version shouldBe "2.0.0"
        config.dependencies["std-json"]?.registry shouldBe "default"
    }
    
    test("应该解析路径依赖") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [dependencies]
            local-lib = { path = "../local-lib" }
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.dependencies["local-lib"]?.path shouldBe "../local-lib"
        config.dependencies["local-lib"]?.version shouldBe null
    }
    
    test("应该解析 Git 依赖") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [dependencies]
            http-client = { git = "https://github.com/cangjie/http", tag = "v1.0.0" }
            dev-lib = { git = "https://github.com/cangjie/dev", branch = "develop" }
            fixed-lib = { git = "https://github.com/cangjie/fixed", commit = "abc123" }
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.dependencies.size shouldBe 3
        config.dependencies["http-client"]?.git shouldBe "https://github.com/cangjie/http"
        config.dependencies["http-client"]?.tag shouldBe "v1.0.0"
        config.dependencies["dev-lib"]?.branch shouldBe "develop"
        config.dependencies["fixed-lib"]?.commit shouldBe "abc123"
    }
    
    test("应该解析构建配置") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [build]
            source-dir = "src"
            output-dir = "target"
            parallel = true
            incremental = true
            jobs = 4
            verbose = false
            pre-build = ["echo 'Starting'"]
            post-build = ["echo 'Done'"]
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.build shouldNotBe null
        config.build?.sourceDir shouldBe "src"
        config.build?.outputDir shouldBe "target"
        config.build?.parallel shouldBe true
        config.build?.incremental shouldBe true
        config.build?.jobs shouldBe 4
        config.build?.verbose shouldBe false
        config.build?.preBuild?.size shouldBe 1
        config.build?.postBuild?.size shouldBe 1
    }
    
    test("应该解析配置文件配置") {
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
            
            [profile.release]
            optimization-level = 2
            debug-info = false
            lto = false
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.profile.size shouldBe 2
        
        val debugProfile = config.profile["debug"]
        debugProfile shouldNotBe null
        debugProfile?.optimizationLevel shouldBe 0
        debugProfile?.debugInfo shouldBe true
        debugProfile?.lto shouldBe false
        
        val releaseProfile = config.profile["release"]
        releaseProfile shouldNotBe null
        releaseProfile?.optimizationLevel shouldBe 2
        releaseProfile?.debugInfo shouldBe false
    }
    
    test("应该解析工作区配置") {
        val content = """
            [package]
            name = "test"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            
            [workspace]
            members = ["packages/core", "packages/cli"]
            default-members = ["packages/cli"]
        """.trimIndent()
        
        val config = toml.decodeFromString(CjpmConfig.serializer(), content)
        
        config.workspace shouldNotBe null
        config.workspace?.members?.size shouldBe 2
        config.workspace?.defaultMembers?.size shouldBe 1
        config.workspace?.members?.shouldContain("packages/core")
    }
    
    test("应该解析完整配置") {
        val content = """
            [package]
            name = "my-project"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "executable"
            authors = ["Alice <alice@example.com>"]
            description = "A test project"
            license = "Apache-2.0"
            
            [registry]
            default = "https://repo.cangjie-lang.cn"
            
            [dependencies]
            std-http = "1.2.0"
            local-lib = { path = "../lib" }
            
            [build]
            source-dir = "src"
            parallel = true
            
            [profile.debug]
            optimization-level = 0
            debug-info = true
        """.trimIndent()
        
        shouldNotThrowAny {
            toml.decodeFromString(CjpmConfig.serializer(), content)
        }
    }
    }
}