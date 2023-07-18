package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.devops.scm.pojo.SubModule

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeRepoInfoVO(
        val repoId: String?,
        val revision: String?,
        val branch: String?,
        val url: String?,
        val commitID:String?,
        val subModules:MutableList<SubModule>? = mutableListOf<SubModule>()
)
