package com.tencent.devops.pojo.exception.user

import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.devops.pojo.exception.CodeCCException
import com.tencent.devops.pojo.exception.ErrorCode

data class CodeCCUserException(
    private val error : ErrorCode,
    override val errorMsg: String,
    override val params: Array<String>? = emptyArray(),
    override val cause: Throwable? = null
) : CodeCCException(error.errorCode, ErrorType.USER.num, errorMsg, params, cause)
