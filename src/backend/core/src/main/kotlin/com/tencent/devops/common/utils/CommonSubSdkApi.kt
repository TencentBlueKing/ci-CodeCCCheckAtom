package com.tencent.devops.common.utils


import com.tencent.devops.common.api.SubSdkApi

class CommonSubSdkApi : SubSdkApi {
    private val updatePath: String = "/ms/task/api/build/task"

    override fun getUrl(): String {
        return updatePath
    }

    // 添加工蜂扫描参数，普通任务返回空
    override fun addInputParam(codeccOpenSourceJson: String): Map<String, String> {
        return mapOf()
    }
}