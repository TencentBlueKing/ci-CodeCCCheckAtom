package com.tencent.devops.utils

import com.tencent.devops.chain.OpenSourceToolFilterChain
import com.tencent.devops.chain.filters.impl.BgToolFilter
import com.tencent.devops.chain.filters.impl.FrequencyControlToolFilter
import com.tencent.devops.chain.filters.impl.ManualControlToolFilter
import com.tencent.devops.chain.filters.impl.ScheduleControlToolFilter
import com.tencent.devops.docker.Build
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OpenScanConfigParam

object OpenSourceUtils {

    /**
     * 根据过滤条件过滤开源扫描的扫描工具
     */
    fun calculateOpensourceThread(scanTools: MutableList<String>,
                                  openScanConfigParam: OpenScanConfigParam,
                                  commandParam: CommandParam): MutableList<String> {
        LogUtils.printDebugLog("codeccTaskInfo create from is gongfeng_scan")
        println("openScanConfigParam is $openScanConfigParam")
        return if (Build.codeccTaskInfo!!.gongfengProjectId != null) {
            val openSourceToolFilterChain = OpenSourceToolFilterChain()
            openSourceToolFilterChain.addChain(ScheduleControlToolFilter()).addChain(ManualControlToolFilter()).
                    addChain(BgToolFilter(commandParam, openScanConfigParam)).addChain(FrequencyControlToolFilter(openScanConfigParam))
            openSourceToolFilterChain.process(scanTools)
        } else {
            scanTools.minus("RIPS").minus("rips").minus(ToolConstants.COMPILE_TOOLS).toMutableList()
        }
    }


}