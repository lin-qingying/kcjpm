package org.cangnova.kcjpm.lock

import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.ConfigLoader
import org.cangnova.kcjpm.dependency.CentralRepositoryCoordinate
import org.cangnova.kcjpm.dependency.CentralRepositorySettingsLoader
import org.cangnova.kcjpm.dependency.RegistryCacheLayout
import org.cangnova.kcjpm.process.ProcessRunner
import java.nio.file.Path
import java.time.Duration

data class LockFileValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

interface LockFileValidator {
    fun validate(
        lockFile: LockFile,
        projectRoot: Path,
        currentDependencies: List<Dependency>
    ): LockFileValidationResult
}

class DefaultLockFileValidator : LockFileValidator {
    
    override fun validate(
        lockFile: LockFile,
        projectRoot: Path,
        currentDependencies: List<Dependency>
    ): LockFileValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        validateVersion(lockFile, errors)
        validateMetadata(lockFile, warnings)
        validatePackages(lockFile, errors)
        validateDependencyConsistency(lockFile, currentDependencies, warnings)
        validateChecksums(lockFile, projectRoot, warnings)
        
        return LockFileValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    private fun validateVersion(lockFile: LockFile, errors: MutableList<String>) {
        if (lockFile.version != 1) {
            errors.add("不支持的锁文件版本: ${lockFile.version}，期望版本: 1")
        }
    }
    
    private fun validateMetadata(lockFile: LockFile, warnings: MutableList<String>) {
        if (lockFile.metadata.kcjpmVersion.isBlank()) {
            warnings.add("锁文件缺少 kcjpm 版本信息")
        }
    }
    
    private fun validatePackages(lockFile: LockFile, errors: MutableList<String>) {
        val packageNames = mutableSetOf<String>()
        
        lockFile.packages.forEach { pkg ->
            if (pkg.name.isBlank()) {
                errors.add("包名称不能为空")
            }
            
            if (pkg.version.isBlank()) {
                errors.add("包 ${pkg.name} 缺少版本信息")
            }
            
            if (!packageNames.add(pkg.name)) {
                errors.add("重复的包: ${pkg.name}")
            }
        }
    }
    
    private fun validateDependencyConsistency(
        lockFile: LockFile,
        currentDependencies: List<Dependency>,
        warnings: MutableList<String>
    ) {
        val lockedNames = lockFile.packages.map { it.name }.toSet()
        val currentNames = currentDependencies.map { it.name }.toSet()
        
        val missing = currentNames - lockedNames
        if (missing.isNotEmpty()) {
            warnings.add("配置文件中的依赖未在锁文件中: ${missing.joinToString(", ")}")
        }
        
        val lockedTransitiveNames = lockFile.packages
            .flatMap { it.dependencies }
            .toSet()
        val extra = lockedNames - currentNames - lockedTransitiveNames
        if (extra.isNotEmpty()) {
            warnings.add("锁文件中存在多余的依赖: ${extra.joinToString(", ")}")
        }
        
        currentDependencies.forEach { dep ->
            val locked = lockFile.findPackage(dep.name)
            if (locked != null) {
                validateVersionMatch(dep, locked, warnings)
            }
        }
    }
    
    private fun validateVersionMatch(
        dep: Dependency,
        locked: LockedPackage,
        warnings: MutableList<String>
    ) {
        val depVersion = dep.version
        if (depVersion != null && depVersion != locked.version) {
            warnings.add(
                "依赖 ${dep.name} 版本不匹配: 配置=${depVersion}, 锁定=${locked.version}"
            )
        }
    }
    
    private fun validateChecksums(
        lockFile: LockFile,
        projectRoot: Path,
        warnings: MutableList<String>
    ) {
        lockFile.packages.forEach { pkg ->
            if (pkg.checksum != null && pkg.checksum != "unknown") {
                val depPath = resolveDependencyPath(pkg, projectRoot)
                if (depPath != null) {
                    val currentChecksum = calculateCurrentChecksum(pkg, depPath)
                    if (currentChecksum != null && currentChecksum != pkg.checksum) {
                        warnings.add(
                            "包 ${pkg.name} 的校验和不匹配，可能已被修改"
                        )
                    }
                }
            }
        }
    }
    
    private fun resolveDependencyPath(pkg: LockedPackage, projectRoot: Path): Path? {
        return when (val source = pkg.source) {
            is PackageSource.Path -> projectRoot.resolve(source.path)
            is PackageSource.Git -> {
                resolveDependencyCacheDir(projectRoot)?.resolve("git")?.resolve(gitCacheName(pkg.name))
            }
            is PackageSource.Registry -> {
                resolveDependencyCacheDir(projectRoot)
                    ?.resolve("registry")
                    ?.resolve(RegistryCacheLayout.registryScope(source.url))
                    ?.resolve(registryCacheName(pkg.name))
                    ?.resolve(pkg.version)
            }
        }
    }

    private fun resolveDependencyCacheDir(projectRoot: Path): Path? {
        return CentralRepositorySettingsLoader.load(projectRoot).getOrNull()?.repositoryCacheDir
    }

    private fun gitCacheName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun registryCacheName(name: String): String {
        return runCatching { CentralRepositoryCoordinate.parse(name).cacheName }
            .getOrElse { name.replace("::", "__") }
    }
    
    private fun calculateCurrentChecksum(pkg: LockedPackage, path: Path): String? {
        return when (pkg.source) {
            is PackageSource.Git -> {
                try {
                    val result = ProcessRunner.run(
                        listOf("git", "rev-parse", "HEAD"),
                        workingDirectory = path,
                        timeout = Duration.ofSeconds(10)
                    ).getOrThrow()
                    if (result.exitCode == 0) "git-commit:${result.output.trim()}" else null
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}

interface LockFileReader {
    fun read(projectRoot: Path): Result<LockFile>
    fun readIfExists(projectRoot: Path): LockFile?
}

class DefaultLockFileReader(
    private val serializer: LockFileSerializer = TomlLockFileSerializer(),
    private val validator: LockFileValidator = DefaultLockFileValidator()
) : LockFileReader {
    
    override fun read(projectRoot: Path): Result<LockFile> {
        return LockFileIO.read(projectRoot, serializer)
    }
    
    override fun readIfExists(projectRoot: Path): LockFile? {
        return if (LockFileIO.exists(projectRoot)) {
            read(projectRoot).getOrNull()
        } else {
            null
        }
    }
    
    fun readAndValidate(
        projectRoot: Path,
        currentDependencies: List<Dependency>
    ): Result<Pair<LockFile, LockFileValidationResult>> {
        return read(projectRoot).mapCatching { lockFile ->
            val validationResult = validator.validate(lockFile, projectRoot, currentDependencies)
            lockFile to validationResult
        }
    }
}
