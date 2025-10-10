package org.cangnova.kcjpm.platform

expect class Instant {
    companion object {
        fun now(): Instant
        fun parse(isoString: String): Instant
    }
    
    override fun toString(): String
}