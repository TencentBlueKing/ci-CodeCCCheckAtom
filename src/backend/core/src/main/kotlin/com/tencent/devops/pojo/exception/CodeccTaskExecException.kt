package com.tencent.devops.pojo.exception

data class CodeccTaskExecException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199004, errorMsg, toolName, cause)