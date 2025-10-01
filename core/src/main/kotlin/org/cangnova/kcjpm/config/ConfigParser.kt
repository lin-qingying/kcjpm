package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import java.nio.file.Path

interface ConfigParser {
    fun loadConfig(configPath: Path): Result<CjpmConfig>
    
    fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig>
    
    fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget = CompilationTarget.current(),
        profileName: String = "release"
    ): Result<CompilationContext>
    
    fun getConfigFileName(): String
}