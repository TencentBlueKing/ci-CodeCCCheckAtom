package com.tencent.devops.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
class CodeccCheckAtomParamV3 : CodeccCheckAtomParam() {

    // 1.基础设置tab
    override var script: String? = ""

    override var codeCCTaskName: String? = ""
    override var codeCCTaskCnName: String? = null // 暂时没用
    override var codeCCTaskId: String? = null // 调用接口用到

    override var checkerSetType: String? = "normal"
    var checkerSetEnvType: String? = null
    override var languages: String? = null // [PYTHON,KOTLIN]
    override var asynchronous: Boolean? = true
    override var asyncTask: Boolean? = false
    override var asyncTaskId: Long? = null
    override var path: String? = "" // 白名单

    override var pyVersion: String? = null
    override var goPath: String? = null
    override var projectBuildType: String? = null
    override var projectBuildCommand: String? = null
    override var needCodeContent: String? = null

    var languageRuleSetMap: String? = "" // 规则集

    var beAutoLang: Boolean? = false

    var multiPipelineMark: String? = null

    // 2.通知报告tab
    var rtxReceiverType: String? = null // rtx接收人类型：0-所有项目成员；1-接口人；2-自定义；3-无
    var rtxReceiverList: String? = null // rtx接收人列表，rtxReceiverType=2时，自定义的接收人保存在该字段
    var emailReceiverType: String? = null // 邮件收件人类型：0-所有项目成员；1-接口人；2-自定义；3-无
    var emailReceiverList: String? = null // 邮件收件人列表，当emailReceiverType=2时，自定义的收件人保存在该字段
    var emailCCReceiverList: String? = null
    var reportStatus: String? = null // 定时报告任务的状态，有效：1，暂停：2 (目前看了都是1)
    var reportDate: String? = null
    var reportTime: String? = null
    var instantReportStatus: String? = null // 即时报告状态，有效：1，暂停：2
    var reportTools: String? = null
    var botWebhookUrl: String? = null
    var botRemindSeverity: String? = null // 7-总问题数； 3-严重 + 一般问题数；1-严重问题数
    var botRemaindTools: String? = null
    var botRemindRange: String? = null // 1-新增 2-遗留

    // 3.扫描配置tab
    var toolScanType: String? = null // 对应接口的scanType, 1：增量；0：全量 2: diff模式
    var newDefectJudgeFromDate: String? = null
    var newDefectJudgeBy: String? = null // 判定方式1：按日期；2：按构建(目前都填1)
    var transferAuthorList: String? = null
    var mrCommentEnable: Boolean? = null
    var byFile: Boolean? = null
    var prohibitIgnore: Boolean? = null  // 是否允许页面忽略告警
    var diffBranch : String? = null

    //路径白名单
    var pathList: List<String>? = null
    // 4.路径屏蔽tab
    var whileScanPaths: List<String>? = listOf() // 目前暂时不用
    var pathType: String? = "" // CUSTOM - 自定义 ； DEFAULT - 系统默认（目前之用CUSTOM）
    var openScanFilterEnable: Boolean? = false // 是否允许开源扫描设置过滤路径
    var customPath: String? = null // 黑名单，添加后的代码路径将不会产生问题
    var filterDir: List<String>? = listOf() // 暂时不用
    var filterFile: List<String>? = listOf() // 暂时不用
    var scanTestSource: Boolean? = null // 是否扫描测试代码，true-扫描，false-不扫描，默认不扫描

    // 5.提单tab
    val issueSystem: String? = null
    val issueSubSystem: String? = null
    val issueSubSystemId: String? = null
    val issueSubSystemCn: String? = null
    val issueResolvers: String? = null
    val issueCreators: String? = null
    val issueReceivers: String? = null
    val issueFindByVersion: String? = null
    val maxIssue: Int? = null
    val issueAutoCommit: Boolean? = null
    val issueTools: String? = null
    val issueSeverities: String? = null

    // 工具集成冒烟测试变量
    var debugToolList: String? = null
    var debugLangList: String? = null
    var debugCheckerSetList: String? = null

    // 非页面参数
    // 如果指定_CODECC_FILTER_TOOLS，则只做_CODECC_FILTER_TOOLS的扫描
    @JsonProperty("_CODECC_FILTER_TOOLS")
    override var filterTools: String? = null // [TOOL1,TOOL2]

    @JsonProperty("BK_CI_START_CHANNEL")
    override var channelCode: String? = ""

    @JsonProperty("BK_CI_REPO_WEB_HOOK_HASHID")
    var hookRepoId: String? = null

    @JsonProperty("BK_CI_REPO_GIT_WEBHOOK_SOURCE_BRANCH")
    var hookMrSourceBranch: String? = null

    @JsonProperty("BK_CI_REPO_GIT_WEBHOOK_TARGET_BRANCH")
    var hookMrTargetBranch: String? = null

    // 是否不扫描代码行
    @JsonProperty("BK_CODECC_PROHIBIT_CLOC")
    var prohibitCloc: String? = null

    var localSCMBlameRun: String?  = null

    // 开启后bkcheck工具执行时将归档中间结果到流水线制品库，便于定位Bug
    var bkcheckDebug: Boolean = false
    // 中间结果文件存放路径，运行时组装写入，无需配置
    var codeccWorkspacePath: String? = null
}
