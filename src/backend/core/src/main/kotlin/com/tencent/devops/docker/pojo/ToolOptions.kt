package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolOptions(
    val optionName: String,
    val optionValue: String,
    val osType: String? = null,
    val buildEnv: Map<String, String>? = null
)