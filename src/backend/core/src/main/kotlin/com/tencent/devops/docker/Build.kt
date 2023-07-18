package com.tencent.devops.docker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.inject.Inject
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.*
import com.tencent.devops.docker.scm.ScmInfoNew
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.*
import com.tencent.devops.pojo.exception.*
import com.tencent.devops.pojo.exception.plugin.CodeCCPluginException
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.pojo.exception.user.CodeCCUserException
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.injector.service.ScanFilterService
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object Build {

    var codeccTaskInfo: TaskBaseVO? = null

    val toolRunResultMap = ConcurrentHashMap<String, ToolRunResult>()

    var codeRepoRevision : String? = null

    var headFileFiter : Boolean = false

    @Inject
    lateinit var scanFilterService : ScanFilterService

    fun build(
        param: CodeccCheckAtomParamV3,
        timeOut: Long,
        commandParam: CommandParam,
        openScanConfigParam: OpenScanConfigParam
    ): String {
        codeccTaskInfo = getCodeCCTaskInfo(commandParam.landunParam, param.codeCCTaskId!!.toLong())

        val streamName = param.codeCCTaskName!!
        var scanTools = getScanTools(commandParam, streamName)
        LogUtils.printLog("scanTools: $scanTools")
        if (scanTools.isEmpty()) {
            throw CodeCCUserException(
                ErrorCode.USER_SCAN_TOOL_EMPTY,
                "Scan tools is empty!"
            )
        }

        initToolResultMap(scanTools)

        if (scanTools.contains(ToolConstants.PINPOINT) && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            throw CodeCCUserException(
                ErrorCode.USER_PINPOINT_NO_SUPPORT,
                "pinpoint no support MacOS and Windows"
            )
        }

        // 过滤扫描工具
        scanTools = scanFilterService.filterScanTool(scanTools, openScanConfigParam, commandParam)

        if (!scanTools.contains(ToolConstants.SCC)) {
            LogUtils.printLog("no scc task should pre set ignore defect flag and head file flag")
            IgnoreDefectParser.getIgnoreDefectInfo(null, param.codeCCTaskId!!.toLong(), null, null)
            OCHeaderFileParser.getOcHeadFileInfo(null, param.codeCCTaskId!!.toLong(), null, null)
        }

        var staticScanTools = scanTools.minus(ToolConstants.COMPILE_TOOLS)
        if (staticScanTools.isNotEmpty() && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            if (!checkDocker(commandParam.landunParam.streamCodePath)) {
                throw CodeCCUserException(
                    ErrorCode.USER_ENV_MISSING,
                    "need install docker to run multiple tool",
                    arrayOf("docker")
                )
            }
        }

        val successCount = AtomicInteger(0)
        var runCompileTools = false
        var threadCount = if (staticScanTools.size == scanTools.size) {
            staticScanTools.size
        } else {
            runCompileTools = true
            staticScanTools.size + 1
        }

        // 如果选定开源扫描规则集，则过滤编译形语言
        scanFilterService.filterScanCheckerSet(param.checkerSetType, param)

        val scanToolsUperCase = scanTools.map { toolName -> toolName.toUpperCase() }
        CodeccSdkApi.uploadActualExeTools(param, scanToolsUperCase)


        // 异步执行所以需要用同步的StringBuffer追加日志
        val errorMsg = StringBuffer()
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val lock = CountDownLatch(threadCount)
            // 获取本次扫描版本信息
            if (commandParam.repos.isNullOrEmpty()) {
                commandParam.scmType = "git"
                commandParam.repos = listOf(
                    CodeccExecuteConfig.RepoItem(
                        repositoryConfig = null,
                        type = "git",
                        relPath = "",
                        relativePath = "",
                        url = "",
                        repoHashId = ""
                    )
                );
            }
            LogUtils.printDebugLog("start scm info...")
            if (!ScmInfoNew(commandParam, "", streamName, 0).scmLocalOperate()) {
                throw CodeCCScmException(
                    ErrorCode.SCM_INFO_NEW_FAIL,
                    "scm info failed."
                )
            }
            LogUtils.printDebugLog("start scm success")

            staticScanTools.forEach { tool ->
                executor.execute {
                    try {
                        LogUtils.printLog("start execute tool: $tool")
                        LocalParam.set(tool, param)
                        ScanComposer.scan(streamName, tool, commandParam.copy())
                        LogUtils.printLog("finish execute tool: $tool")
                        successCount.getAndIncrement()
                        toolRunResultMap[tool]?.status = ToolRunResult.ToolRunResultStatus.SUCCESS
                    } catch (e: Throwable) {
                        errorMsg.append("#### execute tool $tool exception ####: ${e.message}: \n" +
                            " ${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n\n")
                        toolRunResultMap[tool]?.status = ToolRunResult.ToolRunResultStatus.FAIL
                        if (e is CodeCCException) {
                            toolRunResultMap[tool]?.errorCode = e.errorCode
                            toolRunResultMap[tool]?.errorMsg = e.errorMsg
                        } else {
                            toolRunResultMap[tool]?.errorCode = CodeCCException.defaultErrorCode
                            toolRunResultMap[tool]?.errorMsg = e.message ?: CodeCCException.defaultErrorMsg
                        }
                    } finally {
                        if(tool == ToolConstants.SCC) {
                            IgnoreDefectParser.getIgnoreDefectInfo(null, param.codeCCTaskId!!.toLong(), null, null)
                            OCHeaderFileParser.getOcHeadFileInfo(null, param.codeCCTaskId!!.toLong(), null, null)
                        }
                        lock.countDown()
                        toolRunResultMap[tool]?.endTime = System.currentTimeMillis()
                    }
                }
            }

//            runCompileTools = false //just for test
            if (runCompileTools) {
                // 编译型工具，串行
                CodeccWeb.downloadAnyUnzip(commandParam)
                executor.execute {
                    var curTool = ""
                    try {
                        ToolConstants.COMPILE_TOOLS.forEach { compileTool ->
                            if (scanTools.contains(compileTool)) {
                                curTool = compileTool
                                LogUtils.printLog("start run $compileTool...")
                                LocalParam.set(compileTool, param)
                                ScanComposer.scan(streamName, compileTool, commandParam.copy())
                                LogUtils.printLog("finish run $compileTool")
                                LogUtils.finishLogTag(curTool)
                            }
                        }
                        successCount.getAndIncrement()
                        toolRunResultMap[curTool]?.status = ToolRunResult.ToolRunResultStatus.SUCCESS
                    } catch (e: Throwable) {
                        errorMsg.append("#### run $curTool fail ####: ${e.message}: \n " +
                            "${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n")
                        toolRunResultMap[curTool]?.status = ToolRunResult.ToolRunResultStatus.FAIL
                        if (e is CodeCCException) {
                            toolRunResultMap[curTool]?.errorCode = e.errorCode
                            toolRunResultMap[curTool]?.errorType = e.errorType
                            toolRunResultMap[curTool]?.errorMsg = e.errorMsg
                        } else {
                            toolRunResultMap[curTool]?.errorCode = CodeCCException.defaultErrorCode
                            toolRunResultMap[curTool]?.errorType = CodeCCException.defaultErrorType
                            toolRunResultMap[curTool]?.errorMsg = e.message ?: CodeCCException.defaultErrorMsg
                        }
                        LogUtils.finishLogTag(curTool)
                    } finally {
                        lock.countDown()
                        toolRunResultMap[curTool]?.endTime = System.currentTimeMillis()
                    }
                }
            }

            LogUtils.printLog("CodeCC scan task startup...")
            lock.await(timeOut, TimeUnit.MINUTES)
            if (successCount.get() != threadCount) {
                val toolRunResult = toolRunResultMap.entries.firstOrNull { it.value.status != ToolRunResult.ToolRunResultStatus.SUCCESS }?.value
                val errorCode = toolRunResult?.errorCode ?: CodeCCException.defaultErrorCode
                val errorType = toolRunResult?.errorType ?: CodeCCException.defaultErrorType
                throw CodeCCException(
                    errorCode = errorCode, errorType = errorType,
                    errorMsg = "\nFailed to run CodeCC task: $errorMsg", params = emptyArray()
                )
            }
            LogUtils.printLog("execute finished")
        } catch (e: InterruptedException) {
            toolRunResultMap.filter { it.value.status == ToolRunResult.ToolRunResultStatus.SKIP }
                .forEach { it.value.status = ToolRunResult.ToolRunResultStatus.TIMEOUT }
            throw CodeCCPluginException(
                ErrorCode.PLUGIN_TIMEOUT,
                e.message ?: ""
            )
        } catch (e: CodeCCException) {
            throw e
        } catch (e: Throwable) {
            throw CodeCCPluginException(ErrorCode.UNKNOWN_PLUGIN_ERROR, e.message ?: "")
        } finally {
            executor.shutdownNow()
        }

        // 结束
        LogUtils.printLog("[codecc] CodeCC scan finished, finish time: ${Date()}")
        return "success"
    }

    private fun initToolResultMap(scanTools: List<String>) {
        scanTools.forEach { tool ->
            toolRunResultMap[tool] = ToolRunResult(tool)
        }
    }

    private fun checkDocker(workspace: String): Boolean {
        return try {
            ScriptUtils.execute("docker -v", File(workspace))
            true
        } catch (e: Exception) {
            LogUtils.printErrorLog("machine do not has docker, need to install docker manual!")
            false
        }
    }

    private fun getScanTools(commandParam: CommandParam, streamName: String): MutableList<String> {
        val responseStr = CodeccWeb.codeccConfigByStream(commandParam.landunParam, streamName)
        val responseObj = jacksonObjectMapper().readValue<Map<String, Any?>>(responseStr)
        val scanTools = mutableListOf<String>()
        if (responseObj["data"] != null) {
            val data = responseObj["data"] as Map<String, Any?>
            if ((data["status"] as Int) == 1) {
                throw CodeCCUserException(
                    ErrorCode.USER_PROJECT_STOP,
                    "this project already stopped, can't scan by CodeCC!"
                )
            }
            if (data["toolSet"] != null) {
                scanTools.addAll((data["toolSet"] as List<String>).map { it.toLowerCase() })
            }
            if (data["codeLang"] != null) {
                val codeLang = data["codeLang"] as Int
                if ((codeLang and 2) > 0 && (codeLang and 16) > 0) {
                    LogUtils.printLog("project with c++ and oc need to filter head file")
                    headFileFiter = true
                }
            }
        }
        if (scanTools.contains(ToolConstants.SCC) && commandParam.prohibitCloc != null &&
                commandParam.prohibitCloc!! && scanTools.size > 1) {
            scanTools.remove(ToolConstants.SCC)
        }
        return scanTools
    }


    private fun getCodeCCTaskInfo(landunParam: LandunParam, taskId: Long): TaskBaseVO? {
        return try {
            val responseStr = CodeccWeb.codeccTaskInfoByTaskId(landunParam, taskId)
            val result = jacksonObjectMapper().readValue<Result<TaskBaseVO>>(responseStr)
            result.data
        } catch (e: Throwable) {
            LogUtils.printErrorLog("Get CodeCC taskInfo failed, e: ${e.message}")
            null
        }
    }

}
