package com.tencent.devops.pojo.sdk

data class RuntimeUpdateMetaVO (
    /**
     * 任务ID
     */
    val taskId: String,
    /**
     * 构建ID
     */
    val buildId: String?,
    /**
     * 项目ID
     */
    val projectId: String?,
    /**
     * 项目名称
     */
    val projectName: String?,
    /**
     * 流水线对应的插件ID
     */
    val pipelineTaskId: String?,

    /**
     * 流水线对应的插件名称
     */
    val pipelineTaskName: String?,

    /**
     * 任务超时时间
     */
    val timeout: Int?,
)
