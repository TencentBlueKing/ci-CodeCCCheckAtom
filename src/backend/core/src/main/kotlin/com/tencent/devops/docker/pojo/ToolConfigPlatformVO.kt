package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolConfigPlatformVO(
    var taskId: Long? = null,
    val toolName: String? = null,
    val nameEn: String? = null,
    val nameCn: String? = null,
    val ip: String? = null,
    val port: String? = null,
    val userName: String? = null,
    val password: String? = null,
    val specConfig: String? = null
)
