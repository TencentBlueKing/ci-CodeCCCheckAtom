package com.tencent.devops.processor

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.processor.annotation.ProcessAnnotation

@ProcessAnnotation(name = "ccnIgnore", type = "ccn", order = 1)
class CCNIgnoreDefectSubProcessor : AbstractDefectSubProcessor() {

    companion object{
        private const val ccnRule = "CCN"
        private const val ccnThresholdRule = "CCN_threshold"
    }

    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        if(null == inputDefectInfo.ignoreDefectInfo || inputDefectInfo.ignoreDefectInfo.isNullOrEmpty()) {
            return inputDefectInfo
        }
        val inputDefectList = inputDefectInfo.ccnDefects
        val ignoreDefectMap  = inputDefectInfo.ignoreDefectInfo.associateBy { it.lineNum }
        inputDefectList?.forEach {
            try{
                val ignoreDefect = ignoreDefectMap[it.startLine.toInt()]
                if(null != ignoreDefect && !ignoreDefect.ignoreRule.isNullOrEmpty()) {
                    if (ignoreDefect.ignoreRule.containsKey(ccnRule)) {
                        val ignoreReason = ignoreDefect.ignoreRule[ccnRule]
                        it.ignoreCommentDefect = true
                        it.ignoreCommentReason = ignoreReason
                    } else if (ignoreDefect.ignoreRule.containsKey(ccnThresholdRule)) {
                        val ignoreReason = ignoreDefect.ignoreRule[ccnThresholdRule]
                        it.ignoreCommentDefect = true
                        it.ignoreCommentReason = ignoreReason
                    }
                }
            } catch (t: Throwable) {
                LogUtils.printLog("get ccn ignore defect info failed! file path: ${inputDefectInfo.filePath}, " +
                        "start line: ${it.startLine}, message: ${t.message}")
            }

        }
        return inputDefectInfo
    }
}
