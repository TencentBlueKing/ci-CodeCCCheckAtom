package com.tencent.devops.docker.pojo

data class CodeRepoVO(
    val repoId: String?,
    val url: String?,
    val revision: String?,
    val branch: String?,
    val aliasName: String
)
