package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType
import java.lang.RuntimeException

open class CodeccException constructor(
    open val errorCode: Int = CodeccException.errorCode,
    open val errorType: Int = CodeccException.errorType,
    open val errorMsg: String,
    open val toolName: String = "",
    override val cause: Throwable? = null
): RuntimeException(errorMsg) {
    companion object {
        const val errorCode = 2199001
        val errorType = ErrorType.PLUGIN.num
        const val errorMsg = "插件异常"
    }
}
