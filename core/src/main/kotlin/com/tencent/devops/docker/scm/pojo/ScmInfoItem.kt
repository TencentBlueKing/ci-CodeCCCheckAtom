package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ScmInfoItem(
    val branch: String?,
    val fileUpdateAuthor: String?,
    val fileUpdateTime: String?,
    val revision: String?,
    val scmType: String?,
    val url: String,
    val rootUrl: String?,
    val subModules: List<SubModule>?,
    var repoId: String?,
    var taskId: String?,
    var buildId: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubModule(
        val subModule: String?,
        val url: String?
)