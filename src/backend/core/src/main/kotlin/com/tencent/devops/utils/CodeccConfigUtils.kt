package com.tencent.devops.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import java.io.BufferedReader

object CodeccConfigUtils {

    private var configMap: Map<String, String> = mapOf()

    @Synchronized
    fun getPropConfig(key: String): String? {
        if (configMap.isEmpty()) {
            val json = BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("config.json")!!.reader()).readText()
            configMap = JsonUtil.fromJson(json, object : TypeReference<Map<String, String>>(){})
        }
        return configMap[key]
    }
}