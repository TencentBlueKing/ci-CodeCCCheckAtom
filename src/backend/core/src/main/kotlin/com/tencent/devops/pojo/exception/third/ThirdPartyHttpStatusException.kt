package com.tencent.devops.pojo.exception.third

import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.devops.pojo.exception.CodeCCException

data class ThirdPartyHttpStatusException(
    private val statusCode: Int,
    override val errorMsg: String,
    private val thirdParty: ThirdParty,
    override val cause: Throwable? = null
) : CodeCCException(thirdParty.code * 100 + statusCode, ErrorType.THIRD_PARTY.num, errorMsg, emptyArray(), cause) {

    override fun errorCodeMsg(): String {
        return "third: ${thirdParty.code} en: ${thirdParty.nameEn} cn: ${thirdParty.nameCn} request fail. " +
                "status code: $statusCode"
    }
}
