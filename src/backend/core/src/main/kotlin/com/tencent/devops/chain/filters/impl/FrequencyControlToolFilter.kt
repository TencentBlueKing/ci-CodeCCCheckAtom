package com.tencent.devops.chain.filters.impl

import com.tencent.devops.chain.filters.IToolFilter
import com.tencent.devops.docker.Build
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.pojo.OpenScanConfigParam
import java.time.LocalDate

/**
 * 频率控制过滤器
 */
class FrequencyControlToolFilter constructor(
        private val openScanConfigParam: OpenScanConfigParam
) : IToolFilter(2) {
    override fun processFilterTool(scanTools: MutableList<String>): Pair<Boolean, MutableList<String>> {
        println("enter frequency control tool filter process!")
        var resultToolList = scanTools
        if (!filterCompileForOpenSource(Build.codeccTaskInfo!!.taskId, openScanConfigParam.coverityScanPeriod ?: 1)) {
            println("【frequency control tool filter】 开源扫描工程，过滤掉编译型工具, tools: $scanTools")
            resultToolList = resultToolList.minus(ToolConstants.COMPILE_TOOLS).toMutableList()
        }
        return Pair(true, resultToolList)
    }

    /**
     * coverity分天跑
     */
    private fun filterCompileForOpenSource(taskId: Long, modNum: Int): Boolean {
        if (taskId == 0L) {
            return false
        }
        val yearDay = LocalDate.now().dayOfYear
        println("mod value: ${(taskId % modNum).toInt()}, year day: $yearDay")
        return ((taskId % modNum).toInt() == (yearDay % modNum))
    }
}