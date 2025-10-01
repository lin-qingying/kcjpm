package org.cangnova.kcjpm.dependency

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.PackageInfo
import org.cangnova.kcjpm.test.BaseTest
import org.cangnova.kcjpm.test.writeConfig

class DependencyManagerTest : BaseTest() {
    init {
        test("应该解析直接依赖") {
            val project = createTestProject()
            val depDir = project.createDependency("local-lib")
            val cacheDir = createTempDir("cache")
            
            val manager = DefaultDependencyManager(cacheDir)
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "local-lib" to DependencyConfig(path = "../local-lib")
                )
            )
            
            val result = manager.resolveDependencies(config, project.root)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 1
            deps[0].name shouldBe "local-lib"
            deps[0].shouldBeInstanceOf<Dependency.PathDependency>()
        }
        
        test("应该解析多个直接依赖") {
            val project = createTestProject()
            val dep1 = project.createDependency("local-lib")
            val cacheDir = createTempDir("cache")
            
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            val resolver = DefaultDependencyResolver(
                listOf(PathDependencyFetcher(), fetcher)
            )
            val manager = DefaultDependencyManager(cacheDir, resolver)
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "local-lib" to DependencyConfig(path = "../local-lib"),
                    "std-http" to DependencyConfig(version = "1.2.0")
                )
            )
            
            val result = manager.resolveDependencies(config, project.root)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 2
            deps[0].name shouldBe "local-lib"
            deps[1].name shouldBe "std-http"
        }
        
        test("应该解析传递依赖") {
            val project = createTestProject()
            val depDir = project.createDependency("lib-a")
            val transDir = project.createDependency("lib-b")
            
            depDir.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-a",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    ),
                    dependencies = mapOf(
                        "lib-b" to DependencyConfig(path = "../lib-b")
                    )
                )
            )
            
            transDir.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-b",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    )
                )
            )
            
            val cacheDir = createTempDir("cache")
            val manager = DefaultDependencyManager(cacheDir)
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "lib-a" to DependencyConfig(path = "../lib-a")
                )
            )
            
            val result = manager.installDependencies(config, project.root)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 2
            deps.any { it.name == "lib-a" } shouldBe true
            deps.any { it.name == "lib-b" } shouldBe true
        }
        
        test("应该去重依赖") {
            val project = createTestProject()
            val libA = project.createDependency("lib-a")
            val libB = project.createDependency("lib-b")
            val shared = project.createDependency("shared")
            
            libA.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-a",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    ),
                    dependencies = mapOf(
                        "shared" to DependencyConfig(path = "../shared")
                    )
                )
            )
            
            libB.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-b",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    ),
                    dependencies = mapOf(
                        "shared" to DependencyConfig(path = "../shared")
                    )
                )
            )
            
            shared.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "shared",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    )
                )
            )
            
            val cacheDir = createTempDir("cache")
            val manager = DefaultDependencyManager(cacheDir)
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "lib-a" to DependencyConfig(path = "../lib-a"),
                    "lib-b" to DependencyConfig(path = "../lib-b")
                )
            )
            
            val result = manager.installDependencies(config, project.root)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 3
            deps.count { it.name == "shared" } shouldBe 1
        }
        
        test("应该检测版本冲突") {
            val project = createTestProject()
            val cacheDir = createTempDir("cache")
            
            val httpClient = MockDependencyHttpClient()
            val fetcher = RegistryDependencyFetcher(cacheDir, httpClient)
            val resolver = DefaultDependencyResolver(listOf(fetcher))
            val manager = DefaultDependencyManager(cacheDir, resolver)
            
            val libA = cacheDir.resolve("registry/lib-a/1.0.0")
            libA.toFile().mkdirs()
            libA.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-a",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    ),
                    dependencies = mapOf(
                        "shared" to DependencyConfig(version = "1.0.0")
                    )
                )
            )
            
            val libB = cacheDir.resolve("registry/lib-b/1.0.0")
            libB.toFile().mkdirs()
            libB.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "lib-b",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    ),
                    dependencies = mapOf(
                        "shared" to DependencyConfig(version = "2.0.0")
                    )
                )
            )
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "lib-a" to DependencyConfig(version = "1.0.0"),
                    "lib-b" to DependencyConfig(version = "1.0.0")
                )
            )
            
            val result = manager.installDependencies(config, project.root)
            
            result.isFailure shouldBe true
        }
        
        test("应该处理没有版本的依赖") {
            val project = createTestProject()
            val depDir = project.createDependency("local-lib")
            
            depDir.writeConfig(
                CjpmConfig(
                    `package` = PackageInfo(
                        name = "local-lib",
                        version = "1.0.0",
                        cjcVersion = "1.0.0",
                        outputType = org.cangnova.kcjpm.config.OutputType.LIBRARY
                    )
                )
            )
            
            val cacheDir = createTempDir("cache")
            val manager = DefaultDependencyManager(cacheDir)
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "local-lib" to DependencyConfig(path = "../local-lib")
                )
            )
            
            val result = manager.installDependencies(config, project.root)
            
            result.isSuccess shouldBe true
        }
        
        test("应该返回缓存目录") {
            val cacheDir = createTempDir("cache")
            val manager = DefaultDependencyManager(cacheDir)
            
            manager.getCacheDir() shouldBe cacheDir
        }
        
        test("应该清理缓存") {
            val cacheDir = createTempDir("cache")
            val testFile = cacheDir.resolve("test.txt")
            testFile.toFile().writeText("test")
            
            val manager = DefaultDependencyManager(cacheDir)
            val result = manager.clearCache()
            
            result.isSuccess shouldBe true
            cacheDir.toFile().exists() shouldBe false
        }
        
        test("应该处理缺失的传递配置") {
            val project = createTestProject()
            val depDir = project.createDependency("lib-a")
            
            val cacheDir = createTempDir("cache")
            val manager = DefaultDependencyManager(cacheDir)
            
            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = org.cangnova.kcjpm.config.OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "lib-a" to DependencyConfig(path = "../lib-a")
                )
            )
            
            val result = manager.installDependencies(config, project.root)
            
            result.isSuccess shouldBe true
            val deps = result.getOrThrow()
            deps.size shouldBe 1
        }
        
        test("DependencyGraph 拓扑排序应该返回正确顺序") {
            val graph = DependencyGraph(
                mapOf(
                    "a" to DependencyNode("a", "1.0.0", listOf("b", "c")),
                    "b" to DependencyNode("b", "1.0.0", listOf("c")),
                    "c" to DependencyNode("c", "1.0.0", emptyList())
                )
            )
            
            val result = graph.topologicalSort()
            
            result.isSuccess shouldBe true
            val order = result.getOrThrow()
            order.size shouldBe 3
            (order.indexOf("c") < order.indexOf("b")) shouldBe true
            (order.indexOf("b") < order.indexOf("a")) shouldBe true
        }
        
        test("DependencyGraph 拓扑排序应该检测循环") {
            val graph = DependencyGraph(
                mapOf(
                    "a" to DependencyNode("a", "1.0.0", listOf("b")),
                    "b" to DependencyNode("b", "1.0.0", listOf("c")),
                    "c" to DependencyNode("c", "1.0.0", listOf("a"))
                )
            )
            
            val result = graph.topologicalSort()
            
            result.isFailure shouldBe true
        }
        
        test("DependencyGraph 拓扑排序应该处理空图") {
            val graph = DependencyGraph(emptyMap())
            
            val result = graph.topologicalSort()
            
            result.isSuccess shouldBe true
            result.getOrThrow().isEmpty() shouldBe true
        }
        
        test("DependencyGraph 拓扑排序应该处理单节点") {
            val graph = DependencyGraph(
                mapOf(
                    "a" to DependencyNode("a", "1.0.0", emptyList())
                )
            )
            
            val result = graph.topologicalSort()
            
            result.isSuccess shouldBe true
            result.getOrThrow() shouldBe listOf("a")
        }
    }
}