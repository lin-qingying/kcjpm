package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.RegistryConfig
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.writeText

class CentralRepositorySettingsTest : BaseTest() {
    init {
        test("应该按官方格式读取项目内 cangjie-repo.toml") {
            val project = createTestProject()
            project.root.resolve("cangjie-repo.toml").writeText(
                """
                [repository.cache]
                path = ".central-cache"

                [repository.home]
                registry = "https://central.example.com/registry"
                token = "user-token"
                """.trimIndent()
            )

            val settings = CentralRepositorySettingsLoader.load(project.root).getOrThrow()

            settings.configPath shouldBe project.root.resolve("cangjie-repo.toml")
            settings.registryUrl shouldBe "https://central.example.com/registry"
            settings.token shouldBe "user-token"
            settings.cacheRoot shouldBe project.root.resolve(".central-cache").normalize()
            settings.repositoryCacheDir shouldBe project.root.resolve(".central-cache/repository").normalize()
        }

        test("依赖管理器工厂应该使用中心仓缓存目录") {
            val project = createTestProject()
            project.root.resolve("cangjie-repo.toml").writeText(
                """
                [repository.cache]
                path = "repo-cache"

                [repository.home]
                registry = "https://factory.example.com/registry"
                """.trimIndent()
            )

            val manager = DependencyManagerFactory.create(project.root, CjpmConfig()).getOrThrow()

            manager.getCacheDir() shouldBe project.root.resolve("repo-cache/repository").normalize()
        }

        test("未配置项目 registry 时应该使用中心仓配置解析依赖") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val resolver = DefaultDependencyResolver(listOf(RegistryDependencyFetcher(cacheDir, httpClient)))
            val manager = DefaultDependencyManager(
                cacheDir = cacheDir,
                resolver = resolver,
                centralRepositoryClient = CentralRepositoryClient(httpClient),
                defaultRegistryConfig = RegistryConfig(default = "https://central.example.com/registry")
            )
            val config = CjpmConfig(
                dependencies = mapOf("std-http" to DependencyConfig(version = "1.2.0"))
            )

            val dependencies = manager.resolveDependencies(config, project.root).getOrThrow()

            dependencies.size shouldBe 1
            val dependency = dependencies.single() as org.cangnova.kcjpm.build.Dependency.RegistryDependency
            dependency.registryUrl shouldBe "https://central.example.com/registry"
            httpClient.requestedUrls.first() shouldBe "https://central.example.com/registry/index/st/d-/std-http"
        }

        test("项目 registry 应该优先于中心仓默认配置") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            val httpClient = MockDependencyHttpClient()
            val resolver = DefaultDependencyResolver(listOf(RegistryDependencyFetcher(cacheDir, httpClient)))
            val manager = DefaultDependencyManager(
                cacheDir = cacheDir,
                resolver = resolver,
                centralRepositoryClient = CentralRepositoryClient(httpClient),
                defaultRegistryConfig = RegistryConfig(default = "https://central.example.com/registry")
            )
            val config = CjpmConfig(
                registry = RegistryConfig(default = "https://project.example.com/registry"),
                dependencies = mapOf("std-http" to DependencyConfig(version = "1.2.0"))
            )

            val dependencies = manager.resolveDependencies(config, project.root).getOrThrow()

            dependencies.size shouldBe 1
            val dependency = dependencies.single() as org.cangnova.kcjpm.build.Dependency.RegistryDependency
            dependency.registryUrl shouldBe "https://project.example.com/registry"
            httpClient.requestedUrls.first() shouldBe "https://project.example.com/registry/index/st/d-/std-http"
        }
    }
}
