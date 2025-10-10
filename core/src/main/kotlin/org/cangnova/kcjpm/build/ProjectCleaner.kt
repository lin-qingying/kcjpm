package org.cangnova.kcjpm.build

import java.nio.file.Path

interface ProjectCleaner {
    fun clean(projectRoot: Path, options: CleanOptions = CleanOptions()): Result<CleanReport>
}

data class CleanOptions(
    val targetDir: String = "target",
    val cleanDebugOnly: Boolean = false,
    val cleanCoverage: Boolean = true,
    val cleanBuildCache: Boolean = false,
    val cleanIncrementalCache: Boolean = false,
    val dryRun: Boolean = false
)

data class CleanReport(
    val deletedPaths: List<Path>,
    val freedSpace: Long,
    val errors: List<CleanError> = emptyList()
)

data class CleanError(
    val path: Path,
    val error: String
)