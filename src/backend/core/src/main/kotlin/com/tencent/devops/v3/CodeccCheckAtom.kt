package com.tencent.devops.v3

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.ArtifactData
import com.tencent.bk.devops.atom.pojo.MonitorData
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.Build
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.codeccDetail
import com.tencent.devops.pojo.codeccFrontHost
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
        val monitorData = MonitorData()
        monitorData.channel = atomContext.param.channelCode
        monitorData.startTime = System.currentTimeMillis()
        try {
            if (atomContext.param.asyncTask == true) {
                val asyncTaskId = atomContext.param.asyncTaskId ?: throw CodeccUserConfigException("任务ID不能为空!")
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

            // 归档报告
            CodeccReportUtilsV2.report(atomContext)
        }  catch (e: Throwable) {
            val isCodeCCException = e is CodeccException
            atomContext.result.errorCode = if (isCodeCCException) (e as CodeccException).errorCode else CodeccException.errorCode
            atomContext.result.errorType = if (isCodeCCException) (e as CodeccException).errorType else CodeccException.errorType
            atomContext.result.message = if (isCodeCCException) (e as CodeccException).errorMsg else CodeccException.errorMsg
            atomContext.result.status = Status.failure
            if (null != atomContext.param.openScanPrj && atomContext.param.openScanPrj!!) {
                println("CodeCC执行失败，归档agent.log日志")
                atomContext.result.data["codeccAgentLog"] = ArtifactData(setOf("/data/landun/logs/1/agent.log"))
            }
            throw e
        } finally {
            monitorData.endTime = System.currentTimeMillis()
            monitorData.extData["BK_CI_CODEC_TOOL_RUN_RESULT"] = Build.toolRunResultMap
            monitorData.extData["BK_CI_CODECC_TASK_BG_ID"] = Build.codeccTaskInfo?.bgId
            monitorData.extData["BK_CI_CODECC_TASK_CENTER_ID"] = Build.codeccTaskInfo?.centerId
            monitorData.extData["BK_CI_CODECC_TASK_DEPT_ID"] = Build.codeccTaskInfo?.deptId
            monitorData.extData["BK_CI_DEVOPS_BUILD_NUM"] = atomContext.param.pipelineBuildNum.toString()
            atomContext.result.monitorData = monitorData
            if (atomContext.result.errorCode != null) {
                LogUtils.printErrorLog("atomContext.result.errorCode: ${atomContext.result.errorCode}")
                LogUtils.printErrorLog("atomContext.result.type: ${atomContext.result.errorType}")
            }
            LogUtils.printDebugLog("atomContext.result.message: ${atomContext.result.message}")
            LogUtils.printDebugLog("atomContext.result.status: ${atomContext.result.status}")
            with(atomContext.param) {
                println("CodeCC任务详情：<a href='${detailLink(projectName, codeCCTaskId)}' target='_blank'>查看详情</a>")
            }

            //开源项目上报commitId
            if (null != atomContext.param.openScanPrj && atomContext.param.openScanPrj!! && !Build.codeRepoRevision.isNullOrBlank()){
                println("上报commitId至映射表")
                CodeccSdkApi.updateCommitId(atomContext.param.pipelineBuildId, Build.codeRepoRevision!!)
            }

            //清理.temp目录
            CodeccEnvHelper.deleteCodeccWorkspace()
        }
    }

    private fun detailLink(projectName: String, codeCCTaskId: String?): String {
        return if (projectName.startsWith("git_")) {
            "${codeccDetail}/codecc/$projectName/task/$codeCCTaskId/detail"
        } else {
            "${codeccFrontHost}/console/codecc/$projectName/task/$codeCCTaskId/detail"
        }
    }
}
