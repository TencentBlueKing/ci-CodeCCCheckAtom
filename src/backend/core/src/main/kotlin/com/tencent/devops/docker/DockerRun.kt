package com.tencent.devops.docker

import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.plugin.api.impl.KubernetesBuildApi
import com.tencent.bk.devops.plugin.docker.CommonExecutor
import com.tencent.bk.devops.plugin.docker.PcgDevCloudExecutor
import com.tencent.bk.devops.plugin.docker.DockerApi
import com.tencent.bk.devops.plugin.docker.KubernetesExecutor
import com.tencent.bk.devops.plugin.docker.ThirdPartExecutor
import com.tencent.bk.devops.plugin.docker.exception.DockerRunLogException
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogRequest
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunLogResponse
import com.tencent.bk.devops.plugin.docker.pojo.DockerRunRequest
import com.tencent.bk.devops.plugin.docker.pojo.common.DockerStatus
import com.tencent.bk.devops.plugin.docker.pojo.common.KubernetesPodStatus
import com.tencent.bk.devops.plugin.docker.pojo.job.response.JobStatusResp
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import java.io.File

object DockerRun {
    val api = DockerApi()

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
            )),
            // 不指定CPU
            cpu = null,
            memory = null
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
        try {
            return dockerRunGetLog(
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
        } catch (e: Exception) {
            LogUtils.printErrorLog("fail to get docker run log: ${commandParam.landunParam.buildId}, " +
                "${commandParam.landunParam.devopsVmSeqId}, " +
                extraOptions.filter { !it.key.contains("token", ignoreCase = true) }
            )
            e.printStackTrace()
        }
        return DockerRunLogResponse(
            log = listOf(),
            status = DockerStatus.running,
            message = "",
            extraOptions = extraOptions
        )
    }

    private fun dockerRunGetLog(
        projectId: String,
        pipelineId: String,
        buildId: String,
        param: DockerRunLogRequest
    ): Result<DockerRunLogResponse> {
        try {
            val property = System.getenv("devops_slave_model")
            val kubernetesEnv = System.getenv("devops.build.node.environment")

            val response = when {
                "docker" == property -> CommonExecutor.getLogs(projectId, pipelineId, buildId, param)
                "Kubernetes" == kubernetesEnv -> getLogs(param)
                else -> ThirdPartExecutor.getLogs(param)
            }


            return com.tencent.bk.devops.atom.pojo.Result(response)
        } catch (e: Exception) {
            throw DockerRunLogException(e.message ?: "")
        }
    }

    fun getLogs(param: DockerRunLogRequest): DockerRunLogResponse {
        val extraOptions = param.extraOptions.toMutableMap()

        val api = KubernetesBuildApi()

        LogUtils.printLog("[kubernetes]:getLog|param|$param")

        // get job status
        val jobStatusFlag = param.extraOptions["jobStatusFlag"]
        val jobName = param.extraOptions["kubernetesJobName"] ?: throw RuntimeException("kubernetesJobName is null")
        var jobStatusResp: JobStatusResp? = null
        var jobIp = ""
        if (jobStatusFlag.isNullOrBlank() || jobStatusFlag == DockerStatus.running) {
            jobStatusResp = api.getJobStatus(jobName).data

            LogUtils.printLog("[kubernetes]:getLog|jobStatusFlag|resp|$jobStatusResp")

            jobIp = jobStatusResp?.pod_result!![0].ip ?: ""
            val jobStatus = jobStatusResp.status
            if (KubernetesPodStatus.failed != jobStatus &&
                KubernetesPodStatus.successed != jobStatus &&
                KubernetesPodStatus.running != jobStatus
            ) {
                return DockerRunLogResponse(
                    status = DockerStatus.running,
                    message = "get job status...",
                    extraOptions = extraOptions
                )
            }
        }
        extraOptions["jobIp"] = jobIp
        extraOptions["jobStatusFlag"] = DockerStatus.success

        // actual get log logic
        val startTimeStamp = extraOptions["startTimeStamp"]?.apply {
            if (trim().length == 13) {
                toLong() / 1000
            } else {
                toLong()
            }
        }?.toLong() ?: (System.currentTimeMillis() / 1000)
        val logs = mutableListOf<String>()

        val logResult = api.getLog(jobName, startTimeStamp)

        // only if not blank then add start time
        val isNotBlank = logResult.isNullOrBlank()
        if (!isNotBlank) extraOptions["startTimeStamp"] = (startTimeStamp + param.timeGap).toString()

        // add logs
        if (!isNotBlank) logs.add(logResult!!)

        if (jobStatusResp == null) {
            jobStatusResp = api.getJobStatus(jobName).data
        }
        val finalStatus = jobStatusResp
        val podResults = finalStatus?.pod_result
        podResults?.forEach { ps ->
            ps.events?.forEach { event ->
                // add logs
                logs.add(event.message)
            }
        }
        LogUtils.printLog("[kubernetes]:getLog|finalStatus|resp|$finalStatus")

        if (finalStatus?.status in listOf(KubernetesPodStatus.failed, KubernetesPodStatus.successed)) {
            Thread.sleep(6000)
            val finalLogs = api.getLog(jobName, startTimeStamp + 6000)
            if (finalStatus?.status == KubernetesPodStatus.failed) {
                return DockerRunLogResponse(
                    log = logs.plus(finalLogs ?: ""),
                    status = DockerStatus.failure,
                    message = "docker run fail...",
                    extraOptions = extraOptions
                )
            }
            return DockerRunLogResponse(
                log = logs.plus(finalLogs ?: ""),
                status = DockerStatus.success,
                message = "docker run success...",
                extraOptions = extraOptions
            )
        }

        return DockerRunLogResponse(
            log = logs,
            status = DockerStatus.running,
            message = "get log...",
            extraOptions = extraOptions
        )
    }
}
