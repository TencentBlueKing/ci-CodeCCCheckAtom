package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
class ToolMetaDetailVO : ToolMetaBaseVO() {
    var briefIntroduction: String? = null
    var description: String? = null
    var privated = false
    var logo: String? = null
    var graphicDetails: String? = null
    var supportedLanguages: List<String>? = null
    var dockerTriggerShell: String? = null
    var dockerImageURL: String? = null
    var dockerImageVersion: String? = null
    var dockerImageAccount: String? = null
    var dockerImagePasswd: String? = null
    var debugPipelineId: String? = null
    var toolHomeBin: String? = null
    var toolScanCommand: String? = null
    var toolEnv: String? = null
    var toolRunType: String? = null
    var toolVersion: String? = null
    var toolHistoryVersion: List<String>? = null
    var toolOptions: List<ToolOption>? = null
}

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolOption(
    val varName: String? = null,
    val varType: String? = null,
    val labelName: String? = null,
    val varDefault: String? = null,
    val varTips: String? = null,
    val varRequired: Boolean? = false,
    val varOptionList: List<VarOption>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class VarOption(
    val name: String? = null,
    val id: String? = null
)
