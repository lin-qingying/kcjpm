package org.cangnova.kcjpm.build

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.*

class IncrementalCacheTest : BaseTest() {
    init {
        test("缓存管理器应该能创建和加载空缓存") {
            val tempDir = createTempDir()
            val cacheManager = IncrementalCacheManager(tempDir)
            
            val cache = cacheManager.loadCache()
            cache.packages shouldBe emptyMap()
            cache.version shouldBe CompilationCache.CACHE_VERSION
        }
        
        test("缓存管理器应该能保存和加载缓存数据") {
            val tempDir = createTempDir()
            val cacheManager = IncrementalCacheManager(tempDir)
            
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val emptyCache = CompilationCache()
            val updatedCache = cacheManager.updatePackageCache(
                cache = emptyCache,
                packageInfo = packageInfo,
                outputPath = tempDir.resolve("test.a"),
                buildConfigHash = "test-hash"
            )
            
            cacheManager.saveCache(updatedCache)
            
            val loadedCache = cacheManager.loadCache()
            loadedCache.packages.size shouldBe 1
            loadedCache.packages["test"] shouldNotBe null
            loadedCache.packages["test"]?.packageName shouldBe "test"
            loadedCache.packages["test"]?.buildConfigHash shouldBe "test-hash"
        }
        
        test("文件变更检测器应该检测到首次编译") {
            val tempDir = createTempDir()
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, null, "hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.NoCacheFound>()
        }
        
        test("文件变更检测器应该检测到构建配置变更") {
            val tempDir = createTempDir()
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val cachedEntry = PackageCacheEntry(
                packageName = "test",
                packageRoot = tempDir.toString(),
                outputPath = tempDir.resolve("test.a").toString(),
                sourceFiles = mapOf(
                    sourceFile.toString() to FileMetadata(
                        path = sourceFile.toString(),
                        lastModified = sourceFile.getLastModifiedTime().toMillis(),
                        contentHash = computeFileHash(sourceFile),
                        size = sourceFile.fileSize()
                    )
                ),
                compilationTimestamp = System.currentTimeMillis(),
                buildConfigHash = "old-hash"
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, cachedEntry, "new-hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.BuildConfigChanged>()
        }
        
        test("文件变更检测器应该检测到文件修改") {
            val tempDir = createTempDir()
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val cachedEntry = PackageCacheEntry(
                packageName = "test",
                packageRoot = tempDir.toString(),
                outputPath = tempDir.resolve("test.a").toString(),
                sourceFiles = mapOf(
                    sourceFile.toString() to FileMetadata(
                        path = sourceFile.toString(),
                        lastModified = sourceFile.getLastModifiedTime().toMillis(),
                        contentHash = "old-hash",
                        size = sourceFile.fileSize()
                    )
                ),
                compilationTimestamp = System.currentTimeMillis(),
                buildConfigHash = "hash"
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, cachedEntry, "hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.FilesChanged>()
            val changedResult = result as ChangeDetectionResult.FilesChanged
            changedResult.modified.size shouldBe 1
        }
        
        test("文件变更检测器应该检测到文件新增") {
            val tempDir = createTempDir()
            val sourceFile1 = tempDir.resolve("test1.cj")
            sourceFile1.writeText("package test\n\nmain() {}")
            
            val sourceFile2 = tempDir.resolve("test2.cj")
            sourceFile2.writeText("package test\n\nfunc foo() {}")
            
            val outputPath = tempDir.resolve("test.a")
            outputPath.writeText("fake output")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile1, sourceFile2)
            )
            
            val cachedEntry = PackageCacheEntry(
                packageName = "test",
                packageRoot = tempDir.toString(),
                outputPath = outputPath.toString(),
                sourceFiles = mapOf(
                    sourceFile1.toString() to FileMetadata(
                        path = sourceFile1.toString(),
                        lastModified = sourceFile1.getLastModifiedTime().toMillis(),
                        contentHash = computeFileHash(sourceFile1),
                        size = sourceFile1.fileSize()
                    )
                ),
                compilationTimestamp = System.currentTimeMillis(),
                buildConfigHash = "hash"
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, cachedEntry, "hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.FilesChanged>()
            val changedResult = result as ChangeDetectionResult.FilesChanged
            changedResult.added.size shouldBe 1
        }
        
        test("文件变更检测器应该检测到输出文件缺失") {
            val tempDir = createTempDir()
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val outputPath = tempDir.resolve("test.a")
            
            val cachedEntry = PackageCacheEntry(
                packageName = "test",
                packageRoot = tempDir.toString(),
                outputPath = outputPath.toString(),
                sourceFiles = mapOf(
                    sourceFile.toString() to FileMetadata(
                        path = sourceFile.toString(),
                        lastModified = sourceFile.getLastModifiedTime().toMillis(),
                        contentHash = computeFileHash(sourceFile),
                        size = sourceFile.fileSize()
                    )
                ),
                compilationTimestamp = System.currentTimeMillis(),
                buildConfigHash = "hash"
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, cachedEntry, "hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.OutputMissing>()
        }
        
        test("文件变更检测器应该检测到无变更") {
            val tempDir = createTempDir()
            val sourceFile = tempDir.resolve("test.cj")
            sourceFile.writeText("package test\n\nmain() {}")
            
            val outputPath = tempDir.resolve("test.a")
            outputPath.writeText("fake output")
            
            val packageInfo = PackageInfo(
                name = "test",
                packageRoot = tempDir,
                sourceFiles = listOf(sourceFile)
            )
            
            val cachedEntry = PackageCacheEntry(
                packageName = "test",
                packageRoot = tempDir.toString(),
                outputPath = outputPath.toString(),
                sourceFiles = mapOf(
                    sourceFile.toString() to FileMetadata(
                        path = sourceFile.toString(),
                        lastModified = sourceFile.getLastModifiedTime().toMillis(),
                        contentHash = computeFileHash(sourceFile),
                        size = sourceFile.fileSize()
                    )
                ),
                compilationTimestamp = System.currentTimeMillis(),
                buildConfigHash = "hash"
            )
            
            val detector = FileChangeDetector()
            val result = detector.detectChanges(packageInfo, cachedEntry, "hash")
            
            result.shouldBeInstanceOf<ChangeDetectionResult.NoChanges>()
        }
        
        test("哈希计算应该一致") {
            val tempDir = createTempDir()
            val file = tempDir.resolve("test.txt")
            file.writeText("test content")
            
            val hash1 = computeFileHash(file)
            val hash2 = computeFileHash(file)
            
            hash1 shouldBe hash2
        }
        
        test("构建配置哈希应该一致") {
            val config = BuildConfig(
                target = CompilationTarget.LINUX_X64,
                optimizationLevel = OptimizationLevel.RELEASE,
                debugInfo = false,
                parallel = true
            )
            
            val hash1 = computeBuildConfigHash(config)
            val hash2 = computeBuildConfigHash(config)
            
            hash1 shouldBe hash2
        }
    }
}