package com.tencent.devops.pojo.scan


data class SetForceFullScanReqVO (
    val landunBuildId: String,
    val toolNames: List<String>
)