package com.tencent.devops.pojo

data class ActualExeToolsVO (
        val taskId: Long,

        val buildId: String,

        val tools: List<String>
)
