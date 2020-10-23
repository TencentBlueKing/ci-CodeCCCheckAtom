package com.tencent.devops.hash.core

import com.tencent.devops.hash.constant.ROLLING_WINDOW
import com.tencent.devops.hash.pojo.RollState

/**
 * 负责强哈希和若哈希的工具类
 */
@ExperimentalUnsignedTypes
object RollHashUtil {

    /**
     * adler-32滚动哈希算法，但是这里和标准哈希有所区别，少了额外加的1
     * 滚动哈希的好处是每次只需要计算去掉字符的影响因素，并加上新增字符
     * 的影响因素，就可以得到最新值(此处滚动哈希方法有带商榷)
     */
    fun rollHash(rollState: RollState, singleChar: Char) {
        val charValue = singleChar.toInt().toUInt()
        //计算h2的值，新增的字符影响最大
        rollState.h2 -= rollState.h1
        rollState.h2 += ROLLING_WINDOW.toUInt() * charValue

        rollState.h1 += charValue
        rollState.h1 -= rollState.window[rollState.n].toInt().toUInt()

        rollState.window[rollState.n] = singleChar
        rollState.n++

        if (rollState.n == ROLLING_WINDOW) {
            rollState.n = 0
        }

        rollState.h3 = rollState.h3 shl 5
        rollState.h3 = rollState.h3 xor charValue
    }

    /**
     * 返回滚动哈希的和
     */
    fun rollSum(rollState: RollState): UInt {
        return rollState.h1 + rollState.h2 + rollState.h3
    }
}