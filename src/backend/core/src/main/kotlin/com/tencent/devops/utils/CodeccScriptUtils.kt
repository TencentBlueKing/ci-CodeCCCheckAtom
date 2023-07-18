package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCBusinessException
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.File

class CodeccScriptUtils : BaseApi() {

    private val CODECC_SCRIPT_NAME = CodeccConfigUtils.getPropConfig("codeccScriptFile")!!
    private val CODECC_PYTHON_NAME = "Python-3.5.1.tgz"
    private val CODECC_HOST = CodeccConfigUtils.getPropConfig("codeccHost")
    private val fileSizeUrl = "/ms/schedule/api/build/fs/download/fileSize"
    private val downloadUrl = "/ms/schedule/api/build/fs/download"

    fun downloadScriptFile(codeccExecuteConfig: CodeccExecuteConfig, codeccWorkspace: File): File {
        LogUtils.printLog("Download CodeCC script...")
        // 1) get file size
        val fileSizeParams = mapOf(
            "fileName" to CODECC_SCRIPT_NAME,
            "downloadType" to "BUILD_SCRIPT"
        )
        val fileSizeRequest = buildPost(fileSizeUrl, RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), JsonUtil.getObjectMapper().writeValueAsString(fileSizeParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$fileSizeUrl")
            .build()
        val fileSize = OkhttpUtils.doHttp(fileSizeRequest).use {
            val data = it.body.use { body -> body!!.string() }
            val jsonData = JsonUtil.getObjectMapper().readValue<Map<String, Any>>(data)
            if (jsonData["status"] != 0) {
                throw CodeCCBusinessException(
                    ErrorCode.CODECC_RETURN_STATUS_CODE_ERROR,
                    "get file size fail!: $jsonData",
                    arrayOf(
                        jsonData["status"]?.toString() ?: "",
                        jsonData["code"]?.toString() ?: "",
                        jsonData["msg"]?.toString() ?: "")
                )
            }
            val dataSize = jsonData["data"] as Int
            LogUtils.printLog("get CodeCC script file size success, size: $dataSize")
            dataSize
        }

        // 2) download
        val downloadParams = mapOf(
            "fileName" to CODECC_SCRIPT_NAME,
            "downloadType" to "BUILD_SCRIPT",
            "beginIndex" to "0",
            "btyeSize" to fileSize
        )
        val downloadRequest = buildPost(downloadUrl, RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), JsonUtil.getObjectMapper().writeValueAsString(downloadParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$downloadUrl")
            .build()
        OkhttpUtils.doHttp(downloadRequest).use {
            val data = it.body.use { body -> body!!.string() }
            LogUtils.printLog("Download CodeCC script successfully")
            val file = File(codeccWorkspace, CODECC_SCRIPT_NAME)
            file.writeText(data)
            return file
        }
    }

    fun downloadPython(codeccWorkspace: File): File? {
        LogUtils.printLog("Download python...")
        // 1) get file size
        val fileSizeParams = mapOf(
            "fileName" to CODECC_PYTHON_NAME,
            "downloadType" to "BUILD_SCRIPT"
        )
        val fileSizeRequest = buildPost(fileSizeUrl, RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), JsonUtil.getObjectMapper().writeValueAsString(fileSizeParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$fileSizeUrl")
            .build()
        val fileSize = OkhttpUtils.doHttp(fileSizeRequest).use {
            val data = it.body.use { body -> body!!.string() }
            val jsonData = JsonUtil.getObjectMapper().readValue<Map<String, Any>>(data)
            if (jsonData["status"] != 0) {
                throw CodeCCBusinessException(
                    ErrorCode.CODECC_RETURN_STATUS_CODE_ERROR,
                    "get file size fail!: $jsonData",
                    arrayOf(
                        jsonData["status"]?.toString() ?: "",
                        jsonData["code"]?.toString() ?: "",
                        jsonData["msg"]?.toString() ?: "")
                )
            }
            val dataSize = jsonData["data"] as Int
            LogUtils.printLog("get CodeCC python file size success, size: $dataSize")
            dataSize.toLong()
        }

        val pythonFile = File(codeccWorkspace, CODECC_PYTHON_NAME)

        if (pythonFile.length() != 0L && fileSize == pythonFile.length()) {
            LogUtils.printLog("python file is not change and do not download")
            return null
        }
        LogUtils.printLog("previous python file size is: ${pythonFile.length()}")

        // 2) download
        val downloadParams = mapOf(
            "fileName" to CODECC_PYTHON_NAME,
            "downloadType" to "BUILD_SCRIPT",
            "beginIndex" to "0",
            "btyeSize" to fileSize
        )
        val downloadRequest = buildPost(downloadUrl,
            RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(),
                JsonUtil.getObjectMapper().writeValueAsString(downloadParams)), mutableMapOf())
            .newBuilder()
            .url("$CODECC_HOST$downloadUrl")
            .build()
        OkhttpUtils.doHttp(downloadRequest).use {
            it.body.use { body ->
                LogUtils.printLog("[init] Download CodeCC python successfully")
                codeccWorkspace.mkdirs()
                pythonFile.writeBytes(body!!.bytes())
            }

            return pythonFile
        }
    }
}
