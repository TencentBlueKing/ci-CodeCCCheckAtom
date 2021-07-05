package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType

data class CodeccUserConfigException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199002, ErrorType.USER.num, errorMsg, toolName, cause)