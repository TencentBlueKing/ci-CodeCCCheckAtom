package com.tencent.devops.pojo.exception

data class CodeccTaskExecException(
    override val errorMsg: String
): CodeccException(2199004, errorMsg)