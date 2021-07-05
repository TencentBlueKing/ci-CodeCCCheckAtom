package com.tencent.devops.hash.pojo

import com.tencent.devops.hash.constant.ROLLING_WINDOW

data class RollState @ExperimentalUnsignedTypes constructor(
        val window: CharArray = CharArray(ROLLING_WINDOW) {0.toChar()},
        var h1: UInt,
        var h2: UInt,
        var h3: UInt,
        var n: Int
) {
    @ExperimentalUnsignedTypes
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RollState

        if (!window.contentEquals(other.window)) return false
        if (h1 != other.h1) return false
        if (h2 != other.h2) return false
        if (h3 != other.h3) return false
        if (n != other.n) return false

        return true
    }

    @ExperimentalUnsignedTypes
    override fun hashCode(): Int {
        var result = window.contentHashCode()
        result = 31 * result + h1.hashCode()
        result = 31 * result + h2.hashCode()
        result = 31 * result + h3.hashCode()
        result = 31 * result + n
        return result
    }
}