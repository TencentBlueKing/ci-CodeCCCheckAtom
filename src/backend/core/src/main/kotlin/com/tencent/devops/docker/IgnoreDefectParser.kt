package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.IgnoreDefectSubInfo
import java.io.File

object IgnoreDefectParser {

    @Volatile private var ignoreFileFlag : Boolean = false

    private var ignoreDefectMap = mutableMapOf<String, List<IgnoreDefectSubInfo>>()

    /**
     * 初始化注释告警忽略映射,这里几个要求
     * 1. 全局只配置一次
     * 2. 如果入参filename不为空，则取本地的文件
     * 3. 如果入参filename为空，则拉取后端信息
     */
    fun getIgnoreDefectInfo(
        ignoreFileName : String?,
        taskId: Long,
        commandParam: CommandParam?,
        streamName: String?) {
        if (ignoreFileFlag) {
            return
        }
        try {
            if (!ignoreDefectMap.isNullOrEmpty()) {
                return
            }
            synchronized(this) {
                if (!ignoreDefectMap.isNullOrEmpty()) {
                    return
                }
                val cachedIgnoreDefectMap = try {
                    CodeccSdkApi.getIgnoreDefectInfo(taskId)?.ignoreDefectMap?: mapOf()
                } catch (e : Exception) {
                    mapOf<String, List<IgnoreDefectSubInfo>>()
                }
                /**
                 * 分为几个场景：
                 * 1.忽略文件名不为空，即SCC正常扫描完成
                 *   1.1 传入的文件存在，则读取文件，并且和缓存的键值合并在一起，作为总体的键值进行处理
                 *   1.2 传入的文件不存在，则读取缓存的信息，作为总体的键值进行处理
                 * 2.忽略的文件名为空，即SCC工具没有执行
                 *   2.1 读取缓存的信息，作为总体的键值进行处理
                 */
                if(!ignoreFileName.isNullOrBlank()) {
                    val ignoreFile = File(ignoreFileName)
                    if (ignoreFile.exists()) {
                        ignoreDefectMap.putAll(cachedIgnoreDefectMap)
                        ignoreDefectMap.putAll(jacksonObjectMapper().readValue(ignoreFile.readText(), object : TypeReference<Map<String, List<IgnoreDefectSubInfo>>>(){}))
                        if (null != commandParam && !streamName.isNullOrBlank()) {
                            CodeccWeb.upload(
                                landunParam = commandParam.landunParam,
                                filePath = ignoreFileName,
                                resultName = streamName + "_" + ToolConstants.SCC.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_ignore.json",
                                uploadType = "SCM_JSON",
                                toolName = ToolConstants.SCC
                            )
                        }
                    } else {
                        ignoreDefectMap.putAll(cachedIgnoreDefectMap)
                    }
                } else {
                    ignoreDefectMap.putAll(cachedIgnoreDefectMap)
                }
            }
        } catch (t : Throwable) {
            t.printStackTrace()
            LogUtils.printLog("process ignore defect file fail!")
        } finally {
            LogUtils.printLog("ignore defect map content: $ignoreDefectMap")
            ignoreFileFlag = true
        }
    }

    /**
     * 配置忽略文件标识，作为兜底
     */
    fun resetIgnoreDefectFlag() {
        if (!ignoreFileFlag) {
            ignoreFileFlag = true
        }
    }

    /**
     * 获取对应的忽略注释信息
     */
    fun getIgnoreDefectMapByFilePath(filePath : String) : List<IgnoreDefectSubInfo>? {
        return ignoreDefectMap[filePath]
    }

    /**
     * 是否能够继续处理
     */
    fun continueProcess() : Boolean {
        return ignoreFileFlag
    }

}
