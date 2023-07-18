package com.tencent.devops.pojo

data class BaseDataVO (
    val paramCode: String?,

    val paramName: String?,

    val paramValue: String?,

    val paramType: String?,

    val paramStatus: String?,

    val paramExtend1: String?,

    val paramExtend2: String?,

    val paramExtend3: String?,

    val paramExtend4: String?,

    val paramExtend5: String?,

    val langFullKey: String,

    val langType: String?,

    val openSourceCheckerListVO: List<OpenSourceCheckerSetVO>?,

    val epcCheckerSets: List<OpenSourceCheckerSetVO>?
)