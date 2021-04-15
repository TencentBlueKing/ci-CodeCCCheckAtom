package com.tencent.devops.api

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccDependentException
import okhttp3.Protocol
import okhttp3.Response

class CodeccApi : BaseApi() {

    fun saveTask(projectId: String, pipelineId: String, buildId: String): Result<String> {
        try {
            val path = "/ms/plugin/api/build/codecc/save/task/$projectId/$pipelineId/$buildId"
            val request = buildPost(path)
            val responseContent = request(request, "保存CodeCC原子信息失败")
            return Result(responseContent)
        } catch (e: Exception) {
            println("写入codecc任务失败: ${e.message}")
        }
        return Result("")
    }

    fun downloadTool(tool: String, osType: OSType, fileMd5: String, is32Bit: Boolean): Response {
        val path = "/ms/plugin/api/build/codecc/$tool?osType=${osType.name}&fileMd5=$fileMd5&is32Bit=$is32Bit"
        val request = buildGet(path)

        val response = OkhttpUtils.doHttp(request)
        val responseCode = response.code()
        if (responseCode == 304) {
            return Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .code(304).build()
        }
        if (!response.isSuccessful) {
            throw CodeccDependentException("下载Codecc的 $tool 工具失败: $responseCode, ${response.body()!!.string()}")
        }
        return response
    }

    fun downloadToolScript(osType: OSType, fileMd5: String): Response {
        val path = "/ms/plugin/api/build/codecc/tools/script?osType=${osType.name}&fileMd5=$fileMd5"
        val request = buildGet(path)
        val response = OkhttpUtils.doHttp(request)
        if (response.code() == 304) {
            return Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("")
                .code(304).build()
        }

        if (!response.isSuccessful) {
            throw CodeccDependentException("下载codecc的多工具执行脚本失败")
        }
        return response
    }
}
