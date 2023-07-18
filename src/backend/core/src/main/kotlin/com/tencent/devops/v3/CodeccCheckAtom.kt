package com.tencent.devops.v3

import com.google.inject.Inject
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.ArtifactData
import com.tencent.bk.devops.atom.pojo.MonitorData
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.Build
import com.tencent.devops.docker.CCNDefectProcessor
import com.tencent.devops.docker.CommonDefectProcessor
import com.tencent.devops.docker.LintDefectProcessor
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.injector.ServiceInjector
import com.tencent.devops.injector.service.ScanFinishProcessService
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.env.PluginRuntimeInfo
import com.tencent.devops.pojo.exception.CodeCCException
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCBusinessException
import com.tencent.devops.pojo.scan.SetForceFullScanReqVO
import com.tencent.devops.utils.CodeccConfigUtils
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.CodeccReportUtilsV2
import com.tencent.devops.utils.CodeccSdkUtils
import com.tencent.devops.utils.CodeccUtils
import com.tencent.devops.utils.I18NUtils
import com.tencent.devops.utils.ToolUtils
import com.tencent.devops.utils.WindowsCodeccUtils

@AtomService(paramClass = CodeccCheckAtomParamV3::class)
class CodeccCheckAtom : TaskAtom<CodeccCheckAtomParamV3> {

    @Inject
    lateinit var scanFinishProcessService: ScanFinishProcessService

    override fun execute(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        if (atomContext.param.toolScanType == "2" && atomContext.param.byFile == true) {
            atomContext.param.toolScanType = "5"
        }
        PluginRuntimeInfo.initRuntimeInfo(atomContext)
        //注入
        ServiceInjector.injectService()
        scanFinishProcessService = ServiceInjector.inject(ScanFinishProcessService::class.java)

        LintDefectProcessor.init(listOf("lintPPHash", "md5", "lintIgnore"))
        CCNDefectProcessor.init(listOf("ccnPPHash", "md5", "ccnIgnore"))
        CommonDefectProcessor.init(listOf("md5"))
        I18NUtils.init(atomContext.param)
        val monitorData = MonitorData()
        monitorData.channel = atomContext.param.channelCode
        monitorData.startTime = System.currentTimeMillis()
        try {
            if (atomContext.param.asyncTask == true) {
                val asyncTaskId = atomContext.param.asyncTaskId ?: throw CodeCCBusinessException(
                    ErrorCode.CODECC_RETURN_PARAMS_CHECK_FAIL,
                    "AsyncTaskId cannot be null!",
                    arrayOf("asyncTaskId")
                )
                LogUtils.printLog("Execute asynchronous CodeCC task, task ID: $asyncTaskId")
                CodeccSdkUtils.executeAsyncTask(asyncTaskId, atomContext.param.pipelineStartUserName)
                atomContext.param.codeCCTaskId = asyncTaskId.toString()
                LogUtils.printLog("Start asynchronous CodeCC task successfully")
                CodeccReportUtilsV2.asyncReport(atomContext, detailLink(atomContext.param.projectName, atomContext.param.codeCCTaskId))
                return
            }
            LogUtils.printLog("asyncTask is false")

            val equalAutoTask = doAutoLang(atomContext)
            doExecute(atomContext)
            updateTaskLangAuto(atomContext, equalAutoTask)
            doFinalJob(atomContext)
        } catch (e: Exception) {
            val isCodeCCException = e is CodeCCException
            if (isCodeCCException) {
                (e as CodeCCException).logErrorCodeMsg()
            }
            atomContext.result.errorCode =
                if (isCodeCCException) (e as CodeCCException).errorCode else CodeCCException.defaultErrorCode
            atomContext.result.errorType =
                if (isCodeCCException) (e as CodeCCException).errorType else CodeCCException.defaultErrorType
            atomContext.result.message =
                if (isCodeCCException) (e as CodeCCException).errorMsg else CodeCCException.defaultErrorMsg
            atomContext.result.status = Status.failure
            if (null != atomContext.param.openScanPrj && atomContext.param.openScanPrj!!) {
                LogUtils.printErrorLog("CodeCC execution failed, archive agent.log log")
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
                LogUtils.printLog("CodeCC task details：<a href='${
                        detailLink(projectName, codeCCTaskId)}' target='_blank'>view details</a>")
            }

            // 完成后回调
            scanFinishProcessService.processAfterScanFinish(atomContext)
        }
    }

    private fun doFinalJob(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        // 流水线和stream创建任务则检查红线数据上传状态
        if (atomContext.param.channelCode == "BS" || atomContext.param.channelCode == "GIT") {
            LogUtils.printLog("report channel ${atomContext.param.channelCode}, ready to get redRine status")
            val res = atomContext.param.codeCCTaskId?.let {
                CodeccReportUtilsV2.getRedLineMetadataReportStatus(atomContext.param.projectName,
                    it, atomContext.param.pipelineBuildId)
            }
            if (res == null || res?.data == false) {
                LogUtils.printErrorLog("report redline metadata fail. tried 5 minutes!!")
            }
        }
        // 写入环境变量
        atomContext.result.data.putAll(CodeccEnvHelper.getCodeccEnv(atomContext.param.bkWorkspace))
        atomContext.result.data["BK_CI_CODECC_TASK_ID"] = StringData(atomContext.param.codeCCTaskId)
        atomContext.result.data["BK_CI_CODECC_TASK_STATUS"] = StringData("true")
        atomContext.result.data["BK_CI_CODECC_SCAN_TYPE"] = StringData(atomContext.param.scanType)
        LogUtils.printLog("scanType: " + atomContext.result.data["BK_CI_CODECC_SCAN_TYPE"])
        // 归档报告
        CodeccReportUtilsV2.report(atomContext)
        // 生成工具分析中间结果构件
        ToolUtils.bkcheckDebugOutput(atomContext)
    }

    private fun doAutoLang(atomContext: AtomContext<CodeccCheckAtomParamV3>): Boolean {
        val param = atomContext.param

        if (param.beAutoLang == null || param.beAutoLang == false) {
            LogUtils.printLog("do not scc first and get lang")
            return false
        }

        val oldTaskInfo = CodeccSdkApi.getTaskByPipelineId(param.pipelineId, param.multiPipelineMark, param.pipelineStartUserName)

        if (oldTaskInfo != null && oldTaskInfo.autoLang
            && atomContext.param.toolScanType != "6"
            && atomContext.param.toolScanType != "2") {
            LogUtils.printLog("task auto lang status is equal, do scc scan first for lang: ${oldTaskInfo.codeLanguages}")
            param.languages = JsonUtil.toJson(oldTaskInfo.codeLanguages)
            CodeccSdkApi.changeScanType(oldTaskInfo.taskId,
                SetForceFullScanReqVO(param.pipelineBuildId, listOf(ToolConstants.SCC.toUpperCase())))
            return true
        }

        LogUtils.printLog("do scc scan first")

        // do cloc first
        LogUtils.printLog("param.checkerSetType is ${param.checkerSetType}")

        // set force full scan
        if (oldTaskInfo != null) {
            CodeccSdkApi.changeScanType(oldTaskInfo.taskId,
                SetForceFullScanReqVO(param.pipelineBuildId, listOf(ToolConstants.SCC.toUpperCase())))
        }

        param.languageRuleSetMap = JsonUtil.toJson(mapOf("OTHERS_RULE" to listOf("standard_scc")))
        val originCheckerSetType = param.checkerSetType
        param.checkerSetType = "normal"
        doExecute(atomContext)

        // get current lang set
        val langSet = ToolUtils.getCleanClocLangSet(ToolConstants.SCC,
            param.codeCCTaskName!!,
            CodeccEnvHelper.getCodeccWorkspace(param).canonicalPath,
            param.pipelineStartUserName)

        param.languages = JsonUtil.toJson(langSet)
        param.checkerSetType = originCheckerSetType
        return false
    }

    private fun updateTaskLangAuto(atomContext: AtomContext<CodeccCheckAtomParamV3>, equalAutoTask: Boolean) {
        if (equalAutoTask) {
            val param = atomContext.param
            val langSet = ToolUtils.getCleanClocLangSet(ToolConstants.SCC,
                param.codeCCTaskName!!,
                CodeccEnvHelper.getCodeccWorkspace(param).canonicalPath,
                param.pipelineStartUserName)
            LogUtils.printLog("update codecc task lang to $langSet")

            if (langSet.isEmpty()) {
                return
            }

            param.languages = JsonUtil.toJson(langSet)

            CodeccSdkApi.createTask(param)
        }
    }

    private fun doExecute(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        // 创建或更新codecc任务
        val codeccExecuteConfig = CodeccSdkUtils.initCodecc(atomContext)

        // 第三方机器初始化
        CodeccEnvHelper.thirdInit(codeccExecuteConfig)

        // 执行codecc的python脚本
        if (CodeccEnvHelper.getOS() == OSType.WINDOWS) WindowsCodeccUtils().executeCommand(codeccExecuteConfig)
        else CodeccUtils().executeCommand(codeccExecuteConfig)
    }

    private fun detailLink(projectName: String, codeCCTaskId: String?): String {
        return if (projectName.startsWith("git_")) {
            "${CodeccConfigUtils.getPropConfig("codeccDetail")}/codecc/$projectName/task/$codeCCTaskId/detail"
        } else {
            "${CodeccConfigUtils.getPropConfig("codeccFrontHost")}/console/codecc/$projectName/task/$codeCCTaskId/detail"
        }
    }
}
