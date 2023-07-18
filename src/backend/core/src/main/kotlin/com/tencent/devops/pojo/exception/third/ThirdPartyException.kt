package com.tencent.devops.pojo.exception.third

import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.devops.pojo.exception.CodeCCException
import com.tencent.devops.pojo.exception.ErrorCode

data class ThirdPartyException(
    val error: ErrorCode,
    override val errorMsg: String,
    override val params: Array<String>? = emptyArray(),
    private val thirdParty: ThirdParty,
    override val cause: Throwable? = null
) : CodeCCException(error.errorCode, ErrorType.THIRD_PARTY.num, errorMsg, params, cause){

    override fun errorCodeMsg(): String {
        return "third: ${thirdParty.code} en: ${thirdParty.nameEn} cn: ${thirdParty.nameCn} " + super.errorCodeMsg()
    }
}
