package com.tencent.devops.pojo.exception

data class CodeccUserConfigException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199002, errorMsg, toolName, cause)