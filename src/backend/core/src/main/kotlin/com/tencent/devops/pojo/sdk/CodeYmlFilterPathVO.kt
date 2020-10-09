package com.tencent.devops.pojo.sdk

data class CodeYmlFilterPathVO(
    val testSourceFilterPath: MutableList<String> = mutableListOf(),
    val autoGenFilterPath: MutableList<String> = mutableListOf(),
    val thirdPartyFilterPath: MutableList<String> = mutableListOf()
)