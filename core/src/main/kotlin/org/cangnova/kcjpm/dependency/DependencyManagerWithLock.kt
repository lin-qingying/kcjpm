package org.cangnova.kcjpm.dependency

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
import org.cangnova.kcjpm.config.DependencyConfig
import org.cangnova.kcjpm.lock.*
import java.nio.file.Path

class DependencyManagerWithLock(
    private val baseManager: DependencyManager,
    private val lockFileGenerator: LockFileGenerator = DefaultLockFileGenerator(),
    private val lockFileReader: DefaultLockFileReader = DefaultLockFileReader()
) : DependencyManager by baseManager {
    
    fun installWithLock(
        config: CjpmConfig,
        projectRoot: Path,
        updateLock: Boolean = false
    ): Result<Pair<List<Dependency>, LockFile>> = runCatching {
        val existingLock = if (!updateLock) {
            lockFileReader.readIfExists(projectRoot)
        } else {
            null
        }
        
        val dependencies = if (existingLock != null) {
            installFromLockFile(existingLock, config, projectRoot).getOrThrow()
        } else {
            baseManager.installDependencies(config, projectRoot).getOrThrow()
        }
        
        val lockFile = if (existingLock != null && !updateLock) {
            lockFileGenerator.update(projectRoot, existingLock, dependencies).getOrThrow()
        } else {
            lockFileGenerator.generate(projectRoot, dependencies).getOrThrow()
        }
        
        LockFileIO.write(projectRoot, lockFile).getOrThrow()
        
        dependencies to lockFile
    }
    
    private fun installFromLockFile(
        lockFile: LockFile,
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>> = runCatching {
        val currentDeps = baseManager.resolveDependencies(config, projectRoot).getOrThrow()
        
        val (_, validationResult) = lockFileReader.readAndValidate(
            projectRoot,
            currentDeps
        ).getOrThrow()
        
        if (validationResult.hasErrors) {
            throw IllegalStateException(
                "锁文件验证失败:\n${validationResult.errors.joinToString("\n")}"
            )
        }
        
        val lockedDeps = installLockedPackages(lockFile, config, projectRoot).getOrThrow()
        validateLockedResolution(lockFile, lockedDeps).getOrThrow()

        lockedDeps
    }

    private fun installLockedPackages(
        lockFile: LockFile,
        config: CjpmConfig,
        projectRoot: Path
    ): Result<List<Dependency>> = runCatching {
        val lockedPackagesByName = lockFile.packages.associateBy { it.name }
        val lockedPackageNames = collectLockedPackageClosure(config.dependencies.keys, lockedPackagesByName)

        val lockedDependencyConfig = lockedPackageNames.mapNotNull { packageName ->
            val lockedPackage = lockedPackagesByName[packageName] ?: return@mapNotNull null
            lockedPackage.name to lockedPackage.toDependencyConfig()
        }.toMap()
        val missingDirectDependencyConfig = config.dependencies.filterKeys { it !in lockedPackagesByName }

        val dependencies = mutableListOf<Dependency>()
        val lockedConfig = config.copy(dependencies = lockedDependencyConfig)
        if (lockedDependencyConfig.isNotEmpty()) {
            dependencies.addAll(baseManager.resolveDependencies(lockedConfig, projectRoot).getOrThrow())
        }

        if (missingDirectDependencyConfig.isNotEmpty()) {
            val missingConfig = config.copy(dependencies = missingDirectDependencyConfig)
            dependencies.addAll(baseManager.installDependencies(missingConfig, projectRoot).getOrThrow())
        }

        dependencies.distinctBy { it.name }
    }

    private fun collectLockedPackageClosure(
        directDependencyNames: Set<String>,
        lockedPackagesByName: Map<String, LockedPackage>
    ): Set<String> {
        val selected = linkedSetOf<String>()

        fun visit(packageName: String) {
            if (!selected.add(packageName)) {
                return
            }

            val lockedPackage = lockedPackagesByName[packageName] ?: return
            lockedPackage.dependencies.forEach(::visit)
        }

        directDependencyNames.forEach(::visit)
        return selected.filterTo(linkedSetOf()) { it in lockedPackagesByName }
    }

    private fun LockedPackage.toDependencyConfig(): DependencyConfig {
        val lockedVersion = version.takeUnless { it == "unknown" }
        return when (val source = source) {
            is PackageSource.Registry -> DependencyConfig(
                version = version,
                registry = source.url
            )
            is PackageSource.Path -> DependencyConfig(
                version = lockedVersion,
                path = source.path
            )
            is PackageSource.Git -> {
                val resolvedCommit = source.resolvedCommit.takeUnless { it == "unknown" }
                if (resolvedCommit != null) {
                    DependencyConfig(
                        version = lockedVersion,
                        git = source.url,
                        commit = resolvedCommit
                    )
                } else {
                    when (val reference = source.reference) {
                        is PackageSource.GitReference.Tag -> DependencyConfig(
                            version = lockedVersion,
                            git = source.url,
                            tag = reference.name
                        )
                        is PackageSource.GitReference.Branch -> DependencyConfig(
                            version = lockedVersion,
                            git = source.url,
                            branch = reference.name
                        )
                        is PackageSource.GitReference.Commit -> DependencyConfig(
                            version = lockedVersion,
                            git = source.url,
                            commit = reference.hash
                        )
                    }
                }
            }
        }
    }

    private fun validateLockedResolution(
        lockFile: LockFile,
        dependencies: List<Dependency>
    ): Result<Unit> = runCatching {
        val lockedPackages = lockFile.packages.associateBy { it.name }

        dependencies.forEach { dependency ->
            val locked = lockedPackages[dependency.name]
                ?: return@forEach
            val dependencyVersion = dependency.version ?: "unknown"
            if (dependencyVersion != locked.version) {
                throw IllegalStateException(
                    "锁文件依赖版本不一致: ${dependency.name} 锁定=${locked.version}, 实际=$dependencyVersion"
                )
            }

            if (dependency is Dependency.RegistryDependency && locked.checksum != null) {
                if (dependency.checksum != locked.checksum) {
                    throw IllegalStateException(
                        "锁文件依赖校验和不一致: ${dependency.name} 锁定=${locked.checksum}, 实际=${dependency.checksum}"
                    )
                }
            }
        }
    }
    
    fun validateLockFile(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<LockFileValidationResult> = runCatching {
        val lockFile = lockFileReader.read(projectRoot).getOrThrow()
        val currentDeps = baseManager.resolveDependencies(config, projectRoot).getOrThrow()
        
        DefaultLockFileValidator().validate(lockFile, projectRoot, currentDeps)
    }
    
    fun updateDependencies(
        config: CjpmConfig,
        projectRoot: Path
    ): Result<Pair<List<Dependency>, LockFile>> = runCatching {
        val existingLock = lockFileReader.readIfExists(projectRoot)
        
        val configDeps = baseManager.resolveDependencies(config, projectRoot).getOrThrow()
        
        val (missingDeps, changedDeps) = if (existingLock != null) {
            detectChanges(existingLock, configDeps, projectRoot)
        } else {
            configDeps to emptyList()
        }
        
        val depsToFetch = missingDeps.ifEmpty { configDeps }
        
        val allDeps = baseManager.installDependencies(config, projectRoot).getOrThrow()
        
        val lockFile = if (existingLock != null) {
            lockFileGenerator.update(projectRoot, existingLock, allDeps).getOrThrow()
        } else {
            lockFileGenerator.generate(projectRoot, allDeps).getOrThrow()
        }
        
        LockFileIO.write(projectRoot, lockFile).getOrThrow()
        
        allDeps to lockFile
    }
    
    private fun detectChanges(
        lockFile: LockFile,
        configDeps: List<Dependency>,
        projectRoot: Path
    ): Pair<List<Dependency>, List<Dependency>> {
        val lockedPackages = lockFile.packages.associateBy { it.name }
        val missing = mutableListOf<Dependency>()
        val changed = mutableListOf<Dependency>()
        
        for (dep in configDeps) {
            val locked = lockedPackages[dep.name]
            if (locked == null) {
                missing.add(dep)
            } else {
                val currentSource = PackageSource.fromDependency(dep, projectRoot)
                if (currentSource.toSourceString() != locked.source.toSourceString() ||
                    dep.version != locked.version) {
                    changed.add(dep)
                }
            }
        }
        
        return missing to changed
    }
}
