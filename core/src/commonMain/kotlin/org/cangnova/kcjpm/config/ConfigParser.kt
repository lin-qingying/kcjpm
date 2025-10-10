package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import org.cangnova.kcjpm.platform.KPath

enum class ConfigFormat {
    OFFICIAL,
    CUSTOM
}

interface ConfigParser {
    fun getSupportedFormat(): ConfigFormat
    
    fun loadConfig(configPath: KPath): Result<CjpmConfig>
    
    fun loadFromProjectRoot(projectRoot: KPath): Result<CjpmConfig>
    
    fun loadAndConvert(
        projectRoot: KPath,
        targetPlatform: CompilationTarget? = null,
        profileName: String = "release"
    ): Result<CompilationContext>
    
    fun getConfigFileName(): String
}