package com.tencent.devops.common.docker

import com.tencent.devops.common.api.SubScan
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.pojo.ToolOptions
import com.tencent.devops.docker.tools.LogUtils

class CommonSubScan :SubScan {
    override fun removeCompiledLanguages(inputLanguage: Long): Long {
        return inputLanguage
    }

    override fun toolOptionsPro(toolName:String, commandParam: CommandParam, analyzeConfigInfo: AnalyzeConfigInfo, inputData: MutableMap<String, Any?>): Any? {
        if (toolName == ToolConstants.CODEQL) {
            val option = ToolOptions(
                    optionName = "subPath",
                    optionValue = commandParam.codeqlHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append codeql success")
        }
        if (toolName == ToolConstants.CLANG) {
            val option = ToolOptions(
                    optionName = "subPath",
                    optionValue = commandParam.clangHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append clang success")
        }
        if (toolName == ToolConstants.SPOTBUGS) {
            val option = ToolOptions(
                    optionName = "subPath",
                    optionValue = commandParam.spotBugsHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append spotbugs success")
        }
        if (toolName == ToolConstants.PINPOINT) {
            // pinpoint仅支持linux，公共机直接有包，第三方机需要挂载，所以不用下载
            val option = ToolOptions(
                    optionName = "subPath",
                    optionValue = commandParam.pinpointHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append pinpoint success")
        }
        return inputData["toolOptions"]
    }
}