package com.tencent.devops.pojo.report

data class TaskFailReportReq(
    val taskId : Long,
    val pipelineId : String,
    val projectId : String,
    val buildId : String,
    val machineIp : String,
    val timeCost : Long
)