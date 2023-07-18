package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.inject.Inject
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CodeYaml
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.YAMLParse
import com.tencent.devops.injector.service.TaskIssueService
import com.tencent.devops.injector.service.ThirdEnvService
import com.tencent.devops.pojo.BuildVO
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.env.PluginRuntimeInfo
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCBusinessException
import com.tencent.devops.pojo.sdk.CodeYmlFilterPathVO
import com.tencent.devops.pojo.sdk.NotifyCustom
import com.tencent.devops.pojo.sdk.ScanConfiguration
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.common.AtomUtils.parseStringToList
import com.tencent.devops.utils.common.AtomUtils.parseStringToSet
import com.tencent.devops.utils.script.ScriptUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.util.LinkedList

object CodeccSdkUtils {

    private val logger = LoggerFactory.getLogger(CodeccSdkUtils::class.java)

    @Inject
    lateinit var taskIssueService: TaskIssueService

    @Inject
    lateinit var thirdEnvService: ThirdEnvService

    fun initCodecc(atomContext: AtomContext<CodeccCheckAtomParamV3>): CodeccExecuteConfig {
        val params = atomContext.param
        params.asynchronous = false
        val codeccExecuteConfig = CodeccParamsHelper.getCodeccExecuteConfig(atomContext)


        val codeccWorkspace = CodeccEnvHelper.getCodeccWorkspace(codeccExecuteConfig.atomContext.param)
        // /data/landun/workspace/.temp/codecc_b-xxxx 目录
        params.codeccWorkspacePath = codeccWorkspace.absolutePath
        if (runWithOldPython(codeccWorkspace)) {
            logger.info("Special Note:")
            logger.info("Currently using a third-party build machine, it is recommended to install Docker!")
            if (CodeccEnvHelper.getOS() == OSType.LINUX) {
                thirdEnvService.logThirdHelpInfo()
            }
        }

        val debugProjects = (atomContext.getSensitiveConfParam("PROJECT_CODE_DEBUG")
            ?: "").split(",")
        if (debugProjects.contains(params.projectName) || atomContext.allParameters["BK_CI_CODECC_DEBUG"] == "true") {
            LogUtils.setDebug(true)
            LogUtils.printDebugLog("project is in debug list, set log to debug.")
        }

        LogUtils.printLog("parameter BK_CI_TOOL_NAME is ${atomContext.allParameters["BK_CI_TOOL_NAME"]}, " +
                "BK_CI_TOOL_SUPPORT_LANG_LIST is ${atomContext.allParameters["BK_CI_TOOL_SUPPORT_LANG_LIST"]}, " +
                "BK_CI_CHECKER_NAME_LIST is ${atomContext.allParameters["BK_CI_CHECKER_NAME_LIST"]}")

        // 获取工具集成插件输出参数
        if (atomContext.allParameters["BK_CI_TOOL_NAME"] != null &&
            atomContext.allParameters["BK_CI_TOOL_SUPPORT_LANG_LIST"] != null &&
            atomContext.allParameters["BK_CI_CHECKER_NAME_LIST"] != null) {
            params.debugToolList = atomContext.allParameters["BK_CI_TOOL_NAME"].toString()
            params.debugLangList = atomContext.allParameters["BK_CI_TOOL_SUPPORT_LANG_LIST"].toString()
            params.debugCheckerSetList = atomContext.allParameters["BK_CI_CHECKER_NAME_LIST"].toString()
        }

        //是否开启本地scm blame扫描
        if (atomContext.allParameters["BK_CI_CODECC_SCM_BLAME_LOCAL"] != null && atomContext.allParameters["BK_CI_CODECC_SCM_BLAME_LOCAL"] == "true") {
            params.localSCMBlameRun = atomContext.allParameters["BK_CI_CODECC_SCM_BLAME_LOCAL"].toString()
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
            CodeccSdkApi.startTask(params.codeCCTaskId!!, params.pipelineBuildId)       // 通知codecc启动
            if (params.channelCode != "GONGFENGSCAN") {
                CodeccSdkApi.updateScanConfiguration(getScanConfig(params), params.pipelineStartUserName)
            }

            if (params.channelCode != "GONGFENGSCAN" || params.projectName == "CUSTOMPROJ_PCG_RD"
                    || (!params.projectName.startsWith("CODE_") && params.openScanFilterEnable != null
                            && params.openScanFilterEnable!!)
            ) {
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
            val pipelineTaskVo = CodeccSdkApi.getTaskByPipelineId(params.pipelineId, params.multiPipelineMark, params.pipelineStartUserName)!!
            params.codeCCTaskId = pipelineTaskVo.taskId.toString()
            params.codeCCTaskName = pipelineTaskVo.enName
            // 如果工具集成冒烟测试变量（工具列表）为空，或者插件配置了自动识别语言
            if (params.debugToolList.isNullOrBlank() || params.beAutoLang == true) {
                codeccExecuteConfig.tools = pipelineTaskVo.tools.map { it.toolName }
            } else {
                codeccExecuteConfig.tools = params.debugToolList?.split(",") ?: listOf()
            }
        }

        // 保存构建信息
        try {
            val buildVO = BuildVO(
                buildId = params.pipelineBuildId,
                buildNum = params.pipelineBuildNum,
                buildTime = params.pipelineStartTimeMills.toLong(),
                buildUser = params.pipelineStartUserName,
                taskId = params.codeCCTaskId?.toLong() ?: 0L,
                projectId = params.projectName,
                pipelineId = params.pipelineId
            )
            CodeccSdkApi.saveBuildInfo(buildVO, params)
        } catch (t: Throwable) {
            logger.info("save build info fail!")
        }

        val codeYmlFilterPathVO = getCodeYmlVo(codeccExecuteConfig.repos, params.scanTestSource, params.bkWorkspace)
            ?: return codeccExecuteConfig
        CodeccSdkApi.addCodeYmlFilterPath(params, codeYmlFilterPathVO)
        CodeccSdkApi.addCodeYmlRepoOwner(params, codeYmlFilterPathVO)
        CodeccSdkApi.runtimeInfoUpdate(params)

        taskIssueService.updateTaskIssueInfo(params)

        return codeccExecuteConfig
    }

    private fun getCodeYmlVo(repos: List<CodeccExecuteConfig.RepoItem>, scanTestSource: Boolean?,
                             bkWorkspace: String): CodeYmlFilterPathVO? {
        val codeYmlFilterPathVO = CodeYmlFilterPathVO(scanTestSource ?: false)
        try {
            repos.forEach { repo ->
                val parent = File(bkWorkspace, repo.relPath)
                val codeYml = File(parent, ".code.yml")
                uploadCodeYmlFilterPath(codeYml, false, bkWorkspace, codeYmlFilterPathVO)
            }

            // 流水线前面没有拉取代码插件，就只认工作空间下的
            if (repos.isEmpty()) {
                val codeYml = File(bkWorkspace, ".code.yml")
                uploadCodeYmlFilterPath(codeYml, false, bkWorkspace, codeYmlFilterPathVO)
            }
            LogUtils.printLog("code yml parse: $codeYmlFilterPathVO")
        } catch (e: Exception) {
            LogUtils.printErrorLog("Parsing .code.yml failed: ${e.message}")
            return null
        }
        return codeYmlFilterPathVO
    }


    /**
     * 遍历根目录下所有的.code.yml文件
     */
    private fun getCodeYmlFromParentPath(parentDir: File, depth: Int, pathList: List<String>?,
                                         filterList: List<String>?): List<File> {
        val whitePathFilter = filterEmptyList(
            pathList, false,
            parentDir.canonicalPath, parentDir.canonicalPath
        )
        LogUtils.printLog("whitePathFilter: $whitePathFilter")
        val blackPathFilter = filterEmptyList(
            filterList, false,
            parentDir.canonicalPath, parentDir.canonicalPath
        )
        LogUtils.printLog("blackPathFilter: $blackPathFilter")
        val codeYmlFiles = LinkedList<File>()
        val dirLink = LinkedList<Pair<File,Int>>()
        dirLink.add(Pair(parentDir, 0))
        while (!dirLink.isEmpty()) {
            val subPair = dirLink.pop()
            val subFiles = subPair.first.listFiles()
            if (subFiles.isNullOrEmpty()) {
                continue
            }
            subFiles.forEach { file ->
                if (subPair.second > depth) {
                    return@forEach
                }
                if (file.isDirectory && file.name != ".git") {
                    dirLink.addLast(Pair(file, subPair.second + 1))
                } else if (".code.yml" == file.name
                    && checkPathValid(file.canonicalPath, whitePathFilter, blackPathFilter)) {
                    codeYmlFiles.add(file)
                }
            }
        }
        return codeYmlFiles
    }


    /**
     * 通过用户配置的白名单与黑名单过滤，减少.code.yml的数量
     */
    private fun checkPathValid(filePath: String, whitePathList: List<String>?, filterPathList: List<String>?): Boolean {
        // 白名单过滤
        if (!whitePathList.isNullOrEmpty() && whitePathList.none { Regex(it).containsMatchIn(filePath) }) {
            LogUtils.printLog("checkPathValid $filePath in whitePathList fail!")
            return false
        }
        // 黑名单过滤
        if (!filterPathList.isNullOrEmpty() && filterPathList.any { Regex(it).containsMatchIn(filePath) }) {
            LogUtils.printLog("checkPathValid $filePath in filterPathList fail!")
            return false
        }
        return true
    }

    private fun uploadCodeYmlFilterPath(codeYml: File, subCodeYml: Boolean,
                                        bkWorkspace: String, codeYmlFilterPathVO: CodeYmlFilterPathVO) {
        try {
            if (codeYml.exists()) {
                logger.info(".code.yml exist in path: ${codeYml.canonicalPath}")
                LogUtils.printDebugLog(codeYml.readText())
                val codeYmlItem = YAMLParse.parseDto(codeYml.canonicalPath, CodeYaml::class)
                //添加RepoOwner
                if (codeYmlItem.repo?.repo_owners != null) {
                    codeYmlFilterPathVO.repoOwners.addAll(codeYmlItem.repo!!.repo_owners!!)
                }
                val parent = codeYml.parentFile
                codeYmlFilterPathVO.autoGenFilterPath.addAll(
                    filterEmptyList(
                        codeYmlItem.source?.auto_generate_source?.filepath_regex,
                        subCodeYml,
                        bkWorkspace,
                        parent.canonicalPath
                    )
                )
                codeYmlFilterPathVO.testSourceFilterPath.addAll(
                    filterEmptyList(
                        codeYmlItem.source?.test_source?.filepath_regex,
                        subCodeYml,
                        bkWorkspace,
                        parent.canonicalPath
                    )
                )
                codeYmlFilterPathVO.thirdPartyFilterPath.addAll(
                    filterEmptyList(
                        codeYmlItem.source?.third_party_source?.filepath_regex,
                        subCodeYml,
                        bkWorkspace,
                        parent.canonicalPath
                    )
                )
            }
        } catch (e: Exception) {
            LogUtils.printErrorLog("Parsing .code.yml failed: ${e.message}")
        }
    }

    private fun filterEmptyList(list: List<String>?, subCodeYml: Boolean,
                                bkWorkspace: String, scanPath: String): List<String> {
        /*
        * 兼容以下过滤方式：
        * '/src/test/.*'  ->  '/data/landun/workspace/src/test/.*'
        * '^/src/test/.*' ->  '/data/landun/workspace/src/test/.*'
        * 'src/test/.*' ->  '/data/landun/workspace/src/test/.*'
        * 以上过滤路径，会从项目根路径开始进行匹配。
        */
        val skipPathForProjectRoot = mutableSetOf<String>()
        val refisrt = Regex("^[a-zA-Z]|[0-9]")
        list?.forEach { subSkipPath ->
            var skipPath = subSkipPath
            val firstLetter = subSkipPath?.firstOrNull()
            if (subSkipPath?.startsWith("/")) {
                //匹配到第一种情况
                processAndAddSkipPath(skipPathForProjectRoot, scanPath, scanPath + subSkipPath)
            } else if (subSkipPath?.startsWith("^/")) {
                //匹配到第二种情况
                processAndAddSkipPath(skipPathForProjectRoot, scanPath,
                    scanPath + subSkipPath.substring(1))
            }else if (refisrt.matches(firstLetter.toString())) {
                //匹配到第三种情况
                processAndAddSkipPath(skipPathForProjectRoot, scanPath, "$scanPath/$subSkipPath")
            } else if(subCodeYml && !skipPath.startsWith(bkWorkspace)){
                //子目录的.code.yml 且 过滤路径不包含根目录
                if (subSkipPath?.startsWith("./")) {
                    processAndAddSkipPath(
                        skipPathForProjectRoot, scanPath,
                        scanPath + subSkipPath.substring(1)
                    )
                } else if (subSkipPath.startsWith(".*/")) {
                    processAndAddSkipPath(
                        skipPathForProjectRoot, scanPath,
                        "$scanPath/(.*/)?${subSkipPath.substring(3)}"
                    )
                } else {
                    processAndAddSkipPath(skipPathForProjectRoot, scanPath, "$scanPath/$subSkipPath")
                }

            }else{
                processAndAddSkipPath(skipPathForProjectRoot, scanPath, skipPath)
            }
        }

        return skipPathForProjectRoot?.filter { !it.isBlank() } ?: listOf()
    }

    private fun processAndAddSkipPath(skipPaths: MutableCollection<String>, scanPath: String, skipPath: String) {
        if (Regex(skipPath).matches(scanPath+"/")){
            return
        }
        if (skipPath.contains("+") && ! skipPath.contains("\\+")){
            skipPaths.add(skipPath.replace("+", "\\+"))
        }else{
            skipPaths.add(skipPath)
        }
    }

    fun executeAsyncTask(codeccTaskId: Long, userId: String) {
        val result = CodeccSdkApi.executeTask(codeccTaskId, userId)
        if (result.data != true) {
            throw CodeCCBusinessException(
                ErrorCode.CODECC_RETURN_PARAMS_CHECK_FAIL,
                "Failed to start the scan task, message：${result.message}",
                arrayOf("")
            )
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
            mrCommentEnable = params.mrCommentEnable,
            prohibitIgnore = params.prohibitIgnore,
            compileCommand = params.script
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
                LogUtils.printErrorLog("machine do not has docker, need to install docker manual!")
                true
            }
        }
        return false
    }

}
