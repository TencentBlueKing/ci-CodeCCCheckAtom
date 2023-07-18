package com.tencent.devops.utils

import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.pojo.env.PluginRuntimeInfo
import java.io.BufferedReader

object CodeccConfigUtils {

    private var configMap: MutableMap<String, String?> = mutableMapOf()

    @Synchronized
    fun getPropConfig(key: String): String? {
        if (configMap.isEmpty()) {
            val json = BufferedReader(
                ClassLoader.getSystemClassLoader()
                        .getResourceAsStream("config.json").reader()
            ).readText()
            val localConfig: MutableMap<String, String?> = JsonUtil.to(json)
            val codeccFrontHost = PluginRuntimeInfo.atomContext?.getSensitiveConfParam("BK_CI_PUBLIC_URL")
            val codeccDetail = PluginRuntimeInfo.atomContext?.getSensitiveConfParam("BK_CODECC_PUBLIC_URL")
            val codeccHost = PluginRuntimeInfo.atomContext?.getSensitiveConfParam("BK_CODECC_PRIVATE_URL")
            val codeccScriptFile =
                PluginRuntimeInfo.atomContext?.getSensitiveConfParam("BK_CODECC_SCRIPT_FILE")
            val codeccScriptZip = PluginRuntimeInfo.atomContext?.getSensitiveConfParam("BK_CODECC_SCRIPT_ZIP")
            configMap["codeccFrontHost"] = codeccFrontHost ?: localConfig["codeccFrontHost"] ?: ""
            configMap["codeccDetail"] = codeccDetail ?: localConfig["codeccDetail"] ?: ""
            configMap["codeccHost"] = codeccHost ?: localConfig["codeccHost"] ?: ""
            configMap["codeccScriptFile"] = codeccScriptFile ?: localConfig["codeccScriptFile"] ?: ""
            configMap["codeccScriptZip"] = codeccScriptZip ?: localConfig["codeccScriptZip"] ?: ""
        }
        return configMap[key]
    }
}