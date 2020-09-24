package com.tencent.devops.utils

import com.tencent.bk.devops.plugin.utils.JsonUtil
import java.io.BufferedReader

object CodeccConfigUtils {

    private var configMap: Map<String, String> = mapOf()

    @Synchronized
    fun getPropConfig(key: String): String? {
        if (configMap.isEmpty()) {
            val json = BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("config.json").reader()).readText()
            configMap = JsonUtil.to(json)
        }
        return configMap[key]
    }
}