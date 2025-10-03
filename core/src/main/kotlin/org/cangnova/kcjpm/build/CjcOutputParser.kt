package org.cangnova.kcjpm.build

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStream

sealed interface CjcOutputEvent {
    data class CompilationStarted(val message: String) : CjcOutputEvent
    data class CompilationProgress(val message: String) : CjcOutputEvent
    data class CompilationWarning(val warning: CjcDiagnostic) : CjcOutputEvent
    data class CompilationError(val error: CjcDiagnostic) : CjcOutputEvent
    data class CompilationSuccess(val outputPath: String?) : CjcOutputEvent
    data class CompilationFailed(val exitCode: Int) : CjcOutputEvent
    data class RawOutput(val line: String, val isError: Boolean) : CjcOutputEvent
}

data class CjcDiagnostic(
    val severity: Severity,
    val message: String,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val code: String? = null,
    val snippet: String? = null
) {
    enum class Severity { WARNING, ERROR }
}

class CjcOutputParser {
    private val ansiEscapePattern = Regex("""\u001b\[[0-9;]*m""")
    private val simpleWarningPattern = Regex("""^warning:\s*(.+)$""")
    private val simpleErrorPattern = Regex("""^error:\s*(.+)$""")
    private val arrowLocationPattern = Regex("""^==>\s+(.+?):(\d+):(\d+):$""")
    private val compilingPackagePattern = Regex("""^Compiling package `([^`]+)`""")
    private val warningStatPattern = Regex("""^(\d+) warning[s]? generated, (\d+) warning[s]? printed\.$""")
    
    private var pendingDiagnostic: PendingDiagnostic? = null
    private var collectingSnippet = false
    private val snippetLines = mutableListOf<String>()
    private var diagnosticComplete = false
    
    private data class PendingDiagnostic(
        val severity: CjcDiagnostic.Severity,
        val message: String,
        var file: String? = null,
        var line: Int? = null,
        var column: Int? = null
    )
    
    private fun stripAnsiCodes(text: String): String {
        return ansiEscapePattern.replace(text, "")
    }

    fun parseStream(stdout: InputStream, stderr: InputStream): Flow<CjcOutputEvent> = flow {
        val stdoutReader = stdout.bufferedReader()
        val stderrReader = stderr.bufferedReader()

        val stdoutLines = mutableListOf<String>()
        val stderrLines = mutableListOf<String>()

        while (true) {
            val stdoutLine = stdoutReader.readLineOrNull()
            val stderrLine = stderrReader.readLineOrNull()

            if (stdoutLine == null && stderrLine == null) break

            stdoutLine?.let {
                stdoutLines.add(it)
                val event = parseLine(it, false)
                if (event != null) emit(event)
            }

            stderrLine?.let {
                stderrLines.add(it)
                val event = parseLine(it, true)
                if (event != null) emit(event)
            }
        }
    }

    fun parseLines(lines: Sequence<String>, isError: Boolean = false): Flow<CjcOutputEvent> = flow {
        for (line in lines) {
            val event = parseLine(line, isError)
            if (event != null) emit(event)
        }
    }

    fun parseLine(line: String, isError: Boolean = false): CjcOutputEvent? {
        val cleaned = stripAnsiCodes(line.trim())
        
        simpleWarningPattern.matchEntire(cleaned)?.let { match ->
            val message = match.groupValues[1]
            pendingDiagnostic = PendingDiagnostic(CjcDiagnostic.Severity.WARNING, message)
            collectingSnippet = false
            snippetLines.clear()
            diagnosticComplete = false
            return null
        }

        simpleErrorPattern.matchEntire(cleaned)?.let { match ->
            val message = match.groupValues[1]
            pendingDiagnostic = PendingDiagnostic(CjcDiagnostic.Severity.ERROR, message)
            collectingSnippet = false
            snippetLines.clear()
            diagnosticComplete = false
            return null
        }

        arrowLocationPattern.matchEntire(cleaned)?.let { match ->
            val (file, lineNum, column) = match.destructured
            val pending = pendingDiagnostic
            
            if (pending != null) {
                pending.file = file
                pending.line = lineNum.toIntOrNull()
                pending.column = column.toIntOrNull()
                collectingSnippet = true
                diagnosticComplete = false
                return null
            }
            return null
        }

        if (collectingSnippet) {
            if (cleaned.isEmpty() || cleaned.startsWith("#") || warningStatPattern.matches(cleaned)) {
                collectingSnippet = false
                val pending = pendingDiagnostic
                pendingDiagnostic = null
                
                if (pending != null && !diagnosticComplete) {
                    diagnosticComplete = true
                    val diagnostic = CjcDiagnostic(
                        severity = pending.severity,
                        message = pending.message,
                        file = pending.file,
                        line = pending.line,
                        column = pending.column,
                        code = null,
                        snippet = snippetLines.joinToString("\n").takeIf { it.isNotEmpty() }
                    )
                    snippetLines.clear()
                    
                    return if (pending.severity == CjcDiagnostic.Severity.ERROR) {
                        CjcOutputEvent.CompilationError(diagnostic)
                    } else {
                        CjcOutputEvent.CompilationWarning(diagnostic)
                    }
                }
                return null
            } else {
                snippetLines.add(stripAnsiCodes(line))
                return null
            }
        }

        compilingPackagePattern.find(cleaned)?.let { match ->
            val packageName = match.groupValues[1]
            return CjcOutputEvent.CompilationProgress("Compiling package: $packageName")
        }

        warningStatPattern.matchEntire(cleaned)?.let {
            return CjcOutputEvent.RawOutput(line, isError)
        }

        if (cleaned.contains("Compiling") || cleaned.contains("Building")) {
            return CjcOutputEvent.CompilationProgress(cleaned)
        }

        return CjcOutputEvent.RawOutput(line, isError)
    }

    private fun BufferedReader.readLineOrNull(): String? = try {
        readLine()
    } catch (e: Exception) {
        null
    }
}