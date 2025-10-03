package org.cangnova.kcjpm.logging

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

interface Logger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun error(message: String, throwable: Throwable)
}

class ConsoleLogger(
    private val minLevel: LogLevel = LogLevel.INFO
) : Logger {
    override fun debug(message: String) {
        if (minLevel <= LogLevel.DEBUG) {
            println("[DEBUG] $message")
        }
    }

    override fun info(message: String) {
        if (minLevel <= LogLevel.INFO) {
            println("[INFO] $message")
        }
    }

    override fun warn(message: String) {
        if (minLevel <= LogLevel.WARN) {
            println("[WARN] $message")
        }
    }

    override fun error(message: String) {
        if (minLevel <= LogLevel.ERROR) {
            System.err.println("[ERROR] $message")
        }
    }

    override fun error(message: String, throwable: Throwable) {
        if (minLevel <= LogLevel.ERROR) {
            System.err.println("[ERROR] $message")
            throwable.printStackTrace(System.err)
        }
    }
}

object LoggerFactory {
    private var logger: Logger = ConsoleLogger()

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    fun getLogger(): Logger = logger
}