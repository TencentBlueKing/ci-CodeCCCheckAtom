package com.tencent.devops.hash.core

import com.tencent.devops.hash.constant.*
import com.tencent.devops.hash.pojo.BlockHashContext
import com.tencent.devops.hash.pojo.FuzzyState
import com.tencent.devops.hash.pojo.RollState


@ExperimentalUnsignedTypes
object FuzzyHashGenerate {

    private val SSDEEP_TOTAL_SIZE_MAX = blockSizeShift(NUM_BLOCKHASHES - 1) * SPAMSUM_LENGTH.toULong()

    private const val b64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * 生成模糊哈希步骤，总共分两大步：
     * 1. 根据传入的字符串逐个滚动哈希，并且计算每个blockSize的强哈希值
     * 2. 根据滚动哈希计算的结果，进行消化总结，生成最终的模糊哈希值
     */
    fun fuzzyHashGenerate(inputStr: String): String? {
        //1. 初始化上下文对象
        val fuzzyContext = FuzzyState(
                fixedSize = 0,
                reduceBorder = MIN_BLOCKSIZE.toLong() * SPAMSUM_LENGTH,
                bhStart = 0,
                bhEnd = 1,
                bhEndLimit = NUM_BLOCKHASHES - 1,
                flags = 0,
                rollMask = 0,
                rollState = RollState(
                        h1 = 0u,
                        h2 = 0u,
                        h3 = 0u,
                        n = 0
                ),
                lasth = Char.MIN_VALUE.toInt()
        )
        fuzzyContext.bh[0] = BlockHashContext(
                dIndex = 0,
                halfDigest = Char.MIN_VALUE,
                h = HASH_INIT,
                halfh = HASH_INIT
        )
        fuzzyContext.bh[0]!!.digest[0] = Char.MIN_VALUE
        //2.初始化上下文参数
        if (!fuzzySetTotalInputLength(fuzzyContext, inputStr)) {
            return null
        }
        inputStr.forEach {
            fuzzyEngineStep(fuzzyContext, it)
        }
        return fuzzyDigest(fuzzyContext)
    }

    private fun blockSizeShift(index: Int): ULong {
        return MIN_BLOCKSIZE shl index
    }

    /**
     * 初始化长度记录，及位移上限
     */
    private fun fuzzySetTotalInputLength(fuzzyContext: FuzzyState, inputStr: String): Boolean {
        var bi = 0
        //如果输入的字符串长度超过上限，则报错
        if (inputStr.length.toULong() > SSDEEP_TOTAL_SIZE_MAX) {
            println("input string too long!")
            return false
        }
        //如果是固定的状态，但是固定长度不等于输入字符串长度，则报错
        if (fuzzyContext.flags and FUZZY_STATE_SIZE_FIXED > 0 &&
                fuzzyContext.fixedSize != inputStr.length.toLong()
        ) {
            println("fixed size state, but size not equal!")
            return false
        }
        fuzzyContext.flags = fuzzyContext.flags or FUZZY_STATE_SIZE_FIXED
        fuzzyContext.fixedSize = inputStr.length.toLong()
        //确定位移的上限
        while (blockSizeShift(bi) * SPAMSUM_LENGTH.toULong() < inputStr.length.toULong()) {
            bi++
            if (bi == NUM_BLOCKHASHES - 2) {
                break
            }
        }
        bi++
        fuzzyContext.bhEndLimit = bi
        return true
    }

    /**
     * 最主要的哈希引擎逻辑
     */
    private fun fuzzyEngineStep(fuzzyContext: FuzzyState, singleChar: Char) {
        var h: UInt
        //计算滚动哈希的值
        RollHashUtil.rollHash(fuzzyContext.rollState, singleChar)
        //对于滚动哈希求和
        val horg: UInt = RollHashUtil.rollSum(fuzzyContext.rollState) + 1u
        //求出滚动哈希值与最小分片的商，便于之后调整分片值时直接进行移位操作
        h = horg / (MIN_BLOCKSIZE.toUInt())

        //先根据传进来的字符累加计算FNV，并取后6位（从已经计算好的table里面来取）
        for (j in fuzzyContext.bhStart until fuzzyContext.bhEnd) {
            if (null != fuzzyContext.bh[j]) {
                fuzzyContext.bh[j]!!.h = sumTable[fuzzyContext.bh[j]!!.h][singleChar.toInt() and 0x3f]
                fuzzyContext.bh[j]!!.halfh =
                        sumTable[fuzzyContext.bh[j]!!.halfh][singleChar.toInt() and 0x3f]
            }
        }
        if ((fuzzyContext.flags and FUZZY_STATE_NEED_LASTHASH) != 0) {
            fuzzyContext.lasth = sumTable[fuzzyContext.lasth][singleChar.toInt() and 0x3f]
        }
        //判断几个条件，如果都通过了则进行分片的逻辑
        if (horg == 0u) {
            println("rolling hash zero result")
            return
        }
        if ((h.toInt() and fuzzyContext.rollMask) != 0) {
            return
        }
        if (horg % MIN_BLOCKSIZE > 0u) {
            return
        }

        h = h shr fuzzyContext.bhStart
        var i = fuzzyContext.bhStart

        //达到分片条件
        run trigger@{
            do {
                with(fuzzyContext) {
                    if (null != bh[i] && bh[i]!!.dIndex == 0) {
                        fuzzyTryForkBlockHash(this)
                    }
                    if (null == bh[i]) {
                        bh[i] = BlockHashContext(
                                dIndex = 0,
                                halfDigest = Char.MIN_VALUE,
                                h = HASH_INIT,
                                halfh = HASH_INIT
                        )
                        bh[i]!!.digest[0] = Char.MIN_VALUE
                    }
                    bh[i]!!.digest[bh[i]!!.dIndex] = b64[bh[i]!!.h]
                    bh[i]!!.halfDigest = b64[bh[i]!!.halfh]
                    if (bh[i]!!.dIndex < SPAMSUM_LENGTH - 1) {
                        bh[i]!!.digest[++(bh[i]!!.dIndex)] = Char.MIN_VALUE
                        bh[i]!!.h = HASH_INIT
                        if (bh[i]!!.dIndex < SPAMSUM_LENGTH / 2) {
                            bh[i]!!.halfh = HASH_INIT
                            bh[i]!!.halfDigest = Char.MIN_VALUE
                        }
                    } else {
                        fuzzyTryReduceBlockHash(fuzzyContext)
                    }
                    if ((h and 1u) != 0u) {
                        return@trigger
                    }
                    h = h shr 1
                }
            } while (++i < fuzzyContext.bhEnd)
        }
    }

    /**
     * 扩展一个新的blocksize对应的数组
     */
    private fun fuzzyTryForkBlockHash(fuzzyContext: FuzzyState) {
        val oldbh = fuzzyContext.bh[fuzzyContext.bhEnd - 1]
        if (fuzzyContext.bhEnd <= fuzzyContext.bhEndLimit) {
            if(null == fuzzyContext.bh[fuzzyContext.bhEnd]){
                fuzzyContext.bh[fuzzyContext.bhEnd] = BlockHashContext(
                        dIndex = 0,
                        halfDigest = Char.MIN_VALUE,
                        h = oldbh?.h ?: HASH_INIT,
                        halfh = oldbh?.halfh ?: HASH_INIT
                )
                fuzzyContext.bh[fuzzyContext.bhEnd]!!.digest[0] = Char.MIN_VALUE
                fuzzyContext.bhEnd++
            }
        } else if (fuzzyContext.bhEnd == NUM_BLOCKHASHES &&
                (fuzzyContext.flags and FUZZY_STATE_NEED_LASTHASH == 0)
        ) {
            fuzzyContext.flags = fuzzyContext.flags or FUZZY_STATE_NEED_LASTHASH
            fuzzyContext.lasth = oldbh?.h ?: Char.MIN_VALUE.toInt()
        }
    }

    /**
     * 更新模糊哈希的界限，及屏蔽位数,并且舍弃对应bhStart，右移一位
     */
    private fun fuzzyTryReduceBlockHash(fuzzyContext: FuzzyState) {
        if (fuzzyContext.bhEnd - fuzzyContext.bhStart < 2) {
            return
        }
        if (fuzzyContext.reduceBorder >= fuzzyContext.fixedSize) {
            return
        }
        if (null != fuzzyContext.bh[fuzzyContext.bhStart + 1] && fuzzyContext.bh[fuzzyContext.bhStart + 1]!!.dIndex < SPAMSUM_LENGTH / 2) {
            return
        }
        fuzzyContext.bhStart++
        fuzzyContext.reduceBorder *= 2
        fuzzyContext.rollMask = fuzzyContext.rollMask * 2 + 1
    }

    /**
     * 根据哈希的计算值，拼装完整的计算结果
     */
    private fun fuzzyDigest(fuzzyContext: FuzzyState): String? {
        var bi = fuzzyContext.bhStart
        val h = RollHashUtil.rollSum(fuzzyContext.rollState)
        var result: String

        if(fuzzyContext.bhStart > fuzzyContext.bhEnd){
            println("start point is larger than end")
            return null
        }
        if (!(bi == 0 || blockSizeShift(bi).toLong() / 2 * SPAMSUM_LENGTH < fuzzyContext.fixedSize)) {
            println("length with blocksize shift $bi is to large")
            return null
        }

        if (fuzzyContext.fixedSize.toULong() > SSDEEP_TOTAL_SIZE_MAX) {
            println("fixed size is larger then limit")
            return null
        }

        //估算blocksize确定值
        while (blockSizeShift(bi).toLong() * SPAMSUM_LENGTH < fuzzyContext.fixedSize) {
            bi++
        }
        if (bi >= fuzzyContext.bhEnd) {
            bi = fuzzyContext.bhEnd - 1
        }
        while (bi > fuzzyContext.bhStart && (null == fuzzyContext.bh[bi] ||
                        (null != fuzzyContext.bh[bi] && fuzzyContext.bh[bi]!!.dIndex < SPAMSUM_LENGTH / 2))
        ) {
            bi--
        }
        if (bi > 0 && (null == fuzzyContext.bh[bi] || fuzzyContext.bh[bi]!!.dIndex < SPAMSUM_LENGTH / 2)) {
            println("block size length is smaller than spamsum length")
            return null
        }

        //拼装结果
        //先拼装一倍blocksize的结果,判断滚动哈希不为空，则表示最新的一个分片采用滚动哈希的值
        if (h != 0u) {
            fuzzyContext.bh[bi]!!.digest[fuzzyContext.bh[bi]!!.dIndex] = b64[fuzzyContext.bh[bi]!!.h]
        }
        result =
                "${blockSizeShift(bi)}:${String(fuzzyContext.bh[bi]!!.digest).filter {it != Char.MIN_VALUE}}"
        if (result.length > FUZZY_MAX_RESULT - 1) {
            println("result length is smaller than fuzzy max result")
            return null
        }

        //再拼装两倍blocksize的结果
        if (bi < fuzzyContext.bhEnd - 1) {
            ++bi
            if (null == fuzzyContext.bh[bi]) {
                println("fuzzy context is null")
                return null
            }
            var secondHash = String(fuzzyContext.bh[bi]!!.digest).filter { it != Char.MIN_VALUE }
            if (secondHash.length > SPAMSUM_LENGTH / 2 - 1
            ) {
                secondHash = secondHash.substring(0, SPAMSUM_LENGTH / 2 - 1)
            }
            fuzzyContext.bh[bi]!!.digest[fuzzyContext.bh[bi]!!.dIndex] = if (h != 0u) {
                b64[fuzzyContext.bh[bi]!!.halfh]
            } else {
                fuzzyContext.bh[bi]!!.halfDigest
            }
            if (fuzzyContext.bh[bi]!!.digest[fuzzyContext.bh[bi]!!.dIndex] != Char.MIN_VALUE
            ) {
                secondHash = secondHash.plus(fuzzyContext.bh[bi]!!.digest[fuzzyContext.bh[bi]!!.dIndex])
            }
            result = "$result:$secondHash"
        } else if (h != 0u) {
            if (bi != 0 && bi != NUM_BLOCKHASHES - 1) {
                println("block index is not equal to number blockhashes")
                return null
            }
            result = if(bi == 0) {
                if(null != fuzzyContext.bh[bi]) {
                    "$result:${b64[fuzzyContext.bh[bi]!!.h]}"
                } else {
                    result
                }
            } else {
                "$result:${b64[fuzzyContext.lasth]}"
            }
        }
//        result = result.plus(0.toChar())
        if (result.length > FUZZY_MAX_RESULT) {
            println("result length is larger than fuzzy max result")
            return null
        }
        return result
    }
}
