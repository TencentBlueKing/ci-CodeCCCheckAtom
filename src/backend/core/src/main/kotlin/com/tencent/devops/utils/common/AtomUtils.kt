package com.tencent.devops.utils.common

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.utils.json.JsonUtil

object AtomUtils {

    fun parseStringToList(str: String?): List<String> {
        if (str.isNullOrBlank()) return listOf()
        return JsonUtil.fromJson(str, object : TypeReference<List<String>>(){})
    }

    fun parseStringToSet(str: String?, defaultSet: Set<String> = setOf()): Set<String> {
        if (str.isNullOrBlank()) return defaultSet
        return JsonUtil.fromJson(str, object : TypeReference<Set<String>>(){})
    }
}