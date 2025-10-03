package org.cangnova.kcjpm.build

import java.nio.file.Path
import java.time.Instant

data class CompilationReport(
    val timestamp: Instant = Instant.now(),
    val packages: List<PackageCompilationReport> = emptyList(),
    val linking: LinkingReport? = null,
    val overallSuccess: Boolean = true
) {
    val totalErrors: Int
        get() = packages.sumOf { it.errors.size } + (linking?.errors?.size ?: 0)
    
    val totalWarnings: Int
        get() = packages.sumOf { it.warnings.size } + (linking?.warnings?.size ?: 0)
    
    fun hasErrors(): Boolean = totalErrors > 0
    
    fun hasWarnings(): Boolean = totalWarnings > 0
}

data class PackageCompilationReport(
    val packageName: String,
    val packageRoot: Path,
    val success: Boolean,
    val errors: List<CjcDiagnostic> = emptyList(),
    val warnings: List<CjcDiagnostic> = emptyList(),
    val outputPath: Path? = null,
    val stdoutLog: Path? = null,
    val stderrLog: Path? = null,
    val exception: Throwable? = null
) {
    fun hasErrors(): Boolean = errors.isNotEmpty() || exception != null
    
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
}

data class LinkingReport(
    val success: Boolean,
    val errors: List<CjcDiagnostic> = emptyList(),
    val warnings: List<CjcDiagnostic> = emptyList(),
    val outputPath: Path? = null,
    val stdoutLog: Path? = null,
    val stderrLog: Path? = null,
    val exception: Throwable? = null
) {
    fun hasErrors(): Boolean = errors.isNotEmpty() || exception != null
    
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
}

class CompilationReportBuilder {
    private val packages = mutableListOf<PackageCompilationReport>()
    private var linking: LinkingReport? = null
    private var overallSuccess = true
    
    fun addPackageReport(report: PackageCompilationReport) {
        packages.add(report)
        if (!report.success) {
            overallSuccess = false
        }
    }
    
    fun setLinkingReport(report: LinkingReport) {
        linking = report
        if (!report.success) {
            overallSuccess = false
        }
    }
    
    fun build(): CompilationReport {
        return CompilationReport(
            packages = packages.toList(),
            linking = linking,
            overallSuccess = overallSuccess
        )
    }
}