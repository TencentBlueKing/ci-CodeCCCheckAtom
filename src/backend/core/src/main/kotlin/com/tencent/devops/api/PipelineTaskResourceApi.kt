package com.tencent.devops.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.pojo.process.PipelineBuildTaskInfo

class PipelineTaskResourceApi : BaseApi() {

    fun getAllBuildTask(): Result<List<PipelineBuildTaskInfo>> {
        val path = "/ms/process/api/build/task/getAllBuildTask"
        val request = buildGet(path)
        val responseContent = request(request, "Failed to get build machine task details")
        return JsonUtil.to(responseContent, object : TypeReference<Result<List<PipelineBuildTaskInfo>>>() {})
    }
}
