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
    var botRemindSeverity: String? = null // 7-总告警数； 3-严重 + 一般告警数；1-严重告警数
    var botRemaindTools: String? = null
    var botRemindRange: String? = null // 1-新增 2-遗留

    // 3.扫描配置tab
    var toolScanType: String? = null // 对应接口的scanType, 1：增量；0：全量 2: diff模式
    var newDefectJudgeFromDate: String? = null
    var newDefectJudgeBy: String? = null // 判定方式1：按日期；2：按构建(目前都填1)
    var transferAuthorList: String? = null
    var mrCommentEnable: Boolean? = null

    // 4.路径屏蔽tab
    var whileScanPaths: List<String>? = listOf() // 目前暂时不用
    var pathType: String? = "" // CUSTOM - 自定义 ； DEFAULT - 系统默认（目前之用CUSTOM）
    var customPath: String? = null // 黑名单，添加后的代码路径将不会产生告警
    var filterDir: List<String>? = listOf() // 暂时不用
    var filterFile: List<String>? = listOf() // 暂时不用



    // 非页面参数
    // 如果指定_CODECC_FILTER_TOOLS，则只做_CODECC_FILTER_TOOLS的扫描
    @JsonProperty("_CODECC_FILTER_TOOLS")
    override var filterTools: String? = null // [TOOL1,TOOL2]

    @JsonProperty("pipeline.start.channel")
    override var channelCode: String? = ""

    @JsonProperty("BK_CI_REPO_WEB_HOOK_HASHID")
    var hookRepoId: String? = null

    @JsonProperty("BK_CI_REPO_GIT_WEBHOOK_SOURCE_BRANCH")
    var hookMrSourceBranch: String? = null

    @JsonProperty("BK_CI_REPO_GIT_WEBHOOK_TARGET_BRANCH")
    var hookMrTargetBranch: String? = null
}