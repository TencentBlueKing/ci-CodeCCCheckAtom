package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType

data class CodeccRepoServiceException(
    override val errorMsg: String,
    override val toolName: String = "",
    override val cause: Throwable? = null
): CodeccException(2190001, ErrorType.THIRD_PARTY.num, errorMsg, toolName, cause)