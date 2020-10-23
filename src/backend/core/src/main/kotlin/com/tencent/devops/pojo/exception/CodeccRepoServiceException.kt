package com.tencent.devops.pojo.exception

data class CodeccRepoServiceException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199006, errorMsg, toolName, cause)