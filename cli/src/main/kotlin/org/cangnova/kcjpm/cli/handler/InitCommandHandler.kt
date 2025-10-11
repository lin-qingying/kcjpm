package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.init.DefaultProjectInitializer
import org.cangnova.kcjpm.init.InitOptions
import org.cangnova.kcjpm.init.OutputType
import kotlin.io.path.Path

class InitCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {

    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Init) {
            return handleError(Messages.get("error.invalidCommand"))
        }

        val targetPath = Path(command.path).toAbsolutePath()

        output.info(Messages.get("init.initializing", command.name))
        output.info(Messages.get("init.path", targetPath))
        output.info(Messages.get("init.template", command.template))
        output.newline()

        return try {
            val initializer = DefaultProjectInitializer()

            output.startProgress(Messages.get("init.loadingTemplate"))
            val template = initializer.getTemplate(command.template).getOrThrow()
            output.completeProgress(Messages.get("init.templateLoaded"))

            val initOptions = InitOptions(
                projectName = command.name,
                outputType = if (command.lib) OutputType.LIBRARY else OutputType.EXECUTABLE
            )

            output.startProgress(Messages.get("init.creating"))
            val result = initializer.initProject(targetPath, template, initOptions).getOrThrow()
            output.completeProgress(Messages.get("init.created"))

            output.newline()
            output.success(Messages.get("init.success", command.name))
            output.info(Messages.get("init.filesCreated", result.createdFiles.size))
            result.createdFiles.forEach { file ->
                output.info("  - ${file.fileName}")
            }

            output.newline()
            output.info(Messages.get("init.nextSteps"))
            output.info("  cd ${command.path}")
            output.info("  kcjpm build")

            0
        } catch (e: Exception) {
            handleError(Messages.get("init.failed", e.message ?: "Unknown error"), e)
        }
    }
}