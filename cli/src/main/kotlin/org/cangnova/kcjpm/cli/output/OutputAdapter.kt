package org.cangnova.kcjpm.cli.output

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

sealed class OutputLevel {
    data object Info : OutputLevel()
    data object Success : OutputLevel()
    data object Warning : OutputLevel()
    data object Error : OutputLevel()
    data object Debug : OutputLevel()
}

interface OutputAdapter {
    fun info(message: String)
    fun success(message: String)
    fun warning(message: String)
    fun error(message: String)
    fun debug(message: String)
    
    fun progress(current: Int, total: Int, message: String)
    fun startProgress(message: String)
    fun updateProgress(message: String)
    fun completeProgress(message: String)
    
    fun newline()
}

class ConsoleOutputAdapter(
    useColors: Boolean = true,
    private val showDebug: Boolean = false
) : OutputAdapter {
    
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val supportsColors: Boolean = useColors && initWindowsAnsi()
    
    private val RESET = if (supportsColors) "\u001B[0m" else ""
    private val GREEN = if (supportsColors) "\u001B[32m" else ""
    private val YELLOW = if (supportsColors) "\u001B[33m" else ""
    private val RED = if (supportsColors) "\u001B[31m" else ""
    private val CYAN = if (supportsColors) "\u001B[36m" else ""
    private val GRAY = if (supportsColors) "\u001B[90m" else ""
    
    private val successSymbol = if (isWindows) "[OK]" else "✓"
    private val errorSymbol = if (isWindows) "[ERROR]" else "✗"
    private val warningSymbol = if (isWindows) "[WARN]" else "⚠"
    private val progressSymbol = if (isWindows) ">" else "▸"
    
    private interface Kernel32 : StdCallLibrary {
        fun GetStdHandle(nStdHandle: Int): Int
        fun GetConsoleMode(hConsoleHandle: Int, lpMode: IntByReference): Boolean
        fun SetConsoleMode(hConsoleHandle: Int, dwMode: Int): Boolean
        
        companion object {
            const val STD_OUTPUT_HANDLE = -11
            const val ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004
        }
    }
    
    private fun initWindowsAnsi(): Boolean {
        if (!isWindows) return true
        
        return try {
            val kernel32 = Native.load("kernel32", Kernel32::class.java) as Kernel32
            val handle = kernel32.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE)
            
            if (handle == -1) return false
            
            val mode = IntByReference()
            if (!kernel32.GetConsoleMode(handle, mode)) return false
            
            kernel32.SetConsoleMode(handle, mode.value or Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun info(message: String) {
        print("\r\u001B[K")  // 清除整行并回到行首
        println("$CYAN$message$RESET")
    }
    
    override fun success(message: String) {
        print("\r\u001B[K")  // 清除整行并回到行首
        println("$GREEN$successSymbol$RESET $message")
    }
    
    override fun warning(message: String) {
        print("\r\u001B[K")  // 清除整行并回到行首
        println("$YELLOW$warningSymbol$RESET $message")
    }
    
    override fun error(message: String) {
        print("\r\u001B[K")  // 清除整行并回到行首
        System.err.print("$RED$errorSymbol$RESET $message")
        System.err.println()
        System.err.flush()
    }
    
    override fun debug(message: String) {
        if (showDebug) {
            print("\r\u001B[K")  // 清除整行并回到行首
            println("$GRAY[DEBUG]$RESET $message")
        }
    }
    
    override fun progress(current: Int, total: Int, message: String) {
        val percentage = (current * 100) / total
        print("\r[$current/$total] ($percentage%) $message")
        if (current == total) {
            println()
        }
    }
    
    override fun startProgress(message: String) {
        print("$CYAN$progressSymbol$RESET $message...")
    }
    
    override fun updateProgress(message: String) {
        print("\r$CYAN$progressSymbol$RESET $message...")
    }
    
    override fun completeProgress(message: String) {
        print("\r$GREEN$successSymbol$RESET $message")
        println()
    }
    
    override fun newline() {
        println()
    }
}