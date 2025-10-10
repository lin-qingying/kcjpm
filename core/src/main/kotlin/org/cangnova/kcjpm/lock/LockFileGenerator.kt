package org.cangnova.kcjpm.lock

import kotlinx.datetime.Clock
import org.cangnova.kcjpm.build.Dependency
import org.cangnova.kcjpm.config.ConfigLoader
import java.nio.file.Path
import java.security.MessageDigest

interface LockFileGenerator {
    fun generate(
        projectRoot: Path,
        dependencies: List<Dependency>
    ): Result<LockFile>
    
    fun update(
        projectRoot: Path,
        existingLockFile: LockFile,
        newDependencies: List<Dependency>
    ): Result<LockFile>
}

class DefaultLockFileGenerator(
    private val kcjpmVersion: String = "0.1.0"
) : LockFileGenerator {
    
    override fun generate(
        projectRoot: Path,
        dependencies: List<Dependency>
    ): Result<LockFile> = runCatching {
        val metadata = LockMetadata(
            generatedAt = Clock.System.now(),
            kcjpmVersion = kcjpmVersion
        )
        
        val packages = dependencies.map { dep ->
            createLockedPackage(dep, projectRoot)
        }.sortedBy { it.name }
        
        LockFile(
            version = 1,
            metadata = metadata,
            packages = packages
        )
    }
    
    override fun update(
        projectRoot: Path,
        existingLockFile: LockFile,
        newDependencies: List<Dependency>
    ): Result<LockFile> = runCatching {
        val existingPackages = existingLockFile.packages.associateBy { it.name }
        val newPackages = mutableListOf<LockedPackage>()
        
        for (dep in newDependencies) {
            val existing = existingPackages[dep.name]
            
            val lockedPackage = if (existing != null && shouldKeepExisting(dep, existing)) {
                existing
            } else {
                createLockedPackage(dep, projectRoot)
            }
            
            newPackages.add(lockedPackage)
        }
        
        val metadata = LockMetadata(
            generatedAt = Clock.System.now(),
            kcjpmVersion = kcjpmVersion
        )
        
        LockFile(
            version = 1,
            metadata = metadata,
            packages = newPackages.sortedBy { it.name }
        )
    }
    
    private fun createLockedPackage(dep: Dependency, projectRoot: Path): LockedPackage {
        val source = PackageSource.fromDependency(dep, projectRoot)
        val checksum = calculateChecksum(dep)
        val transitiveDeps = resolveTransitiveDependencies(dep)
        
        return LockedPackage(
            name = dep.name,
            version = dep.version ?: "unknown",
            source = source,
            checksum = checksum,
            dependencies = transitiveDeps
        )
    }
    
    private fun shouldKeepExisting(dep: Dependency, existing: LockedPackage): Boolean {
        return when (dep) {
            is Dependency.PathDependency -> true
            is Dependency.GitDependency -> {
                val currentSource = PackageSource.fromDependency(dep, Path.of("."))
                currentSource.toSourceString() == existing.source.toSourceString()
            }
            is Dependency.RegistryDependency -> {
                dep.version == existing.version
            }
        }
    }
    
    private fun calculateChecksum(dep: Dependency): String? {
        return when (dep) {
            is Dependency.PathDependency -> null
            is Dependency.GitDependency -> {
                dep.localPath?.let { path ->
                    val commit = PackageSource.fromDependency(dep, Path.of("."))
                    when (commit) {
                        is PackageSource.Git -> "git-commit:${commit.resolvedCommit}"
                        else -> null
                    }
                }
            }
            is Dependency.RegistryDependency -> {
                dep.localPath?.let { calculateDirectoryChecksum(it) }
            }
        }
    }
    
    private fun calculateDirectoryChecksum(path: Path): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val files = path.toFile().walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.path }
                .toList()
            
            files.forEach { file ->
                digest.update(file.readBytes())
            }
            
            val hash = digest.digest()
            "sha256:" + hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    private fun resolveTransitiveDependencies(dep: Dependency): List<String> {
        val depPath = when (dep) {
            is Dependency.PathDependency -> dep.path
            is Dependency.GitDependency -> dep.localPath ?: return emptyList()
            is Dependency.RegistryDependency -> dep.localPath ?: return emptyList()
        }
        
        return try {
            val config = ConfigLoader.loadFromProjectRoot(depPath).getOrNull()
            config?.dependencies?.keys?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}