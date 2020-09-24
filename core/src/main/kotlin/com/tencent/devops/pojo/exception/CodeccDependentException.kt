package com.tencent.devops.pojo.exception


data class CodeccDependentException(
    override val errorMsg: String
): CodeccException(2199003, errorMsg)