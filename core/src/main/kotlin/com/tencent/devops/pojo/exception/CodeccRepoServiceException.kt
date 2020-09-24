package com.tencent.devops.pojo.exception

data class CodeccRepoServiceException(
    override val errorMsg: String
): CodeccException(
    errorCode = 2199006,
    errorMsg = errorMsg
)