package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeRepoInfoVO(
        val repoId: String?,
        val revision: String?,
        val branch: String?
)
