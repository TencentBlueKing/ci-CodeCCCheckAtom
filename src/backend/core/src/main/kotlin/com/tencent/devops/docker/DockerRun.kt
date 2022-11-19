package com.tencent.devops.docker

import com.tencent.bk.devops.plugin.docker.PcgDevCloudExecutor
import com.tencent.bk.devops.plugin.docker.DockerApi
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogRequest
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogResponse
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunRequest
import com.tencent.bk.devops.plugin.docker.pojo.common.DockerStatus
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import java.io.File

object DockerRun {
    val api = DockerApi()

    // 64位containerId，预留docker后续升级拓展位
    private val containerIdRegex = Regex("^[0-9a-z]{64,}$")

    fun runImage(imageParam: ImageParam, commandParam: CommandParam, toolName: String) {
        LogUtils.printLog("execute image params: $imageParam")

        val param = DockerRunRequest(
            userId = commandParam.landunParam.userId,
            imageName = imageParam.imageName,
            command = imageParam.command,
            dockerLoginUsername = imageParam.registryUser,
            dockerLoginPassword = imageParam.registryPwd,
            workspace = File(commandParam.landunParam.streamCodePath),
            extraOptions = imageParam.env.plus(mapOf(
                "devCloudAppId" to (commandParam.extraPrams["devCloudAppId"] ?: ""),
                "devCloudUrl" to (commandParam.extraPrams["devCloudUrl"] ?: ""),
                "devCloudToken" to (commandParam.extraPrams["devCloudToken"] ?: ""),
                PcgDevCloudExecutor.PCG_TOKEN_SECRET_ID to (commandParam.extraPrams[PcgDevCloudExecutor.PCG_TOKEN_SECRET_ID] ?: ""),
                PcgDevCloudExecutor.PCG_TOKEN_SECRET_KEY to (commandParam.extraPrams[PcgDevCloudExecutor.PCG_TOKEN_SECRET_KEY] ?: ""),
                PcgDevCloudExecutor.PCG_REQUEST_HOST to (commandParam.extraPrams[PcgDevCloudExecutor.PCG_REQUEST_HOST] ?: "")
            ))
        )
        val dockerRunResponse = api.dockerRunCommand(
            projectId = commandParam.landunParam.devopsProjectId,
            pipelineId = commandParam.landunParam.devopsPipelineId,
            buildId = commandParam.landunParam.buildId,
            param = param
        ).data!!

        var extraOptions = dockerRunResponse.extraOptions
        val channelCode = CodeccConfig.getConfig("LANDUN_CHANNEL_CODE")

        val isGongFengScan = channelCode == "GONGFENGSCAN" || commandParam.extraPrams["BK_CODECC_SCAN_MODE"] == "GONGFENGSCAN"

        val timeGap = if (isGongFengScan) 30 * 1000L else 5000L
        for (i in 1..100000000) {
            Thread.sleep(timeGap)

            val runLogResponse = getRunLogResponse(api, commandParam, extraOptions, timeGap)

            extraOptions = runLogResponse.extraOptions

            var isBlank = false
            runLogResponse.log?.forEachIndexed { index, s ->
                if (s.isBlank()) {
                    isBlank = true
                    LogUtils.printStr(".")
                } else {
                    if (isBlank) {
                        isBlank = false;
                        LogUtils.printLog("")
                    }
                    LogUtils.printLog("[docker]: $s")
                }
            }

            when (runLogResponse.status) {
                DockerStatus.success -> {
                    LogUtils.printLog("docker run success: $runLogResponse")
                    return
                }
                DockerStatus.failure -> {
                    throw CodeccTaskExecException(errorMsg = "docker run fail: $runLogResponse", toolName = toolName)
                }
                else -> {
                    if (i % 16 == 0) LogUtils.printLog("docker run status: $runLogResponse")
                }
            }
        }
    }

    private fun getRunLogResponse(api: DockerApi, commandParam: CommandParam, extraOptions: Map<String, String>, timeGap: Long): DockerRunLogResponse {
        var response: DockerRunLogResponse? = null
        try {
            response = api.dockerRunGetLog(
                projectId = commandParam.landunParam.devopsProjectId,
                pipelineId = commandParam.landunParam.devopsPipelineId,
                buildId = commandParam.landunParam.buildId,
                param = DockerRunLogRequest(
                    userId = commandParam.landunParam.userId,
                    workspace = File(commandParam.landunParam.streamCodePath),
                    timeGap = timeGap,
                    extraOptions = extraOptions
                )
            ).data!!
            return response
        } catch (e: Exception) {
            LogUtils.printErrorLog("get docker run log response: $response")
            LogUtils.printErrorLog("fail to get docker run log: ${commandParam.landunParam.buildId}, " +
                    "${commandParam.landunParam.devopsVmSeqId}, " +
                    extraOptions.filter { !it.key.contains("token", ignoreCase = true)  }
            )

            // container id不存在则认为失败
            val containerId = extraOptions["dockerContainerId"]
            if (containerId.isNullOrBlank() || !containerId.matches(containerIdRegex)) {
                LogUtils.printErrorLog("get docker containerId fail : $containerId")
                throw CodeccDependentException(e.message ?: "")
            } else {
                e.printStackTrace()
            }
        }
        return DockerRunLogResponse(
            log = listOf(),
            status = DockerStatus.running,
            message = "",
            extraOptions = extraOptions
        )
    }
}
