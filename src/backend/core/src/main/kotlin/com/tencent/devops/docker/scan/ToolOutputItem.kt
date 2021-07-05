package com.tencent.devops.docker.scan

import com.tencent.devops.docker.pojo.DefectsEntity

data class ToolOutputItem(
    val code: Int?,
    val message: String?,
    val defects: List<DefectsEntity>
)