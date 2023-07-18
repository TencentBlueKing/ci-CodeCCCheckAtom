package com.tencent.devops.processor

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.processor.annotation.ProcessAnnotation

@ProcessAnnotation(name = "lintIgnore", type = "lint", order = 1)
class LintIgnoreDefectSubProcessor : AbstractDefectSubProcessor() {

    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        if(null == inputDefectInfo.ignoreDefectInfo || inputDefectInfo.ignoreDefectInfo.isNullOrEmpty()) {
            return inputDefectInfo
        }
        val inputDefectList = inputDefectInfo.lintDefects
        val ignoreDefectMap  = inputDefectInfo.ignoreDefectInfo.associateBy { it.lineNum }
        inputDefectList?.forEach {
            try{
                val ignoreDefect = ignoreDefectMap[it.line!!.toInt()]
                if(null != ignoreDefect && !ignoreDefect.ignoreRule.isNullOrEmpty()) {
                    if (ignoreDefect.ignoreRule.containsKey(it.checkerName)) {
                        val ignoreReason = ignoreDefect.ignoreRule[it.checkerName]
                        it.ignoreCommentDefect = true
                        it.ignoreCommentReason = ignoreReason
                    }
                }
            } catch (t : Throwable) {
                LogUtils.printLog("get lint ignore defect info failed! file path: ${inputDefectInfo.filePath}, " +
                        "line: ${it.line}, message: ${t.message}")
            }

        }
        return inputDefectInfo
    }
}
