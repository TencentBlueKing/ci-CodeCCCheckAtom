package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScmIncrementItem(
    val deleteFileList: List<String>,
    val latestRevision: String,
    val updateFileList: List<String>
)
