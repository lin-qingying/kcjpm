package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.build.DefaultProjectCleaner
import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import kotlin.io.path.Path
import kotlin.io.path.exists

class CleanCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Clean) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        output.info(Messages.get("clean.cleaning", projectPath))
        output.newline()
        
        return try {
            val cleaner = DefaultProjectCleaner()
            
            output.startProgress(Messages.get("clean.progress"))
            val result = cleaner.clean(projectPath).getOrThrow()
            output.completeProgress(Messages.get("clean.complete"))
            
            output.newline()
            output.success(Messages.get("clean.deleted", result.deletedPaths.size))
            output.info(Messages.get("clean.freedSpace", formatBytes(result.freedSpace)))
            
            if (options.verbose && result.deletedPaths.isNotEmpty()) {
                output.newline()
                output.info(Messages.get("clean.deletedFiles"))
                result.deletedPaths.take(10).forEach { path ->
                    output.info("  - ${path.fileName}")
                }
                if (result.deletedPaths.size > 10) {
                    output.info(Messages.get("clean.moreFiles", result.deletedPaths.size - 10))
                }
            }
            
            0
        } catch (e: Exception) {
            handleError(Messages.get("clean.failed", e.message ?: "Unknown error"), e)
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}