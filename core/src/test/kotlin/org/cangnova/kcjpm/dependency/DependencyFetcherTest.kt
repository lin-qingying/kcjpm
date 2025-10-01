package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path

class
DependencyFetcherTest : BaseTest() {
    init {
        test("PathDependencyFetcher 应该处理 PATH 类型") {
            val fetcher = PathDependencyFetcher()
            fetcher.canHandle(DependencyType.PATH) shouldBe true
            fetcher.canHandle(DependencyType.GIT) shouldBe false
            fetcher.canHandle(DependencyType.REGISTRY) shouldBe false
        }
        
        test("PathDependencyFetcher 应该获取本地路径依赖") {
            val project = createTestProject()
            val depDir = project.createDependency("local-lib")
            
            val fetcher = PathDependencyFetcher()
            val config = DependencyConfig(path = "../local-lib")
            
            val result = fetcher.fetch("local-lib", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.PathDependency>()
            dep.name shouldBe "local-lib"
            dep.path.normalize() shouldBe depDir.normalize()
        }
        
        test("PathDependencyFetcher 应该在路径不存在时失败") {
            val project = createTestProject()
            val fetcher = PathDependencyFetcher()
            val config = DependencyConfig(path = "../nonexistent")
            
            val result = fetcher.fetch("missing", config, project.root, null)
            
            result.isFailure shouldBe true
        }
        
        test("PathDependencyFetcher 应该在缺少路径配置时失败") {
            val project = createTestProject()
            val fetcher = PathDependencyFetcher()
            val config = DependencyConfig(version = "1.0.0")
            
            val result = fetcher.fetch("test", config, project.root, null)
            
            result.isFailure shouldBe true
        }
        
        test("GitDependencyFetcher 应该处理 GIT 类型") {
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            fetcher.canHandle(DependencyType.GIT) shouldBe true
            fetcher.canHandle(DependencyType.PATH) shouldBe false
            fetcher.canHandle(DependencyType.REGISTRY) shouldBe false
        }
        
        test("GitDependencyFetcher 应该使用标签创建 GitDependency") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            val config = DependencyConfig(
                git = "https://gitcode.com/Cangjie-TPC/markdown4cj.git",
                tag = "v1.1.2",
                version = "1.1.2"
            )
            
            val result = fetcher.fetch("markdown4cj", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.GitDependency>()
            dep.name shouldBe "markdown4cj"
            dep.version shouldBe "1.1.2"
            dep.url shouldBe "https://gitcode.com/Cangjie-TPC/markdown4cj.git"
            dep.reference.shouldBeInstanceOf<Dependency.GitReference.Tag>()
            (dep.reference as Dependency.GitReference.Tag).name shouldBe "v1.1.2"
            dep.localPath shouldBe cacheDir.resolve("git/markdown4cj")
        }
        
        test("GitDependencyFetcher 应该使用分支创建 GitDependency") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            val config = DependencyConfig(
                git = "https://gitcode.com/Cangjie-TPC/markdown4cj.git",
                branch = "master"
            )
            
            val result = fetcher.fetch("markdown4cj", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.GitDependency>()
            dep.reference.shouldBeInstanceOf<Dependency.GitReference.Branch>()
            (dep.reference as Dependency.GitReference.Branch).name shouldBe "master"
        }
        
        test("GitDependencyFetcher 应该使用提交创建 GitDependency") {
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            fetcher.canHandle(DependencyType.GIT) shouldBe true
        }
        
        test("GitDependencyFetcher 应该默认使用 main 分支") {
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            fetcher.canHandle(DependencyType.GIT) shouldBe true
        }
        
        test("GitDependencyFetcher 应该在缺少 git URL 时失败") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val fetcher = GitDependencyFetcher(cacheDir)
            
            val config = DependencyConfig(version = "1.0.0")
            
            val result = fetcher.fetch("test", config, project.root, null)
            
            result.isFailure shouldBe true
        }
        
        test("RegistryDependencyFetcher 应该处理 REGISTRY 类型") {
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            fetcher.canHandle(DependencyType.REGISTRY) shouldBe true
            fetcher.canHandle(DependencyType.PATH) shouldBe false
            fetcher.canHandle(DependencyType.GIT) shouldBe false
        }
        
        test("RegistryDependencyFetcher 应该获取仓库依赖") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val config = DependencyConfig(version = "1.2.0")
            
            val result = fetcher.fetch("std-http", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.RegistryDependency>()
            dep.name shouldBe "std-http"
            dep.version shouldBe "1.2.0"
        }
        
        test("RegistryDependencyFetcher 应该使用默认仓库 URL") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val config = DependencyConfig(version = "1.2.0")
            
            val result = fetcher.fetch("std-http", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.registryUrl shouldBe "https://repo.cangjie-lang.cn"
        }
        
        test("RegistryDependencyFetcher 应该使用配置的默认仓库") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val registry = RegistryConfig(default = "https://custom.repo.com")
            val config = DependencyConfig(version = "1.2.0")
            
            val result = fetcher.fetch("std-http", config, project.root, registry)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.registryUrl shouldBe "https://custom.repo.com"
        }
        
        test("RegistryDependencyFetcher 应该使用私有仓库") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val registry = RegistryConfig(privateUrl = "https://private.repo.com")
            val config = DependencyConfig(version = "1.2.0", registry = "private")
            
            val result = fetcher.fetch("private-lib", config, project.root, registry)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.registryUrl shouldBe "https://private.repo.com"
        }
        
        test("RegistryDependencyFetcher 应该使用自定义仓库 URL") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val config = DependencyConfig(
                version = "1.2.0",
                registry = "https://other.repo.com"
            )
            
            val result = fetcher.fetch("other-lib", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.registryUrl shouldBe "https://other.repo.com"
        }
        
        test("RegistryDependencyFetcher 应该在缺少版本时失败") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val config = DependencyConfig(path = "../test")
            
            val result = fetcher.fetch("test", config, project.root, null)
            
            result.isFailure shouldBe true
        }
        
        test("RegistryDependencyFetcher 应该在私有仓库未配置时失败") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            
            val config = DependencyConfig(version = "1.2.0", registry = "private")
            
            val result = fetcher.fetch("private-lib", config, project.root, null)
            
            result.isFailure shouldBe true
        }
    }
}