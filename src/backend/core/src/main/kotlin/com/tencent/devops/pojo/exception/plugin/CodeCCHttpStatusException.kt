package com.tencent.devops.pojo.exception.plugin

import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.devops.pojo.exception.CodeCCException

/**
 *  CodeCC 后台接口状态码异常，
 *  1023XX - 1025XX
 */
data class CodeCCHttpStatusException(
    private val statusCode: Int,
    override val errorMsg: String,
    override val cause: Throwable? = null
) : CodeCCException(102000 + statusCode, ErrorType.PLUGIN.num, errorMsg, emptyArray(), cause){
    override fun errorCodeMsg(): String {
        return "CodeCC request fail , status code: $statusCode"
    }
}
