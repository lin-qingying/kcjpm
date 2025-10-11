package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.config.ConfigLoader
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AddCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Add) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        output.info(Messages.get("add.adding", command.dependency))
        
        return try {
            output.startProgress(Messages.get("add.loadingConfig"))
            val configFile = projectPath.resolve("cjpm.toml")
            if (!configFile.exists()) {
                return handleError(Messages.get("add.configNotFound"))
            }
            
            val config = ConfigLoader.loadConfig(configFile).getOrThrow()
            output.completeProgress(Messages.get("add.configLoaded"))
            
            val newDependency = when {
                command.git != null -> {
                    buildString {
                        append("${command.dependency} = { git = \"${command.git}\"")
                        if (command.tag != null) append(", tag = \"${command.tag}\"")
                        else if (command.branch != null) append(", branch = \"${command.branch}\"")
                        append(" }")
                    }
                }
                command.localPath != null -> {
                    "${command.dependency} = { path = \"${command.localPath}\" }"
                }
                else -> {
                    return handleError(Messages.get("add.sourceRequired"))
                }
            }
            
            output.startProgress(Messages.get("add.updatingConfig"))
            val configContent = configFile.toFile().readText()
            val updatedContent = addDependencyToConfig(configContent, newDependency)
            configFile.writeText(updatedContent)
            output.completeProgress(Messages.get("add.configUpdated"))
            
            output.newline()
            output.success(Messages.get("add.success", command.dependency))
            output.info(Messages.get("add.buildHint"))
            
            0
        } catch (e: Exception) {
            handleError(Messages.get("add.failed", e.message ?: "Unknown error"), e)
        }
    }
    
    private fun addDependencyToConfig(content: String, dependency: String): String {
        val lines = content.lines().toMutableList()
        
        val dependenciesIndex = lines.indexOfFirst { it.trim().startsWith("[dependencies]") }
        
        if (dependenciesIndex == -1) {
            lines.add("")
            lines.add("[dependencies]")
            lines.add(dependency)
        } else {
            var insertIndex = dependenciesIndex + 1
            while (insertIndex < lines.size && !lines[insertIndex].trim().startsWith("[")) {
                insertIndex++
            }
            lines.add(insertIndex, dependency)
        }
        
        return lines.joinToString("\n")
    }
}