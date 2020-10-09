package com.tencent.devops.docker.scm.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScmIncrementJson(
    @JsonProperty("scm_increment")
    val scmIncrementList: List<ScmIncrementItem>?,

    @JsonProperty("is_pre_revision")
    val isPreRevision: Boolean?
)