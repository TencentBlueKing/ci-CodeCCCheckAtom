package com.tencent.devops.api

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.plugin.pojo.Result
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.pojo.repo.Repository
import com.tencent.devops.pojo.repo.RepositoryConfig

class RepositoryApi : BaseApi() {

    fun get(repositoryConfig: RepositoryConfig): Result<Repository> {
        val repositoryId = repositoryConfig.getURLEncodeRepositoryId()
        val name = repositoryConfig.repositoryType.name
        val path = "/ms/repository/api/build/repositories?repositoryId=$repositoryId&repositoryType=$name"
        val request = buildGet(path)
        val responseContent = request(request, "获取代码库失败")
        return JsonUtil.to(responseContent, object : TypeReference<Result<Repository>>() {})
    }
}
