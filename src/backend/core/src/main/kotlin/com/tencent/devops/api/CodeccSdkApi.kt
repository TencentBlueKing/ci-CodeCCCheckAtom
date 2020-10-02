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
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.common.factory.SubProcessorFactory
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.CodeccCheckAtomParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV2
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CoverityResult
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.exception.CodeccException
import com.tencent.devops.pojo.report.TaskFailReportReq
import com.tencent.devops.pojo.sdk.CodeYmlFilterPathVO
import com.tencent.devops.pojo.sdk.FilterPathInput
import com.tencent.devops.pojo.sdk.NotifyCustom
import com.tencent.devops.pojo.sdk.ScanConfiguration
import com.tencent.devops.utils.CodeccConfigUtils
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.CodeccEnvHelper.getOS
import com.tencent.devops.utils.common.AtomUtils
import okhttp3.MediaType
import okhttp3.RequestBody
import java.net.URLEncoder

object CodeccSdkApi : BaseApi() {

    private val codeccApiUrl: String = CodeccConfigUtils.getPropConfig("codeccHost")!!
    private val createPath: String = "/ms/task/api/build/task"
    private val existPath: String = "/ms/task/api/build/task/exists"
    private val deletePath: String = "/ms/task/api/build/task"
    private val startPath: String = "/ms/task/api/build/task/startSignal/taskId/{taskId}/buildId/{buildId}"
    private val executePath: String = "/ms/task/api/build/task/execute"
    private val metadataPath = "/ms/task/api/build/meta/metadatas"
    private val getTaskPath: String = "/ms/task/api/build/task/pipeline/{pipelineId}?userId={userId}"

    private val objectMapper = JsonUtil.getObjectMapper()
    private const val USER_NAME_HEADER = "X-DEVOPS-UID"
    private const val DEVOPS_PROJECT_ID = "X-DEVOPS-PROJECT-ID"
    private const val CONTENT_TYPE = "Content-Type"
    private const val CONTENT_TYPE_JSON = "application/json"

    fun createTask(param: CodeccCheckAtomParam, openScanProj: Boolean? = false): CoverityResult {
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
            var body = mapOf(
                "projectId" to projectName,
                "projectName" to projectNameCn,
                "pipelineId" to pipelineId,
                "pipelineName" to pipelineName,
                "scanType" to (scanType ?: (if (openScanProj == true) "0" else "1")),
                "compilePlat" to getPlatForm(),
                "devopsCodeLang" to (languages ?: "[]"),
                "devopsTools" to "",
                "devopsToolParams" to devopsToolParams,
                "checkerSetList" to getRuleSetV3(param),
                "nameCn" to pipelineName,
                "atomCode" to "CodeccCheckAtomDebug",
                "projectBuildCommand" to script,
                "projectBuildType" to if (getOS() == OSType.WINDOWS) "BAT" else "SHELL",
                "compilePlat" to getOS().name,
                "forceToUpdateOpenSource" to (System.getenv("FORCE_UPDATE_CODECC_OPEN_SOURCE") == "true"),
                "openSourceCheckerSetType" to System.getenv("OPEN_SOURCE_CHECKER_SET_TYPE")

            )

            // 创建对应的工蜂或者普通的处理类
            val subSdkApi = SubProcessorFactory().createSubSdkApi(openScanProj!!)

            // 添加工蜂扫描任务参数，区别工蜂任务
            val codeccOpenSourceJson = System.getenv("BK_CI_OPEN_SOURCE_JSON")
            if (!codeccOpenSourceJson.isNullOrBlank() && openScanProj == true) {
                println("add opensource custom input param")
                body = body.plus(subSdkApi.addInputParam(codeccOpenSourceJson))
            }

            LogUtils.printDebugLog("start to create task: $body")

            val header = mutableMapOf(
                USER_NAME_HEADER to param.pipelineStartUserName,
                DEVOPS_PROJECT_ID to param.projectName,
                CONTENT_TYPE to CONTENT_TYPE_JSON
            )

            // 设置url,区分工蜂和普通创建任务url
            val url =subSdkApi.getUrl()

            val result =
                getCodeccResult(taskExecution(objectMapper.writeValueAsString(body), url, header, "POST"))
            val resultMap = result.data as Map<String, Any>

            println("[初始化] Create CodeCC task response: $resultMap")

            param.codeCCTaskName = resultMap["nameEn"]?.toString() ?: throw CodeccDependentException("create task fail")
            param.codeCCTaskId = resultMap["taskId"]?.toString() ?: throw CodeccDependentException("create task fail")

            // 通知codecc启动
            startTask(param.codeCCTaskId!!, param.pipelineBuildId)

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

    private fun startTask(taskId: String, buildId: String) {
        val path = startPath.replace("{taskId}", taskId).replace("{buildId}", buildId)
        taskExecution(path = path)
    }

    private fun getRuleSetV3(param: CodeccCheckAtomParam): List<RuleSetCheckV3> {
        return if (param is CodeccCheckAtomParamV3) {
            if (param.languageRuleSetMap.isNullOrBlank()) return listOf()
            val languageRuleSetMap =
                JsonUtil.getObjectMapper().readValue<Map<String, List<String>>>(param.languageRuleSetMap!!)
            val ruleSetIds = mutableSetOf<String>()
            languageRuleSetMap.values.forEach { ruleSetIds.addAll(it) }
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

    fun updateTask(param: CodeccCheckAtomParam, openScanProj: Boolean? = false): CoverityResult {
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
                "openSourceCheckerSetType" to System.getenv("OPEN_SOURCE_CHECKER_SET_TYPE")
            )

            // 创建对应的工蜂或者普通的处理类
            val subSdkApi = SubProcessorFactory().createSubSdkApi(openScanProj!!)

            // 添加工蜂扫描任务参数，区别工蜂任务
            val codeccOpenSourceJson = System.getenv("BK_CI_OPEN_SOURCE_JSON")
            if (!codeccOpenSourceJson.isNullOrBlank() && openScanProj == true) {
                println("add opensource custom input param")
                body = body.plus(subSdkApi.addInputParam(codeccOpenSourceJson))
            }
            println("Update the coverity task($body)")
            val header = mutableMapOf(
                USER_NAME_HEADER to param.pipelineStartUserName,
                CONTENT_TYPE to CONTENT_TYPE_JSON
            )

            // 设置url,区分工蜂和普通创建任务url
            val url =subSdkApi.getUrl()

            getCodeccResult(taskExecution(objectMapper.writeValueAsString(body), url, header, "PUT"))
        }
    }

    fun isTaskExist(taskId: String): Boolean {
        println("Check the coverity task if exist")
        val header = mutableMapOf(CONTENT_TYPE to CONTENT_TYPE_JSON)
        val result = getCodeccResult(taskExecution("", "$existPath/$taskId", header, "GET"))
        println("Get the exist result($result)")
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

    fun getTaskByPipelineId(pipelineId: String, userId: String): PipelineTaskVO {
        println("get the codecc task if exist: $pipelineId, $userId")
        val header = mutableMapOf(CONTENT_TYPE to CONTENT_TYPE_JSON)
        val path = getTaskPath.replace("{pipelineId}", pipelineId).replace("{userId}", userId)
        val result = taskExecution("", path, header, "GET")
        println("Get the codecc task($result)")
        return JsonUtil.to(result, object : TypeReference<Result<PipelineTaskVO>>() {}).data!!
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
            println("do not add filter path")
            Result(true)
        }
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

    private fun getFileInput(params: CodeccCheckAtomParamV3): FilterPathInput {
        val oldPaths = getFilterPath(params).data?.filterPaths ?: listOf()
        val curPaths = AtomUtils.parseStringToList(params.customPath).map { it.trim() }
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
        val newPaths = AtomUtils.parseStringToList(param.customPath)
        val userId = param.pipelineStartUserName
        val taskId = param.codeCCTaskId
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId!!,
            "X-DEVOPS-TASK-ID" to taskId!!
        )
        oldPaths.minus(newPaths).forEach { path ->
            taskExecution(path = "/ms/task/api/build/task/del/filter?path=${URLEncoder.encode(path, "utf8")}&pathType=CUSTOM", headers = headers, method = "DELETE")
            println("delete filter path success: $path")
        }
    }

    fun getCodeccToolType(userId: String): List<ToolType> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val path = "/ms/task/api/build/meta/toolList?isDetail=false"
        val responseContent = taskExecution(path = path, headers = headers, printLog = false)
        println("get tool type successs")
        return JsonUtil.to(responseContent, object : TypeReference<Result<List<ToolType>>>() {}).data ?: listOf()
    }

    fun getMetadataToolType(userId: String): List<MetaToolType> {
        val headers = mutableMapOf(
            "X-DEVOPS-UID" to userId
        )
        val responseContent =
            taskExecution(path = "$metadataPath?metadataType=TOOL_TYPE", headers = headers, printLog = false)
        println("get metadata tool type success")
        val result = JsonUtil.getObjectMapper().readValue<Result<Map<String, List<Map<String, String>>>>>(responseContent).data
            ?: mapOf()
        return (result["TOOL_TYPE"] as List<Map<String, Any>>).map {
            MetaToolType(
                key = it["key"] as String,
                name = it["name"] as String
            )
        }
    }



    fun taskExecution(
        jsonBody: String = "",
        path: String,
        headers: MutableMap<String, String> = mutableMapOf(),
        method: String = "GET",
        printLog: Boolean = true
    ): String {

        val requestBody = RequestBody.create(
            MediaType.parse("application/json; charset=utf-8"), jsonBody
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
                throw CodeccException(errorMsg = "error method to execute: $method")
            }
        }

        val backendRequest = request.newBuilder()
            .url("$codeccApiUrl$path")
            .build()

        if (printLog) {
            LogUtils.printDebugLog("taskExecution url: ${backendRequest.url()} (${backendRequest.method()}))")
            LogUtils.printDebugLog("CodeCC http request body: $jsonBody")
        }

        OkhttpUtils.doHttp(backendRequest).use { response ->
            val responseBody = response.body()!!.string()
            if (!response.isSuccessful) {
                System.err.println(
                    "Fail to execute($path) task($jsonBody) " +
                        "because of ${response.message()} with response: $responseBody"
                )
                throw CodeccDependentException("Fail to invoke CodeCC request")
            }
            if (printLog) {
                LogUtils.printDebugLog("Get the task response body - $responseBody")
            }
            return responseBody
        }
    }

    private fun getCodeccResult(responseBody: String): CoverityResult {
        val result = objectMapper.readValue<CoverityResult>(responseBody)
        if (result.code != "0" || result.status != 0) throw CodeccDependentException("execute CodeCC task fail, code: ${result.code}, status: ${result.status} msg: ${result.message}")
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

    private data class RuleSetCheckV3(
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
        val tools: List<PipelineTaskToolVO>
    )

    data class PipelineTaskToolVO(
        val toolName: String,
        val params: List<Map<String, String>>?
    )
}
