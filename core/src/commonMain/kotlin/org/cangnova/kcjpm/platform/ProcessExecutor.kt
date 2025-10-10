package org.cangnova.kcjpm.platform

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

interface ProcessExecutor {
    fun execute(
        command: List<String>,
        workingDirectory: KPath? = null,
        environment: Map<String, String> = emptyMap(),
        redirectOutput: Boolean = true
    ): Result<ProcessResult>
    
    fun executeAsync(
        command: List<String>,
        workingDirectory: KPath? = null,
        environment: Map<String, String> = emptyMap(),
        onOutput: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ): Result<ProcessHandle>
}

interface ProcessHandle {
    fun waitFor(): Result<Int>
    fun isAlive(): Boolean
    fun destroy()
}

expect fun getProcessExecutor(): ProcessExecutor