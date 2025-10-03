package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import java.nio.file.Path
import java.util.ServiceLoader

object ConfigLoader {
    private val parsers = mutableMapOf<ConfigFormat, ConfigParser>()
    
    init {
        autoDiscoverParsers()
    }
    
    private fun autoDiscoverParsers() {
        val loader = ServiceLoader.load(ConfigParser::class.java)
        for (parser in loader) {
            val format = parser.getSupportedFormat()
            registerParser(format, parser)
        }
    }
    
    fun registerParser(format: ConfigFormat, parser: ConfigParser) {
        parsers[format] = parser
    }
    
    fun getParser(format: ConfigFormat): ConfigParser? {
        return parsers[format]
    }
    
    fun loadConfig(configPath: Path, format: ConfigFormat? = null): Result<CjpmConfig> {
        val detectedFormat = format ?: ConfigFormatDetector.detectFormat(configPath).getOrElse { 
            return Result.failure(it)
        }
        
        val parser = getParser(detectedFormat)
            ?: return Result.failure(
                IllegalStateException("未注册 ${detectedFormat.name} 格式的解析器")
            )
        
        return parser.loadConfig(configPath)
    }
    
    fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig> {
        return ConfigFormatDetector.detectFromProjectRoot(projectRoot).mapCatching { (format, configPath) ->
            val parser = getParser(format)
                ?: throw IllegalStateException("未注册 ${format.name} 格式的解析器")
            
            parser.loadConfig(configPath).getOrThrow()
        }
    }
    
    fun <T> loadAndConvert(
        configPath: Path,
        converter: (CjpmConfig) -> T,
        format: ConfigFormat? = null
    ): Result<T> {
        return loadConfig(configPath, format).map(converter)
    }
    
    fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget ? = null,
        profileName: String = "release"
    ): Result<CompilationContext> {
        return ConfigFormatDetector.detectFromProjectRoot(projectRoot).mapCatching { (format, configPath) ->
            val parser = getParser(format)
                ?: throw IllegalStateException("未注册 ${format.name} 格式的解析器")
            
            parser.loadAndConvert(projectRoot, targetPlatform, profileName).getOrThrow()
        }
    }
}