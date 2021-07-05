package com.tencent.devops.common.api

import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam

interface SubScanComposer {
    fun getStatus(commandParam: CommandParam, analyzeConfigInfo: AnalyzeConfigInfo, toolName: String)

    fun uploadTaskLog(analyzeConfigInfo: AnalyzeConfigInfo, streamName: String, toolName: String, commandParam: CommandParam)

    fun downloadCovResultPro(commandParam: CommandParam, streamName: String, toolName: String)

    fun lastUploadTaskLog(analyzeConfigInfo: AnalyzeConfigInfo, streamName: String, toolName: String, commandParam: CommandParam)
}