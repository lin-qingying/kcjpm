package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.writeText

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
            
            val fetcher = FakeGitDependencyFetcher()
            val resolver = DefaultDependencyResolver(listOf(fetcher))
            
            val config = DependencyConfig(
                git = "https://example.com/markdown4cj.git",
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
            (dep as Dependency.GitDependency).url shouldBe "https://example.com/markdown4cj.git"
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

private class FakeGitDependencyFetcher : DependencyFetcher {
    override fun canHandle(type: DependencyType): Boolean = type == DependencyType.GIT

    override fun fetch(
        name: String,
        config: DependencyConfig,
        projectRoot: java.nio.file.Path,
        registry: RegistryConfig?
    ): Result<Dependency> = runCatching {
        Dependency.GitDependency(
            name = name,
            version = config.version,
            url = config.git ?: error("git is required"),
            reference = Dependency.GitReference.Tag(config.tag ?: "v1.0.0")
        )
    }
}

class MockDependencyHttpClient : DependencyHttpClient {
    val requestedUrls: MutableList<String> = mutableListOf()
    val failingTextUrlPrefixes: MutableSet<String> = mutableSetOf()

    var indexText: String = """
        {"organization":null,"name":"std-http","version":"1.2.0","sha256sum":"mock-sha256","yanked":false,"index-version":1}
        {"organization":null,"name":"std-http","version":"1.3.0","sha256sum":"mock-sha256-130","yanked":false,"index-version":1}
        {"organization":null,"name":"other-lib","version":"1.2.0","sha256sum":"mock-sha256-other","yanked":false,"index-version":1}
        {"organization":null,"name":"private-lib","version":"1.2.0","sha256sum":"mock-sha256-private","yanked":false,"index-version":1}
        {"organization":null,"name":"lib-a","version":"1.0.0","sha256sum":"mock-sha256-lib-a","yanked":false,"index-version":1}
        {"organization":null,"name":"lib-b","version":"1.0.0","sha256sum":"mock-sha256-lib-b","yanked":false,"index-version":1}
        {"organization":null,"name":"shared","version":"1.0.0","sha256sum":"mock-sha256-shared-100","yanked":false,"index-version":1}
        {"organization":null,"name":"shared","version":"2.0.0","sha256sum":"mock-sha256-shared-200","yanked":false,"index-version":1}
        {"organization":"org","name":"dep4","version":"4.0.0","sha256sum":"mock-sha256-org","yanked":false,"index-version":1}
    """.trimIndent()

    override fun download(url: String, targetDir: java.nio.file.Path): Result<Unit> = runCatching {
        requestedUrls.add(url)
        targetDir.toFile().mkdirs()
    }

    override fun getText(url: String, headers: Map<String, String>): Result<String> = runCatching {
        requestedUrls.add(url)
        if (failingTextUrlPrefixes.any { url.startsWith(it) }) {
            throw RuntimeException("mock index not found: $url")
        }
        indexText
    }

    override fun downloadToFile(
        url: String,
        targetFile: java.nio.file.Path,
        headers: Map<String, String>
    ): Result<Unit> = runCatching {
        requestedUrls.add(url)
        targetFile.writeText("mock archive")
    }

    override fun verifySha256(file: java.nio.file.Path, expectedSha256: String): Result<Unit> = runCatching {
    }

    override fun extractArchive(archiveFile: java.nio.file.Path, targetDir: java.nio.file.Path): Result<Unit> = runCatching {
        targetDir.toFile().mkdirs()
        targetDir.resolve("cjpm.toml").writeText(
            """
            [package]
            name = "mock"
            version = "1.0.0"
            cjc-version = "1.0.0"
            output-type = "library"
            """.trimIndent()
        )
    }
}
