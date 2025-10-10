package org.cangnova.kcjpm.build

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class DefaultProjectCleaner : ProjectCleaner {
    
    override fun clean(projectRoot: Path, options: CleanOptions): Result<CleanReport> = runCatching {
        require(projectRoot.exists() && projectRoot.isDirectory()) {
            "Project root does not exist or is not a directory: $projectRoot"
        }
        
        val pathsToDelete = mutableListOf<Path>()
        val errors = mutableListOf<CleanError>()
        var totalSize = 0L
        
        val targetDir = projectRoot.resolve(options.targetDir)
        if (targetDir.exists()) {
            if (options.cleanDebugOnly) {
                val debugDir = targetDir.resolve("debug")
                if (debugDir.exists()) {
                    pathsToDelete.add(debugDir)
                    totalSize += calculateSize(debugDir)
                }
            } else {
                pathsToDelete.add(targetDir)
                totalSize += calculateSize(targetDir)
            }
        }
        
        if (options.cleanCoverage) {
            val covOutputDir = projectRoot.resolve("cov_output")
            if (covOutputDir.exists()) {
                pathsToDelete.add(covOutputDir)
                totalSize += calculateSize(covOutputDir)
            }
            
            findGcovFiles(projectRoot).forEach { gcovFile ->
                pathsToDelete.add(gcovFile)
                totalSize += gcovFile.fileSize()
            }
        }
        
        if (options.cleanBuildCache) {
            val buildCacheDir = projectRoot.resolve("build-script-cache")
            if (buildCacheDir.exists()) {
                pathsToDelete.add(buildCacheDir)
                totalSize += calculateSize(buildCacheDir)
            }
        }
        
        if (options.cleanIncrementalCache) {
            findIncrementalCacheFiles(targetDir).forEach { cacheFile ->
                pathsToDelete.add(cacheFile)
                totalSize += cacheFile.fileSize()
            }
        }
        
        if (!options.dryRun) {
            pathsToDelete.forEach { path ->
                try {
                    deleteRecursively(path)
                } catch (e: IOException) {
                    errors.add(CleanError(path, e.message ?: "Unknown error"))
                }
            }
        }
        
        CleanReport(
            deletedPaths = pathsToDelete,
            freedSpace = totalSize,
            errors = errors
        )
    }
    
    private fun calculateSize(path: Path): Long {
        if (!path.exists()) return 0L
        
        var totalSize = 0L
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                totalSize += attrs.size()
                return FileVisitResult.CONTINUE
            }
            
            override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
        return totalSize
    }
    
    private fun deleteRecursively(path: Path) {
        if (!path.exists()) return
        
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
    
    private fun findGcovFiles(projectRoot: Path): List<Path> {
        val gcovFiles = mutableListOf<Path>()
        
        if (!projectRoot.exists() || !projectRoot.isDirectory()) {
            return gcovFiles
        }
        
        Files.walkFileTree(projectRoot, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val fileName = file.name
                if (fileName.endsWith(".gcno") || fileName.endsWith(".gcda")) {
                    gcovFiles.add(file)
                }
                return FileVisitResult.CONTINUE
            }
            
            override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
        
        return gcovFiles
    }
    
    private fun findIncrementalCacheFiles(targetDir: Path): List<Path> {
        val cacheFiles = mutableListOf<Path>()
        
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            return cacheFiles
        }
        
        Files.walkFileTree(targetDir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (file.name.endsWith(".incremental.json")) {
                    cacheFiles.add(file)
                }
                return FileVisitResult.CONTINUE
            }
            
            override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
        
        return cacheFiles
    }
}