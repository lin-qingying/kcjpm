package org.cangnova.kcjpm.cli.parser

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CommandParserTest : FunSpec({
    test("add 命令应该区分项目路径和本地依赖路径") {
        val result = CommandParser().parse(
            arrayOf("add", "local-lib", "--project", "demo", "--path", "../local-lib")
        )

        result.shouldBeInstanceOf<ParseResult.Success>()
        val command = result.command.shouldBeInstanceOf<Command.Add>()
        command.path shouldBe "demo"
        command.dependency shouldBe "local-lib"
        command.localPath shouldBe "../local-lib"
    }

    test("run 命令应该解析项目路径和程序参数") {
        val result = CommandParser().parse(
            arrayOf("run", "demo", "--", "--flag", "value")
        )

        result.shouldBeInstanceOf<ParseResult.Success>()
        val command = result.command.shouldBeInstanceOf<Command.Run>()
        command.path shouldBe "demo"
        command.args shouldBe listOf("--flag", "value")
    }

    test("update 命令应该支持指定项目路径") {
        val result = CommandParser().parse(
            arrayOf("update", "std-http", "--project", "demo")
        )

        result.shouldBeInstanceOf<ParseResult.Success>()
        val command = result.command.shouldBeInstanceOf<Command.Update>()
        command.path shouldBe "demo"
        command.dependency shouldBe "std-http"
    }
})
