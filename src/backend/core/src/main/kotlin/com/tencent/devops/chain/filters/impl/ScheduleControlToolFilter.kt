package com.tencent.devops.chain.filters.impl

import com.tencent.devops.chain.filters.IToolFilter

/**
 * 是否定时工具过滤
 */
class ScheduleControlToolFilter : IToolFilter(1) {

    /**
     * 如果是定时的话，则还需要判断，如果是手动的话，则直接通过
     */
    override fun processFilterTool(scanTools: MutableList<String>): Pair<Boolean, MutableList<String>> {
        println("enter schedule tool filter process!")
        val scheduledTriggerFlag = System.getenv("scheduledTriggerPipeline")
        return if (!(!scheduledTriggerFlag.isNullOrBlank() && scheduledTriggerFlag == "false")) {
            Pair(true, scanTools)
        } else {
            println("【schedule control tool filter】 非定时触发的自动放开")
            Pair(false, scanTools)
        }
    }
}