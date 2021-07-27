package com.tencent.devops.hash.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class HashFileTotalCCNFile(
        @JsonProperty("file_path")
        val filePath : String,
        @JsonProperty("file_rel_path")
        val fileRelPath: String?,
        @JsonProperty("total_ccn_count")
        val totalCCNCount : String
)
