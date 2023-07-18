package com.tencent.devops.docker.scm

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.pojo.exception.user.CodeCCUserException
import com.tencent.devops.utils.P4Client
import com.tencent.devops.utils.common.CredentialUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.io.File

abstract class Scm(
    open val commandParam: CommandParam,
    open val toolName: String,
    open val streamName: String,
    open val taskId: Long
) {

    fun scmOperate(): Boolean {
        if (commandParam.repos.isNullOrEmpty()) {
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
            runCmd(imageParam, inputFile, outputFile)
            // DockerRun.runImage(imageParam, commandParam, toolName)
        } catch (e: Throwable) {
            LogUtils.printLog("Scm operate exception, message: ${ExceptionUtils.getStackTrace(e)}")
            scmOpFail(inputFile)
            throw CodeCCScmException(
                ErrorCode.SCM_COMMAND_RUN_FAIL,
                e.message ?: "",
                arrayOf("")
            )
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

    fun scmLocalOperate(): Boolean {
        if (commandParam.repos.isNullOrEmpty()) {
            LogUtils.printLog("no scm element, return")
            return true
        }

        val inputFile = generateLocalInputFile()
        val outputFile = generateOutputFile()

        localRunCmd(inputFile,outputFile)

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

    protected fun getP4Client(): P4Client {
        val p4RepoInfo = commandParam.repos.filter {
            it.type == "perforce"
        }

        if (p4RepoInfo.size != 1) {
            LogUtils.printLog("perforce repo info is invalid, $p4RepoInfo")
            if (p4RepoInfo.isEmpty()) {
                throw CodeCCUserException(
                    ErrorCode.USER_NO_P4_REPO_FOUNT,
                    "none perforce repo info found!"
                )
            }
        }

        // 从流水线变量拿上游 perforce 信息创建客户端建立连接
        val perforceRepoInfo = p4RepoInfo.first()
        val (userName, credential) =
            CredentialUtils.getCredentialWithType(perforceRepoInfo.perforceTicketId ?: "").first
        return P4Client(
            uri = perforceRepoInfo.perforceDepotUrl!!,
            userName = userName,
            password = credential,
            charsetName = perforceRepoInfo.perforceCharset ?: "none",
            clientName = perforceRepoInfo.perforceClientName
        )
    }

    protected abstract fun localRunCmd(inputFile: String, outputFile: String)

    protected abstract fun generateCmd(inputFile: String, outputFile: String): List<String>

    protected abstract fun scmOpFail(inputFile: String)

    protected abstract fun uploadInputFile(inputFile: String)

    protected abstract fun scmOpSuccess(outputFile: String)

    protected abstract fun generateInputFile(): String

    protected abstract fun generateLocalInputFile(): String

    protected abstract fun generateOutputFile(): String

    protected abstract fun runCmd(imageParam: ImageParam, inputFile: String, outputFile: String)
}
