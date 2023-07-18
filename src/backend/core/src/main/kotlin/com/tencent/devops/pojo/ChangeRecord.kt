package com.tencent.devops.pojo

data class ChangeRecord(
    var author: String? = null,
    var lineRevisionId: String? = null,
    var lineShortRevisionId: String? = null,
    var lineUpdateTime: Long? = null,
    var line: Int? = null,
    var lines: MutableList<Any>? = null
)
