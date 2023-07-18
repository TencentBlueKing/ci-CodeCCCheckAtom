package com.tencent.devops.pojo.exception

import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.devops.docker.tools.LogUtils
import java.text.MessageFormat

open class CodeCCException constructor(
    open val errorCode: Int = defaultErrorCode,
    open val errorType: Int = defaultErrorType,
    open val errorMsg: String = defaultErrorMsg,
    open val params: Array<String>?,
    override val cause: Throwable? = null
): RuntimeException(errorMsg) {

    companion object {
        val defaultErrorCode = ErrorCode.UNKNOWN_PLUGIN_ERROR.errorCode
        val defaultErrorType = ErrorType.PLUGIN.num
        const val defaultErrorMsg = "plugin error"
    }

    fun logErrorCodeMsg() {
        LogUtils.printErrorLog(errorCodeMsg())
    }

    open fun errorCodeMsg(): String {
        val errorCodeEnum = ErrorCode.valueOf(errorCode) ?: return errorMsg
        return if (params.isNullOrEmpty()) {
            errorCodeEnum.errorMsg
        } else {
            MessageFormat.format(errorCodeEnum.errorMsg, *params!!)
        }
    }

}
