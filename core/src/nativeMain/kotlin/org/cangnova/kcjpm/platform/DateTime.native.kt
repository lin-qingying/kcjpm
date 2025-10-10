package org.cangnova.kcjpm.platform

import kotlinx.cinterop.*

actual class Instant private constructor(private val value: String) {
    actual companion object {
        actual fun now(): Instant {
            return Instant("now")
        }
        
        actual fun parse(isoString: String): Instant {
            return Instant(isoString)
        }
    }
    
    actual override fun toString(): String = value
}