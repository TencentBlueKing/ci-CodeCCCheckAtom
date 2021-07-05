package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType

class CodeccTimeOutException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2199005, ErrorType.THIRD_PARTY.num, errorMsg, toolName, cause)