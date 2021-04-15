package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.exception.CodeccDependentException
import okhttp3.MediaType
import okhttp3.RequestBody
import java.io.File

class CodeccScriptUtils : BaseApi() {

    private val CODECC_SCRIPT_NAME = CodeccConfigUtils.getPropConfig("codeccScriptFile")!!
    private val CODECC_HOST = CodeccConfigUtils.getPropConfig("codeccHost")
    private val fileSizeUrl = "/ms/schedule/api/build/fs/download/fileSize"
    private val downloadUrl = "/ms/schedule/api/build/fs/download"

    fun downloadScriptFile(codeccExecuteConfig: CodeccExecuteConfig, codeccWorkspace: File): File {
        println("[初始化] 下载CodeCC script...")
        // 1) get file size
        val fileSizeParams = mapOf(
            "fileName" to CODECC_SCRIPT_NAME,
            "downloadType" to "BUILD_SCRIPT"
        )
        val fileSizeRequest = buildPost(fileSizeUrl, RequestBody.create(MediaType.parse("application/json; charset=utf-8"), JsonUtil.getObjectMapper().writeValueAsString(fileSizeParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$fileSizeUrl")
            .build()
        val fileSize = OkhttpUtils.doHttp(fileSizeRequest).use {
            val data = it.body()!!.string()
            val jsonData = JsonUtil.getObjectMapper().readValue<Map<String, Any>>(data)
            if (jsonData["status"] != 0) {
                throw CodeccDependentException("get file size fail!: $jsonData")
            }
            val dataSize = jsonData["data"] as Int
            println("[初始化] get CodeCC script file size success, size: $dataSize")
            dataSize
        }

        // 2) download
        val downloadParams = mapOf(
            "fileName" to CODECC_SCRIPT_NAME,
            "downloadType" to "BUILD_SCRIPT",
            "beginIndex" to "0",
            "btyeSize" to fileSize
        )
        val downloadRequest = buildPost(downloadUrl, RequestBody.create(MediaType.parse("application/json; charset=utf-8"), JsonUtil.getObjectMapper().writeValueAsString(downloadParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$downloadUrl")
            .build()
        OkhttpUtils.doHttp(downloadRequest).use {
            val data = it.body()!!.string()
            print("[初始化] 下载CodeCC script成功,")
            val file = File(codeccWorkspace, CODECC_SCRIPT_NAME)
            file.writeText(data)
            return file
        }
    }

}
