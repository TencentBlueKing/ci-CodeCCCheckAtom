package com.tencent.devops.pojo.exception

class CodeccTimeOutException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199005, errorMsg, toolName, cause)