package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.pojo.artifactory.ChannelCode

@JsonIgnoreProperties(ignoreUnknown = true)
data class LandunParam(
    val userId: String,
    val buildId: String,
    // 构建机信息
    val devopsProjectId: String,
    val devopsBuildType: String,
    val devopsAgentId: String,
    val devopsAgentSecretKey: String,
    val devopsAgentVmSid: String,

    val devopsPipelineId: String,
    val devopsVmSeqId: String,
    val ldEnvType: String, // getEnvType()
    val streamCodePath: String,

    val channelCode: String?
) {
    fun copy(): LandunParam {
        val json = jacksonObjectMapper().writeValueAsString(this)
        return jacksonObjectMapper().readValue(json)
    }

    override fun toString(): String {
        return "LandunParam(userId='$userId', buildId='$buildId', devopsProjectId='$devopsProjectId', devopsBuildType='$devopsBuildType', devopsAgentId='$devopsAgentId', devopsAgentSecretKey='$devopsAgentSecretKey', devopsAgentVmSid='$devopsAgentVmSid', devopsPipelineId='$devopsPipelineId', devopsVmSeqId='$devopsVmSeqId', ldEnvType='$ldEnvType', streamCodePath='$streamCodePath')"
    }
}
