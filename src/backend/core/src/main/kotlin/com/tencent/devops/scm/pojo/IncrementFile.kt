package com.tencent.devops.scm.pojo

data class IncrementFile (
    var latestRevision: String? = "",
    var updateFileList: MutableSet<String>? = mutableSetOf<String>(),
    var deleteFileList: MutableSet<String>? = mutableSetOf<String>(),
    var diffFileList: MutableList<FileDiffLines>? = mutableListOf<FileDiffLines>()
)