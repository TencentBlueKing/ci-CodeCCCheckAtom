package com.tencent.devops.hash.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class HashCCNCommonFile(
        val ccn : String,
        @JsonProperty("condition_lines")
        val conditionLines : String,
        val filePath : String,
        @JsonProperty("function_lines")
        val functionLines : String,
        @JsonProperty("function_name")
        val functionNames : String,
        @JsonProperty("long_name")
        val longName : String,
        @JsonProperty("total_lines")
        val totalLines : String,
        val startLine : String,
        val endLine : String,
        var ignoreCommentDefect : Boolean? = false,
        var ignoreCommentReason : String? = null,
        var pinpointHash: String? = null
)
