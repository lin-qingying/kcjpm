
package org.cangnova.kcjpm.build

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.native.OsFamily
import kotlin.native.CpuArchitecture

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual fun getAvailableProcessors(): Int {
    return 1
}

@OptIn(ExperimentalNativeApi::class)
actual fun detectCurrentTarget(): CompilationTarget {
    return when {
        Platform.osFamily == OsFamily.WINDOWS -> CompilationTarget.WINDOWS_X64
        Platform.osFamily == OsFamily.MACOSX -> {
            if (Platform.cpuArchitecture == CpuArchitecture.ARM64) {
                CompilationTarget.MACOS_ARM64
            } else {
                CompilationTarget.MACOS_X64
            }
        }
        Platform.osFamily == OsFamily.LINUX -> {
            if (Platform.cpuArchitecture == CpuArchitecture.ARM64) {
                CompilationTarget.LINUX_ARM64
            } else {
                CompilationTarget.LINUX_X64
            }
        }
        else -> throw UnsupportedOperationException("Unsupported platform: ${Platform.osFamily}")
    }
}