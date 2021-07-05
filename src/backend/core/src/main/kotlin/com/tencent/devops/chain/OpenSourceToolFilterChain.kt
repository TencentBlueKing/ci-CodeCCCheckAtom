package com.tencent.devops.chain

import com.tencent.devops.chain.filters.IToolFilter

/**
 * 利用责任链方式做开源扫描的工具过滤
 */
class OpenSourceToolFilterChain {

    private val toolFilterChain = mutableListOf<IToolFilter>()


    /**
     * 添加处理链
     */
    fun addChain(toolFilter: IToolFilter): OpenSourceToolFilterChain {
        toolFilterChain.add(toolFilter)
        return this
    }


    /**
     * 链中的过滤器迭代执行
     */
    fun process(scanTools: MutableList<String>): MutableList<String> {
        if (toolFilterChain.isEmpty()) {
            return mutableListOf()
        }
        //如果pair的第一部分是false，则不会再过滤，否则会继续过滤
        return toolFilterChain.sortedBy { it.order }.fold(Pair(true, scanTools)) { acc, iToolFilter ->
            if (acc.first) {
                iToolFilter.processFilterTool(acc.second)
            } else {
                Pair(false, acc.second)
            }
        }.second.toMutableList()
    }

}