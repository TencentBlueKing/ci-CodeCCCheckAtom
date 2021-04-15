package com.tencent.devops.chain.filters.impl

import com.tencent.devops.chain.filters.IToolFilter

class ManualControlToolFilter : IToolFilter(1) {

    /**
     * 如果是手动的话，则直接通过，如果是定时的话，则还需要往下走
     */
    override fun processFilterTool(scanTools: MutableList<String>): Pair<Boolean, MutableList<String>> {
        println("enter manual tool filter process!")
        val manualTriggerFlag = System.getenv("manualTriggerPipeline")
        return if (!manualTriggerFlag.isNullOrBlank() && manualTriggerFlag == "true") {
            println("【manual control tool filter】 手动触发的自动放开")
            Pair(false, scanTools)
        } else {
            Pair(true, scanTools)
        }
    }
}