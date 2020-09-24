package com.tencent.devops.docker

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.factory.SubProcessorFactory
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.docker.pojo.Result
import com.tencent.devops.docker.pojo.TaskBaseVO
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.OpenScanConfigParam
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object Build {

    fun build(
        param: CodeccCheckAtomParamV3,
//        streamName: String,
//        codeccTaskId: String,
        timeOut: Long,
        commandParam: CommandParam,
        openScanConfigParam: OpenScanConfigParam
    ): String {
        val streamName = param.codeCCTaskName!!
        val codeccTaskId = param.codeCCTaskId!!
        val scanTools = getScanTools(commandParam.landunParam, streamName)
        println("[初始化] scanTools: $scanTools")
        if (scanTools.isEmpty()) {
            throw CodeccUserConfigException("Scan tools is empty!")
        }

        if (scanTools.contains(ToolConstants.PINPOINT) && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            throw CodeccUserConfigException("pinpoint工具不支持MACOS和Windows第三方构建机, 请重新选择规则集")
        }
        var staticScanTools = scanTools.minus(ToolConstants.COMPILE_TOOLS)
        if (staticScanTools.isNotEmpty() && AgentEnv.isThirdParty() && (CodeccEnvHelper.getOS() != OSType.LINUX)) {
            if (!checkDocker(commandParam.landunParam.streamCodePath)) {
                println("多工具需要在MACOS和Windows第三方构建机上安装Docker")
                throw CodeccUserConfigException("多工具需要在MACOS和Windows第三方构建机上安装Docker")
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

        // 如果是开源扫描的工程，则只扫静态工具
        val codeccTaskInfo = getCodeCCTaskInfo(commandParam.landunParam, codeccTaskId.toLong())
        val subBuild = SubProcessorFactory().createSubBuild(codeccTaskInfo)
        val subBuildInfo = subBuild.subBuild(codeccTaskInfo,commandParam,staticScanTools,openScanConfigParam,threadCount,runCompileTools)
        if (subBuildInfo != null) {
            staticScanTools = subBuildInfo["staticScanTools"] as List<String>
            runCompileTools = subBuildInfo["runCompileTools"] as Boolean
            threadCount = subBuildInfo["threadCount"] as Int
        }

        // 异步执行所以需要用同步的StringBuffer追加日志
        val errorMsg = StringBuffer()
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val lock = CountDownLatch(threadCount)
            staticScanTools.forEach { tool ->
                executor.execute {
                    try {
                        println("[$tool] start execute tool: $tool")
                        LocalParam.set(tool, param)
                        ScanComposer.scan(streamName, tool, commandParam.copy())
                        println("[$tool] finish execute tool: $tool")
                        successCount.getAndIncrement()
                    } catch (e: Exception) {
                        errorMsg.append("#### execute tool $tool exception ####: ${e.message}: \n" +
                            " ${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n\n")
                    } finally {
                        lock.countDown()
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
                                println("[$compileTool] start run $compileTool...")
                                LocalParam.set(compileTool, param)
                                ScanComposer.scan(streamName, compileTool, commandParam.copy())
                                println("[$compileTool] finish run $compileTool")
                            }
                        }
                        successCount.getAndIncrement()
                    } catch (e: Exception) {
                        errorMsg.append("#### run $curTool fail ####: ${e.message}: \n " +
                            "${e.stackTrace.map { "${it.className}#${it.methodName}(${it.lineNumber})\n" }};\n")
                    } finally {
                        lock.countDown()
                    }
                }
            }

            println("CodeCC scan task startup...")
            lock.await(timeOut, TimeUnit.MINUTES)
            if (successCount.get() != threadCount) throw CodeccTaskExecException("运行CodeCC任务失败:\n $errorMsg")
            println("execute finished")
        } finally {
            executor.shutdownNow()
        }

        // 结束
        println("[codecc] CodeCC scan finished, finish time: ${Date()}")
        return "success"
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
                throw CodeccUserConfigException("this project already stopped, can't scan by CodeCC!")
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
