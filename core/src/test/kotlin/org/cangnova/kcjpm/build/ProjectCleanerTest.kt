package org.cangnova.kcjpm.build

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.writeText

class ProjectCleanerTest : BaseTest() {
    
    init {
        test("清理 target 目录") {
            val project = createTestProject("clean-test")
            val targetDir = project.root.resolve("target")
            Files.createDirectories(targetDir.resolve("debug"))
            Files.createDirectories(targetDir.resolve("release"))
            targetDir.resolve("debug/test.a").writeText("debug binary")
            targetDir.resolve("release/test.a").writeText("release binary")
            
            val cleaner = DefaultProjectCleaner()
            val result = cleaner.clean(project.root)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain targetDir
            report.freedSpace shouldBeGreaterThan 0L
            targetDir.exists() shouldBe false
        }
        
        test("只清理 debug 构建") {
            val project = createTestProject("clean-debug-only")
            val targetDir = project.root.resolve("target")
            Files.createDirectories(targetDir.resolve("debug"))
            Files.createDirectories(targetDir.resolve("release"))
            targetDir.resolve("debug/test.a").writeText("debug binary")
            targetDir.resolve("release/test.a").writeText("release binary")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(cleanDebugOnly = true)
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            val debugDir = targetDir.resolve("debug")
            report.deletedPaths shouldContain debugDir
            debugDir.exists() shouldBe false
            targetDir.resolve("release").exists() shouldBe true
        }
        
        test("清理覆盖率文件") {
            val project = createTestProject("clean-coverage")
            val covOutputDir = project.root.resolve("cov_output")
            Files.createDirectories(covOutputDir)
            covOutputDir.resolve("coverage.html").writeText("coverage report")
            
            project.root.resolve("test.gcno").writeText("gcov note")
            project.root.resolve("test.gcda").writeText("gcov data")
            
            val cleaner = DefaultProjectCleaner()
            val result = cleaner.clean(project.root)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain covOutputDir
            report.deletedPaths shouldContain project.root.resolve("test.gcno")
            report.deletedPaths shouldContain project.root.resolve("test.gcda")
            covOutputDir.exists() shouldBe false
            project.root.resolve("test.gcno").exists() shouldBe false
            project.root.resolve("test.gcda").exists() shouldBe false
        }
        
        test("不清理覆盖率文件") {
            val project = createTestProject("no-clean-coverage")
            val covOutputDir = project.root.resolve("cov_output")
            Files.createDirectories(covOutputDir)
            covOutputDir.resolve("coverage.html").writeText("coverage report")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(cleanCoverage = false)
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths.contains(covOutputDir) shouldBe false
            covOutputDir.exists() shouldBe true
        }
        
        test("清理构建脚本缓存") {
            val project = createTestProject("clean-build-cache")
            val buildCacheDir = project.root.resolve("build-script-cache")
            Files.createDirectories(buildCacheDir.resolve("debug"))
            buildCacheDir.resolve("debug/script-log").writeText("build log")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(cleanBuildCache = true)
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain buildCacheDir
            buildCacheDir.exists() shouldBe false
        }
        
        test("清理增量编译缓存") {
            val project = createTestProject("clean-incremental-cache")
            val targetDir = project.root.resolve("target")
            Files.createDirectories(targetDir.resolve("debug"))
            targetDir.resolve("debug/test.incremental.json").writeText("{}")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(cleanIncrementalCache = true)
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain targetDir.resolve("debug/test.incremental.json")
            targetDir.resolve("debug/test.incremental.json").exists() shouldBe false
        }
        
        test("干运行模式不删除文件") {
            val project = createTestProject("dry-run")
            val targetDir = project.root.resolve("target")
            Files.createDirectories(targetDir.resolve("debug"))
            targetDir.resolve("debug/test.a").writeText("debug binary")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(dryRun = true)
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain targetDir
            report.freedSpace shouldBeGreaterThan 0L
            targetDir.exists() shouldBe true
        }
        
        test("自定义 target 目录") {
            val project = createTestProject("custom-target")
            val customTargetDir = project.root.resolve("build")
            Files.createDirectories(customTargetDir.resolve("debug"))
            customTargetDir.resolve("debug/test.a").writeText("debug binary")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(targetDir = "build")
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain customTargetDir
            customTargetDir.exists() shouldBe false
        }
        
        test("项目不存在时返回错误") {
            val nonExistentPath = createTempDir("non-existent").resolve("missing")
            val cleaner = DefaultProjectCleaner()
            val result = cleaner.clean(nonExistentPath)
            
            result.isFailure shouldBe true
        }
        
        test("清理空项目不报错") {
            val project = createTestProject("empty-project")
            val cleaner = DefaultProjectCleaner()
            val result = cleaner.clean(project.root)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldHaveSize 0
            report.freedSpace shouldBe 0L
        }
        
        test("综合清理测试") {
            val project = createTestProject("comprehensive-clean")
            
            val targetDir = project.root.resolve("target")
            Files.createDirectories(targetDir.resolve("debug"))
            Files.createDirectories(targetDir.resolve("release"))
            targetDir.resolve("debug/test.a").writeText("debug binary")
            targetDir.resolve("debug/test.incremental.json").writeText("{}")
            targetDir.resolve("release/test.a").writeText("release binary")
            
            val covOutputDir = project.root.resolve("cov_output")
            Files.createDirectories(covOutputDir)
            covOutputDir.resolve("coverage.html").writeText("coverage report")
            
            project.root.resolve("test.gcno").writeText("gcov note")
            project.root.resolve("test.gcda").writeText("gcov data")
            
            val buildCacheDir = project.root.resolve("build-script-cache")
            Files.createDirectories(buildCacheDir.resolve("debug"))
            buildCacheDir.resolve("debug/script-log").writeText("build log")
            
            val cleaner = DefaultProjectCleaner()
            val options = CleanOptions(
                cleanCoverage = true,
                cleanBuildCache = true,
                cleanIncrementalCache = true
            )
            val result = cleaner.clean(project.root, options)
            
            result.isSuccess shouldBe true
            val report = result.getOrThrow()
            report.deletedPaths shouldContain targetDir
            report.deletedPaths shouldContain covOutputDir
            report.deletedPaths shouldContain buildCacheDir
            report.deletedPaths shouldContain project.root.resolve("test.gcno")
            report.deletedPaths shouldContain project.root.resolve("test.gcda")
            report.freedSpace shouldBeGreaterThan 0L
            
            targetDir.exists() shouldBe false
            covOutputDir.exists() shouldBe false
            buildCacheDir.exists() shouldBe false
            project.root.resolve("test.gcno").exists() shouldBe false
            project.root.resolve("test.gcda").exists() shouldBe false
        }
    }
}