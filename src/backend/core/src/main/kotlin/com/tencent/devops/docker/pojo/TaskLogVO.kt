package com.tencent.devops.docker.pojo

data class TaskLogVO(
    var streamName: String?,
    val taskId: Long?,
    val toolName: String?,
    val currStep: Int = 0,
    val flag: Int = 0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val elapseTime: Long = 0,
    val pipelineId: String?,
    val buildId: String?,
    val buildNum: String?,
    val triggerFrom: String?,
    val stepArray: List<TaskUnit>?
)

data class TaskUnit(
    val stepNum: Int?,
    val flag: Int?,
    val startTime: Long?,
    val endTime: Long?,
    val msg: String?,
    val elapseTime: Long?,
    val dirStructSuggestParam: Boolean?,
    val compileResult: Boolean
)
