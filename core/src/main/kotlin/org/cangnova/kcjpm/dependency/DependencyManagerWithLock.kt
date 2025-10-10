package org.cangnova.kcjpm.dependency

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.CjpmConfig
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
        
        currentDeps
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