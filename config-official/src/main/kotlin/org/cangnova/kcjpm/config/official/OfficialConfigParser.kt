package org.cangnova.kcjpm.config.official

import net.peanuuutz.tomlkt.Toml
import org.cangnova.kcjpm.build.CompilationContext
import org.cangnova.kcjpm.build.CompilationTarget
import org.cangnova.kcjpm.config.*
import java.nio.file.Path
import kotlin.io.path.readText

class OfficialConfigParser : ConfigParser {
    
    private val toml = Toml {
        ignoreUnknownKeys = true
    }
    
    override fun getSupportedFormat(): ConfigFormat = ConfigFormat.OFFICIAL
    
    override fun loadConfig(configPath: Path): Result<CjpmConfig> = runCatching {
        val content = configPath.readText()
        val officialConfig = toml.decodeFromString(OfficialCjpmConfig.serializer(), content)
        convertToCustomConfig(officialConfig)
    }
    
    override fun loadFromProjectRoot(projectRoot: Path): Result<CjpmConfig> {
        val configPath = projectRoot.resolve(getConfigFileName())
        return loadConfig(configPath)
    }
    
    override fun loadAndConvert(
        projectRoot: Path,
        targetPlatform: CompilationTarget,
        profileName: String
    ): Result<CompilationContext> {
        return loadFromProjectRoot(projectRoot).mapCatching { config ->
            ConfigToContextConverter.convert(config, projectRoot, targetPlatform, profileName)
                .getOrThrow()
        }
    }
    
    override fun getConfigFileName(): String = "cjpm.toml"
    
    private fun convertToCustomConfig(official: OfficialCjpmConfig): CjpmConfig {
        return CjpmConfig(
            `package` = convertPackageInfo(official.`package`),
            dependencies = convertDependencies(official.dependencies),
            build = convertBuildConfig(official.`package`)
        )
    }
    
    private fun convertPackageInfo(official: OfficialPackageInfo): PackageInfo {
        return PackageInfo(
            name = official.name,
            version = official.version,
            cjcVersion = official.cjcVersion,
            outputType = convertOutputType(official.outputType),
            description = official.description.takeIf { it.isNotBlank() }
        )
    }
    
    private fun convertOutputType(outputType: String): OutputType {
        return when (outputType.lowercase()) {
            "executable", "exe" -> OutputType.EXECUTABLE
            "library", "lib" -> OutputType.LIBRARY
            "static", "static-library", "staticlib" -> OutputType.STATIC_LIBRARY
            "dynamic", "dynamic-library", "dylib" -> OutputType.DYNAMIC_LIBRARY
            else -> OutputType.EXECUTABLE
        }
    }
    
    private fun convertDependencies(
        official: Map<String, OfficialDependencyConfig>
    ): Map<String, DependencyConfig> {
        return official.mapValues { (_, dep) ->
            DependencyConfig(
                version = dep.version,
                path = dep.path,
                git = dep.git,
                tag = dep.tag,
                branch = dep.branch,
                commit = dep.commit
            )
        }
    }
    
    private fun convertBuildConfig(official: OfficialPackageInfo): BuildConfig? {
        val sourceDir = official.srcDir.takeIf { it.isNotBlank() } ?: return null
        val targetDir = official.targetDir.takeIf { it.isNotBlank() }
        
        return BuildConfig(
            sourceDir = sourceDir,
            outputDir = targetDir ?: "target"
        )
    }
}