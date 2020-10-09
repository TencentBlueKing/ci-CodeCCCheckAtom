package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.plugin.pojo.artifactory.ChannelCode
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CodeYaml
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.YAMLParse
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.sdk.CodeYmlFilterPathVO
import com.tencent.devops.pojo.sdk.FilterPathInput
import com.tencent.devops.pojo.sdk.NotifyCustom
import com.tencent.devops.pojo.sdk.ScanConfiguration
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.common.AtomUtils.parseStringToList
import com.tencent.devops.utils.common.AtomUtils.parseStringToSet
import org.slf4j.LoggerFactory
import java.io.File

object CodeccSdkUtils {

    private val logger = LoggerFactory.getLogger(CodeccSdkUtils::class.java)

    fun initCodecc(atomContext: AtomContext<CodeccCheckAtomParamV3>): CodeccExecuteConfig {
        val params = atomContext.param
        val codeccExecuteConfig = CodeccParamsHelper.getCodeccExecuteConfig(atomContext)

        if (AgentEnv.isThirdParty()) {
            logger.info("特别说明：")
            logger.info("当前使用第三方构建机，建议安装Docker！")
            if (CodeccEnvHelper.getOS() == OSType.LINUX) {
                logger.info("当前使用第三方Linux构建机未安装Docker，且非root账号启动agent, 请使用root帐号登录构建机运行以下命令：")
                logger.info("mkdir -p /data/codecc_software")
                logger.info("mount -t nfs -o xxx.xxxxx.com:/data/codecc_software /data/codecc_software")
            }
        }

        val debugProjects = (atomContext.getSensitiveConfParam("PROJECT_CODE_DEBUG")
            ?: "").split(",")
        if (debugProjects.contains(params.projectName) || atomContext.allParameters["BK_CI_CODECC_DEBUG"] == "true") {
            LogUtils.setDebug(true)
            LogUtils.printDebugLog("project is in debug list, set log to debug.")
        }

        if (params.channelCode != "CODECC" && params.channelCode != "CODECC_EE") {
            CodeccSdkApi.createTask(params, params.openScanPrj)
            CodeccSdkApi.updateScanConfiguration(getScanConfig(params), params.pipelineStartUserName)
            if (params.channelCode != "GONGFENGSCAN") {
                CodeccSdkApi.deleteFilter(params)
                CodeccSdkApi.addFilterPath(params)
            }
            CodeccSdkApi.report(getNotifyCustom(params))
        }

        // init id, name, tool
        if (params.channelCode != "GONGFENGSCAN") {
            val pipelineTaskVo = CodeccSdkApi.getTaskByPipelineId(params.pipelineId, params.pipelineStartUserName)
            params.codeCCTaskId = pipelineTaskVo.taskId.toString()
            params.codeCCTaskName = pipelineTaskVo.enName
            codeccExecuteConfig.tools = pipelineTaskVo.tools.map { it.toolName }
        }

        // deal with code yml filter
        val codeYmlFilterPathVO = getCodeYmlVo(codeccExecuteConfig.repos, params.bkWorkspace) ?: return codeccExecuteConfig
        CodeccSdkApi.addCodeYmlFilterPath(params, codeYmlFilterPathVO)

        return codeccExecuteConfig
    }

    private fun getCodeYmlVo(repos: List<CodeccExecuteConfig.RepoItem>, bkWorkspace: String): CodeYmlFilterPathVO? {
        val codeYmlFilterPathVO = CodeYmlFilterPathVO()
        try {
            repos.forEach { repo ->
                val parent = File(bkWorkspace, repo.relPath)
                val codeYml = File(parent, ".code.yml")
                if (codeYml.exists()) {
                    logger.info(".code.yml exist in path: ${codeYml.canonicalPath}")
                    logger.info(codeYml.readText())
                    val codeYmlItem = YAMLParse.parseDto(codeYml.canonicalPath, CodeYaml::class)
                    codeYmlFilterPathVO.autoGenFilterPath.addAll(filterEmptyList(codeYmlItem.source?.auto_generate_source?.filepath_regex))
                    codeYmlFilterPathVO.testSourceFilterPath.addAll(filterEmptyList(codeYmlItem.source?.test_source?.filepath_regex))
                    codeYmlFilterPathVO.thirdPartyFilterPath.addAll(filterEmptyList(codeYmlItem.source?.third_party_source?.filepath_regex))
                }
            }
        } catch (e: Exception) {
            System.err.println("解析.code.yml失败: ${e.message}")
            return null
        }
        return codeYmlFilterPathVO
    }

    private fun filterEmptyList(list: List<String>?): List<String> {
        return list?.filter { !it.isBlank() } ?: listOf()
    }

    fun executeAsyncTask(codeccTaskId: Long, userId: String) {
        val result = CodeccSdkApi.executeTask(codeccTaskId, userId)
        if (result.data != true) {
            throw CodeccDependentException("启动任务失败，信息：${result.message}")
        }
    }

    private fun getNotifyCustom(params: CodeccCheckAtomParamV3): NotifyCustom {
        val reportTimeArr = if (params.reportTime.isNullOrBlank()) listOf()
        else params.reportTime!!.split(":")
        return NotifyCustom(
            taskId = params.codeCCTaskId,
            rtxReceiverType = params.rtxReceiverType,
            rtxReceiverList = parseStringToSet(params.rtxReceiverList),
            emailReceiverType = params.emailReceiverType,
            emailReceiverList = parseStringToSet(params.emailReceiverList),
            emailCCReceiverList = parseStringToSet(params.emailCCReceiverList),
            reportStatus = params.reportStatus?.toInt(),
            reportDate = parseStringToList(params.reportDate).map { it.toInt() },
            reportTime = reportTimeArr.firstOrNull()?.toInt(),
            reportMinute = reportTimeArr.lastOrNull()?.toInt(),
            instantReportStatus = params.instantReportStatus,
            reportTools = parseStringToSet(params.reportTools),
            botWebhookUrl = params.botWebhookUrl,
            botRemindSeverity = params.botRemindSeverity?.toInt(),
            botRemaindTools = parseStringToSet(params.botRemaindTools, setOf("COVERITY", "KLOCWORK")),
            botRemindRange = params.botRemindRange?.toInt()
        )
    }

    private fun getScanConfig(params: CodeccCheckAtomParamV3): ScanConfiguration {
        return ScanConfiguration(
            taskId = params.codeCCTaskId,
            scanType = params.toolScanType?.toInt(),
            timeAnalysisConfig = null,
            transferAuthorList = if (params.transferAuthorList.isNullOrBlank()) listOf() else JsonUtil.getObjectMapper().readValue(params.transferAuthorList!!),
            newDefectJudge = ScanConfiguration.NewDefectJudge(
                fromDate = params.newDefectJudgeFromDate,
                judgeBy = params.newDefectJudgeBy?.toInt()
            ),
            mrCommentEnable = params.mrCommentEnable
        )
    }
}