package org.cangnova.kcjpm.lock

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.Clock
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.dependency.DefaultDependencyManager
import org.cangnova.kcjpm.dependency.DependencyManagerWithLock
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
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn"),
                        checksum = "sha256:abc123",
                        dependencies = listOf("json-parser")
                    )
                )
            )
            
            val serializer = TomlLockFileSerializer()
            val content = serializer.serialize(lockFile)
            
            content shouldContain "version = 1"
            content shouldContain "http-client"
            content shouldContain "registry+https://repo.cangjie-lang.cn"
            
            val deserialized = serializer.deserialize(content).getOrThrow()
            
            deserialized.version shouldBe 1
            deserialized.packages shouldHaveSize 1
            deserialized.packages[0].name shouldBe "http-client"
        }
        
        test("应该解析Registry来源") {
            val source = PackageSource.parse("registry+https://repo.cangjie-lang.cn")
            
            source shouldBe PackageSource.Registry("https://repo.cangjie-lang.cn")
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
                registryUrl = "https://repo.cangjie-lang.cn"
            )
            
            val source = PackageSource.fromDependency(registryDep, projectRoot)
            
            source shouldBe PackageSource.Registry("https://repo.cangjie-lang.cn")
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
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn")
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
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn")
                    )
                )
            )
            
            val currentDeps = listOf(
                Dependency.RegistryDependency(
                    name = "test-pkg",
                    version = "1.0.0",
                    registryUrl = "https://repo.cangjie-lang.cn"
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
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn")
                    )
                )
            )
            
            val currentDeps = listOf(
                Dependency.RegistryDependency(
                    name = "new-pkg",
                    version = "2.0.0",
                    registryUrl = "https://repo.cangjie-lang.cn"
                )
            )
            
            val validator = DefaultLockFileValidator()
            val result = validator.validate(lockFile, projectRoot, currentDeps)
            
            result.hasWarnings shouldBe true
            result.warnings.any { it.contains("配置文件中的依赖未在锁文件中") } shouldBe true
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
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn")
                    ),
                    LockedPackage(
                        name = "pkg-b",
                        version = "2.0.0",
                        source = PackageSource.Registry("https://repo.cangjie-lang.cn")
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
    }
}