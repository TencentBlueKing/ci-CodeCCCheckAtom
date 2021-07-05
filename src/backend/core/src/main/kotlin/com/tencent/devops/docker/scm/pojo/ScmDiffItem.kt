package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScmDiffItem(
    val deleteFileList: List<String>,
    val diffFileList: List<DiffFileItem>,
    val updateFileList: List<String>
) {
    data class DiffFileItem(
        val diffLineList: List<Long>,
        val filePath: String
    )
}