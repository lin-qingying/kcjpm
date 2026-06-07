package org.cangnova.kcjpm.config

import net.peanuuutz.tomlkt.Toml
import java.nio.file.Path
import kotlin.io.path.writeText

object ConfigModifier {

    private val toml = Toml {}
    
    fun addDependency(
        config: CjpmConfig,
        name: String,
        dependencyConfig: DependencyConfig
    ): CjpmConfig {
        return config.copy(
            dependencies = config.dependencies + (name to dependencyConfig)
        )
    }
    
    fun saveConfig(config: CjpmConfig, configFilePath: Path) {
        val content = serializeToToml(config)
        configFilePath.writeText(content)
    }
    
    private fun serializeToToml(config: CjpmConfig): String =
        toml.encodeToString(CjpmConfig.serializer(), config).trimEnd()
}
