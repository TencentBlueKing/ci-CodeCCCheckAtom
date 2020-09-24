package com.tencent.devops.common.docker

import com.tencent.devops.common.api.SubScanComposer
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.utils.CodeccWeb

class CommonSubScanComposer :SubScanComposer {

    override fun getStatus(commandParam: CommandParam, analyzeConfigInfo: AnalyzeConfigInfo, toolName: String) {
        ScanComposer.getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 4)
    }

    override fun uploadTaskLog(analyzeConfigInfo: AnalyzeConfigInfo, streamName: String, toolName: String, commandParam: CommandParam) {
        // 排队结束
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 1)

        // 下载开始
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 3)

        // 下载结束
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 1)

        // 扫描开始
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 3)
    }

    override fun downloadCovResultPro(commandParam: CommandParam, streamName: String, toolName: String) {
    }

    override fun lastUploadTaskLog(analyzeConfigInfo: AnalyzeConfigInfo, streamName: String, toolName: String, commandParam: CommandParam) {
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 1)

        ScanComposer.getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 4)
    }
}