package com.tencent.devops.pojo.exception

data class CodeccUserConfigException(
    override val errorMsg: String
): CodeccException(2199002, errorMsg)