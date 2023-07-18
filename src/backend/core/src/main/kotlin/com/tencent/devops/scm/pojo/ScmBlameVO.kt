package com.tencent.devops.scm.pojo

data class ScmBlameVO (
    var scmType: String? = "",
    var filePath: String? = "",
    var fileRelPath: String? = "",
    var branch: String? = "",
    var fileUpdateTime: Long? = 0,
    var longRevision: String? = "",
    var revision: String? = "",
    var url: String? = "",
    val taskId: Long? = 0,
    var rootUrl: String? = "",
    var changeRecords: MutableList<ChangeRecord>? = mutableListOf<ChangeRecord>()
)