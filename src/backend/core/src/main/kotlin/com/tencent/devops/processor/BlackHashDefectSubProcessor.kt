package com.tencent.devops.processor

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.processor.annotation.ProcessAnnotation
import org.apache.commons.codec.digest.DigestUtils

@ProcessAnnotation(name = "blackHash", type = "lint", order = 1)
class BlackHashDefectSubProcessor : AbstractDefectSubProcessor() {
    /**
     * 利用 description 值计算 BlackDuck 工具告警的 hash 值
     */
    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        LogUtils.printDebugLog("black hash: ${inputDefectInfo.lintDefects}")
        inputDefectInfo.lintDefects?.forEach {
            it.pinpointHash = DigestUtils.md5Hex(it.description)
        }
        return inputDefectInfo
    }
}
