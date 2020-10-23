package com.tencent.devops.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import com.tencent.devops.pojo.report.CodeccCallback

class CodeccReportApi : BaseApi() {

    fun getCodeccReport(buildId: String): Result<CodeccCallback> {
        val path = "/ms/plugin/api/build/codecc/report/builds/$buildId"
        val request = buildGet(path)
        val responseContent = request(request, "获取codecc报告失败")
        return JsonUtil.fromJson(responseContent, object : TypeReference<Result<CodeccCallback>>(){})
    }
}
