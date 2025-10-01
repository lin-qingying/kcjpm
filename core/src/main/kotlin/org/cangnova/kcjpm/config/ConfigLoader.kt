package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import java.nio.file.Path

object ConfigLoader {
    private var parser: ConfigParser? = null
    
    fun setParser(configParser: ConfigParser) {
        parser = configParser
    }
    
    fun getParser(): ConfigParser {
        return parser ?: throw IllegalStateException("ConfigParser not initialized. Call setParser() first.")
    }
    
    fun loadConfig(configPath: Path): Result<CjpmConfig> {
        return getParser().loadConfig(configPath)
    }
    
    fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig> {
        return getParser().loadFromProjectRoot(projectRoot)
    }
    
    fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget = CompilationTarget.current(),
        profileName: String = "release"
    ): Result<CompilationContext> {
        return getParser().loadAndConvert(projectRoot, targetPlatform, profileName)
    }
}