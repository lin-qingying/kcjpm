package org.cangnova.kcjpm.build

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.cangnova.kcjpm.test.BaseTest
import java.nio.file.Path
import kotlin.io.path.Path

class BuildScriptTest : BaseTest() {
    
    init {
        test("应该解析链接库指令") {
            val line = "kcjpm:link-lib=ssl"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.LinkLibrary("ssl")
        }
        
        test("应该解析包含目录指令") {
            val line = "kcjpm:include-dir=include"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.IncludeDir(Path("/tmp/project/include"))
        }
        
        test("应该解析重新运行条件指令") {
            val line = "kcjpm:rerun-if-changed=build.cj"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.RerunIfChanged(Path("/tmp/project/build.cj"))
        }
        
        test("应该解析警告指令") {
            val line = "kcjpm:warning=This is a warning"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.Warning("This is a warning")
        }
        
        test("应该解析错误指令") {
            val line = "kcjpm:error=This is an error"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.Error("This is an error")
        }
        
        test("应该解析自定义标志") {
            val line = "kcjpm:custom-flag=value"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe BuildScriptInstruction.CustomFlag("custom-flag", "value")
        }
        
        test("不应该解析非kcjpm前缀的行") {
            val line = "regular output"
            val projectRoot = Path("/tmp/project")
            
            val instruction = BuildScriptInstruction.parse(line, projectRoot)
            
            instruction shouldBe null
        }
        
        test("应该生成环境变量") {
            val projectRoot = Path("tmp", "project")
            val outDir = Path("tmp", "out")
            
            val context = BuildScriptContext(
                projectRoot = projectRoot,
                outDir = outDir,
                target = CompilationTarget.LINUX_X64,
                profile = "debug",
                packageName = "test-package",
                packageVersion = "1.0.0"
            )
            
            val env = context.toEnvironmentVariables()
            
            env["KCJPM_OUT_DIR"] shouldBe outDir.toString()
            env["KCJPM_TARGET"] shouldBe "x86_64-unknown-linux-gnu"
            env["KCJPM_PROFILE"] shouldBe "debug"
            env["KCJPM_MANIFEST_DIR"] shouldBe projectRoot.toString()
            env["KCJPM_PKG_NAME"] shouldBe "test-package"
            env["KCJPM_PKG_VERSION"] shouldBe "1.0.0"
        }
        
        test("应该检测build.cj存在性") {
            val projectDir = createTempDir("build-script-detection")
            val buildScript = projectDir.resolve("build.cj")
            buildScript.toFile().writeText("main(): Int64 { return 0 }")
            
            val compiler = DefaultBuildScriptCompiler()
            val detected = compiler.detectBuildScript(projectDir)
            
            detected shouldBe buildScript
        }
        
        test("不存在build.cj时应该返回null") {
            val projectDir = createTempDir("no-build-script")
            
            val compiler = DefaultBuildScriptCompiler()
            val detected = compiler.detectBuildScript(projectDir)
            
            detected shouldBe null
        }
        
        test("BuildScriptResult应该正确识别错误") {
            val resultWithErrors = BuildScriptResult(
                errors = listOf("error 1", "error 2")
            )
            
            resultWithErrors.hasErrors shouldBe true
            
            val resultNoErrors = BuildScriptResult()
            
            resultNoErrors.hasErrors shouldBe false
        }
        
        test("应该聚合多个指令到BuildScriptResult") {
            val result = BuildScriptResult(
                linkLibraries = listOf("ssl", "crypto"),
                includeDirs = listOf(Path("/usr/include"), Path("/opt/include")),
                warnings = listOf("warning1"),
                errors = emptyList()
            )
            
            result.linkLibraries shouldHaveSize 2
            result.linkLibraries shouldContain "ssl"
            result.linkLibraries shouldContain "crypto"
            result.includeDirs shouldHaveSize 2
            result.warnings shouldHaveSize 1
            result.hasErrors shouldBe false
        }
    }
}