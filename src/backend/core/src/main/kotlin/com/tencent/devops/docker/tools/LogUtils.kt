package com.tencent.devops.docker.tools

import com.tencent.devops.docker.LocalParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.utils.CommonUtils
import org.slf4j.LoggerFactory

object LogUtils {

    private var isDebug = false

    private val logger = LoggerFactory.getLogger(LogUtils::class.java)

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun getDebug(): Boolean {
        return isDebug
    }

    // 下面日志格式不能改到
    fun printDefect(defect: DefectsEntity, toolName: String) {
        when {
            defect.ccn != null -> {
                logger.infoInTag(CommonUtils.changePathToWindows(defect.filePath ?: "") + ":" + defect.startLine
                    + " ccn:" + defect.ccn
                    + " function_name:" + defect.functionName
                    + " function_lines:" + defect.functionLines + " ", toolName)
            }
            defect.dupRate != null -> {
                logger.infoInTag(CommonUtils.changePathToWindows(defect.file_path ?: "") + " dup_rate:" + defect.dupRate
                    + " dup_lines:" + defect.dupLines, toolName)
            }
            else -> {
                logger.infoInTag((defect.severity ?: "")+ " ["+(toolName ?: "")+"] "+ CommonUtils.changePathToWindows(defect.filePath ?: "") + ":" + (defect.line
                    ?: "") + " " + (defect.checkerName ?: "") + " " + (defect.description ?: ""), toolName)
            }
        }
    }

    fun printLog(msg: Any?) {
        logger.infoInTag("[${getToolName()}]" + msg?.toString(), getToolName())
    }

    fun printErrorLog(msg: Any?) {
        logger.errorInTag("[${getToolName()}]" + msg?.toString(), getToolName())
    }

    fun printErrorLog(msg: Any?, t: Throwable) {
        logger.error("[${getToolName()}]" + msg?.toString(), getToolName(), t)
    }

    fun printStr(msg: Any?) {
        print(msg)
    }

    fun printDebugLog(msg: Any?) {
        if (this.isDebug) {
            logger.infoInTag(msg?.toString(), getToolName())
        }
    }

    fun finishLogTag(toolName: String) {
        logger.finishTag("$toolName scan finish", toolName)
    }

    private fun getToolName(): String {
        var toolName = LocalParam.toolName.get()
        if (toolName.isNullOrBlank()) toolName = "common"
        return toolName
    }
}
