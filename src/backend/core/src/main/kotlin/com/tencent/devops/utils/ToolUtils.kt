package com.tencent.devops.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.pojo.ArtifactData
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.hash.constant.ComConstants
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import java.io.File


object ToolUtils {
    fun getClocLangSet(toolName: String, streamName: String, dataRootPath: String): Pair<Boolean, Set<String>> {
        val toolDataPath = ScanComposer.generateToolDataPath(dataRootPath, streamName, toolName)
        val outputFile = File(toolDataPath + File.separator + "tool_scan_output.json")
        if (outputFile.exists()) {
            LogUtils.printLog("outputFile exist: $outputFile")
            val outputData = JsonUtil.to(outputFile.readText(), object : TypeReference<Map<String, Any>>() {})
            val defects = outputData["defects"]
            if (defects is List<*>) {
                LogUtils.printLog("defects is list, size: ${defects.size}")
                val languageSet = mutableSetOf<String>()
                defects.forEachIndexed { _, it ->
                    val defectStr = jacksonObjectMapper().writeValueAsString(it)
                    val defect = JsonUtil.to(defectStr, object : TypeReference<DefectsEntity>() {})
                    if (defect.language != null) {
                        languageSet.add(defect.language)
                    }
                }
                LogUtils.printLog("scc language set is: $languageSet")
                return Pair(true, languageSet)
            }
        } else {
            LogUtils.printErrorLog("codecc scc scan file not exist: ${outputFile.canonicalPath}")
            return Pair(false, emptySet())
        }
        return Pair(true, emptySet())
    }

    fun getCleanClocLangSet(toolName: String, streamName: String, dataRootPath: String, userId: String): Set<String> {
        val langSetPair = getClocLangSet(toolName, streamName, dataRootPath)
        val langSet = langSetPair.second
        // SCC 输出为空，返回空列表
        if (!langSetPair.first) {
            return emptySet()
        }

        val langSetFilter = CodeccSdkApi.getLangBaseData(userId).filter { baseData ->
            val langArr = JsonUtil.to(baseData.paramExtend2!!, object : TypeReference<List<String>>(){})

            // 如果有交集，则命中语言
            langArr.minus(langSet).size != langArr.size
        }.map { it.langFullKey }.toSet()

        if (langSetFilter.isNotEmpty()) {
            return langSetFilter
        }

        return mutableSetOf("OTHERS")
    }

    /**
     * 是否产出构件：bkcheck工具分析中间结果文件
     */
    fun bkcheckDebugOutput(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        if (atomContext.param.bkcheckDebug) {
            LogUtils.printLog("Building artifacts")
            val filePath = "${atomContext.param.codeccWorkspacePath}/bkcheck_data.db"
            if (!File(filePath).exists()) {
                LogUtils.printErrorLog("file is not found: $filePath")
                return
            }
            val password: String? = atomContext.getSensitiveConfParam(ComConstants.ZIP_FILE_PASSWORD)
            // 压缩再归档
            val zipFile: String? = FileUtil.zipWithPassword(
                needZipPath = filePath,
                toFilePath = atomContext.param.codeccWorkspacePath!!,
                password = password
            )
            if (zipFile == null) {
                LogUtils.printErrorLog("executed zipWithPassword failed! $filePath")
                return
            }
            atomContext.result.data["bkcheckDebug"] = ArtifactData(setOf(zipFile))
        }
    }
}
