package com.tencent.devops.pojo

data class OpenSourceCheckerSetVO (
    val checkerSetId: String?,

    val toolList: Set<String>?,

    val checkerSetType: String?,

    val version: Int?
)