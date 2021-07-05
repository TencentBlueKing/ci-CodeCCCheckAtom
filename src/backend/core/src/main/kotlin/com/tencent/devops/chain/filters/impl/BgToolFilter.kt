package com.tencent.devops.chain.filters.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.chain.filters.IToolFilter
import com.tencent.devops.docker.Build
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.docker.pojo.Result
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.GongfengStatProjVO
import com.tencent.devops.pojo.OpenScanConfigParam

class BgToolFilter constructor(
        private val commandParam: CommandParam,
        private val openScanConfigParam: OpenScanConfigParam
) : IToolFilter(2) {

    override fun processFilterTool(scanTools: MutableList<String>): Pair<Boolean, MutableList<String>> {
        println("enter bg tool filter process!")
        if (scanTools.isEmpty()) {
            println("empty list input, will return empty output")
            return Pair(true, mutableListOf())
        }
        var resultToolList = scanTools
        //获取配置的过滤清单
        val filterBgIdList = if (!openScanConfigParam.coverityFilterBg.isNullOrBlank()) {
            openScanConfigParam.coverityFilterBg!!.split(",")
        } else {
            emptyList()
        }
        //获取git信息
        val gitProjectInfo = getGitProjectInfo(commandParam.landunParam, Build.codeccTaskInfo!!.gongfengProjectId!!)
        println("git project info is $gitProjectInfo")
        //原有过滤逻辑，必要时可以去掉
        if (!openScanConfigParam.openScanFilterBgId.isNullOrBlank()) {
            println("开源扫描工程，需要过滤的bgId: ${openScanConfigParam.openScanFilterBgId}")
            if (gitProjectInfo?.bgId != null) {
                if (openScanConfigParam.openScanFilterBgId!!.split(",").contains(gitProjectInfo.bgId.toString())) {
                    println("项目所属bgId:(${gitProjectInfo.bgId})在过滤条件中，将直接跳过...")
                    return Pair(false, mutableListOf())
                }
            } else {
                LogUtils.printDebugLog("项目所属bgId:(${gitProjectInfo?.bgId})不在过滤条件中，将直接跳过...")
            }
        }
        //根据bg_id过滤lint工具
        if (gitProjectInfo?.bgId == null || !filterBgIdList.contains(gitProjectInfo.bgId.toString())) {
            println("【bg tool filter】 non teg project or the other half of teg project need to remove rips")
            if (scanTools.contains("rips")) {
                LogUtils.printDebugLog("need to remove rips for gongfeng scan project！")
                resultToolList = resultToolList.minus("rips").toMutableList()
            }
            if (scanTools.contains("RIPS")) {
                LogUtils.printDebugLog("need to remove RIPS for gongfeng scan project！")
                resultToolList = resultToolList.minus("RIPS").toMutableList()
            }
        }

        //根据bg_id过滤coverity工具
        if (gitProjectInfo?.bgId == null || !filterBgIdList.contains(gitProjectInfo.bgId.toString())) {
            println("【bg tool filter】 开源扫描工程，过滤掉编译型工具, tools: $scanTools")
            resultToolList = resultToolList.minus(ToolConstants.COMPILE_TOOLS).toMutableList()
        }
        return Pair(true, resultToolList)
    }

    /**
     * 获取git项目信息
     */
    private fun getGitProjectInfo(landunParam: LandunParam, gitProjectId: Long): GongfengStatProjVO? {
        return try {
            val responseStr = CodeccWeb.codeccGitProjectInfoByProjectId(landunParam, gitProjectId)
            val result = jacksonObjectMapper().readValue<Result<GongfengStatProjVO>>(responseStr)
            result.data
        } catch (e: Throwable) {
            println("Get git project info failed, e: ${e.message}")
            null
        }
    }

}