package org.cangnova.kcjpm.cli.handler

import org.cangnova.kcjpm.cli.output.OutputAdapter
import org.cangnova.kcjpm.cli.parser.Command
import org.cangnova.kcjpm.cli.parser.GlobalOptions

interface CommandHandler {
    suspend fun handle(command: Command, options: GlobalOptions): Int
}

abstract class BaseCommandHandler(
    protected val output: OutputAdapter
) : CommandHandler {
    
    protected fun handleError(message: String, throwable: Throwable? = null): Int {
        output.error(message)
        if (throwable != null) {
            output.debug("详细错误: ${throwable.stackTraceToString()}")
        }
        return 1
    }
    
    protected fun handleSuccess(message: String): Int {
        output.success(message)
        return 0
    }
}