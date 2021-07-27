package com.tencent.devops.hash.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class HashCCNOutputFile(
        override val ccn: String,
        @JsonProperty("condition_lines")
        override val conditionLines: String,
        override val filePath: String,
        @JsonProperty("function_lines")
        override val functionLines: String,
        @JsonProperty("function_name")
        override val functionNames: String,
        @JsonProperty("long_name")
        override val longName: String,
        @JsonProperty("total_lines")
        override val totalLines: String,
        override val startLine: String,
        override val endLine: String,
        val pinpointHash: String?
) : HashCCNInputFile(ccn, conditionLines, filePath, functionLines, functionNames, longName, totalLines, startLine, endLine)