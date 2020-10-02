package com.tencent.devops.pojo.exception

class CodeccTimeOutException(
    override val errorMsg: String
): CodeccException(2199005, errorMsg)