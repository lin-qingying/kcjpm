package org.cangnova.kcjpm.config

import org.cangnova.kcjpm.build.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

object ConfigToContextConverter {
    fun convert(
        config: CjpmConfig,
        projectRoot: Path,
        targetPlatform: CompilationTarget? = null,
        profileName: String = "release"
    ): Result<CompilationContext> = runCatching {
        val profile = config.profile[profileName] ?: getDefaultProfile(profileName)
        val buildConfig = createBuildConfig(config, targetPlatform, profile)
        val dependencies = parseDependencies(config.dependencies, projectRoot, config.registry)
        val sourceDir = config.build?.sourceDir ?: "src"
        val sourceFiles = discoverSourceFiles(projectRoot, config.build)
        val outputPath = determineOutputPath(projectRoot, config.build, profileName)
        
        DefaultCompilationContext(
            projectRoot = projectRoot,
            buildConfig = buildConfig,
            dependencies = dependencies,
            sourceFiles = sourceFiles,
            outputPath = outputPath,
            outputType = config.`package`.outputType,
            sourceDir = sourceDir
        )
    }
    
    private fun getDefaultProfile(profileName: String): ProfileConfig {
        return when (profileName) {
            "debug" -> ProfileConfig(optimizationLevel = 0, debugInfo = true, lto = false)
            "release" -> ProfileConfig(optimizationLevel = 2, debugInfo = false, lto = false)
            "release-lto" -> ProfileConfig(optimizationLevel = 3, debugInfo = false, lto = true)
            else -> ProfileConfig(optimizationLevel = 2, debugInfo = false, lto = false)
        }
    }
    
    private fun createBuildConfig(
        config: CjpmConfig,
        targetPlatform: CompilationTarget?,
        profile: ProfileConfig
    ): org.cangnova.kcjpm.build.BuildConfig {
        val optimizationLevel = when (profile.optimizationLevel) {
            0 -> OptimizationLevel.DEBUG
            1 -> OptimizationLevel.RELEASE
            2 -> OptimizationLevel.RELEASE
            3 -> OptimizationLevel.SPEED
            else -> OptimizationLevel.RELEASE
        }
        
        val jobs = config.build?.jobs ?: Runtime.getRuntime().availableProcessors()
        
        return org.cangnova.kcjpm.build.BuildConfig(
            target = targetPlatform,
            optimizationLevel = optimizationLevel,
            debugInfo = profile.debugInfo,
            parallel = config.build?.parallel ?: true,
            maxParallelSize = jobs,
            incremental = config.build?.incremental ?: true,
            verbose = config.build?.verbose ?: false
        )
    }
    
    private fun parseDependencies(
        deps: Map<String, DependencyConfig>,
        projectRoot: Path,
        registry: RegistryConfig?
    ): List<Dependency> {
        return deps.mapNotNull { (name, depConfig) ->
            parseDependency(name, depConfig, projectRoot, registry)
        }
    }
    
    private fun parseDependency(
        name: String,
        config: DependencyConfig,
        projectRoot: Path,
        registry: RegistryConfig?
    ): Dependency? {
        if (config.optional) {
            return null
        }
        
        return when {
            config.path != null -> {
                val depPath = projectRoot.resolve(config.path)
                Dependency.PathDependency(
                    name = name,
                    version = config.version,
                    path = depPath
                )
            }
            config.git != null -> {
                val gitUrl = config.git
                val tag = config.tag
                val branch = config.branch
                val commit = config.commit
                
                val reference = when {
                    tag != null -> Dependency.GitReference.Tag(tag)
                    branch != null -> Dependency.GitReference.Branch(branch)
                    commit != null -> Dependency.GitReference.Commit(commit)
                    else -> Dependency.GitReference.Branch("main")
                }
                Dependency.GitDependency(
                    name = name,
                    version = config.version,
                    url = gitUrl!!,
                    reference = reference
                )
            }
            config.version != null -> {
                val version = config.version
                val registryUrl = resolveRegistryUrl(config.registry, registry)
                Dependency.RegistryDependency(
                    name = name,
                    version = version!!,
                    registryUrl = registryUrl
                )
            }
            else -> throw IllegalArgumentException(
                "Dependency '$name' must specify 'version', 'path', or 'git'"
            )
        }
    }
    
    private fun resolveRegistryUrl(registryName: String?, registry: RegistryConfig?): String {
        if (registryName == null) {
            return registry?.default ?: "https://repo.cangjie-lang.cn"
        }
        
        return when (registryName) {
            "default" -> registry?.default ?: "https://repo.cangjie-lang.cn"
            "private" -> registry?.privateUrl ?: throw IllegalArgumentException("Private registry not configured")
            else -> registryName
        }
    }
    
    private fun discoverSourceFiles(projectRoot: Path, buildConfig: BuildConfig?): List<Path> {
        val sourceDir = buildConfig?.sourceDir ?: "src"
        val srcDir = projectRoot.resolve(sourceDir)
        
        if (!Files.exists(srcDir)) {
            return emptyList()
        }
        
        return Files.walk(srcDir)
            .asSequence()
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".cj") }
            .toList()
    }
    
    private fun determineOutputPath(
        projectRoot: Path,
        buildConfig: BuildConfig?,
        profileName: String
    ): Path {
        val outputDir = buildConfig?.outputDir ?: "target"
        return projectRoot.resolve(outputDir).resolve(profileName)
    }
}