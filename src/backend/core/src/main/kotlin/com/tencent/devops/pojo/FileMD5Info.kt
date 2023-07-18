package com.tencent.devops.pojo


data class FileMD5Info(
    var fileRelPath: String? = null,
    var fileAbsolutePath: String? = null,
    var fileMd5: String? = null,
)
