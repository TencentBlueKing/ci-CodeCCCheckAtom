package com.tencent.devops.pojo.exception

import java.lang.RuntimeException

open class CodeccException constructor(
    open val errorCode: Int = CodeccException.errorCode,
    open val errorMsg: String?
): RuntimeException(errorMsg) {
    companion object {
        const val errorCode = 2199001
        const val errorMsg = "插件异常"
    }
}
