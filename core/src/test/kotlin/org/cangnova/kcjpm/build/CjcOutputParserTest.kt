package org.cangnova.kcjpm.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CjcOutputParserTest : FunSpec({
    val parser = CjcOutputParser()

    test("解析 cjc 多行错误格式") {
        val lines = listOf(
            "error: 'main' is missing",
            " ==> D:\\code\\cangjie\\bson\\src\\a\\b.cj:1:1:",
            "  |",
            "1 | package bson.a",
            "  | ^",
            "  |",
            "",
            "1 error generated, 1 error printed."
        )

        val events = mutableListOf<CjcOutputEvent>()
        for (line in lines) {
            parser.parseLine(line, true)?.let { events.add(it) }
        }

        val errorEvent = events.find { it is CjcOutputEvent.CompilationError }
        errorEvent.shouldBeInstanceOf<CjcOutputEvent.CompilationError>()
        errorEvent.error.severity shouldBe CjcDiagnostic.Severity.ERROR
        errorEvent.error.file shouldBe "D:\\code\\cangjie\\bson\\src\\a\\b.cj"
        errorEvent.error.line shouldBe 1
        errorEvent.error.column shouldBe 1
        errorEvent.error.message shouldBe "'main' is missing"
    }

    test("解析 cjc 多行警告格式") {
        val lines = listOf(
            "warning: unused function:'name'",
            " ==> D:\\code\\cangjie\\bson\\src\\b\\b.cj:3:1:",
            "  |",
            "3 |   func name() {",
            "  |  _^",
            "4 | |",
            "5 | | }",
            "  | |_^ unused function",
            "  |",
            "  # note: this warning can be suppressed by setting the compiler option `-Woff unused`"
        )

        val events = mutableListOf<CjcOutputEvent>()
        for (line in lines) {
            parser.parseLine(line, false)?.let { events.add(it) }
        }

        val warningEvent = events.find { it is CjcOutputEvent.CompilationWarning }
        warningEvent.shouldBeInstanceOf<CjcOutputEvent.CompilationWarning>()
        warningEvent.warning.severity shouldBe CjcDiagnostic.Severity.WARNING
        warningEvent.warning.file shouldBe "D:\\code\\cangjie\\bson\\src\\b\\b.cj"
        warningEvent.warning.line shouldBe 3
        warningEvent.warning.column shouldBe 1
        warningEvent.warning.message shouldBe "unused function:'name'"
    }

    test("解析简单警告信息") {
        val line = "warning: optimization disabled"
        val event = parser.parseLine(line, false)

        event.shouldBeInstanceOf<CjcOutputEvent.RawOutput>()
    }

    test("解析编译进度信息") {
        val line = "Compiling package `mylib`"
        val event = parser.parseLine(line, false)

        event.shouldBeInstanceOf<CjcOutputEvent.CompilationProgress>()
        event.message shouldBe "Compiling package: mylib"
    }

    test("解析警告统计信息") {
        val line = "1 warning generated, 1 warning printed."
        val event = parser.parseLine(line, false)

        event.shouldBeInstanceOf<CjcOutputEvent.RawOutput>()
    }

    test("解析原始输出") {
        val line = "Some random output"
        val event = parser.parseLine(line, false)

        event.shouldBeInstanceOf<CjcOutputEvent.RawOutput>()
        event.line shouldBe "Some random output"
        event.isError shouldBe false
    }

    test("解析多个包编译输出") {
        val lines = sequenceOf(
            "Compiling package `bson`: cjc.exe ...",
            "Compiling package `bson.b`: cjc.exe ...",
            "warning: unused function:'name'",
            " ==> D:\\code\\cangjie\\bson\\src\\b\\b.cj:3:1:",
            "1 warning generated, 1 warning printed.",
            "Compiling package `bson.a`: cjc.exe ..."
        )

        val events = mutableListOf<CjcOutputEvent>()
        for (line in lines) {
            parser.parseLine(line, false)?.let { events.add(it) }
        }

        val progressEvents = events.filterIsInstance<CjcOutputEvent.CompilationProgress>()
        progressEvents.size shouldBe 3

        val warningEvents = events.filterIsInstance<CjcOutputEvent.CompilationWarning>()
        warningEvents.size shouldBe 1
    }

    test("解析 Windows 路径格式") {
        val lines = listOf(
            "warning: unused variable",
            " ==> D:\\projects\\src\\main.cj:15:8:"
        )

        val events = lines.map { parser.parseLine(it, false) }
        val warningEvent = events.find { it is CjcOutputEvent.CompilationWarning }
        
        warningEvent.shouldBeInstanceOf<CjcOutputEvent.CompilationWarning>()
        warningEvent.warning.file shouldBe "D:\\projects\\src\\main.cj"
        warningEvent.warning.line shouldBe 15
        warningEvent.warning.column shouldBe 8
    }

    test("解析 Linux 路径格式") {
        val lines = listOf(
            "error: type mismatch",
            " ==> /home/user/project/src/main.cj:20:3:"
        )

        val events = lines.map { parser.parseLine(it, true) }
        val errorEvent = events.find { it is CjcOutputEvent.CompilationError }
        
        errorEvent.shouldBeInstanceOf<CjcOutputEvent.CompilationError>()
        errorEvent.error.file shouldBe "/home/user/project/src/main.cj"
        errorEvent.error.line shouldBe 20
        errorEvent.error.column shouldBe 3
    }
})