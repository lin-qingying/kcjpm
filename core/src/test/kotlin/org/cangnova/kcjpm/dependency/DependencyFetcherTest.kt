package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.process.ProcessRunner
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.writeText

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
            val gitRepository = createLocalGitRepository()
            val fetcher = GitDependencyFetcher(cacheDir)
            
            val config = DependencyConfig(
                git = gitRepository.toUri().toString(),
                tag = "v1.1.2",
                version = "1.1.2"
            )
            
            val result = fetcher.fetch("local-git-lib", config, project.root, null)
            
            result.isSuccess shouldBe true
            val dep = result.getOrThrow()
            dep.shouldBeInstanceOf<Dependency.GitDependency>()
            dep.name shouldBe "local-git-lib"
            dep.version shouldBe "1.1.2"
            dep.url shouldBe gitRepository.toUri().toString()
            dep.reference.shouldBeInstanceOf<Dependency.GitReference.Tag>()
            (dep.reference as Dependency.GitReference.Tag).name shouldBe "v1.1.2"
            dep.localPath shouldBe cacheDir.resolve("git/local-git-lib")
        }
        
        test("GitDependencyFetcher 应该使用分支创建 GitDependency") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val gitRepository = createLocalGitRepository()
            val fetcher = GitDependencyFetcher(cacheDir)
            
            val config = DependencyConfig(
                git = gitRepository.toUri().toString(),
                branch = "master"
            )
            
            val result = fetcher.fetch("local-git-lib", config, project.root, null)
            
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

        test("RegistryDependencyFetcher 应该兼容字符串形式的中心仓 index-version") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient().also {
                it.indexText = """
                    {"name":"demo","version":"1.1.8","dependencies":[],"sha256sum":"mock-sha256-demo","yanked":false,"test-dependencies":[],"script-dependencies":[],"cjc-version":"1.0.1","index-version":"1"}
                """.trimIndent()
            }
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val result = fetcher.fetch("demo", DependencyConfig(version = "1.1.8"), project.root, null)

            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.version shouldBe "1.1.8"
            dep.checksum shouldBe "sha256:mock-sha256-demo"
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
            dep.registryUrl shouldBe CentralRepositoryDefaults.DEFAULT_REGISTRY_URL
            httpClient.requestedUrls shouldBe listOf(
                "${CentralRepositoryDefaults.DEFAULT_REGISTRY_URL}/index/st/d-/std-http",
                "${CentralRepositoryDefaults.DEFAULT_REGISTRY_URL}/pkg/std-http/1.2.0"
            )
        }

        test("RegistryDependencyFetcher 应该解析中心仓版本范围并选择最高可用版本") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val config = DependencyConfig(version = "[1.2.0, 2.0.0)")

            val result = fetcher.fetch("std-http", config, project.root, null)

            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.version shouldBe "1.3.0"
            dep.checksum shouldBe "sha256:mock-sha256-130"
            httpClient.requestedUrls.last() shouldBe "${CentralRepositoryDefaults.DEFAULT_REGISTRY_URL}/pkg/std-http/1.3.0"
        }

        test("RegistryDependencyFetcher 应该支持组织内制品") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val config = DependencyConfig(version = "4.0.0")

            val result = fetcher.fetch("org::dep4", config, project.root, null)

            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.name shouldBe "org::dep4"
            dep.version shouldBe "4.0.0"
            dep.cacheName shouldBe "org__dep4"
            dep.localPath shouldBe cacheDir
                .resolve("registry")
                .resolve(RegistryCacheLayout.registryScope(CentralRepositoryDefaults.DEFAULT_REGISTRY_URL))
                .resolve("org__dep4")
                .resolve("4.0.0")
            httpClient.requestedUrls shouldBe listOf(
                "${CentralRepositoryDefaults.DEFAULT_REGISTRY_URL}/index/de/p4/dep4?organization=org",
                "${CentralRepositoryDefaults.DEFAULT_REGISTRY_URL}/pkg/dep4/4.0.0?organization=org"
            )
        }

        test("RegistryDependencyFetcher 应该在默认仓失败后使用镜像") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient().also {
                it.failingTextUrlPrefixes.add("https://empty.repo/registry")
                it.indexText = """
                    {"organization":null,"name":"std-http","version":"1.2.0","sha256sum":"mock-sha256","yanked":false,"index-version":1}
                """.trimIndent()
            }
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val registry = RegistryConfig(
                default = "https://empty.repo/registry",
                mirrors = listOf("https://mirror.repo/registry")
            )
            val config = DependencyConfig(version = "1.2.0")

            val result = fetcher.fetch("std-http", config, project.root, registry)

            result.isSuccess shouldBe true
            val dep = result.getOrThrow() as Dependency.RegistryDependency
            dep.registryUrl shouldBe "https://mirror.repo/registry"
            httpClient.requestedUrls shouldBe listOf(
                "https://empty.repo/registry/index/st/d-/std-http",
                "https://mirror.repo/registry/index/st/d-/std-http",
                "https://mirror.repo/registry/pkg/std-http/1.2.0"
            )
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

        test("RegistryDependencyFetcher 不应该复用不同仓库的同名同版本缓存") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val defaultDep = fetcher.fetch(
                "std-http",
                DependencyConfig(version = "1.2.0"),
                project.root,
                null
            ).getOrThrow() as Dependency.RegistryDependency
            val customDep = fetcher.fetch(
                "std-http",
                DependencyConfig(version = "1.2.0"),
                project.root,
                RegistryConfig(default = "https://custom.repo.com")
            ).getOrThrow() as Dependency.RegistryDependency

            defaultDep.localPath shouldNotBe customDep.localPath
            defaultDep.localPath?.parent?.parent shouldNotBe customDep.localPath?.parent?.parent
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

        test("RegistryDependencyFetcher 应该将中心仓 404 归类为依赖不存在") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = object : DependencyHttpClient {
                override fun download(url: String, targetDir: Path): Result<Unit> =
                    Result.failure(UnsupportedOperationException("download is not used"))

                override fun getText(url: String, headers: Map<String, String>): Result<String> =
                    Result.failure(RuntimeException("Failed to read from $url: HTTP 404"))
            }
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)

            val result = fetcher.fetch(
                "missing-lib",
                DependencyConfig(version = "1.0.0"),
                project.root,
                null
            )

            result.isFailure shouldBe true
            result.exceptionOrNull()?.message shouldBe "Dependency not found in registry: missing-lib@1.0.0"
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

    private fun createLocalGitRepository(): Path {
        val repository = createTempDir("git-repository")
        runGit(repository, "init")
        runGit(repository, "config", "user.email", "kcjpm-test@example.com")
        runGit(repository, "config", "user.name", "KCJPM Test")

        repository.resolve("README.md").writeText("local git dependency")
        runGit(repository, "add", "README.md")
        runGit(repository, "commit", "-m", "Initial commit")
        runGit(repository, "branch", "-M", "master")
        runGit(repository, "tag", "v1.1.2")

        return repository
    }

    private fun runGit(repository: Path, vararg args: String) {
        val result = ProcessRunner.run(
            listOf("git") + args,
            workingDirectory = repository,
            timeout = Duration.ofSeconds(30)
        ).getOrThrow()

        require(result.exitCode == 0) {
            "git ${args.joinToString(" ")} failed: ${result.output}"
        }
    }
}
