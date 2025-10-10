package org.cangnova.kcjpm.lock

import kotlinx.datetime.Instant
import org.cangnova.kcjpm.build.Dependency
import java.nio.file.Path

data class LockFile(
    val version: Int = 1,
    val metadata: LockMetadata,
    val packages: List<LockedPackage>
) {
    fun findPackage(name: String): LockedPackage? {
        return packages.find { it.name == name }
    }
    
    fun getAllPackageNames(): Set<String> {
        return packages.map { it.name }.toSet()
    }
}

data class LockMetadata(
    val generatedAt: Instant,
    val kcjpmVersion: String
)

data class LockedPackage(
    val name: String,
    val version: String,
    val source: PackageSource,
    val checksum: String? = null,
    val dependencies: List<String> = emptyList()
)

sealed class PackageSource {
    abstract fun toSourceString(): String
    
    data class Registry(val url: String) : PackageSource() {
        override fun toSourceString(): String = "registry+$url"
    }
    
    data class Path(val path: String) : PackageSource() {
        override fun toSourceString(): String = "path+$path"
    }
    
    data class Git(
        val url: String,
        val reference: GitReference,
        val resolvedCommit: String
    ) : PackageSource() {
        override fun toSourceString(): String {
            val refPart = when (reference) {
                is GitReference.Tag -> "?tag=${reference.name}"
                is GitReference.Branch -> "?branch=${reference.name}"
                is GitReference.Commit -> "?commit=${reference.hash}"
            }
            return "git+$url$refPart#$resolvedCommit"
        }
    }
    
    sealed class GitReference {
        data class Tag(val name: String) : GitReference()
        data class Branch(val name: String) : GitReference()
        data class Commit(val hash: String) : GitReference()
    }
    
    companion object {
        fun fromDependency(dep: Dependency, projectRoot: java.nio.file.Path): PackageSource {
            return when (dep) {
                is Dependency.RegistryDependency -> Registry(dep.registryUrl)
                is Dependency.PathDependency -> {
                    val relativePath = try {
                        projectRoot.relativize(dep.path).toString()
                    } catch (e: Exception) {
                        dep.path.toString()
                    }
                    Path(relativePath)
                }
                is Dependency.GitDependency -> {
                    val gitRef = when (val ref = dep.reference) {
                        is Dependency.GitReference.Tag -> GitReference.Tag(ref.name)
                        is Dependency.GitReference.Branch -> GitReference.Branch(ref.name)
                        is Dependency.GitReference.Commit -> GitReference.Commit(ref.hash)
                    }
                    val commit = resolveGitCommit(dep.localPath)
                    Git(dep.url, gitRef, commit)
                }
            }
        }
        
        private fun resolveGitCommit(localPath: java.nio.file.Path?): String {
            if (localPath == null) return "unknown"
            
            return try {
                val process = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(localPath.toFile())
                    .redirectErrorStream(true)
                    .start()
                
                val output = process.inputStream.bufferedReader().readText().trim()
                val exitCode = process.waitFor()
                
                if (exitCode == 0) output else "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }
        
        fun parse(sourceString: String): PackageSource? {
            return when {
                sourceString.startsWith("registry+") -> {
                    val url = sourceString.substringAfter("registry+")
                    Registry(url)
                }
                sourceString.startsWith("path+") -> {
                    val path = sourceString.substringAfter("path+")
                    Path(path)
                }
                sourceString.startsWith("git+") -> {
                    parseGitSource(sourceString)
                }
                else -> null
            }
        }
        
        private fun parseGitSource(sourceString: String): Git? {
            val content = sourceString.substringAfter("git+")
            val parts = content.split("#")
            if (parts.size != 2) return null
            
            val urlAndRef = parts[0]
            val commit = parts[1]
            
            val urlParts = urlAndRef.split("?")
            val url = urlParts[0]
            
            val reference = if (urlParts.size == 2) {
                val refPart = urlParts[1]
                when {
                    refPart.startsWith("tag=") -> GitReference.Tag(refPart.substringAfter("tag="))
                    refPart.startsWith("branch=") -> GitReference.Branch(refPart.substringAfter("branch="))
                    refPart.startsWith("commit=") -> GitReference.Commit(refPart.substringAfter("commit="))
                    else -> return null
                }
            } else {
                GitReference.Branch("main")
            }
            
            return Git(url, reference, commit)
        }
    }
}