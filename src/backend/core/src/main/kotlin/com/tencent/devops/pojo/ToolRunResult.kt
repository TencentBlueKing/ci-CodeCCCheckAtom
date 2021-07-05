package com.tencent.devops.pojo

data class ToolRunResult (
    val toolName: String,
    var errorMsg: String = "",
    var errorCode: Int = 0,
    var errorType: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long = 0L,
    var status: ToolRunResultStatus = ToolRunResultStatus.SKIP
) {
    enum class ToolRunResultStatus {
        SUCCESS,
        FAIL,
        SKIP,
        TIMEOUT
    }
}