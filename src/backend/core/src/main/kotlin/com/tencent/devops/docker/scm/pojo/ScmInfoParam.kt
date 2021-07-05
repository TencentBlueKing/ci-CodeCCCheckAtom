package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.devops.docker.pojo.ImageParam

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScmInfoParam(
    val repoRelPath: Map<String, String>,
    val scmType: String?,
    val repoUrlMap: Map<String, String>,
    val scmEnv: Map<String, String>,
    val imageParam: ImageParam,
    val env: Map<String, String> = emptyMap()
)