package com.tencent.devops.hash.pojo

import com.tencent.devops.hash.constant.NUM_BLOCKHASHES

data class FuzzyState(
        var fixedSize : Long,
        var reduceBorder : Long,
        var bhStart : Int,
        var bhEnd : Int,
        var bhEndLimit : Int,
        var flags : Int,
        var rollMask : Int,
        val bh : Array<BlockHashContext?> = arrayOfNulls(NUM_BLOCKHASHES),
        val rollState : RollState,
        var lasth : Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FuzzyState

        if (fixedSize != other.fixedSize) return false
        if (reduceBorder != other.reduceBorder) return false
        if (bhStart != other.bhStart) return false
        if (bhEnd != other.bhEnd) return false
        if (bhEndLimit != other.bhEndLimit) return false
        if (flags != other.flags) return false
        if (rollMask != other.rollMask) return false
        if (!bh.contentEquals(other.bh)) return false
        if (rollState != other.rollState) return false
        if (lasth != other.lasth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fixedSize.hashCode()
        result = 31 * result + reduceBorder.hashCode()
        result = 31 * result + bhStart
        result = 31 * result + bhEnd
        result = 31 * result + bhEndLimit
        result = 31 * result + flags
        result = 31 * result + rollMask
        result = 31 * result + bh.contentHashCode()
        result = 31 * result + rollState.hashCode()
        result = 31 * result + lasth
        return result
    }

}