package org.cangnova.kcjpm.lock

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.Clock
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.config.OutputType
import org.cangnova.kcjpm.config.PackageInfo
import org.cangnova.kcjpm.dependency.DefaultDependencyManager
import org.cangnova.kcjpm.dependency.DefaultDependencyResolver
import org.cangnova.kcjpm.dependency.DependencyManagerWithLock
import org.cangnova.kcjpm.dependency.MockDependencyHttpClient
import org.cangnova.kcjpm.dependency.RegistryDependencyFetcher
import org.cangnova.kcjpm.test.BaseTest
import org.cangnova.kcjpm.test.writeConfig
import kotlin.io.path.readText

class LockFileTest : BaseTest() {
    
    init {
        test("应该序列化和反序列化锁文件") {
            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "http-client",
                        version = "1.2.3",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                        checksum = "sha256:abc123",
                        dependencies = listOf("json-parser")
                    )
                )
            )
            
            val serializer = TomlLockFileSerializer()
            val content = serializer.serialize(lockFile)
            
            content shouldContain "version = 1"
            content shouldContain "http-client"
            content shouldContain "registry+https://pkg.cangjie-lang.cn/registry"
            
            val deserialized = serializer.deserialize(content).getOrThrow()
            
            deserialized.version shouldBe 1
            deserialized.packages shouldHaveSize 1
            deserialized.packages[0].name shouldBe "http-client"
        }
        
        test("应该解析Registry来源") {
            val source = PackageSource.parse("registry+https://pkg.cangjie-lang.cn/registry")
            
            source shouldBe PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
        }
        
        test("应该解析Path来源") {
            val source = PackageSource.parse("path+../local-lib")
            
            source shouldBe PackageSource.Path("../local-lib")
        }
        
        test("应该解析Git来源") {
            val source = PackageSource.parse("git+https://github.com/user/repo?tag=v1.0.0#abc123")
            
            source shouldBe PackageSource.Git(
                url = "https://github.com/user/repo",
                reference = PackageSource.GitReference.Tag("v1.0.0"),
                resolvedCommit = "abc123"
            )
        }
        
        test("应该从Dependency创建PackageSource") {
            val projectRoot = createTempDir("lock-test")
            
            val registryDep = Dependency.RegistryDependency(
                name = "test-pkg",
                version = "1.0.0",
                registryUrl = "https://pkg.cangjie-lang.cn/registry"
            )
            
            val source = PackageSource.fromDependency(registryDep, projectRoot)
            
            source shouldBe PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
        }
        
        test("应该生成锁文件") {
            val projectRoot = createTempDir("lock-gen")
            val depPath = projectRoot.resolve("dep")
            depPath.toFile().mkdirs()
            
            val dependencies = listOf(
                Dependency.PathDependency(
                    name = "local-lib",
                    version = "0.1.0",
                    path = depPath
                )
            )
            
            val generator = DefaultLockFileGenerator()
            val lockFile = generator.generate(projectRoot, dependencies).getOrThrow()
            
            lockFile.packages shouldHaveSize 1
            lockFile.packages[0].name shouldBe "local-lib"
            lockFile.packages[0].version shouldBe "0.1.0"
        }

        test("更新锁文件时 registry 来源变化应该重建锁定包") {
            val projectRoot = createTempDir("lock-registry-source-change")
            val existingLockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "std-http",
                        version = "1.2.0",
                        source = PackageSource.Registry("https://old.example.com/registry"),
                        checksum = "sha256:old"
                    )
                )
            )
            val dependencies = listOf(
                Dependency.RegistryDependency(
                    name = "std-http",
                    version = "1.2.0",
                    registryUrl = "https://new.example.com/registry",
                    checksum = "sha256:new"
                )
            )

            val updatedLockFile = DefaultLockFileGenerator()
                .update(projectRoot, existingLockFile, dependencies)
                .getOrThrow()

            val lockedPackage = updatedLockFile.findPackage("std-http")
            lockedPackage?.source shouldBe PackageSource.Registry("https://new.example.com/registry")
            lockedPackage?.checksum shouldBe "sha256:new"
        }
        
        test("应该写入和读取锁文件") {
            val projectRoot = createTempDir("lock-io")
            
            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "test-pkg",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    )
                )
            )
            
            LockFileIO.write(projectRoot, lockFile).getOrThrow()
            
            LockFileIO.exists(projectRoot) shouldBe true
            
            val lockFilePath = projectRoot.resolve("kcjpm.lock")
            lockFilePath.toFile().exists() shouldBe true
            
            val content = lockFilePath.readText()
            content shouldContain "test-pkg"
            
            val readLockFile = LockFileIO.read(projectRoot).getOrThrow()
            
            readLockFile.packages shouldHaveSize 1
            readLockFile.packages[0].name shouldBe "test-pkg"
        }
        
        test("应该验证锁文件") {
            val projectRoot = createTempDir("lock-validate")
            
            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "test-pkg",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    )
                )
            )
            
            val currentDeps = listOf(
                Dependency.RegistryDependency(
                    name = "test-pkg",
                    version = "1.0.0",
                    registryUrl = "https://pkg.cangjie-lang.cn/registry"
                )
            )
            
            val validator = DefaultLockFileValidator()
            val result = validator.validate(lockFile, projectRoot, currentDeps)
            
            result.isValid shouldBe true
            result.hasErrors shouldBe false
        }
        
        test("应该检测依赖不一致") {
            val projectRoot = createTempDir("lock-validate-inconsistent")
            
            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "old-pkg",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    )
                )
            )
            
            val currentDeps = listOf(
                Dependency.RegistryDependency(
                    name = "new-pkg",
                    version = "2.0.0",
                    registryUrl = "https://pkg.cangjie-lang.cn/registry"
                )
            )
            
            val validator = DefaultLockFileValidator()
            val result = validator.validate(lockFile, projectRoot, currentDeps)
            
            result.hasWarnings shouldBe true
            result.warnings.any { it.contains("配置文件中的依赖未在锁文件中") } shouldBe true
        }

        test("锁文件验证不应该将传递依赖误报为多余依赖") {
            val projectRoot = createTempDir("lock-validate-transitive")

            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "direct-lib",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                        dependencies = listOf("transitive-lib")
                    ),
                    LockedPackage(
                        name = "transitive-lib",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    )
                )
            )

            val currentDeps = listOf(
                Dependency.RegistryDependency(
                    name = "direct-lib",
                    version = "1.0.0",
                    registryUrl = "https://pkg.cangjie-lang.cn/registry"
                )
            )

            val validator = DefaultLockFileValidator()
            val result = validator.validate(lockFile, projectRoot, currentDeps)

            result.warnings.any { it.contains("锁文件中存在多余的依赖") } shouldBe false
        }
        
        test("应该找到锁定的包") {
            val lockFile = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "pkg-a",
                        version = "1.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    ),
                    LockedPackage(
                        name = "pkg-b",
                        version = "2.0.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry")
                    )
                )
            )
            
            lockFile.findPackage("pkg-a") shouldNotBe null
            lockFile.findPackage("pkg-a")?.version shouldBe "1.0.0"
            lockFile.findPackage("non-existent") shouldBe null
            
            lockFile.getAllPackageNames() shouldBe setOf("pkg-a", "pkg-b")
        }
        
        test("应该检测缺失和变化的依赖") {
            val projectRoot = createTempDir("lock-update")
            val cacheDir = projectRoot.resolve(".kcjpm/cache")
            
            val depPath1 = projectRoot.resolve("dep1")
            depPath1.toFile().mkdirs()
            depPath1.writeConfig(
                name = "dep1",
                version = "1.0.0"
            )
            
            val depPath2 = projectRoot.resolve("dep2")
            depPath2.toFile().mkdirs()
            depPath2.writeConfig(
                name = "dep2",
                version = "1.0.0"
            )
            
            projectRoot.writeConfig(
                name = "test-project",
                version = "1.0.0",
                dependencies = mapOf(
                    "dep1" to depPath1.toString()
                )
            )
            
            val config = ConfigLoader.loadFromProjectRoot(projectRoot).getOrThrow()
            val baseManager = DefaultDependencyManager(cacheDir)
            val managerWithLock = DependencyManagerWithLock(baseManager)
            
            val (deps1, lockFile1) = managerWithLock.updateDependencies(config, projectRoot).getOrThrow()
            
            deps1 shouldHaveSize 1
            deps1[0].name shouldBe "dep1"
            lockFile1.packages shouldHaveSize 1
            
            projectRoot.writeConfig(
                name = "test-project",
                version = "1.0.0",
                dependencies = mapOf(
                    "dep1" to depPath1.toString(),
                    "dep2" to depPath2.toString()
                )
            )
            
            val config2 = ConfigLoader.loadFromProjectRoot(projectRoot).getOrThrow()
            val (deps2, lockFile2) = managerWithLock.updateDependencies(config2, projectRoot).getOrThrow()
            
            deps2 shouldHaveSize 2
            lockFile2.packages shouldHaveSize 2
            lockFile2.findPackage("dep1") shouldNotBe null
            lockFile2.findPackage("dep2") shouldNotBe null
        }

        test("已有锁文件时中心仓版本范围应该使用锁定版本") {
            val projectRoot = createTempDir("lock-registry-range")
            val cacheDir = projectRoot.resolve(".kcjpm/cache")
            val httpClient = MockDependencyHttpClient()
            val resolver = DefaultDependencyResolver(
                listOf(RegistryDependencyFetcher(cacheDir, httpClient))
            )
            val baseManager = DefaultDependencyManager(cacheDir, resolver)
            val managerWithLock = DependencyManagerWithLock(baseManager)

            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "std-http" to DependencyConfig(version = "[1.0.0, 2.0.0)")
                )
            )
            projectRoot.writeConfig(config)

            val existingLock = LockFile(
                version = 1,
                metadata = LockMetadata(
                    generatedAt = Clock.System.now(),
                    kcjpmVersion = "0.1.0"
                ),
                packages = listOf(
                    LockedPackage(
                        name = "std-http",
                        version = "1.2.0",
                        source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                        checksum = "sha256:mock-sha256"
                    )
                )
            )
            LockFileIO.write(projectRoot, existingLock).getOrThrow()

            val (dependencies, lockFile) = managerWithLock.installWithLock(config, projectRoot).getOrThrow()

            dependencies shouldHaveSize 1
            dependencies.single().version shouldBe "1.2.0"
            lockFile.findPackage("std-http")?.version shouldBe "1.2.0"
        }

        test("已有锁文件存在多余包时不应该继续安装已删除依赖") {
            val projectRoot = createTempDir("lock-registry-removed")
            val cacheDir = projectRoot.resolve(".kcjpm/cache")
            val httpClient = MockDependencyHttpClient()
            val resolver = DefaultDependencyResolver(
                listOf(RegistryDependencyFetcher(cacheDir, httpClient))
            )
            val baseManager = DefaultDependencyManager(cacheDir, resolver)
            val managerWithLock = DependencyManagerWithLock(baseManager)

            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "std-http" to DependencyConfig(version = "1.2.0")
                )
            )

            LockFileIO.write(
                projectRoot,
                LockFile(
                    version = 1,
                    metadata = LockMetadata(
                        generatedAt = Clock.System.now(),
                        kcjpmVersion = "0.1.0"
                    ),
                    packages = listOf(
                        LockedPackage(
                            name = "std-http",
                            version = "1.2.0",
                            source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                            checksum = "sha256:mock-sha256"
                        ),
                        LockedPackage(
                            name = "other-lib",
                            version = "1.2.0",
                            source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                            checksum = "sha256:mock-sha256-other"
                        )
                    )
                )
            ).getOrThrow()

            val (dependencies, lockFile) = managerWithLock.installWithLock(config, projectRoot).getOrThrow()

            dependencies.map { it.name } shouldBe listOf("std-http")
            lockFile.findPackage("other-lib") shouldBe null
        }

        test("已有锁文件缺少新增依赖时应该解析新增依赖并更新锁文件") {
            val projectRoot = createTempDir("lock-registry-added")
            val cacheDir = projectRoot.resolve(".kcjpm/cache")
            val httpClient = MockDependencyHttpClient()
            val resolver = DefaultDependencyResolver(
                listOf(RegistryDependencyFetcher(cacheDir, httpClient))
            )
            val baseManager = DefaultDependencyManager(cacheDir, resolver)
            val managerWithLock = DependencyManagerWithLock(baseManager)

            val config = CjpmConfig(
                `package` = PackageInfo(
                    name = "test-project",
                    version = "1.0.0",
                    cjcVersion = "1.0.0",
                    outputType = OutputType.EXECUTABLE
                ),
                dependencies = mapOf(
                    "std-http" to DependencyConfig(version = "1.2.0"),
                    "other-lib" to DependencyConfig(version = "1.2.0")
                )
            )

            LockFileIO.write(
                projectRoot,
                LockFile(
                    version = 1,
                    metadata = LockMetadata(
                        generatedAt = Clock.System.now(),
                        kcjpmVersion = "0.1.0"
                    ),
                    packages = listOf(
                        LockedPackage(
                            name = "std-http",
                            version = "1.2.0",
                            source = PackageSource.Registry("https://pkg.cangjie-lang.cn/registry"),
                            checksum = "sha256:mock-sha256"
                        )
                    )
                )
            ).getOrThrow()

            val (dependencies, lockFile) = managerWithLock.installWithLock(config, projectRoot).getOrThrow()

            dependencies.map { it.name }.toSet() shouldBe setOf("std-http", "other-lib")
            lockFile.findPackage("other-lib")?.version shouldBe "1.2.0"
        }
    }
}
