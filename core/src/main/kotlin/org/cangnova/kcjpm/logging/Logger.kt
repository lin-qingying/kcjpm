package org.cangnova.kcjpm.logging

import org.slf4j.LoggerFactory as Slf4jLoggerFactory

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

class Slf4jLogger(
    private val minLevel: LogLevel = LogLevel.INFO,
    private val delegate: org.slf4j.Logger = Slf4jLoggerFactory.getLogger("org.cangnova.kcjpm")
) : Logger {
    override fun debug(message: String) {
        if (minLevel <= LogLevel.DEBUG) {
            delegate.debug(message)
        }
    }

    override fun info(message: String) {
        if (minLevel <= LogLevel.INFO) {
            delegate.info(message)
        }
    }

    override fun warn(message: String) {
        if (minLevel <= LogLevel.WARN) {
            delegate.warn(message)
        }
    }

    override fun error(message: String) {
        if (minLevel <= LogLevel.ERROR) {
            delegate.error(message)
        }
    }

    override fun error(message: String, throwable: Throwable) {
        if (minLevel <= LogLevel.ERROR) {
            delegate.error(message, throwable)
        }
    }
}

/**
 * 兼容旧 API 的日志适配器。
 *
 * Core 模块不直接处理控制台输出，该类仅保留名称兼容，实际输出交给 SLF4J 后端。
 */
class ConsoleLogger(
    minLevel: LogLevel = LogLevel.INFO
) : Logger by Slf4jLogger(minLevel)

object LoggerFactory {
    private var logger: Logger = Slf4jLogger()

    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    fun getLogger(): Logger = logger
}
