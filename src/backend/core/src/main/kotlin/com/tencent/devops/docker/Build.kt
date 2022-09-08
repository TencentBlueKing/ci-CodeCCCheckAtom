package com.tencent.devops.docker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.*
import com.tencent.devops.docker.scm.ScmInfoNew
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.OpenScanConfigParam
import com.tencent.devops.pojo.ToolRunResult
import com.tencent.devops.pojo.exception.*
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.OpenSourceUtils
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

    fun build(
        param: CodeccCheckAtomParamV3,
//        streamName: String,
//        codeccTaskId: String,
        timeOut: Long,
        commandParam: CommandParam,
        openScanConfigParam: OpenScanConfigParam
    ): String {
        codeccTaskInfo = getCodeCCTaskInfo(commandParam.landunParam, param.codeCCTaskId!!.toLong())

        val streamName = param.codeCCTaskName!!
        var scanTools = getScanTools(commandParam.landunParam, streamName)
        println("[初始化] scanTools: $scanTools")
        if (scanTools.isEmpty()) {
            throw CodeccUserConfigException("Scan tools is empty!", "")
        }

        initToolResultMap(scanTools)

        if (scanTools.contains(ToolConstants.PINPOINT) && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            throw CodeccUserConfigException("pinpoint工具不支持MACOS和Windows第三方构建机, 请重新选择规则集", "")
        }

        if (scanTools.contains(ToolConstants.RESHARPER) && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.WINDOWS)) {
            throw CodeccUserConfigException("resharper工具不支持MACOS和LINUX第三方构建机，请重新选择规则集", "")
        }

        // 如果是开源扫描的工程，则要根据条件进行过滤
        if (null != codeccTaskInfo && codeccTaskInfo!!.createFrom == "gongfeng_scan") {
            LogUtils.printDebugLog("codeccTaskInfo create from is gongfeng_scan")
            scanTools = OpenSourceUtils.calculateOpensourceThread(scanTools, openScanConfigParam, commandParam)
            // CodeccSdkApi.uploadActualExeTools(param, scanTools)
        }

        var staticScanTools = scanTools.minus(ToolConstants.COMPILE_TOOLS)
        if (staticScanTools.isNotEmpty() && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            if (!checkDocker(commandParam.landunParam.streamCodePath)) {
                throw CodeccUserConfigException("多工具需要在MACOS和Windows第三方构建机上安装Docker", "")
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
        if (param.checkerSetType == "openScan") {
            LogUtils.printDebugLog("checker set type is openScan")
            // scanTools = scanTools.minus(ToolConstants.COMPILE_TOOLS).toMutableList()
            // CodeccSdkApi.uploadActualExeTools(param, scanTools)
            System.setProperty("checkerType", param.checkerSetType!!)
        }
        val scanToolsUperCase = scanTools.map { toolName -> toolName.toUpperCase() }
        CodeccSdkApi.uploadActualExeTools(param, scanToolsUperCase)


        // 异步执行所以需要用同步的StringBuffer追加日志
        val errorMsg = StringBuffer()
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val lock = CountDownLatch(threadCount)
            // 获取本次扫描版本信息
            LogUtils.printDebugLog("start scm info...")
            if (!ScmInfoNew(commandParam, "", streamName, 0).scmOperate()) {
                throw CodeccRepoServiceException("scm info failed.")
            }
            LogUtils.printDebugLog("start scm success")

            staticScanTools.forEach { tool ->
                executor.execute {
                    try {
                        println("[$tool] start execute tool: $tool")
                        LocalParam.set(tool, param)
                        ScanComposer.scan(streamName, tool, commandParam.copy())
                        println("[$tool] finish execute tool: $tool")
                        successCount.getAndIncrement()
                        toolRunResultMap[tool]?.status = ToolRunResult.ToolRunResultStatus.SUCCESS
                    } catch (e: Throwable) {
                        errorMsg.append("#### execute tool $tool exception ####: ${e.message}: \n" +
                            " ${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n\n")
                        toolRunResultMap[tool]?.status = ToolRunResult.ToolRunResultStatus.FAIL
                        if (e is CodeccException) {
                            toolRunResultMap[tool]?.errorCode = e.errorCode
                            toolRunResultMap[tool]?.errorMsg = e.errorMsg
                        } else {
                            toolRunResultMap[tool]?.errorCode = CodeccException.errorCode
                            toolRunResultMap[tool]?.errorMsg = e.message ?: ""
                        }
                    } finally {
                        lock.countDown()
                        toolRunResultMap[tool]?.endTime = System.currentTimeMillis()
                    }
                }
            }

//            runCompileTools = false //just for test
            if (runCompileTools) {
                // 编译型工具，串行
                //CodeccWeb.downloadAnyUnzip(commandParam)
                executor.execute {
                    var curTool = ""
                    try {
                        ToolConstants.COMPILE_TOOLS.forEach { compileTool ->
                            if (scanTools.contains(compileTool)) {
                                curTool = compileTool
                                println("[$compileTool] start run $compileTool...")
                                LocalParam.set(compileTool, param)
                                ScanComposer.scan(streamName, compileTool, commandParam.copy())
                                println("[$compileTool] finish run $compileTool")
                                LogUtils.finishLogTag(curTool)
                            }
                        }
                        successCount.getAndIncrement()
                        toolRunResultMap[curTool]?.status = ToolRunResult.ToolRunResultStatus.SUCCESS
                    } catch (e: Throwable) {
                        errorMsg.append("#### run $curTool fail ####: ${e.message}: \n " +
                            "${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n")
                        toolRunResultMap[curTool]?.status = ToolRunResult.ToolRunResultStatus.FAIL
                        if (e is CodeccException) {
                            toolRunResultMap[curTool]?.errorCode = e.errorCode
                            toolRunResultMap[curTool]?.errorType = e.errorType
                            toolRunResultMap[curTool]?.errorMsg = e.errorMsg
                        } else {
                            toolRunResultMap[curTool]?.errorCode = CodeccException.errorCode
                            toolRunResultMap[curTool]?.errorType = CodeccException.errorType
                            toolRunResultMap[curTool]?.errorMsg = e.message ?: ""
                        }
                        LogUtils.finishLogTag(curTool)
                    } finally {
                        lock.countDown()
                        toolRunResultMap[curTool]?.endTime = System.currentTimeMillis()
                    }
                }
            }

            println("CodeCC scan task startup...")
            lock.await(timeOut, TimeUnit.MINUTES)
            if (successCount.get() != threadCount) {
                val toolRunResult = toolRunResultMap.entries.firstOrNull { it.value.status != ToolRunResult.ToolRunResultStatus.SUCCESS }?.value
                val errorCode = toolRunResult?.errorCode ?: CodeccException.errorCode
                val errorType = toolRunResult?.errorType ?: CodeccException.errorType
                throw CodeccException(errorCode = errorCode, errorType = errorType, errorMsg = "\n运行CodeCC任务失败: $errorMsg", toolName = "")
            }
            println("execute finished")
        } catch (e: InterruptedException) {
            toolRunResultMap.filter { it.value.status == ToolRunResult.ToolRunResultStatus.SKIP }
                .forEach { it.value.status = ToolRunResult.ToolRunResultStatus.TIMEOUT }
            throw CodeccTimeOutException(e.message ?: "")
        } catch (e: CodeccException) {
            throw e
        } catch (e: Throwable) {
            throw CodeccTaskExecException(e.message ?: "")
        } finally {
            executor.shutdownNow()
        }

        // 结束
        println("[codecc] CodeCC scan finished, finish time: ${Date()}")
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
            System.err.println("machine do not has docker, need to install docker manual!")
            false
        }
    }

    private fun getScanTools(landunParam: LandunParam, streamName: String): MutableList<String> {
        val responseStr = CodeccWeb.codeccConfigByStream(landunParam, streamName)
        val responseObj = jacksonObjectMapper().readValue<Map<String, Any?>>(responseStr)
        val scanTools = mutableListOf<String>()
        if (responseObj["data"] != null) {
            val data = responseObj["data"] as Map<String, Any?>
            if ((data["status"] as Int) == 1) {
                throw CodeccUserConfigException(errorMsg = "this project already stopped, can't scan by CodeCC!", toolName = "")
            }
            if (data["toolSet"] != null) {
                scanTools.addAll((data["toolSet"] as List<String>).map { it.toLowerCase() })
            }
        }
        return scanTools
    }


    private fun getCodeCCTaskInfo(landunParam: LandunParam, taskId: Long): TaskBaseVO? {
        return try {
            val responseStr = CodeccWeb.codeccTaskInfoByTaskId(landunParam, taskId)
            val result = jacksonObjectMapper().readValue<Result<TaskBaseVO>>(responseStr)
            result.data
        } catch (e: Throwable) {
            println("Get CodeCC taskInfo failed, e: ${e.message}")
            null
        }
    }

}
