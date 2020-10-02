package com.tencent.devops.docker.scm

import com.tencent.devops.docker.DockerRun
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import java.io.File

abstract class Scm(
    open val commandParam: CommandParam,
    open val toolName: String,
    open val streamName: String,
    open val taskId: Long
) {

    fun scmOperate(): Boolean {
        if (commandParam.repoUrlMap.isBlank()) {
            LogUtils.printLog("no scm element, return")
            return true
        }
        val inputFile = generateInputFile()
        val outputFile = generateOutputFile()
        val dockerInputFile = CommonUtils.changePathToDocker(inputFile)
        val dockerOutputFile = CommonUtils.changePathToDocker(outputFile)

        val cmd = generateCmd(dockerInputFile, dockerOutputFile)
        if (cmd.isEmpty()) {
            LogUtils.printLog("cmd is empty")
            return true
        }
        LogUtils.printDebugLog("cmd: $cmd")
        val imageParam = CodeccConfig.getImage("scm")
        imageParam.command = cmd
        try {
            DockerRun.runImage(imageParam, commandParam)
        } catch (e: Throwable) {
            LogUtils.printLog("Scm operate exception, message: ${e.message}")
            scmOpFail(inputFile)
            throw CodeccTaskExecException(e.message ?: "")
        }

        // 生成output
        return if (File(outputFile).exists()) {
            uploadInputFile(inputFile)
            scmOpSuccess(outputFile)
            true
        } else {
            scmOpFail(inputFile)
            false
        }
    }

    protected abstract fun generateCmd(inputFile: String, outputFile: String): List<String>

    protected abstract fun scmOpFail(inputFile: String)

    protected abstract fun uploadInputFile(inputFile: String)

    protected abstract fun scmOpSuccess(outputFile: String)

    protected abstract fun generateInputFile(): String

    protected abstract fun generateOutputFile(): String
}