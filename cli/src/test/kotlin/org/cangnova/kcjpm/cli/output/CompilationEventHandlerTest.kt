package org.cangnova.kcjpm.cli.output

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.cangnova.kcjpm.build.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.Path

class CompilationEventHandlerTest : FunSpec({
    
    fun captureOutput(handler: CompilationEventHandler, event: CompilationEvent): String {
        val originalOut = System.out
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        
        try {
            handler.onEvent(event)
            return outputStream.toString()
        } finally {
            System.setOut(originalOut)
        }
    }
    
    test("非详细模式下应该简洁地处理编译流水线开始事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = false)
        
        val event = PipelineStartedEvent(totalStages = 3)
        handler.onEvent(event)
        
        // 非详细模式下不应该显示任何信息
        mockOutput.infoMessages.size shouldBe 0
    }
    
    test("详细模式下应该显示编译流水线开始事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = true)
        
        val event = PipelineStartedEvent(totalStages = 3)
        handler.onEvent(event)
        
        mockOutput.infoMessages.size shouldBe 1
        mockOutput.infoMessages[0] shouldContain "开始编译流水线"
        mockOutput.infoMessages[0] shouldContain "3 个阶段"
    }
    
    test("非详细模式下不应该显示阶段进度") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = false)
        
        val startEvent = StageStartedEvent("源码发现", 0, 3)
        handler.onEvent(startEvent)
        
        val completeEvent = StageCompletedEvent("源码发现", 0, 3)
        handler.onEvent(completeEvent)
        
        // 非详细模式下不应该显示阶段进度
        mockOutput.progressStartMessages.size shouldBe 0
        mockOutput.progressCompleteMessages.size shouldBe 0
    }
    
    test("详细模式下应该显示阶段进度") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = true)
        
        val startEvent = StageStartedEvent("源码发现", 0, 3)
        handler.onEvent(startEvent)
        
        val completeEvent = StageCompletedEvent("源码发现", 0, 3)
        handler.onEvent(completeEvent)
        
        mockOutput.progressStartMessages.size shouldBe 1
        mockOutput.progressStartMessages[0] shouldContain "源码发现 [1/3]"
        mockOutput.progressCompleteMessages.size shouldBe 1
        mockOutput.progressCompleteMessages[0] shouldContain "源码发现 [1/3]"
    }
    
    test("非详细模式下应该简洁地处理包发现事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = false)
        
        val packageInfo = PackageDiscoveryInfo(
            name = "test-package",
            path = Path("src/test"),
            sourceFileCount = 5,
            sourceFiles = listOf(Path("src/test/main.cj"))
        )
        
        val event = PackageDiscoveryEvent(
            totalPackages = 1,
            packages = listOf(packageInfo)
        )
        
        handler.onEvent(event)
        
        // 非详细模式下只通过 updateProgress 显示总数
        mockOutput.progressUpdateMessages.size shouldBe 1
        mockOutput.progressUpdateMessages[0] shouldContain "发现 1 个包"
        mockOutput.infoMessages.size shouldBe 0
    }
    
    test("详细模式下应该详细显示包发现事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = true)
        
        val packageInfo = PackageDiscoveryInfo(
            name = "test-package",
            path = Path("src/test"),
            sourceFileCount = 5,
            sourceFiles = listOf(Path("src/test/main.cj"))
        )
        
        val event = PackageDiscoveryEvent(
            totalPackages = 1,
            packages = listOf(packageInfo)
        )
        
        handler.onEvent(event)
        
        mockOutput.infoMessages.size shouldBe 2
        mockOutput.infoMessages[0] shouldContain "发现 1 个包"
        mockOutput.infoMessages[1] shouldContain "test-package (5 个源文件)"
    }
    
    test("应该处理包编译成功事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = false)
        
        val startEvent = PackageCompilationStartedEvent("test-package", Path("src/test"))
        handler.onEvent(startEvent)
        
        val completeEvent = PackageCompilationCompletedEvent(
            packageName = "test-package",
            success = true,
            outputPath = Path("target/test-package.cjo"),
            errors = emptyList(),
            warnings = emptyList()
        )
        handler.onEvent(completeEvent)
        
        mockOutput.progressStartMessages.size shouldBe 1
        mockOutput.progressCompleteMessages.size shouldBe 1
        mockOutput.progressCompleteMessages[0] shouldContain "编译包 test-package -> test-package.cjo"
    }
    
    test("应该处理包编译失败事件") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = false)
        
        val error = CjcDiagnostic(
            severity = CjcDiagnostic.Severity.ERROR,
            message = "未定义的变量",
            file = "src/main.cj",
            line = 10,
            column = 5
        )
        
        val completeEvent = PackageCompilationCompletedEvent(
            packageName = "test-package",
            success = false,
            outputPath = null,
            errors = listOf(error),
            warnings = emptyList()
        )
        handler.onEvent(completeEvent)
        
        mockOutput.errorMessages.size shouldBe 3
        mockOutput.errorMessages[0] shouldContain "编译包 test-package 失败"
        mockOutput.errorMessages[1] shouldContain "错误详情"
        mockOutput.errorMessages[2] shouldContain "src/main.cj:10:5: 未定义的变量"
    }
    
    test("详细模式应该显示更多调试信息") {
        val mockOutput = MockOutputAdapter()
        val handler = CompilationEventHandler(mockOutput, verbose = true)
        
        val validationEvent = ValidationEvent("检查源文件", Path("src/main.cj"))
        handler.onEvent(validationEvent)
        
        val dependencyEvent = DependencyResolutionEvent(
            "正在解析依赖",
            dependencyName = "test-dep",
            dependencyType = "GIT"
        )
        handler.onEvent(dependencyEvent)
        
        mockOutput.debugMessages.size shouldBe 2
        mockOutput.debugMessages[0] shouldContain "验证: 检查源文件 (main.cj)"
        mockOutput.debugMessages[1] shouldContain "依赖解析: test-dep (GIT)"
    }
})

class MockOutputAdapter : OutputAdapter {
    val infoMessages = mutableListOf<String>()
    val successMessages = mutableListOf<String>()
    val warningMessages = mutableListOf<String>()
    val errorMessages = mutableListOf<String>()
    val debugMessages = mutableListOf<String>()
    val progressStartMessages = mutableListOf<String>()
    val progressCompleteMessages = mutableListOf<String>()
    val progressUpdateMessages = mutableListOf<String>()
    
    override fun info(message: String) { infoMessages.add(message) }
    override fun success(message: String) { successMessages.add(message) }
    override fun warning(message: String) { warningMessages.add(message) }
    override fun error(message: String) { errorMessages.add(message) }
    override fun debug(message: String) { debugMessages.add(message) }
    override fun progress(current: Int, total: Int, message: String) {}
    override fun startProgress(message: String) { progressStartMessages.add(message) }
    override fun updateProgress(message: String) { progressUpdateMessages.add(message) }
    override fun completeProgress(message: String) { progressCompleteMessages.add(message) }
    override fun newline() {}
}