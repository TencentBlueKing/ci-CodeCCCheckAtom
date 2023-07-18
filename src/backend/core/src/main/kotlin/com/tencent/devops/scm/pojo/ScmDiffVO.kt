package com.tencent.devops.scm.pojo

import com.fasterxml.jackson.annotation.JsonProperty

data class ScmDiffVO (
    @JsonProperty("scm_increment")
    var scmIncremt: MutableList<IncrementFile>? = mutableListOf<IncrementFile>()

)