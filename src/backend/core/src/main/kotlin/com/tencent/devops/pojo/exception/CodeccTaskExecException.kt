package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType

data class CodeccTaskExecException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199004, ErrorType.PLUGIN.num, errorMsg, toolName, cause)