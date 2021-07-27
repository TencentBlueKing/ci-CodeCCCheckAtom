package com.tencent.devops.hash.pojo

import com.fasterxml.jackson.annotation.JsonProperty

open class HashCCNInputFile(
        open val ccn : String,
        @JsonProperty("condition_lines")
        open val conditionLines : String,
        open val filePath : String,
        @JsonProperty("function_lines")
        open val functionLines : String,
        @JsonProperty("function_name")
        open val functionNames : String,
        @JsonProperty("long_name")
        open val longName : String,
        @JsonProperty("total_lines")
        open val totalLines : String,
        open val startLine : String,
        open val endLine : String
)