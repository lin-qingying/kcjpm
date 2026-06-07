package org.cangnova.kcjpm.integration

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.cangnova.kcjpm.build.CompilationManager
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.config.toml.TomlConfigParser
import org.cangnova.kcjpm.test.BaseTest
import kotlin.io.path.Path
import kotlin.io.path.exists

class EndToEndCompilationTest : BaseTest() {



    private val compilationManager = CompilationManager()
    private val bsonProjectPath = Path("D:\\code\\cangjie\\bson")

    init {
        test("应该能够从 bson 项目路径加载配置并完成整个编译流程").config(enabled = isExternalBsonTestEnabled()) {
            val context = ConfigLoader.loadAndConvert(
                projectRoot = bsonProjectPath,
                profileName = "release"
            ).getOrThrow()

            runBlocking {
                val result = with(context) { compilationManager.compile() }
                result.isSuccess shouldBe true
            }
        }
    }

    private fun isExternalBsonTestEnabled(): Boolean =
        System.getProperty("kcjpm.external.tests") == "true" && bsonProjectPath.exists()
}
