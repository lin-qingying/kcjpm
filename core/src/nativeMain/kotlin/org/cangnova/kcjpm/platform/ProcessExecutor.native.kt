package org.cangnova.kcjpm.platform

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class NativeProcessExecutor : ProcessExecutor {
    override fun execute(
        command: List<String>,
        workingDirectory: KPath?,
        environment: Map<String, String>,
        redirectOutput: Boolean
    ): Result<ProcessResult> = runCatching {
        TODO("Process execution not yet implemented for Native")
    }
    
    override fun executeAsync(
        command: List<String>,
        workingDirectory: KPath?,
        environment: Map<String, String>,
        onOutput: (String) -> Unit,
        onError: (String) -> Unit
    ): Result<ProcessHandle> = runCatching {
        TODO("Async process execution not yet implemented for Native")
    }
}

class NativeProcessHandle : ProcessHandle {
    override fun waitFor(): Result<Int> = runCatching {
        TODO("ProcessHandle not yet implemented for Native")
    }
    
    override fun isAlive(): Boolean = false
    
    override fun destroy() {}
}

actual fun getProcessExecutor(): ProcessExecutor = NativeProcessExecutor()