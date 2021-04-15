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

    // 支持["xxx","xxx"], 逗号分隔两种模式
    fun transferPathParam(path: String?): List<String> {
        return try {
            parseStringToList(path)
        } catch (e: Exception) {
            System.err.println("transfer path param warning: ${e.message}")
            return path?.trim()?.split(",") ?: listOf()
        }
    }
}
