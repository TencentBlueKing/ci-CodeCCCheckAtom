package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenCheckerVO(
    var checkerName: String,
    val nativeChecker: Boolean,
    val checkerOptions: List<CheckerOptions>?
)

data class CheckerOptions(
    val checkerOptionName: String? = null,
    val checkerOptionValue: String? = null
)
