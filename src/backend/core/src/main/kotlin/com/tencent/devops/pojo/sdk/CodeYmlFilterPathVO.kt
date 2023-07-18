package com.tencent.devops.pojo.sdk

data class CodeYmlFilterPathVO(
    val scanTestSource: Boolean,
    val testSourceFilterPath: MutableSet<String> = mutableSetOf(),
    val autoGenFilterPath: MutableSet<String> = mutableSetOf(),
    val thirdPartyFilterPath: MutableSet<String> = mutableSetOf(),
    val repoOwners: MutableSet<String> = mutableSetOf()
)
