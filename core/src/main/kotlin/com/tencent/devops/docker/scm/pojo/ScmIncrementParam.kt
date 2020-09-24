package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.devops.docker.pojo.CodeRepoInfoVO
import com.tencent.devops.docker.pojo.ImageParam

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScmIncrementParam(
    val repoRelPath: Map<String, String>,
    val scmType: String?,
    val env: Map<String, String> = emptyMap(),
    val lastCoderRepos: List<CodeRepoInfoVO>,

    val imageParam: ImageParam
)