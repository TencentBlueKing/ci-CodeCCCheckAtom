package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class DefectsEntity(
    var checkerName: String? = null,
    val description: String? = null,
    val filePath: String? = null,
    val line: String? = null,

    val ccn: String? = null,
    @JsonProperty("condition_lines")
    val conditionLines: String? = null,
    @JsonProperty("function_lines")
    val functionLines: String? = null,
    @JsonProperty("function_name")
    val functionName: String? = null,
    val startLine: String? = null,
    val endLine: String? = null,
    @JsonProperty("long_name")
    val longName: String? = null,
    @JsonProperty("total_lines")
    val total_lines: String? = null,

    val file_path: String? = null,
    @JsonProperty("dup_rate")
    val dupRate: String? = null,
    @JsonProperty("dup_lines")
    val dupLines: Int? = null,

    val filePathname: String? = null,
    val filename: String? = null,
    val pinpointHash: String? = null,

    val language: String? = null
)
