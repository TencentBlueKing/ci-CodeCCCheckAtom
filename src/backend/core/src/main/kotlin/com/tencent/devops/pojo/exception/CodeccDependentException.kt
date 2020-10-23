package com.tencent.devops.pojo.exception


data class CodeccDependentException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199003, errorMsg, toolName, cause)