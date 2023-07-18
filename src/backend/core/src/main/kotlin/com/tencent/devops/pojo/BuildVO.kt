package com.tencent.devops.pojo

data class BuildVO(
    val buildId: String,
    val buildNum: String,
    val buildTime: Long,
    val buildUser: String,
    val taskId: Long,
    val projectId: String,
    val pipelineId: String
)
