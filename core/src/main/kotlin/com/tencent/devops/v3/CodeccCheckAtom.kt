package com.tencent.devops.v3

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.devops.common.factory.SubProcessorFactory
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccException
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.utils.CodeccConfigUtils
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.CodeccReportUtilsV2
import com.tencent.devops.utils.CodeccSdkUtils
import com.tencent.devops.utils.CodeccUtils
import com.tencent.devops.utils.WindowsCodeccUtils

@AtomService(paramClass = CodeccCheckAtomParamV3::class)
class CodeccCheckAtom : TaskAtom<CodeccCheckAtomParamV3> {

    override fun execute(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        val startTime = System.currentTimeMillis()
        try {
            if (atomContext.param.asyncTask == true) {
                val asyncTaskId = atomContext.param.asyncTaskId
                if (null == asyncTaskId) {
                    throw CodeccUserConfigException("任务ID不能为空!")
                }
                println("执行异步CodeCC任务，任务ID：$asyncTaskId")
                CodeccSdkUtils.executeAsyncTask(asyncTaskId, atomContext.param.pipelineStartUserName)
                atomContext.param.codeCCTaskId = asyncTaskId.toString()
                print("启动异步CodeCC任务成功，")
                CodeccReportUtilsV2.asyncReport(atomContext, detailLink(atomContext.param.projectName, atomContext.param.codeCCTaskId))
                return
            }
            println("asyncTask is false")

            // 创建或更新codecc任务
            val codeccExecuteConfig = CodeccSdkUtils.initCodecc(atomContext)

            // 第三方机器初始化
            CodeccEnvHelper.thirdInit(codeccExecuteConfig)

            // 先写入codecc任务
            CodeccEnvHelper.saveTask(atomContext)

            // 执行codecc的python脚本
            // 执行codecc的python脚本
            if (CodeccEnvHelper.getOS() == OSType.WINDOWS) WindowsCodeccUtils().executeCommand(codeccExecuteConfig)
            else CodeccUtils().executeCommand(codeccExecuteConfig)

            // 写入环境变量
            atomContext.result.data.putAll(CodeccEnvHelper.getCodeccEnv(atomContext.param.bkWorkspace))
            atomContext.result.data["BK_CI_CODECC_TASK_ID"] = StringData(atomContext.param.codeCCTaskId)
            atomContext.result.data["BK_CI_CODECC_TASK_STATUS"] = StringData("true")

            // 归档报告，工蜂扫描的获取不到报告
            if (atomContext.param.channelCode != "GONGFENGSCAN") CodeccReportUtilsV2.report(atomContext)
        } catch (e: CodeccException) {
            atomContext.result.errorCode = e.errorCode
            atomContext.result.message = e.message
            atomContext.result.status = Status.failure

            val subReport = SubProcessorFactory().createSubReport(atomContext.param.openScanPrj)
            subReport.doTaskFailReport(atomContext,startTime)

            throw e
        } catch (e: Exception) {
            atomContext.result.errorCode = CodeccException.errorCode
            atomContext.result.message = CodeccException.errorMsg
            atomContext.result.status = Status.failure

            val subReport = SubProcessorFactory().createSubReport(atomContext.param.openScanPrj)
            subReport.doTaskFailReport(atomContext,startTime)

            throw e
        } finally {
            with(atomContext.param) {
                println("CodeCC任务详情：<a href='${detailLink(projectName, codeCCTaskId)}' target='_blank'>查看详情</a>")
            }
        }
    }

    private fun detailLink(projectName: String, codeCCTaskId: String?): String {
        return if (projectName.startsWith("git_")) {
            "${CodeccConfigUtils.getPropConfig("codeccDetail")}/codecc/$projectName/task/$codeCCTaskId/detail"
        } else {
            "${CodeccConfigUtils.getPropConfig("codeccFrontHost")}/console/codecc/$projectName/task/$codeCCTaskId/detail"
        }
    }
}
