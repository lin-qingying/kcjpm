package org.cangnova.kcjpm.build

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.config.OutputType
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path
import kotlin.io.path.Path

class BsonProjectCompilationTest : BaseTest() {
    
    private val compilationManager = CompilationManager()
    private val bsonProjectPath = Path("D:\\code\\cangjie\\bson")
    
    init {
        test("应该能够编译真实的 bson 项目") {
            val sourceFiles = listOf(
                bsonProjectPath.resolve("src/a/b.cj"),
                bsonProjectPath.resolve("src/b/b.cj"),
                bsonProjectPath.resolve("src/main.cj")
            )
            
            val context = createBsonCompilationContext(
                projectRoot = bsonProjectPath,
                sourceFiles = sourceFiles,
                outputPath = bsonProjectPath.resolve("target/bson")
            )
            
            runBlocking {
                val result = with(context) { compilationManager.compile() }
                result.isSuccess shouldBe true
            }
        }
    }
    
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