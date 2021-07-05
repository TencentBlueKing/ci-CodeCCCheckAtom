package com.tencent.devops.hash.pojo

import com.tencent.devops.hash.constant.SPAMSUM_LENGTH

data class BlockHashContext(
        var dIndex : Int,
        val digest: CharArray = CharArray(SPAMSUM_LENGTH),
        var halfDigest : Char,
        var h : Int,
        var halfh : Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockHashContext

        if (dIndex != other.dIndex) return false
        if (!digest.contentEquals(other.digest)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dIndex
        result = 31 * result + digest.contentHashCode()
        return result
    }
}