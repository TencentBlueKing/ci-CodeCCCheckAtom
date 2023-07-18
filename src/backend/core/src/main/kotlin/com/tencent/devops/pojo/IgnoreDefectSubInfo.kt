package com.tencent.devops.pojo

data class IgnoreDefectSubInfo(
    val lineNum: Int,
    val ignoreRule: Map<String, String>?
)
