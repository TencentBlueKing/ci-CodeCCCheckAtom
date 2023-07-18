package com.tencent.devops.scm.pojo

data class ScmInfoVO(
    var scmType: String? = "",
    var branch: String? = "",
    var fileUpdateAuthor: String? = "",
    var revision: String? = "",
    var commitID: String? = "",
    var fileUpdateTime: Long? = 0,
    var url: String? = "",
    var subModules: MutableList<SubModule>? = mutableListOf<SubModule>(),
    var rootUrl: String? = ""
)