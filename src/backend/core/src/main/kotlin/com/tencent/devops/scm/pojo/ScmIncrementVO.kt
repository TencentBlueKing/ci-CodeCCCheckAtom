package com.tencent.devops.scm.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class ScmIncrementVO (
    @JsonProperty("is_pre_revision")
    var isPreRevision: Boolean = false,
    @JsonProperty("scm_increment")
    var scmIncremt: MutableList<IncrementFile>? = mutableListOf<IncrementFile>()

)