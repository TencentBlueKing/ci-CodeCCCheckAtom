package com.tencent.devops.pojo

data class IgnoreDefectInfo(
    val taskId : Long,
    val ignoreDefectMap : Map<String, List<IgnoreDefectSubInfo>>?
)
