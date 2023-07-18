package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import java.io.File

object OCHeaderFileParser {

    @Volatile private var ocHeadFileFlag : Boolean = false

    private var ocHeadFileSet = mutableSetOf<String>()

    fun getOcHeadFileInfo(
        ocHeadFileName: String?,
        taskId: Long,
        commandParam: CommandParam?,
        streamName: String?
    ) {
        if (ocHeadFileFlag) {
            return
        }
        try {
            if (!ocHeadFileSet.isNullOrEmpty()) {
                return
            }
            synchronized(this) {
                if (!ocHeadFileSet.isNullOrEmpty()) {
                    return
                }
                val cachedHeadFileInfo = try {
                    CodeccSdkApi.getHeadFileInfo(taskId)?.headFileSet?: setOf()
                } catch (e : Exception) {
                    setOf()
                }
                /**
                 * 几个场景：
                 * 1.头文件文件名不为空，即SCC正常扫描完成
                 *   1.1 传入的文件存在，则读取文件，并且和缓存清单合在一起，作为总体清单
                 *   1.2 传入的文件不存在，则读取缓存的信息，作为总体的清单进行处理
                 * 2.忽略的文件名为空，即SCC工具没有执行
                 *   2.1 读取缓存的信息，作为总体的清单进行处理
                 */
                if (!ocHeadFileName.isNullOrBlank()) {
                    val ocHeadOutputFile = File(ocHeadFileName)
                    if(ocHeadOutputFile.exists()) {
                        ocHeadFileSet.addAll(cachedHeadFileInfo)
                        ocHeadFileSet.addAll(jacksonObjectMapper().readValue(ocHeadOutputFile.readText(), object : TypeReference<Set<String>>(){}))
                        if (null != commandParam && !streamName.isNullOrBlank()) {
                            CodeccWeb.upload(
                                landunParam = commandParam.landunParam,
                                filePath = ocHeadFileName,
                                resultName = streamName + "_" + ToolConstants.SCC.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_headfile.json",
                                uploadType = "SCM_JSON",
                                toolName = ToolConstants.SCC
                            )
                        }
                    } else {
                        ocHeadFileSet.addAll(cachedHeadFileInfo)
                    }
                }
                if (ocHeadFileName.isNullOrBlank()) {
                    ocHeadFileSet.addAll(cachedHeadFileInfo)
                }
            }
        } catch (t : Throwable) {
            LogUtils.printLog("process ignore defect file fail!")
        } finally {
            LogUtils.printLog("head file defect map content: $ocHeadFileSet")
            ocHeadFileFlag = true
        }
    }

    fun ocHeadFileContains(filePath: String): Boolean {
        return ocHeadFileSet.contains(filePath)
    }
}
