package org.cangnova.kcjpm.build

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

class CompilationManagerWarningTest : BaseTest() {
    
    private val bsonProjectPath = Path("D:\\code\\cangjie\\bson")
    
    init {
        test("应该能够捕获编译警告").config(enabled = isExternalBsonTestEnabled()) {
            val compilationManager = CompilationManager()
            
            val sourceFiles = listOf(
                bsonProjectPath.resolve("src/a/b.cj"),
                bsonProjectPath.resolve("src/b/b.cj"),
                bsonProjectPath.resolve("src/main.cj")
            )
            
            val context = createBsonCompilationContext(
                projectRoot = bsonProjectPath,
                sourceFiles = sourceFiles,
                outputPath = bsonProjectPath.resolve("target/test_warnings")
            )
            
            runBlocking {
                val result = with(context) { compilationManager.compile() }
                
                result.isSuccess shouldBe true
                
                val report = compilationManager.getReport()!!
                
                val packageReports = report.packages
                val totalWarnings = packageReports.sumOf { it.warnings.size }
                
                totalWarnings shouldBe 1
            }
        }
    }

    private fun isExternalBsonTestEnabled(): Boolean =
        System.getProperty("kcjpm.external.tests") == "true" && bsonProjectPath.exists()
    
    private fun createBsonCompilationContext(
        projectRoot: Path,
        sourceFiles: List<Path>,
        outputPath: Path
    ): CompilationContext {
        return object : CompilationContext {
            override val projectRoot = projectRoot
            override val buildConfig = BuildConfig(target = CompilationTarget.current())
            override val dependencies = emptyList<Dependency>()
            override val sourceFiles = sourceFiles
            override val outputPath = outputPath
            override val outputType = org.cangnova.kcjpm.config.OutputType.STATIC_LIBRARY
            
            override fun validate(): Result<Unit> = Result.success(Unit)
        }
    }
}
