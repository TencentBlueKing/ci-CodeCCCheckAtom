package com.tencent.devops.scm.pojo

data class FileDiffLines (
    var filePath: String? = "",
    var diffLineList: MutableList<Int>? = mutableListOf<Int>()
)