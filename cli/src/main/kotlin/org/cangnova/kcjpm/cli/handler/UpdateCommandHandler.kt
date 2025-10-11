package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.cli.i18n.Messages
import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.dependency.DefaultDependencyManager
import org.cangnova.kcjpm.dependency.DependencyManagerWithLock
import kotlin.io.path.Path
import kotlin.io.path.exists

class UpdateCommandHandler(output: OutputAdapter) : BaseCommandHandler(output) {
    
    override suspend fun handle(command: Command, options: GlobalOptions): Int {
        if (command !is Command.Update) {
            return handleError(Messages.get("error.invalidCommand"))
        }
        
        val projectPath = Path(command.path).toAbsolutePath()
        
        if (!projectPath.exists()) {
            return handleError(Messages.get("error.pathNotExist", projectPath))
        }
        
        return try {
            output.startProgress(Messages.get("update.loadingConfig"))
            val config = ConfigLoader.loadFromProjectRoot(projectPath).getOrThrow()
            output.completeProgress(Messages.get("update.configLoaded"))
            
            output.info(Messages.get("update.updating"))
            output.newline()
            
            val cacheDir = Path(System.getProperty("user.home")).resolve(".kcjpm").resolve("cache")
            val dependencyManager = DependencyManagerWithLock(
                DefaultDependencyManager(cacheDir)
            )
            
            if (command.dependency != null) {
                output.startProgress(Messages.get("update.updatingOne", command.dependency))
            } else {
                output.startProgress(Messages.get("update.updatingAll"))
            }
            
            val (dependencies, lockFile) = dependencyManager.updateDependencies(config, projectPath).getOrThrow()
            output.completeProgress(Messages.get("update.complete"))
            
            output.newline()
            output.success(Messages.get("update.success", dependencies.size))
            output.info("更新锁文件: kcjpm.lock (${lockFile.packages.size} 个包)")
            
            0
        } catch (e: Exception) {
            handleError(Messages.get("update.failed", e.message ?: "Unknown error"), e)
        }
    }
}