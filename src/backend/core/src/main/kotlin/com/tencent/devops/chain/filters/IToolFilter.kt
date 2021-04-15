package com.tencent.devops.chain.filters

/**
 * 过滤器模板
 */
abstract class IToolFilter constructor(
        val order : Int
){

    /**
     * 处理抽象方法，传入上一个工具清单[scanTools],输出是否继续过滤标识及过滤工具清单
     */
    abstract fun processFilterTool(scanTools: MutableList<String>): Pair<Boolean, MutableList<String>>
}