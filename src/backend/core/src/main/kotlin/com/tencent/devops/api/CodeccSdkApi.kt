/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.api

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.inject.Inject
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.injector.service.CodeCCSdkService
import com.tencent.devops.pojo.ActualExeToolsVO
import com.tencent.devops.pojo.BaseDataVO
import com.tencent.devops.pojo.BuildVO
import com.tencent.devops.pojo.CodeccCheckAtomParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV2
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CoverityResult
import com.tencent.devops.pojo.HeadFileInfo
import com.tencent.devops.pojo.IgnoreDefectInfo
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.env.PluginRuntimeInfo
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCBusinessException
import com.tencent.devops.pojo.exception.plugin.CodeCCHttpStatusException
import com.tencent.devops.pojo.exception.plugin.CodeCCPluginException
import com.tencent.devops.pojo.report.CodeccCallback
import com.tencent.devops.pojo.report.TaskFailReportReq
import com.tencent.devops.pojo.scan.SetForceFullScanReqVO
import com.tencent.devops.pojo.sdk.CodeYmlFilterPathVO
import com.tencent.devops.pojo.sdk.FilterPathInput
import com.tencent.devops.pojo.sdk.NotifyCustom
import com.tencent.devops.pojo.sdk.RuntimeUpdateMetaVO
import com.tencent.devops.pojo.sdk.ScanConfiguration
import com.tencent.devops.utils.CodeccConfigUtils
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.CodeccEnvHelper.getOS
import com.tencent.devops.utils.I18NUtils
import com.tencent.devops.utils.common.AtomUtils
import com.tencent.devops.utils.script.ScriptUtils
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object CodeccSdkApi : BaseApi() {

    private val codeccApiUrl: String = CodeccConfigUtils.getPropConfig("codeccHost")!!
    private val existPath: String = "/ms/task/api/build/task/exists"
    private val deletePath: String = "/ms/task/api/build/task"
    private val startPath: String = "/ms/task/api/build/task/startSignal/taskId/{taskId}/buildId/{buildId}?timeout={timeout}"
    private val executePath: String = "/ms/task/api/build/task/execute"
    private val metadataPath = "/ms/task/api/build/meta/metadatas"
    private val getTaskPath: String = "/ms/task/api/build/task/pipeline/{pipelineId}?userId={userId}"
    private val relationshipPath = "/ms/report/api/build/checkerSet/relationships"
    private val baseDataPath = "/ms/task/api/build/baseData/paramType/LANG/params"
    private val ignoreDefectPath = "/ms/report/api/build/ignore/taskId/{taskId}"
    private val headFilePath = "/ms/report/api/build/headFile/taskId/{taskId}"
    private val redLineMetadataReportStatusPath = "/ms/report/api/build/snapshot/projectId/{projectId}/taskId/{taskId}/buildId/{buildId}/metadataReportStatus"
    private val saveBuildInfoPath = "/ms/report/api/build/buildInfo/"
    private val getLanguageTagPath = "/ms/report/api/build/i18n/getLanguageTag"

    private val objectMapper = JsonUtil.getObjectMapper()
    private const val USER_NAME_HEADER = "X-DEVOPS-UID"
    private const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"
    private const val CONTENT_TYPE = "Content-Type"
    private const val CONTENT_TYPE_JSON = "application/json"

    @Inject
    lateinit var codeCCSdkService: CodeCCSdkService

    fun createTask(param: CodeccCheckAtomParamV3, openScanProj: Boolean? = false): CoverityResult {
        with(param) {
            val devopsToolParams = mutableListOf<DevOpsToolParams>()
            devopsToolParams.addAll(
                listOf(
                    DevOpsToolParams("compilePlat", getPlatForm()),
                    DevOpsToolParams("phpcs_standard", phpcsStandard ?: ""),
                    DevOpsToolParams("go_path", goPath ?: ""),
                    DevOpsToolParams("py_version", pyVersion ?: ""),
                    DevOpsToolParams("ccn_threshold", ccnThreshold?.toString() ?: ""),
                    DevOpsToolParams("needCodeContent", needCodeContent ?: ""),
                    DevOpsToolParams("eslint_rc", eslintRc ?: "")
                )
            )
            /*val langList: ArrayList<String>?
            if (param.checkerSetType != "normal") {
                langList = JsonUtil.to(
                        languages!!, object : TypeReference<ArrayList<String>>(){})
            } else {
                langList = null
            }*/

            // 工具集成冒烟测试, 如果是插件配置了自动识别语言，则不替换
            if (!param.debugLangList.isNullOrBlank() && (param.beAutoLang == null || param.beAutoLang == false)) {
                LogUtils.printLog("debugLangList is not null: ${param.debugLangList}")
                param.languages = jacksonObjectMapper().writeValueAsString(param.debugLangList!!.split(","))
            }

            //如果是stream项目，则中文名为代码库名(YAML名)，如果是其他情况则按正常情况走
            val uploadPipelineName = if (projectName.startsWith("git_")) {
                projectNameCn.substringAfterLast("/").plus("/$pipelineName")
            } else {
                val repoName = System.getenv("BK_CI_GIT_REPO_NAME")
                if (!repoName.isNullOrBlank()) {
                    repoName.substringAfterLast("/").plus("/$pipelineName")
                } else {
                    pipelineName
                }
            }
            val uploadNameCn = if (projectName.startsWith("git_")) {
                projectNameCn.substringAfterLast("/").plus(pipelineName)
            } else {
                val repoName = System.getenv("BK_CI_GIT_REPO_NAME")
                val prefix = if (!repoName.isNullOrBlank()) {
                    repoName.substringAfterLast("/").plus("/$pipelineName")
                } else {
                    pipelineName
                }
                if(multiPipelineMark.isNullOrBlank())
                    prefix
                else
                    "$prefix($multiPipelineMark)"
            }

            var body = mapOf(
                "projectId" to projectName,
                "projectName" to projectNameCn,
                "pipelineId" to pipelineId,
                "multiPipelineMark" to if(multiPipelineMark.isNullOrBlank()) null else multiPipelineMark,
                "pipelineName" to uploadPipelineName,
                "scanType" to (scanType ?: (if (openScanProj == true) "0" else "1")),
                "compilePlat" to getPlatForm(),
                "devopsCodeLang" to (languages ?: "[]"),
                "devopsTools" to "",
                "devopsToolParams" to devopsToolParams,
                "checkerSetList" to getRuleSetV3(param),
                "checkerSetEnvType" to if (param.checkerSetType.isNullOrBlank() || param.checkerSetType == "normal") {
                    ""
                } else {
                    param.checkerSetEnvType
                },
                "nameCn" to uploadNameCn,
                "atomCode" to "CodeccCheckAtomDebug",
                "projectBuildCommand" to script,
                "projectBuildType" to if (getOS() == OSType.WINDOWS) "BAT" else "SHELL",
                "compilePlat" to getOS().name,
                "forceToUpdateOpenSource" to (System.getenv("FORCE_UPDATE_CODECC_OPEN_SOURCE") == "true"),
                "openSourceCheckerSetType" to System.getenv("OPEN_SOURCE_CHECKER_SET_TYPE"),
                "checkerSetType" to (param.checkerSetType ?: "normal"),
                "autoLang" to param.beAutoLang
            )
            val codeccOpenSourceJson = System.getenv("BK_CI_OPEN_SOURCE_JSON")
            if (!codeccOpenSourceJson.isNullOrBlank() && openScanProj == true) {
                LogUtils.printLog("add opensource custom input param")
                val codeccParamMap = try {
                    JsonUtil.to(codeccOpenSourceJson, object : TypeReference<Map<String, String>>() {})
                } catch (e: Exception) {
                    emptyMap<String, String>()
                }
                body = body.plus(codeccParamMap)
            }

            LogUtils.printDebugLog("start to create task: $body")

            val header = mutableMapOf(
                USER_NAME_HEADER to param.pipelineStartUserName,
                DEVOPS_PROJECT_ID to param.projectName,
                CONTENT_TYPE to CONTENT_TYPE_JSON
            )
            val url = codeCCSdkService.getCreateTaskUrl(openScanProj)
            val result =
                getCodeccResult(taskExecution(objectMapper.writeValueAsString(body), url, header, "POST"))
            val resultMap = result.data as Map<String, Any>

            LogUtils.printLog("Create CodeCC task response: $resultMap")

            param.codeCCTaskName = resultMap["nameEn"]?.toString()
                ?: throw CodeCCBusinessException(ErrorCode.CODECC_REQUIRED_FIELD_IS_EMPTY, "create task fail",
                    arrayOf("nameEn"), null)
            param.codeCCTaskId = resultMap["taskId"]?.toString()
                ?: throw CodeCCBusinessException(ErrorCode.CODECC_REQUIRED_FIELD_IS_EMPTY, "create task fail",
                    arrayOf("taskId"), null)
            return result
        }
    }

    fun executeTask(taskId: Long, userId: String): Result<Boolean> {
        val path = executePath
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId,
            "X-DEVOPS-TASK-ID" to taskId.toString()
        )
        val responseContent = taskExecution(path = path, headers = headers, method = "POST", printLog = false)
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }


    fun startTask(taskId: String, buildId: String) {
        //获取超时时间(S)
        val timeout = getTimeoutValue() ?: TimeUnit.DAYS.toSeconds(1)
        val path = startPath.replace("{taskId}", taskId).replace("{buildId}", buildId)
                .replace("{timeout}", timeout.toString())
        taskExecution(path = path)
    }

    fun getRuleSetV3(param: CodeccCheckAtomParam): List<RuleSetCheckV3> {
        return if (param is CodeccCheckAtomParamV3) {
            if (param.languageRuleSetMap.isNullOrBlank()) return listOf()
            val ruleSetIds = mutableSetOf<String>()
            // 如果工具集成冒烟测试变量（规则集列表）为空，或者插件配置了自动识别语言
            if (param.debugCheckerSetList.isNullOrBlank() || param.beAutoLang == true) {
                val languageRuleSetMap =
                    JsonUtil.getObjectMapper().readValue<Map<String, List<String>>>(param.languageRuleSetMap!!)
                languageRuleSetMap.values.forEach { ruleSetIds.addAll(it) }
            } else {
                LogUtils.printLog("debugCheckerSetList is not null: ${param.debugCheckerSetList}")
                param.debugCheckerSetList!!.split(",").forEach { ruleSetIds.add(it) }
            }
            return ruleSetIds.map { RuleSetCheckV3(it) }
        } else {
            listOf()
        }
    }

    private fun getPlatForm(): String {
        return when (CodeccEnvHelper.getOS()) {
            OSType.LINUX -> "LINUX"
            OSType.WINDOWS -> "WINDOWS"
            else -> "LINUX"
        }
    }

    fun updateTask(param: CodeccCheckAtomParamV3, openScanProj: Boolean? = false): CoverityResult {
        return with(param) {
            val devopsToolParams = mutableListOf(
                DevOpsToolParams("compilePlat", getPlatForm()),
                DevOpsToolParams("scan_type", scanType ?: "1"),
                DevOpsToolParams("phpcs_standard", phpcsStandard ?: ""),
                DevOpsToolParams("go_path", goPath ?: ""),
                DevOpsToolParams("py_version", pyVersion ?: ""),
                DevOpsToolParams("ccn_threshold", ccnThreshold?.toString() ?: ""),
                DevOpsToolParams("needCodeContent", needCodeContent ?: ""),
                DevOpsToolParams("eslint_rc", eslintRc ?: "")
            )
            if (!projectBuildType.isNullOrBlank()) {
                devopsToolParams.add(DevOpsToolParams("PROJECT_BUILD_TYPE", projectBuildType!!))
                devopsToolParams.add(DevOpsToolParams("PROJECT_BUILD_COMMAND", projectBuildCommand ?: ""))
            }
            if (codeCCTaskId.isNullOrBlank()) return CoverityResult()
            var body = mapOf(
                "pipelineName" to pipelineName,
                "devopsCodeLang" to objectMapper.writeValueAsString(AtomUtils.parseStringToList(languages)),
                "devopsTools" to objectMapper.writeValueAsString(AtomUtils.parseStringToList(tools)),
                "taskId" to codeCCTaskId!!,
                "devopsToolParams" to devopsToolParams,
                "checkerSetList" to getRuleSetV3(param),
//                "toolCheckerSets" to genToolCheckerV2(this),
                "nameCn" to pipelineName,
                "forceToUpdateOpenSource" to (System.getenv("FORCE_UPDATE_CODECC_OPEN_SOURCE") == "true"),
                "openSourceCheckerSetType" to System.getenv("OPEN_SOURCE_CHECKER_SET_TYPE"),
                "autoLang" to param.beAutoLang
            )
            val codeccOpenSourceJson = System.getenv("BK_CI_OPEN_SOURCE_JSON")
            if (!codeccOpenSourceJson.isNullOrBlank() && openScanProj == true) {
                LogUtils.printLog("add opensource custom input param")
                val codeccParamMap = try {
                    JsonUtil.to(codeccOpenSourceJson, object : TypeReference<Map<String, String>>() {})
                } catch (e: Exception) {
                    emptyMap<String, String>()
                }
                body = body.plus(codeccParamMap)
                if(openScanProj == true) {
                    LogUtils.printDebugLog("set pipeline id to body")
                    body = body.plus(mapOf("pipelineId" to pipelineId))
                }
            }
            LogUtils.printLog("Update the coverity task($body)")
            val header = mutableMapOf(
                USER_NAME_HEADER to param.pipelineStartUserName,
                CONTENT_TYPE to CONTENT_TYPE_JSON
            )
            // 设置url
            val url = codeCCSdkService.getUpdateTaskUrl(openScanProj)
            getCodeccResult(taskExecution(objectMapper.writeValueAsString(body), url, header, "PUT"))
        }
    }

    fun isTaskExist(taskId: String): Boolean {
        LogUtils.printLog("Check the coverity task if exist")
        val header = mutableMapOf(CONTENT_TYPE to CONTENT_TYPE_JSON)
        val result = getCodeccResult(taskExecution("", "$existPath/$taskId", header, "GET"))
        LogUtils.printLog("Get the exist result($result)")
        return result.data == true
    }

    fun deleteTask(taskId: String, rtx: String) {
        val body = emptyMap<String, String>()

        val headers = mutableMapOf(
            "proj_id" to taskId,
            USER_NAME_HEADER to rtx
        )
        taskExecution(objectMapper.writeValueAsString(body), "$deletePath/$taskId", headers, "DELETE")
    }

    fun getTaskByPipelineId(pipelineId: String, multiPipelineMark: String?, userId: String): PipelineTaskVO? {
        LogUtils.printLog("get the codecc task if exist: $pipelineId, $userId")
        val header = mutableMapOf(CONTENT_TYPE to CONTENT_TYPE_JSON)
        val path = if (multiPipelineMark.isNullOrBlank())
            getTaskPath.replace("{pipelineId}", pipelineId).replace("{userId}", userId)
                else
            getTaskPath.replace("{pipelineId}", pipelineId).replace("{userId}", userId).plus("&multiPipelineMark=$multiPipelineMark")
        val result = taskExecution("", path, header, "GET")
        LogUtils.printLog("Get the codecc task($result)")
        return JsonUtil.to(result, object : TypeReference<Result<PipelineTaskVO>>() {}).data
    }

    fun updateScanConfiguration(scanConfiguration: ScanConfiguration, userId: String): Result<Boolean> {
        val path = "/ms/task/api/build/task/taskId/${scanConfiguration.taskId}/scanConfiguration"
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val jsonBody = objectMapper.writeValueAsString(scanConfiguration)
        val responseContent = taskExecution(
            headers = headers,
            jsonBody = jsonBody,
            path = path,
            method = "POST"
        )
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    fun report(notifyCustom: NotifyCustom): Result<Boolean> {
        val path = "/ms/task/api/build/task/report"
        val jsonBody = objectMapper.writeValueAsString(notifyCustom)
        val headers = mutableMapOf(
            "X-DEVOPS-TASK-ID" to notifyCustom.taskId!!
        )
        val responseContent = taskExecution(
            jsonBody = jsonBody,
            path = path,
            headers = headers,
            method = "POST"
        )
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    fun addFilterPath(params: CodeccCheckAtomParamV3): Result<Boolean> {
        val filterPathInput = getFileInput(params)

        val path = "/ms/task/api/build/task/add/filter/path"
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to params.pipelineStartUserName
        )

        return if (filterPathInput.customPath != null && filterPathInput.customPath!!.isNotEmpty()) {
            val jsonBody = objectMapper.writeValueAsString(filterPathInput)
            val responseContent = taskExecution(
                headers = headers,
                jsonBody = jsonBody,
                path = path,
                method = "POST"
            )
            JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
        } else {
            LogUtils.printLog("do not add filter path")
            Result(true)
        }
    }

    fun addPath(params: CodeccCheckAtomParamV3): Result<Boolean> {
        try {
            val pathList = AtomUtils.transferPathParam(params.path).map { it.trim() }
            val whitePathList = mutableListOf<String>()
            pathList.forEach {
                whitePathList.addAll(it.split(","))
            }
            val path = "/ms/task/api/build/task/path"
            val headers = mutableMapOf(
                    "X-DEVOPS-UID" to params.pipelineStartUserName,
                    "X-DEVOPS-TASK-ID" to params.codeCCTaskId!!
            )

            val jsonBody = objectMapper.writeValueAsString(whitePathList)
            val responseContent = taskExecution(
                    headers = headers,
                    jsonBody = jsonBody,
                    path = path,
                    method = "POST"
            )
            return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
        } catch (e: Throwable) {
            LogUtils.printErrorLog(e.message)
        }
        return Result(false)
    }

    fun addCodeYmlFilterPath(params: CodeccCheckAtomParamV3, codeYmlFilterPathVO: CodeYmlFilterPathVO): Result<Boolean> {
        val path = "/ms/task/api/build/task/code/yml/filter/update"
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to params.pipelineStartUserName,
            "X-DEVOPS-TASK-ID" to params.codeCCTaskId!!
        )

        val jsonBody = objectMapper.writeValueAsString(codeYmlFilterPathVO)
        val responseContent = taskExecution(
            headers = headers,
            jsonBody = jsonBody,
            path = path,
            method = "POST"
        )
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    fun addCodeYmlRepoOwner(params: CodeccCheckAtomParamV3, codeYmlFilterPathVO: CodeYmlFilterPathVO): Result<Boolean> {
        val path = "/ms/task/api/build/task/code/yml/repoOwner/update"
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to params.pipelineStartUserName,
            "X-DEVOPS-TASK-ID" to params.codeCCTaskId!!
        )

        val jsonBody = objectMapper.writeValueAsString(codeYmlFilterPathVO)
        val responseContent = taskExecution(
            headers = headers,
            jsonBody = jsonBody,
            path = path,
            method = "POST"
        )
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    fun runtimeInfoUpdate(params: CodeccCheckAtomParamV3): Result<Boolean> {
        val path = "/ms/task/api/build/task/runtime/update"
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to params.pipelineStartUserName,
            "X-DEVOPS-TASK-ID" to params.codeCCTaskId!!
        )
        //获取超时时间(S)
        val timeout = getTimeoutValue()
        val runtimeUpdateMetaVO = RuntimeUpdateMetaVO(
            params.codeCCTaskId!!,
            params.pipelineBuildId,
            params.projectName,
            params.projectNameCn,
            params.pipelineTaskId,
            params.taskName,
            timeout
        )
        val jsonBody = objectMapper.writeValueAsString(runtimeUpdateMetaVO)
        val responseContent = taskExecution(
            headers = headers,
            jsonBody = jsonBody,
            path = path,
            method = "POST"
        )
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean>>() {})
    }

    private fun getTimeoutValue() : Int? {
        //获取超时时间（分钟）
        val timeout = System.getenv("BK_CI_ATOM_TIMEOUT")
        if (timeout.isNullOrEmpty()) {
            return null
        }
        return try {
            timeout.toInt() * 60
        } catch (e: Exception) {
            null
        }
    }

    fun saveBuildInfo(buildVO: BuildVO, params: CodeccCheckAtomParamV3) {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to params.pipelineStartUserName
        )
        val jsonBody = objectMapper.writeValueAsString(buildVO)
        taskExecution(
            headers = headers,
            jsonBody = jsonBody,
            path = saveBuildInfoPath,
            method = "POST"
        )
    }

    private fun getFileInput(params: CodeccCheckAtomParamV3): FilterPathInput {
        val oldPaths = getFilterPath(params).data?.filterPaths ?: listOf()
        val curPaths = AtomUtils.transferPathParam(params.customPath).map { it.trim() }
        val curSplitPaths = mutableListOf<String>()
        curPaths.forEach {
            curSplitPaths.addAll(it.split(","))
        }
        return FilterPathInput(
            taskId = params.codeCCTaskId?.toLong(),
            pathType = params.pathType ?: "CUSTOM",
            customPath = curSplitPaths.minus(oldPaths).filter { !it.isBlank() },
            filterDir = params.filterDir,
            filterFile = params.filterFile
        )
    }

    private fun getFilterPath(param: CodeccCheckAtomParamV3): Result<FilterPathOutVO> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to param.pipelineStartUserName
        )
        val path = "/ms/task/api/build/task/filter/path/${param.codeCCTaskId}"
        return JsonUtil.to(taskExecution(path = path, headers = headers), object : TypeReference<Result<FilterPathOutVO>>() {})
    }

    // 删除路径屏蔽
    fun deleteFilter(param: CodeccCheckAtomParamV3) {
        val oldPaths = getFilterPath(param).data?.filterPaths ?: listOf()
        val newPaths = AtomUtils.transferPathParam(param.customPath)

        val newSplitPaths = mutableSetOf<String>()
        newPaths.forEach {
            newSplitPaths.addAll(it.split(","))
        }

        val userId = param.pipelineStartUserName
        val taskId = param.codeCCTaskId
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId!!,
            "X-DEVOPS-TASK-ID" to taskId!!
        )
        oldPaths.minus(newSplitPaths).forEach { path ->
            taskExecution(path = "/ms/task/api/build/task/del/filter?path=${URLEncoder.encode(path, "utf8")}&pathType=CUSTOM", headers = headers, method = "DELETE")
            LogUtils.printLog("delete filter path success: $path")
        }
    }

    fun getCodeccToolType(userId: String): List<ToolType> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val path = "/ms/task/api/build/meta/toolList?isDetail=false"
        val responseContent = taskExecution(path = path, headers = headers, printLog = false)
        LogUtils.printLog("get tool type successs")
        return JsonUtil.to(responseContent, object : TypeReference<Result<List<ToolType>>>() {}).data ?: listOf()
    }

    fun getMetadataToolType(userId: String): List<MetaToolType> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val responseContent =
            taskExecution(path = "$metadataPath?metadataType=TOOL_TYPE", headers = headers, printLog = false)
        LogUtils.printLog("get metadata tool type success, $responseContent")
        val result = JsonUtil.getObjectMapper().readValue<Result<Map<String, List<Map<String, String>>>>>(responseContent).data
            ?: mapOf()
        return (result["TOOL_TYPE"] as List<Map<String, Any>>).map {
            MetaToolType(
                key = it["key"] as String,
                name = it["name"] as String
            )
        }
    }

    //扫描失败上报
    fun reportFailTask(
        taskFailReportReq: TaskFailReportReq
    ) {
        try{
            val jsonBody = objectMapper.writeValueAsString(taskFailReportReq)
            val path = "/ms/task/api/build/openScan/task/fail"
            taskExecution(
                jsonBody = jsonBody,
                path = path,
                method = "POST"
            )
        } catch (e : Exception){
            LogUtils.printErrorLog("report codecc server fail!")
        }
    }

    fun installCheckerSet(userId: String, projectId: String, taskId: String, checkerSetList: List<String>) {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val body = checkerSetList.map { mapOf("checkerSetId" to it) }
        val responseContent = taskExecution(
            path = "$relationshipPath?type=PROJECT&projectId=$projectId&taskId=$taskId",
            headers = headers,
            jsonBody = objectMapper.writeValueAsString(body),
            printLog = false,
            method = "POST"
        )
        LogUtils.printLog("install checker set result: $responseContent")
        val result = JsonUtil.getObjectMapper().readValue<Result<Boolean>>(responseContent).data
        if (result != true) {
            throw CodeCCBusinessException(ErrorCode.CODECC_RETURN_PARAMS_CHECK_FAIL,
                "install checker set fail: $checkerSetList", arrayOf(""))
        }
    }

    fun uploadActualExeTools(param: CodeccCheckAtomParamV3, tools: List<String>) {
        try {
            val userId = param.pipelineStartUserName
            val taskId = param.codeCCTaskId
            val headers = mutableMapOf(
                    "X-DEVOPS-UID" to userId,
                    "X-DEVOPS-TASK-ID" to taskId.toString()
            )
            val path = "/ms/report/api/build/taskLogOverview/saveTools"
            val actualExeToolsVO = ActualExeToolsVO(taskId!!.toLong(), param.pipelineBuildId, tools)
            val jsonBody = objectMapper.writeValueAsString(actualExeToolsVO)
            val respStr = taskExecution(
                    jsonBody,
                    path,
                    headers,
                    "POST"
            )
            LogUtils.printLog("upload actual exe tools: $respStr")
        } catch (e: Exception) {
            LogUtils.printErrorLog("upload actual tools fail!")
        }
    }

    fun getLangBaseData(userId: String): List<BaseDataVO> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val respStr = taskExecution(path = baseDataPath, headers = headers, printLog = false)
        return JsonUtil.getObjectMapper().readValue<Result<List<BaseDataVO>>>(respStr).data
            ?: throw CodeCCBusinessException(
                ErrorCode.CODECC_REQUIRED_FIELD_IS_EMPTY,
                "no default lang checker set config", arrayOf("data")
            )
    }

    fun changeScanType(
        taskId: Long,
        setForceFullScanReqVO: SetForceFullScanReqVO
    ) {
        val headers = mutableMapOf(
            "X-DEVOPS-TASK-ID" to taskId.toString()
        )
        val path = "/ms/report/api/build/toolBuildInfo/tasks/$taskId/forceFullScanSymbol"
        val jsonBody = objectMapper.writeValueAsString(setForceFullScanReqVO)
        val responseContent = taskExecution(path = path, headers = headers, jsonBody = jsonBody, method = "POST")
        LogUtils.printDebugLog("response body: $responseContent")
    }

    /**
     * 获取忽略注释信息
     */
    fun getIgnoreDefectInfo(taskId: Long) : IgnoreDefectInfo?{
        val responseContent = taskExecution(
            path = ignoreDefectPath.replace("{taskId}", taskId.toString())
        )
        return JsonUtil.getObjectMapper().readValue<Result<IgnoreDefectInfo>>(responseContent).data
    }

    /**
     * 获取忽略注释信息
     */
    fun getHeadFileInfo(taskId: Long) : HeadFileInfo?{
        val responseContent = taskExecution(
            path = headFilePath.replace("{taskId}", taskId.toString())
        )
        return JsonUtil.getObjectMapper().readValue<Result<HeadFileInfo>>(responseContent).data
    }

    fun getCodeccReport(projectId: String, taskId: String, buildId: String): CodeccCallback? {
        val reportPath = "/ms/report/api/build/snapshot/project/{projectId}/tasks/{taskId}/get?buildId={buildId}"
        val responseContent = taskExecution(
            path = reportPath.replace("{taskId}", taskId)
                .replace("{projectId}", projectId)
                .replace("{buildId}", buildId)
        )
        val callback = JsonUtil.getObjectMapper().readValue<Result<CodeccCallback?>>(responseContent).data
        callback?.taskId = taskId
        LogUtils.printLog("get codecc report snapshot success: " +
            "${callback?.taskId}, ${callback?.toolSnapshotList?.size}")
        return callback
    }

    fun getRedLineMetadataReportStatus(projectId: String, taskId: String, buildId: String) : Result<Boolean?> {
        val path = redLineMetadataReportStatusPath.replace("{projectId}", projectId).replace("{taskId}", taskId).replace("{buildId}", buildId)
        val responseContent = taskExecution(path = path, method = "GET", printLog = false)
        return JsonUtil.to(responseContent, object : TypeReference<Result<Boolean?>>() {})
    }

    fun getI18NLanguageTag(userId: String): String {
        val responseContent = taskExecution(path = getLanguageTagPath)
        return JsonUtil.getObjectMapper().readValue<Result<String>>(responseContent).data!!
    }

    fun taskExecution(
        jsonBody: String = "",
        path: String,
        headers: MutableMap<String, String> = mutableMapOf(),
        method: String = "GET",
        printLog: Boolean = true
    ): String {

        I18NUtils.addAcceptLanguageHeader(headers)
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody
        )

        val request = when (method) {
            "GET" -> {
                val r = buildGet(path)
                val builder = r.newBuilder()
                headers.forEach { (t, u) -> builder.addHeader(t, u) }
                builder.build()
            }
            "POST" -> {
                buildPost(path, requestBody, headers)
            }
            "DELETE" -> {
                buildDelete(path, requestBody, headers)
            }
            "PUT" -> {
                buildPut(path, requestBody, headers)
            }
            else -> {
                throw CodeCCPluginException(
                    ErrorCode.UNSUPPORTED_HTTP_METHOD, "error method to execute: $method", arrayOf(method)
                )
            }
        }

        val backendRequest = request.newBuilder()
            .url("$codeccApiUrl$path")
            .build()

        if (printLog) {
            LogUtils.printDebugLog("taskExecution url: ${backendRequest.url} (${backendRequest.method}))")
            LogUtils.printDebugLog("CodeCC http request body: $jsonBody")
        }

        try {
            OkhttpUtils.doShortHttp(backendRequest).use { response ->
                val responseBody = response.body.use { body -> body!!.string() }
                if (!response.isSuccessful) {
                    LogUtils.printErrorLog(
                        "Fail to execute($path) task($jsonBody) " +
                            "because of ${response.message} with response: $responseBody"
                    )
                    throw CodeCCHttpStatusException(response.code, "Fail to invoke CodeCC request")
                }
                if (printLog) {
                    LogUtils.printDebugLog("Get the task response body - $responseBody")
                }
                return responseBody
            }
        } catch (ex: SocketException) {
            LogUtils.printErrorLog("execute command: curl ${backendRequest.url}")
            ScriptUtils.execute("curl ${backendRequest.url}", null)
            throw ex
        } catch (ex: SocketTimeoutException) {
            LogUtils.printErrorLog("execute command: curl ${backendRequest.url}")
            ScriptUtils.execute("curl ${backendRequest.url}", null)
            throw ex
        }
    }

    private fun getCommonHeader(): MutableMap<String, String> {
        return mutableMapOf(
            "x-devops-build-id" to (PluginRuntimeInfo.buildId ?: ""),
            "x-devops-project-id" to (PluginRuntimeInfo.projectId ?: "")
        )
    }

    private fun getCodeccResult(responseBody: String): CoverityResult {
        val result = objectMapper.readValue<CoverityResult>(responseBody)
        if (result.code != "0" || result.status != 0) {
            throw CodeCCBusinessException(
                ErrorCode.CODECC_RETURN_STATUS_CODE_ERROR, "execute CodeCC task fail",
                arrayOf(result.status.toString(), result.code, result.message ?: "")
            )
        }
        return result
    }

    private fun genToolCheckerV2(param: CodeccCheckAtomParam): List<ToolChecker> {
        val v2Param = param as CodeccCheckAtomParamV2
        return JsonUtil.getObjectMapper().readValue<Map<String, String>>(v2Param.toolRuleSets?.trim() ?: "")
            .map { ToolChecker(it.key, it.value) }
    }

    private fun genToolChecker(param: CodeccCheckAtomParam): List<ToolChecker> {
        return genToolRuleSet(param).map {
            ToolChecker(it.key, it.value)
        }
    }

    private fun genToolRuleSet(param: CodeccCheckAtomParam): Map<String, String> {
        val map = mutableMapOf<String, String>()
        with(param) {
            if (!coverityToolSetId.isNullOrBlank()) map["COVERITY"] = coverityToolSetId!!
            if (!klocworkToolSetId.isNullOrBlank()) map["KLOCWORK"] = klocworkToolSetId!!
            if (!cpplintToolSetId.isNullOrBlank()) map["CPPLINT"] = cpplintToolSetId!!
            if (!eslintToolSetId.isNullOrBlank()) map["ESLINT"] = eslintToolSetId!!
            if (!pylintToolSetId.isNullOrBlank()) map["PYLINT"] = pylintToolSetId!!
            if (!gometalinterToolSetId.isNullOrBlank()) map["GOML"] = gometalinterToolSetId!!
            if (!checkStyleToolSetId.isNullOrBlank()) map["CHECKSTYLE"] = checkStyleToolSetId!!
            if (!styleCopToolSetId.isNullOrBlank()) map["STYLECOP"] = styleCopToolSetId!!
            if (!detektToolSetId.isNullOrBlank()) map["DETEKT"] = detektToolSetId!!
            if (!phpcsToolSetId.isNullOrBlank()) map["PHPCS"] = phpcsToolSetId!!
            if (!sensitiveToolSetId.isNullOrBlank()) map["SENSITIVE"] = sensitiveToolSetId!!
            if (!occheckToolSetId.isNullOrBlank()) map["OCCHECK"] = occheckToolSetId!!
            if (!gociLintToolSetId.isNullOrBlank()) map["GOCILINT"] = gociLintToolSetId!!
            if (!ripsToolSetId.isNullOrBlank()) map["RIPS"] = ripsToolSetId!!
            if (!woodpeckerToolSetId.isNullOrBlank()) map["WOODPECKER_SENSITIVE"] = woodpeckerToolSetId!!
            if (!horuspyToolSetId.isNullOrBlank()) map["HORUSPY"] = horuspyToolSetId!!
            if (!pinpointToolSetId.isNullOrBlank()) map["PINPOINT"] = pinpointToolSetId!!
        }
        return map
    }

    private data class DevOpsToolParams(
        val varName: String,
        val chooseValue: String
    )

    private data class ToolChecker(
        val toolName: String,
        val checkerSetId: String
    )

    data class RuleSetCheckV3(
        val checkerSetId: String
    )

    /*
    {    "pattern" : "COVERITY",    "name" : "COVERITY",    "displayName" : "Coverity",    "type" : "DEFECT",    "lang" : 3839,    "recommend" : false,    "status" : "P",    "params" : "[]"  },
     {    "pattern" : "LINT",    "name" : "TSCCPP",    "displayName" : "TSC-Cpp",    "type" : "DEFECT",    "lang" : 2,    "recommend" : false,    "status" : "P"  },
     */
    data class ToolType(
        val pattern: String,
        val name: String,
        val type: String
    )

    /*
    {      "key" : "DEFECT",      "name" : "代码缺陷",      "fullName" : "发现代码缺陷",      "status" : "P",      "creator" : "systen_admin",      "createTime" : 1556610717    },
    {      "key" : "SENSITIVE_INFO",      "name" : "代码安全",      "fullName" : "发现安全问题",      "status" : "P",      "creator" : "systen_admin",      "createTime" : 1556610717    }
     */
    data class MetaToolType(
        val key: String,
        val name: String
    )

    data class FilterPathOutVO(
        var taskId: Long? = null,
        val defaultFilterPath: List<String>? = null,
        val defaultAddPaths: List<String>? = null,
        val filterPaths: List<String>? = null,
        val pathType: String? = null
    )

    data class PipelineTaskVO(
        var projectId: String,
        val taskId: Long,
        val enName: String,
        val cnName: String,
        val autoLang: Boolean,
        val codeLanguages: List<String>,
        val tools: List<PipelineTaskToolVO>
    )

    data class PipelineTaskToolVO(
        val toolName: String,
        val params: List<Map<String, String>>?
    )
}
