package com.tencent.devops.docker.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.pojo.ToolMetaDetailVO
import com.tencent.devops.docker.tools.AESUtil
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.user.CodeCCUserException
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.script.BatScriptUtil
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.*


object CodeccConfig {
    private val propertiesInfo = mutableMapOf<String, String>()

    fun loadToolMeta(landunParam: LandunParam, apiWebServer: String, aesKey: String) {
        loadProperties() // 先取配置文件，再用后台配置刷新，防止后台有问题导致不能跑

        LogUtils.printDebugLog("apiWebServer: $apiWebServer")
        propertiesInfo["CODECC_API_WEB_SERVER"] = apiWebServer
        val toolMetas = CodeccWeb.getBuildToolMeta(landunParam, landunParam.taskId.toLong(), apiWebServer)
        if (null == toolMetas || toolMetas.isEmpty()) {
            LogUtils.printDebugLog("toolMetas is empty")
            return
        }
        LogUtils.printDebugLog("toolMetas is not empty")
        toolMetas.filterNot { it.name.isNullOrBlank() }.forEach {
            resolveToolMeta(it, aesKey)
        }
        var toolImageTypes = mutableSetOf<String>()
        landunParam.toolNames?.split(",")?.forEach {
            toolImageTypes.add(it.toUpperCase()+":"+propertiesInfo[it!!.toUpperCase()+"_IMAGE_VERSION_TYPE"])
        }
        landunParam.toolImageTypes = toolImageTypes.joinToString(",")
        propertiesInfo["LANDUN_CHANNEL_CODE"] = landunParam.channelCode ?: ""
    }

    private fun resolveToolMeta(toolMetaDetailVO: ToolMetaDetailVO, aesKey: String) {
        try {
            val scanCommandKey = "${toolMetaDetailVO.name!!.toUpperCase()}_SCAN_COMMAND"
            val scanCommandValue = toolMetaDetailVO.dockerTriggerShell ?: ""

            val imagePathKey = "${toolMetaDetailVO.name!!.toUpperCase()}_IMAGE_PATH"
            val imagePathValue = toolMetaDetailVO.dockerImageURL ?: ""

            val imageTagValue = toolMetaDetailVO.dockerImageVersion

            val imageVersionTypeKey = "${toolMetaDetailVO.name!!.toUpperCase()}_IMAGE_VERSION_TYPE"
            val imageVersionTypeValue = toolMetaDetailVO.dockerImageVersionType?: "P"

            val registryUserKey = "${toolMetaDetailVO.name!!.toUpperCase()}_REGISTRYUSER"
            val registryUserValue = toolMetaDetailVO.dockerImageAccount ?: ""

            val registryPwdKey = "${toolMetaDetailVO.name!!.toUpperCase()}_REGISTRYPWD"
            val registryPwdValue = if (toolMetaDetailVO.dockerImagePasswd.isNullOrBlank()) {
                ""
            } else {
                AESUtil.decrypt(aesKey, toolMetaDetailVO.dockerImagePasswd!!)
            }

            if (scanCommandValue.isNotBlank()) {
                propertiesInfo[scanCommandKey] = scanCommandValue
            }
            if (imagePathValue.isNotBlank()) {
                if (imageTagValue.isNullOrBlank()) {
                    propertiesInfo[imagePathKey] = imagePathValue
                } else {
                    propertiesInfo[imagePathKey] = "$imagePathValue:$imageTagValue"
                }
            }

            propertiesInfo[imageVersionTypeKey] = imageVersionTypeValue
            propertiesInfo[registryUserKey] = registryUserValue
            propertiesInfo[registryPwdKey] = registryPwdValue

            if (!toolMetaDetailVO.toolHomeBin.isNullOrBlank()) {
                val toolHomeBinKey = "${toolMetaDetailVO.name!!.toUpperCase()}_HOME_BIN"
                val toolHomeBinValue = toolMetaDetailVO.toolHomeBin!!
                propertiesInfo[toolHomeBinKey] = toolHomeBinValue
            }

            if (toolMetaDetailVO.toolHistoryVersion != null && toolMetaDetailVO.toolHistoryVersion!!.isNotEmpty()) {
                val toolOldVersionKey = "${toolMetaDetailVO.name!!.toUpperCase()}_OLD_VERSION"
                val toolOldVersionValue = toolMetaDetailVO.toolHistoryVersion!!.joinToString(";")
                propertiesInfo[toolOldVersionKey] = toolOldVersionValue
            }

            if (!toolMetaDetailVO.toolVersion.isNullOrBlank()) {
                val toolNewVersionKey = "${toolMetaDetailVO.name!!.toUpperCase()}_NEW_VERSION"
                val toolNewVersionValue = toolMetaDetailVO.toolVersion!!
                propertiesInfo[toolNewVersionKey] = toolNewVersionValue
            }
        } catch (e: Throwable) {
            LogUtils.printErrorLog(e.message)
        }
    }

    fun loadPropertiesForOld(): Map<String, String> {
        try {
            val input = Thread.currentThread().contextClassLoader.getResourceAsStream("config.properties")
            val p = Properties()
            p.load(input)

            for (name in p.stringPropertyNames()) {
                propertiesInfo[name] = p.getProperty(name)
            }
        } catch (e: Exception) {
            LogUtils.printErrorLog("Load config exception: ${e.message}")
        }
        return propertiesInfo
    }

    private fun loadProperties(): Map<String, String> {
        try {
            val input = Thread.currentThread().contextClassLoader.getResourceAsStream("config.properties")
            val p = Properties()
            p.load(input)

            for (name in p.stringPropertyNames()) {
                propertiesInfo[name] = p.getProperty(name)
            }
        } catch (e: Exception) {
            LogUtils.printErrorLog("Load config exception: ${e.message}")
        }
        return propertiesInfo
    }

    fun getConfig(key: String): String? = propertiesInfo[key]

    fun setConfig(key: String, value: String){
        propertiesInfo[key] = value
    }

    fun getServerHost() = propertiesInfo["CODECC_API_WEB_SERVER"]

    fun getImage(toolName: String): ImageParam {
        val toolNameUpperCase = toolName.toUpperCase()
        val cmd = (propertiesInfo["${toolNameUpperCase}_SCAN_COMMAND"] ?: "").split("##")
        val imageName = propertiesInfo["${toolNameUpperCase}_IMAGE_PATH"] ?: ""
        val registerUser = propertiesInfo["${toolNameUpperCase}_REGISTRYUSER"] ?: ""
        val registerPwd = propertiesInfo["${toolNameUpperCase}_REGISTRYPWD"] ?: ""
        val imageVersionType = propertiesInfo["${toolNameUpperCase}_IMAGE_VERSION_TYPE"] ?: "P"
        val env = if (propertiesInfo["${toolNameUpperCase}_ENV"] != null && propertiesInfo["${toolNameUpperCase}_ENV"]!!.isNotBlank()) {
            jacksonObjectMapper().readValue<Map<String, String>>(propertiesInfo["${toolNameUpperCase}_ENV"]!!)
        } else {
            emptyMap()
        }
        if (imageVersionType.equals("T")){
            LogUtils.printLog("Running Test image version: " + imageName)
        } else if (imageVersionType.equals("G")){
            LogUtils.printLog("Running Gray image version: " + imageName)
        } else if (imageVersionType.equals("PRE_PROD")){
            LogUtils.printLog("Running Pre Prod image version: " + imageName)
        } else{
            LogUtils.printLog("Running Prod image version: " + imageName)
        }
        return ImageParam(cmd, imageName, registerUser, registerPwd, env)
    }

    fun fileListFromDefects(inputPath: String): List<String> {
        val filePathSet = mutableSetOf<String>()
        if (File(inputPath).exists()) {
            val inputFileText = File(inputPath).readText()
            val inputFileObj = jacksonObjectMapper().readValue<Map<String, Any?>>(inputFileText)
            val defectsDataList = inputFileObj["defects"] as? List<Map<String, String>>
            defectsDataList?.forEach { defect ->
                when {
                    defect["filePath"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filePath"] as String))
                    defect["filePathname"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filePathname"] as String))
                    defect["filename"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filename"] as String))
                    defect["file_path"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["file_path"] as String))
                }
            }
        }

        return filePathSet.toList()
    }

}