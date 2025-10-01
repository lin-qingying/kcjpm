package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.test.BaseTest

class DependencyResolverTest : BaseTest() {
    init {
        test("应该检测路径依赖类型") {
            val project = createTestProject()
            val depDir = project.createDependency("local-lib")
            
            val fetcher = PathDependencyFetcher()
            val resolver = DefaultDependencyResolver(listOf(fetcher))
            
            val config = DependencyConfig(path = "../local-lib")
            val result = resolver.resolveSingle(
                "local-lib",
                config,
                project.root,
                null
            )
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.PathDependency>()
            dep.name shouldBe "local-lib"
        }
        
        test("应该检测 Git 依赖类型") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            
            val fetcher = GitDependencyFetcher(cacheDir)
            val resolver = DefaultDependencyResolver(listOf(fetcher))
            
            val config = DependencyConfig(
                git = "https://gitcode.com/Cangjie-TPC/markdown4cj.git",
                tag = "v1.1.2",
                version = "1.1.2"
            )
            
            val result = resolver.resolveSingle(
                "markdown4cj",
                config,
                project.root,
                null
            )
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.GitDependency>()
            dep.name shouldBe "markdown4cj"
            (dep as Dependency.GitDependency).url shouldBe "https://gitcode.com/Cangjie-TPC/markdown4cj.git"
        }
        
        test("应该检测仓库依赖类型") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            val resolver = DefaultDependencyResolver(listOf(fetcher))
            
            val config = DependencyConfig(version = "1.2.0")
            
            val result = resolver.resolveSingle(
                "std-http",
                config,
                project.root,
                null
            )
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.RegistryDependency>()
            dep.name shouldBe "std-http"
            (dep as Dependency.RegistryDependency).version shouldBe "1.2.0"
        }
        
        test("应该解析多个依赖") {
            val project = createTestProject()
            val depDir = project.createDependency("local-lib")
            val cacheDir = createTempDir("cache")
            
            val httpClient = MockDependencyHttpClient()
            val fetchers = listOf(
                PathDependencyFetcher(),
                GitDependencyFetcher(cacheDir),
                RegistryDependencyFetcher(cacheDir, httpClient)
            )
            val resolver = DefaultDependencyResolver(fetchers)
            
            val dependencies = mapOf(
                "local-lib" to DependencyConfig(path = "../local-lib"),
                "std-http" to DependencyConfig(version = "1.2.0")
            )
            
            val result = resolver.resolve(dependencies, project.root, null)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 2
            deps[0].name shouldBe "local-lib"
            deps[1].name shouldBe "std-http"
        }
        
        test("应该在找不到依赖类型的获取器时失败") {
            val project = createTestProject()
            val resolver = DefaultDependencyResolver(emptyList())
            
            val config = DependencyConfig(version = "1.0.0")
            val result = resolver.resolveSingle("test", config, project.root, null)
            
            result.isFailure shouldBe true
        }
        
        test("应该在依赖配置无效时失败") {
            val project = createTestProject()
            val resolver = DefaultDependencyResolver(listOf(PathDependencyFetcher()))
            
            val config = DependencyConfig()
            val result = resolver.resolveSingle("test", config, project.root, null)
            
            result.isFailure shouldBe true
        }
    }
}

class MockDependencyHttpClient : DependencyHttpClient {
    override fun download(url: String, targetDir: java.nio.file.Path): Result<Unit> = runCatching {
        targetDir.toFile().mkdirs()
    }
}