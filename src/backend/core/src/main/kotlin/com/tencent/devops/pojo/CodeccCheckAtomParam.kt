package com.tencent.devops.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import lombok.Data
import lombok.EqualsAndHashCode

@Data
@EqualsAndHashCode(callSuper = true)
open class CodeccCheckAtomParam : AtomBaseParam() {

    open var script: String? = ""

    open var codeCCTaskName: String? = ""
    open var codeCCTaskCnName: String? = null // 暂时没用
    open var codeCCTaskId: String? = null // 调用接口用到

    open var languages: String? = null // [PYTHON,KOTLIN]
    open var asynchronous: Boolean? = true
    open var asyncTask: Boolean? = false
    open var asyncTaskId: Long? = null
    open var scanType: String? = ""
    open var path: String? = ""
    @Deprecated("load from codecc api instead")
    open var tools: String? = null // [TOOL1,TOOL2]
    open var openScanPrj: Boolean? = false

    open var pyVersion: String? = null
    open var eslintRc: String? = null
    open var phpcsStandard: String? = null
    open var goPath: String? = null
    open var projectBuildType: String? = null
    open var projectBuildCommand: String? = null
    open var ccnThreshold: Int? = null
    open var needCodeContent: String? = null
    open var coverityToolSetId: String? = null
    open var klocworkToolSetId: String? = null
    open var cpplintToolSetId: String? = null
    open var eslintToolSetId: String? = null
    open var pylintToolSetId: String? = null
    open var gometalinterToolSetId: String? = null
    open var checkStyleToolSetId: String? = null
    open var styleCopToolSetId: String? = null
    open var detektToolSetId: String? = null
    open var phpcsToolSetId: String? = null
    open var sensitiveToolSetId: String? = null
    open var occheckToolSetId: String? = null
    open var ripsToolSetId: String? = null
    open var gociLintToolSetId: String? = null
    open var woodpeckerToolSetId: String? = null
    open var horuspyToolSetId: String? = null
    open var pinpointToolSetId: String? = null

    @JsonProperty("_CODECC_FILTER_TOOLS")
    open var filterTools: String? = null // [TOOL1,TOOL2]

    @JsonProperty("pipeline.start.channel")
    open var channelCode: String? = ""
}