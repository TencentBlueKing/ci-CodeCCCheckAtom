package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CodeYaml
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.YAMLParse
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.pojo.sdk.CodeYmlFilterPathVO
import com.tencent.devops.pojo.sdk.FilterPathInput
import com.tencent.devops.pojo.sdk.NotifyCustom
import com.tencent.devops.pojo.sdk.ScanConfiguration
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.common.AtomUtils
import com.tencent.devops.utils.common.AtomUtils.parseStringToList
import com.tencent.devops.utils.common.AtomUtils.parseStringToSet
import com.tencent.devops.utils.script.ScriptUtils
import org.slf4j.LoggerFactory
import java.io.File

object CodeccSdkUtils {

    private val logger = LoggerFactory.getLogger(CodeccSdkUtils::class.java)

    fun initCodecc(atomContext: AtomContext<CodeccCheckAtomParamV3>): CodeccExecuteConfig {
        val params = atomContext.param
        params.asynchronous = false
        val codeccExecuteConfig = CodeccParamsHelper.getCodeccExecuteConfig(atomContext)

        val codeccWorkspace = CodeccEnvHelper.getCodeccWorkspace(codeccExecuteConfig.atomContext.param)
        if (runWithOldPython(codeccWorkspace)) {
            logger.info("特别说明：")
            logger.info("当前使用第三方构建机，建议安装Docker！")
            if (CodeccEnvHelper.getOS() == OSType.LINUX) {
                doOldThirdLinux()
            }
        }

        val debugProjects = (atomContext.getSensitiveConfParam("PROJECT_CODE_DEBUG")
            ?: "").split(",")
        if (debugProjects.contains(params.projectName) || atomContext.allParameters["BK_CI_CODECC_DEBUG"] == "true") {
            LogUtils.setDebug(true)
            LogUtils.printDebugLog("project is in debug list, set log to debug.")
        }

        // 安装规则集
        if (params.channelCode != "GONGFENGSCAN") {
            CodeccSdkApi.installCheckerSet(
                    userId = params.pipelineStartUserName,
                    projectId = params.projectName,
                    taskId = "",
                    checkerSetList = CodeccSdkApi.getRuleSetV3(params).map { it.checkerSetId })
        }

        if (params.channelCode != "CODECC" && params.channelCode != "CODECC_EE") {
            CodeccSdkApi.createTask(params, params.openScanPrj)
            CodeccSdkApi.updateScanConfiguration(getScanConfig(params), params.pipelineStartUserName)
            if (params.projectName == "CUSTOMPROJ_PCG_RD" || params.channelCode != "GONGFENGSCAN") {
                logger.info("white path is: ${params.path}")
                CodeccSdkApi.deleteFilter(params)
                CodeccSdkApi.addFilterPath(params)
                val result = CodeccSdkApi.addPath(params)
                logger.info("add path res: $result")
            }
            if (params.channelCode != "GONGFENGSCAN") {
                CodeccSdkApi.report(getNotifyCustom(params))
            }
        }

        // init id, name, tool
        if (params.channelCode != "GONGFENGSCAN") {
            val pipelineTaskVo = CodeccSdkApi.getTaskByPipelineId(params.pipelineId, params.pipelineStartUserName)
            params.codeCCTaskId = pipelineTaskVo.taskId.toString()
            params.codeCCTaskName = pipelineTaskVo.enName
            codeccExecuteConfig.tools = pipelineTaskVo.tools.map { it.toolName }
        }

        // deal with code yml filter
        val codeYmlFilterPathVO = getCodeYmlVo(codeccExecuteConfig.repos, params.scanTestSource, params.bkWorkspace) ?: return codeccExecuteConfig
        CodeccSdkApi.addCodeYmlFilterPath(params, codeYmlFilterPathVO)

        return codeccExecuteConfig
    }

    private fun doOldThirdLinux() {
        throw CodeccUserConfigException("当前使用第三方Linux构建机未安装Docker，且非root账号启动agent")
    }

    private fun getCodeYmlVo(repos: List<CodeccExecuteConfig.RepoItem>, scanTestSource: Boolean?, bkWorkspace: String): CodeYmlFilterPathVO? {
        val codeYmlFilterPathVO = CodeYmlFilterPathVO(scanTestSource ?: false)
        try {
            repos.forEach { repo ->
                val parent = File(bkWorkspace, repo.relPath)
                val codeYml = File(parent, ".code.yml")
                if (codeYml.exists()) {
                    logger.info(".code.yml exist in path: ${codeYml.canonicalPath}")
                    LogUtils.printDebugLog(codeYml.readText())
                    val codeYmlItem = YAMLParse.parseDto(codeYml.canonicalPath, CodeYaml::class)
                    codeYmlFilterPathVO.autoGenFilterPath.addAll(filterEmptyList(codeYmlItem.source?.auto_generate_source?.filepath_regex, parent.canonicalPath))
                    codeYmlFilterPathVO.testSourceFilterPath.addAll(filterEmptyList(codeYmlItem.source?.test_source?.filepath_regex, parent.canonicalPath))
                    codeYmlFilterPathVO.thirdPartyFilterPath.addAll(filterEmptyList(codeYmlItem.source?.third_party_source?.filepath_regex, parent.canonicalPath))
                    LogUtils.printDebugLog("code yml parse: $codeYmlFilterPathVO")
                }
            }
        } catch (e: Exception) {
            System.err.println("解析.code.yml失败: ${e.message}")
            return null
        }
        return codeYmlFilterPathVO
    }

    private fun filterEmptyList(list: List<String>?, scanPath: String): List<String> {
        /*
        * 兼容以下两种过滤方式：
        * 'src/test/.*'
        * '/src/test/.*'
        * 以上两种过滤路径，会从项目根路径开始进行匹配。
        */
        val skipPathForProjectRoot = mutableSetOf<String>()
        val refisrt = Regex("^[a-zA-Z]|[0-9]")
        if (list != null) {
            list.forEach { subSkipPath ->
                var skipPath = subSkipPath
                val firstLetter = subSkipPath?.firstOrNull()
                if (refisrt.matches(firstLetter.toString())){
                    //匹配到第一种情况
                    skipPath = scanPath + "/" + subSkipPath
                }else if (subSkipPath?.startsWith("/")){
                    //匹配到第二种情况
                    skipPath = scanPath.toString() + subSkipPath
                }
                if (Regex(skipPath).matches(scanPath+"/"))  return@forEach
                if (skipPath.contains("+") && ! skipPath.contains("\\+")){
                    skipPathForProjectRoot.add(skipPath.replace("+", "\\+"))
                }else{
                    skipPathForProjectRoot.add(skipPath)
                }
            }
        }

        return skipPathForProjectRoot?.filter { !it.isBlank() } ?: listOf()
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
            rtxReceiverType = params.rtxReceiverType ?: "0",
            rtxReceiverList = parseStringToSet(params.rtxReceiverList),
            emailReceiverType = params.emailReceiverType ?: "0",
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

    fun runWithOldPython(codeccWorkspace: File): Boolean {
        return "Teg" == System.getenv("DEVOPS_SLAVE_ENVIRONMENT")
            || isThirdPartyNoDocker(codeccWorkspace)
    }


    private fun isThirdPartyNoDocker(workspace: File): Boolean {
        if (AgentEnv.isThirdParty()) {
            return try {
                ScriptUtils.execute("docker -v", workspace)
                false
            } catch (e: Exception) {
                System.err.println("machine do not has docker, need to install docker manual!")
                true
            }
        }
        return false
    }

}
