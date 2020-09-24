package com.tencent.devops.utils.common

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil

object AtomUtils {

    fun parseStringToList(str: String?): List<String> {
        if (str.isNullOrBlank()) return listOf()
        return JsonUtil.getObjectMapper().readValue(str!!)
    }

    fun parseStringToSet(str: String?, defaultSet: Set<String> = setOf()): Set<String> {
        if (str.isNullOrBlank()) return defaultSet
        return JsonUtil.getObjectMapper().readValue(str!!)
    }
}
